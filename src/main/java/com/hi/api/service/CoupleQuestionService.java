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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hi.api.service.EmailService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class CoupleQuestionService {

    private static final Logger log = LoggerFactory.getLogger(CoupleQuestionService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CoupleQuestionSessionRepository sessionRepository;
    private final DailyQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final PartnerAccessService partnerAccessService;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;
    private final SubscriptionAccessService subscriptionAccessService;
    private final RealtimeEventService realtimeEventService;
    private final EmailService emailService;

    public CoupleQuestionService(CoupleQuestionSessionRepository sessionRepository,
                                 DailyQuestionRepository questionRepository,
                                 UserRepository userRepository,
                                 PartnerAccessService partnerAccessService,
                                 NotificationService notificationService,
                                 MongoTemplate mongoTemplate,
                                 SubscriptionAccessService subscriptionAccessService,
                                 RealtimeEventService realtimeEventService,
                                 EmailService emailService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.partnerAccessService = partnerAccessService;
        this.notificationService = notificationService;
        this.mongoTemplate = mongoTemplate;
        this.subscriptionAccessService = subscriptionAccessService;
        this.realtimeEventService = realtimeEventService;
        this.emailService = emailService;
    }

    private boolean isSessionFinished(CoupleQuestionSession session) {
        if (session == null) return false;
        List<String> participants = session.getParticipantIds();
        return participants != null
                && participants.size() == 2
                && participants.stream().allMatch(participantId -> hasValidAnswer(session, participantId));
    }

    private boolean shouldUnlock(CoupleQuestionSession session) {
        return session != null && session.getUnlockedAt() == null && isSessionFinished(session);
    }

    private CoupleQuestionSession getActiveOrCreateToday(User user, User partner) {
        subscriptionAccessService.requireCouplePremium(user, partner);
        return getOrCreate(user, partner, LocalDate.now(APP_ZONE));
    }

    public Map<String, Object> getToday(String userId) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        if (Boolean.FALSE.equals(partnerAccessService.notificationPreferences(user).getDailyQuestionsEnabled())) {
            throw new IllegalArgumentException("Bạn đang tắt Câu hỏi của chúng mình");
        }
        CoupleQuestionSession session = getActiveOrCreateToday(user, partner);
        return sessionResponse(session, userId, true);
    }

    public CoupleQuestionSession getOrCreate(User user, User partner, LocalDate date) {
        subscriptionAccessService.requireCouplePremium(user, partner);
        String pairKey = partnerAccessService.pairKey(user.getId(), partner.getId());
        Optional<CoupleQuestionSession> unfinished = findOldestUnfinished(pairKey, date);
        if (unfinished.isPresent()) {
            return normalizeLegacyIncompleteSession(unfinished.get());
        }
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
                emitQuestionUpdate(saved);
                return saved;
            } catch (DuplicateKeyException duplicate) {
                return sessionRepository.findByPairKeyAndQuestionDate(pairKey, date).orElseThrow();
            }
        });
    }

    public Map<String, Object> answerToday(String userId, String content) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        CoupleQuestionSession session = getActiveOrCreateToday(user, partner);
        CoupleQuestionSession before = sessionRepository.findById(session.getId()).orElseThrow();
        boolean userAlreadyAnswered = before.getAnswers() != null && before.getAnswers().containsKey(userId);
        boolean partnerAlreadyAnswered = before.getAnswers() != null && before.getAnswers().containsKey(partner.getId());

        Instant now = Instant.now();
        CoupleQuestionSession.Answer answer = new CoupleQuestionSession.Answer();
        answer.setUserId(userId);
        answer.setContent(content.trim());
        answer.setAnsweredAt(userAlreadyAnswered
                ? before.getAnswers().get(userId).getAnsweredAt()
                : now);
        answer.setUpdatedAt(now);

        Query query = Query.query(Criteria.where("id").is(session.getId())
                .and("participantIds").is(userId));
        Update update = new Update()
                .set("answers." + userId, answer)
                .set("updatedAt", now)
                .pull("skippedBy", userId);
        CoupleQuestionSession updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), CoupleQuestionSession.class);
        if (updated == null) {
            throw new IllegalArgumentException("Không tìm thấy câu hỏi để cập nhật");
        }

        if (!userAlreadyAnswered && !partnerAlreadyAnswered) {
            notifyPartnerAnswered(partner, user, updated);
        }
        if (shouldUnlock(updated)) {
            unlock(updated);
            updated = sessionRepository.findById(updated.getId()).orElse(updated);
        }
        emitQuestionUpdate(updated);

        // Send email notification to partner
        if (!userAlreadyAnswered) {
            sendQuestionEmail(user, partner, updated, QuestionEmailEvent.ANSWER, null);
        } else {
            sendQuestionEmail(user, partner, updated, QuestionEmailEvent.EDIT, null);
        }

        return sessionResponse(updated, userId, true);
    }

    public Map<String, Object> skipToday(String userId) {
        throw new IllegalStateException("Cả hai bạn cần trả lời để mở câu hỏi tiếp theo");
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

    public Map<String, Object> answerSession(String userId, String sessionId, String content) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        subscriptionAccessService.requireCouplePremium(user, partner);

        CoupleQuestionSession session = sessionRepository.findByIdAndParticipantIdsContaining(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy câu hỏi"));

        if (!session.getPairKey().equals(partnerAccessService.pairKey(userId, partner.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền trả lời câu hỏi này");
        }

        CoupleQuestionSession before = sessionRepository.findById(session.getId()).orElseThrow();
        boolean userAlreadyAnswered = before.getAnswers() != null && before.getAnswers().containsKey(userId);
        boolean partnerAlreadyAnswered = before.getAnswers() != null && before.getAnswers().containsKey(partner.getId());

        Instant now = Instant.now();
        CoupleQuestionSession.Answer answer = new CoupleQuestionSession.Answer();
        answer.setUserId(userId);
        answer.setContent(content.trim());
        answer.setAnsweredAt(userAlreadyAnswered
                ? before.getAnswers().get(userId).getAnsweredAt()
                : now);
        answer.setUpdatedAt(now);

        Query query = Query.query(Criteria.where("id").is(session.getId())
                .and("participantIds").is(userId));
        Update update = new Update()
                .set("answers." + userId, answer)
                .set("updatedAt", now)
                .pull("skippedBy", userId);
        CoupleQuestionSession updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), CoupleQuestionSession.class);
        if (updated == null) {
            throw new IllegalArgumentException("Không tìm thấy câu hỏi để cập nhật");
        }

        if (!userAlreadyAnswered && !partnerAlreadyAnswered) {
            notifyPartnerAnswered(partner, user, updated);
        }
        if (shouldUnlock(updated)) {
            unlock(updated);
            updated = sessionRepository.findById(updated.getId()).orElse(updated);
        }
        emitQuestionUpdate(updated);

        // Send email notification to partner
        if (!userAlreadyAnswered) {
            sendQuestionEmail(user, partner, updated, QuestionEmailEvent.ANSWER, null);
        } else {
            sendQuestionEmail(user, partner, updated, QuestionEmailEvent.EDIT, null);
        }

        String otherId = updated.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
        boolean activePair = partnerAccessService.isActivePair(userId, otherId);
        return sessionResponse(updated, userId, activePair);
    }

    public Map<String, Object> history(String userId, int page, int limit) {
        return history(userId, page, limit, null, null);
    }

    public Map<String, Object> history(String userId, int page, int limit, LocalDate from, LocalDate to) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        subscriptionAccessService.requireCouplePremium(user, partner);
        int safePage = Math.max(0, page);
        int safeLimit = Math.max(1, Math.min(limit, 62));
        LocalDate safeFrom = from;
        LocalDate safeTo = to;
        if (safeFrom != null && safeTo != null && safeFrom.isAfter(safeTo)) {
            safeFrom = to;
            safeTo = from;
        }

        Query query = Query.query(historyCriteria(userId, safeFrom, safeTo))
                .with(Sort.by(Sort.Order.desc("questionDate")))
                .skip((long) safePage * safeLimit)
                .limit(safeLimit);
        List<CoupleQuestionSession> sessions = mongoTemplate.find(query, CoupleQuestionSession.class);
        long total = mongoTemplate.count(Query.query(historyCriteria(userId, safeFrom, safeTo)), CoupleQuestionSession.class);
        List<Map<String, Object>> items = sessions.stream()
                .map(session -> {
                    String otherId = session.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
                    return sessionResponse(session, userId, partnerAccessService.isActivePair(userId, otherId));
                })
                .toList();
        return Map.of(
                "items", items,
                "page", safePage,
                "limit", safeLimit,
                "total", total,
                "hasMore", ((long) (safePage + 1) * safeLimit) < total
        );
    }

    private Criteria historyCriteria(String userId, LocalDate from, LocalDate to) {
        Criteria criteria = Criteria.where("participantIds").is(userId);
        if (from != null && to != null) {
            criteria = criteria.and("questionDate").gte(from).lte(to);
        } else if (from != null) {
            criteria = criteria.and("questionDate").gte(from);
        } else if (to != null) {
            criteria = criteria.and("questionDate").lte(to);
        }
        return criteria;
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
        if (!isSessionFinished(session)) {
            throw new IllegalArgumentException("Hai bạn cần trả lời trước khi trò chuyện");
        }
        CoupleQuestionSession.Message message = new CoupleQuestionSession.Message();
        message.setId(UUID.randomUUID().toString());
        message.setUserId(userId);
        message.setContent(content.trim());
        message.setCreatedAt(Instant.now());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(sessionId).and("unlockedAt").ne(null)),
                new Update().push("messages", message),
                CoupleQuestionSession.class);
        CoupleQuestionSession updated = sessionRepository.findById(sessionId).orElseThrow();
        for (String participantId : updated.getParticipantIds()) {
            realtimeEventService.sendPartner(participantId, "partner.question.message.created", Map.of(
                    "sessionId", sessionId,
                    "message", message
            ));
        }
        emitQuestionUpdate(updated);

        sendQuestionEmail(user, partner, updated, QuestionEmailEvent.COMMENT, message.getContent());

        return sessionResponse(updated, userId, true);
    }

    private void unlock(CoupleQuestionSession session) {
        CoupleQuestionSession unlocked = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(session.getId()).and("unlockedAt").is(null)),
                new Update().set("unlockedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true),
                CoupleQuestionSession.class);
        if (unlocked == null) return;
        emitQuestionUpdate(unlocked);
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

    private void emitQuestionUpdate(CoupleQuestionSession session) {
        Map<String, Object> data = Map.of(
                "sessionId", session.getId(),
                "questionDate", session.getQuestionDate(),
                "unlocked", session.getUnlockedAt() != null
        );
        for (String participantId : session.getParticipantIds()) {
            realtimeEventService.sendPartner(participantId, "partner.question.updated", data);
        }
    }

    private Map<String, Object> sessionResponse(CoupleQuestionSession session, String userId, boolean activePair) {
        CoupleQuestionSession.Answer myAnswer = session.getAnswers() != null ? session.getAnswers().get(userId) : null;
        String partnerId = session.getParticipantIds().stream().filter(id -> !id.equals(userId)).findFirst().orElse(null);
        CoupleQuestionSession.Answer partnerAnswer = session.getAnswers() != null ? session.getAnswers().get(partnerId) : null;
        boolean unlocked = isSessionFinished(session);
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

    private void sendQuestionEmail(User actor, User recipient, CoupleQuestionSession session,
                                   QuestionEmailEvent eventType, String commentContent) {
        try {
            User.NotificationPreferences prefs = recipient.getNotificationPreferences();
            if (prefs == null) prefs = new User.NotificationPreferences();
            if (!Boolean.TRUE.equals(prefs.getEmailEnabled())) return;

            String recipientName = recipient.getName() != null && !recipient.getName().isBlank() ? recipient.getName() : "bạn";
            String actorName = actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : "Người ấy";
            String questionText = session.getQuestionText();

            switch (eventType) {
                case ANSWER -> {
                    if (!Boolean.TRUE.equals(prefs.getCoupleQuestionAnswerEmailEnabled())) return;
                    emailService.sendCoupleQuestionAnswerEmail(recipient.getEmail(), recipientName, actorName, questionText);
                }
                case EDIT -> {
                    if (!Boolean.TRUE.equals(prefs.getCoupleQuestionEditEmailEnabled())) return;
                    emailService.sendCoupleQuestionEditEmail(recipient.getEmail(), recipientName, actorName, questionText);
                }
                case COMMENT -> {
                    if (!Boolean.TRUE.equals(prefs.getCoupleQuestionCommentEmailEnabled())) return;
                    emailService.sendCoupleQuestionCommentEmail(
                            recipient.getEmail(), recipientName, actorName, questionText, commentContent);
                }
            }
        } catch (Exception e) {
            log.warn("[COUPLE_QUESTION_EMAIL] Failed to send {} email to {}: {}", eventType, recipient.getEmail(), e.getMessage());
        }
    }

    private enum QuestionEmailEvent {
        ANSWER,
        EDIT,
        COMMENT
    }

    private String status(CoupleQuestionSession session, String userId) {
        if (isSessionFinished(session)) return "UNLOCKED";
        if (hasValidAnswer(session, userId)) return "WAITING_PARTNER";
        return "UNANSWERED";
    }

    private Optional<CoupleQuestionSession> findOldestUnfinished(String pairKey, LocalDate throughDate) {
        List<CoupleQuestionSession> sessions = Optional
                .ofNullable(sessionRepository.findByPairKeyOrderByQuestionDateAsc(pairKey))
                .orElseGet(List::of);
        return sessions.stream()
                .filter(session -> session.getQuestionDate() != null && !session.getQuestionDate().isAfter(throughDate))
                .filter(session -> !isSessionFinished(session))
                .findFirst();
    }

    private CoupleQuestionSession normalizeLegacyIncompleteSession(CoupleQuestionSession session) {
        if (session.getUnlockedAt() == null) return session;
        session.setUnlockedAt(null);
        return sessionRepository.save(session);
    }

    private boolean hasValidAnswer(CoupleQuestionSession session, String participantId) {
        if (session.getAnswers() == null || participantId == null) return false;
        CoupleQuestionSession.Answer answer = session.getAnswers().get(participantId);
        return answer != null && answer.getContent() != null && !answer.getContent().isBlank();
    }
}
