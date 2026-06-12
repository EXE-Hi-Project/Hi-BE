package com.hi.api.service;

import com.hi.api.model.CoupleQuestionSession;
import com.hi.api.model.DailyQuestion;
import com.hi.api.model.User;
import com.hi.api.repository.CoupleQuestionSessionRepository;
import com.hi.api.repository.DailyQuestionRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CoupleQuestionService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CoupleQuestionSessionRepository sessionRepository;
    private final DailyQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final PartnerAccessService partnerAccessService;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;
    private final SubscriptionAccessService subscriptionAccessService;

    public CoupleQuestionService(CoupleQuestionSessionRepository sessionRepository,
                                 DailyQuestionRepository questionRepository,
                                 UserRepository userRepository,
                                 PartnerAccessService partnerAccessService,
                                 NotificationService notificationService,
                                 MongoTemplate mongoTemplate,
                                 SubscriptionAccessService subscriptionAccessService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.partnerAccessService = partnerAccessService;
        this.notificationService = notificationService;
        this.mongoTemplate = mongoTemplate;
        this.subscriptionAccessService = subscriptionAccessService;
    }

    public Map<String, Object> getToday(String userId) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        if (Boolean.FALSE.equals(partnerAccessService.notificationPreferences(user).getDailyQuestionsEnabled())) {
            throw new IllegalArgumentException("Bạn đang tắt Câu hỏi của chúng mình");
        }
        CoupleQuestionSession session = getOrCreate(user, partner, LocalDate.now(APP_ZONE));
        return sessionResponse(session, userId, true);
    }

    public CoupleQuestionSession getOrCreate(User user, User partner, LocalDate date) {
        subscriptionAccessService.requireCouplePremium(user, partner);
        String pairKey = partnerAccessService.pairKey(user.getId(), partner.getId());
        return sessionRepository.findByPairKeyAndQuestionDate(pairKey, date).orElseGet(() -> {
            List<DailyQuestion> questions = questionRepository.findByActiveTrueOrderByDisplayOrderAsc();
            if (questions.isEmpty()) {
                throw new IllegalStateException("Kho câu hỏi chưa được khởi tạo");
            }
            int index = Math.floorMod((int) date.toEpochDay(), questions.size());
            DailyQuestion selected = questions.get(index);
            CoupleQuestionSession session = new CoupleQuestionSession();
            session.setPairKey(pairKey);
            session.setQuestionDate(date);
            session.setQuestionId(selected.getId());
            session.setQuestionText(selected.getPrompt());
            session.setCategory(selected.getCategory());
            session.setParticipantIds(List.of(user.getId(), partner.getId()));
            try {
                CoupleQuestionSession saved = sessionRepository.save(session);
                notifyNewQuestion(user, partner, date);
                return saved;
            } catch (DuplicateKeyException duplicate) {
                return sessionRepository.findByPairKeyAndQuestionDate(pairKey, date).orElseThrow();
            }
        });
    }

    public Map<String, Object> answerToday(String userId, String content) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        CoupleQuestionSession session = getOrCreate(user, partner, LocalDate.now(APP_ZONE));
        CoupleQuestionSession before = sessionRepository.findById(session.getId()).orElseThrow();
        boolean partnerAlreadyAnswered = before.getAnswers() != null && before.getAnswers().containsKey(partner.getId());

        Instant now = Instant.now();
        CoupleQuestionSession.Answer answer = new CoupleQuestionSession.Answer();
        answer.setUserId(userId);
        answer.setContent(content.trim());
        answer.setAnsweredAt(before.getAnswers() != null && before.getAnswers().get(userId) != null
                ? before.getAnswers().get(userId).getAnsweredAt()
                : now);
        answer.setUpdatedAt(now);

        Query query = Query.query(Criteria.where("_id").is(session.getId())
                .and("participantIds").is(userId)
                .and("unlockedAt").is(null));
        Update update = new Update()
                .set("answers." + userId, answer)
                .set("updatedAt", now);
        CoupleQuestionSession updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), CoupleQuestionSession.class);
        if (updated == null) {
            throw new IllegalArgumentException("Câu trả lời đã được mở khóa và không thể chỉnh sửa");
        }

        if (!partnerAlreadyAnswered) {
            notifyPartnerAnswered(partner, user, updated);
        }
        if (updated.getAnswers() != null && updated.getAnswers().keySet().containsAll(updated.getParticipantIds())) {
            unlock(updated);
            updated = sessionRepository.findById(updated.getId()).orElse(updated);
        }
        return sessionResponse(updated, userId, true);
    }

    public Map<String, Object> skipToday(String userId) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        CoupleQuestionSession session = getOrCreate(user, partner, LocalDate.now(APP_ZONE));
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(session.getId()).and("participantIds").is(userId)),
                new Update().addToSet("skippedBy", userId),
                CoupleQuestionSession.class);
        return sessionResponse(sessionRepository.findById(session.getId()).orElseThrow(), userId, true);
    }

    public Map<String, Object> getSession(String userId, String sessionId) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        subscriptionAccessService.requireCouplePremium(user, partner);
        CoupleQuestionSession session = sessionRepository.findByIdAndParticipantIdsContaining(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy câu hỏi"));
        String otherId = session.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
        boolean activePair = partnerAccessService.isActivePair(userId, otherId);
        return sessionResponse(session, userId, activePair);
    }

    public Map<String, Object> history(String userId, int page, int limit) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        subscriptionAccessService.requireCouplePremium(user, partner);
        int safeLimit = Math.max(1, Math.min(limit, 31));
        Page<CoupleQuestionSession> result = sessionRepository
                .findByParticipantIdsContainingOrderByQuestionDateDesc(userId, PageRequest.of(Math.max(0, page), safeLimit));
        List<Map<String, Object>> items = result.getContent().stream()
                .map(session -> {
                    String otherId = session.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
                    return sessionResponse(session, userId, partnerAccessService.isActivePair(userId, otherId));
                })
                .toList();
        return Map.of(
                "items", items,
                "page", result.getNumber(),
                "limit", result.getSize(),
                "total", result.getTotalElements(),
                "hasMore", result.hasNext()
        );
    }

    public Map<String, Object> addMessage(String userId, String sessionId, String content) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        subscriptionAccessService.requireCouplePremium(user, partner);
        CoupleQuestionSession session = sessionRepository.findByIdAndParticipantIdsContaining(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy câu hỏi"));
        if (!session.getPairKey().equals(partnerAccessService.pairKey(userId, partner.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền nhắn trong cuộc trò chuyện này");
        }
        if (session.getUnlockedAt() == null) {
            throw new IllegalArgumentException("Hai bạn cần trả lời trước khi trò chuyện");
        }
        CoupleQuestionSession.Message message = new CoupleQuestionSession.Message();
        message.setId(UUID.randomUUID().toString());
        message.setUserId(userId);
        message.setContent(content.trim());
        message.setCreatedAt(Instant.now());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(sessionId).and("unlockedAt").ne(null)),
                new Update().push("messages", message),
                CoupleQuestionSession.class);
        return sessionResponse(sessionRepository.findById(sessionId).orElseThrow(), userId, true);
    }

    private void unlock(CoupleQuestionSession session) {
        CoupleQuestionSession unlocked = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(session.getId()).and("unlockedAt").is(null)),
                new Update().set("unlockedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                CoupleQuestionSession.class);
        if (unlocked == null) return;
        for (String participantId : unlocked.getParticipantIds()) {
            notificationService.createIdempotentNotification(
                    participantId,
                    "COUPLE_QUESTION_UNLOCKED",
                    "Hai câu trả lời đã được mở",
                    "Hai bạn đã cùng hoàn thành câu hỏi hôm nay.",
                    partnerHubUrl("today"),
                    "COUPLE_QUESTION_UNLOCKED:" + unlocked.getId() + ":" + participantId,
                    Map.of("sessionId", unlocked.getId(), "date", unlocked.getQuestionDate().toString())
            );
        }
    }

    private void notifyNewQuestion(User first, User second, LocalDate date) {
        for (User participant : List.of(first, second)) {
            if (Boolean.FALSE.equals(partnerAccessService.notificationPreferences(participant).getDailyQuestionsEnabled())) continue;
            notificationService.createIdempotentNotification(
                    participant.getId(),
                    "COUPLE_DAILY_QUESTION",
                    "Câu hỏi mới của hai bạn",
                    "Một câu hỏi nhỏ đang chờ hai bạn trả lời hôm nay.",
                    partnerHubUrl("today"),
                    "COUPLE_DAILY_QUESTION:" + participant.getId() + ":" + date,
                    Map.of("date", date.toString())
            );
        }
    }

    private void notifyPartnerAnswered(User recipient, User actor, CoupleQuestionSession session) {
        if (Boolean.FALSE.equals(partnerAccessService.notificationPreferences(recipient).getDailyQuestionsEnabled())) return;
        String name = actor.getName() == null || actor.getName().isBlank() ? "Người ấy" : actor.getName();
        notificationService.createIdempotentNotification(
                recipient.getId(),
                "PARTNER_ANSWERED_DAILY_QUESTION",
                name + " đã trả lời",
                "Đến lượt bạn trả lời để cùng mở câu trả lời hôm nay.",
                partnerHubUrl("today"),
                "PARTNER_ANSWERED:" + session.getId() + ":" + recipient.getId(),
                Map.of("sessionId", session.getId())
        );
    }

    private String partnerHubUrl(String tab) {
        return "/settings/notifications?tab=" + tab;
    }

    private Map<String, Object> sessionResponse(CoupleQuestionSession session, String userId, boolean activePair) {
        CoupleQuestionSession.Answer myAnswer = session.getAnswers() != null ? session.getAnswers().get(userId) : null;
        String partnerId = session.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
        CoupleQuestionSession.Answer partnerAnswer = session.getAnswers() != null ? session.getAnswers().get(partnerId) : null;
        boolean unlocked = session.getUnlockedAt() != null;
        boolean canSeePartner = activePair && unlocked;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("_id", session.getId());
        response.put("questionDate", session.getQuestionDate());
        response.put("questionText", session.getQuestionText());
        response.put("category", session.getCategory());
        response.put("status", status(session, userId));
        response.put("activePair", activePair);
        response.put("unlocked", unlocked);
        response.put("myAnswer", myAnswer);
        response.put("partnerAnswer", canSeePartner ? partnerAnswer : null);
        response.put("partnerAnswered", activePair && partnerAnswer != null);
        response.put("messages", canSeePartner
                ? session.getMessages()
                : session.getMessages() == null ? List.of() : session.getMessages().stream()
                        .filter(message -> userId.equals(message.getUserId()))
                        .toList());
        response.put("skipped", session.getSkippedBy() != null && session.getSkippedBy().contains(userId));
        return response;
    }

    private String status(CoupleQuestionSession session, String userId) {
        if (session.getUnlockedAt() != null) return "UNLOCKED";
        if (session.getSkippedBy() != null && session.getSkippedBy().contains(userId)) return "SKIPPED";
        if (session.getAnswers() != null && session.getAnswers().containsKey(userId)) return "WAITING_PARTNER";
        return "UNANSWERED";
    }
}
