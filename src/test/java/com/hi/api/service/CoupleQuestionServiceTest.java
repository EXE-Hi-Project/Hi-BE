package com.hi.api.service;

import com.hi.api.model.CoupleQuestionSession;
import com.hi.api.model.DailyQuestion;
import com.hi.api.model.User;
import com.hi.api.repository.CoupleQuestionSessionRepository;
import com.hi.api.repository.DailyQuestionRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoupleQuestionServiceTest {

    private CoupleQuestionSessionRepository sessionRepository;
    private DailyQuestionRepository questionRepository;
    private PartnerAccessService partnerAccessService;
    private NotificationService notificationService;
    private MongoTemplate mongoTemplate;
    private SubscriptionAccessService subscriptionAccessService;
    private EmailService emailService;
    private CoupleQuestionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(CoupleQuestionSessionRepository.class);
        questionRepository = mock(DailyQuestionRepository.class);
        partnerAccessService = mock(PartnerAccessService.class);
        notificationService = mock(NotificationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        subscriptionAccessService = mock(SubscriptionAccessService.class);
        emailService = mock(EmailService.class);
        service = new CoupleQuestionService(
                sessionRepository,
                questionRepository,
                mock(UserRepository.class),
                partnerAccessService,
                notificationService,
                mongoTemplate,
                subscriptionAccessService,
                mock(RealtimeEventService.class),
                emailService
        );
    }

    @Test
    void hidesPartnerAnswerUntilBothAnswersAreUnlocked() {
        CoupleQuestionSession session = session(false);
        session.getAnswers().remove("user-a");
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(true);

        Map<String, Object> response = service.getSession("user-a", "session-1");

        assertNull(response.get("partnerAnswer"));
        assertEquals(false, response.get("unlocked"));
        assertEquals(true, response.get("partnerAnswered"));
    }

    @Test
    void getTodayReturnsOldestUnfinishedQuestionBeforeToday() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        user.getNotificationPreferences().setDailyQuestionsEnabled(true);
        CoupleQuestionSession completed = session(true);
        completed.setId("completed");
        completed.setQuestionDate(LocalDate.now().minusDays(3));
        CoupleQuestionSession oldestUnfinished = session(false);
        oldestUnfinished.setId("oldest-unfinished");
        oldestUnfinished.setQuestionDate(LocalDate.now().minusDays(2));
        oldestUnfinished.getAnswers().remove("user-b");
        CoupleQuestionSession newerUnfinished = session(false);
        newerUnfinished.setId("newer-unfinished");
        newerUnfinished.setQuestionDate(LocalDate.now().minusDays(1));
        newerUnfinished.getAnswers().remove("user-a");

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(partnerAccessService.notificationPreferences(user)).thenReturn(user.getNotificationPreferences());
        when(sessionRepository.findByPairKeyOrderByQuestionDateAsc("user-a:user-b"))
                .thenReturn(List.of(completed, oldestUnfinished, newerUnfinished));

        Map<String, Object> response = service.getToday("user-a");

        assertEquals("oldest-unfinished", response.get("_id"));
        assertEquals(false, response.get("unlocked"));
    }

    @Test
    void schedulerContractDoesNotCreateTodayWhileBacklogExists() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        LocalDate today = LocalDate.now();
        CoupleQuestionSession backlog = session(false);
        backlog.setId("backlog");
        backlog.setQuestionDate(today.minusDays(1));
        backlog.getAnswers().remove("user-b");

        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(sessionRepository.findByPairKeyOrderByQuestionDateAsc("user-a:user-b"))
                .thenReturn(List.of(backlog));

        CoupleQuestionSession result = service.getOrCreate(user, partner, today);

        assertEquals("backlog", result.getId());
        verify(sessionRepository, never()).findByPairKeyAndQuestionDate("user-a:user-b", today);
    }

    @Test
    void createsTodayQuestionOnlyAfterEveryPastQuestionHasBothAnswers() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        LocalDate today = LocalDate.now();
        CoupleQuestionSession completedPast = session(true);
        completedPast.setQuestionDate(today.minusDays(1));
        DailyQuestion dailyQuestion = new DailyQuestion();
        dailyQuestion.setId("daily-question-1");
        dailyQuestion.setPrompt("Câu hỏi mới");
        dailyQuestion.setCategory("COUPLE");

        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(partnerAccessService.notificationPreferences(user)).thenReturn(user.getNotificationPreferences());
        when(partnerAccessService.notificationPreferences(partner)).thenReturn(partner.getNotificationPreferences());
        when(sessionRepository.findByPairKeyOrderByQuestionDateAsc("user-a:user-b"))
                .thenReturn(List.of(completedPast));
        when(sessionRepository.findByPairKeyAndQuestionDate("user-a:user-b", today))
                .thenReturn(Optional.empty());
        when(questionRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(dailyQuestion));
        when(sessionRepository.save(any(CoupleQuestionSession.class)))
                .thenAnswer(invocation -> {
                    CoupleQuestionSession saved = invocation.getArgument(0);
                    saved.setId("today-session");
                    return saved;
                });

        CoupleQuestionSession result = service.getOrCreate(user, partner, today);

        assertEquals(today, result.getQuestionDate());
        assertEquals("daily-question-1", result.getQuestionId());
        assertEquals(List.of("user-a", "user-b"), result.getParticipantIds());
    }

    @Test
    void legacySkippedSessionWithUnlockedTimestampIsLockedAndNormalized() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        user.getNotificationPreferences().setDailyQuestionsEnabled(true);
        CoupleQuestionSession legacy = session(true);
        legacy.getAnswers().remove("user-b");
        legacy.setSkippedBy(List.of("user-b"));

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(partnerAccessService.notificationPreferences(user)).thenReturn(user.getNotificationPreferences());
        when(sessionRepository.findByPairKeyOrderByQuestionDateAsc("user-a:user-b"))
                .thenReturn(List.of(legacy));
        when(sessionRepository.save(legacy)).thenReturn(legacy);

        Map<String, Object> response = service.getToday("user-a");

        assertEquals(false, response.get("unlocked"));
        assertNull(response.get("partnerAnswer"));
        assertNull(legacy.getUnlockedAt());
        verify(sessionRepository).save(legacy);
    }

    @Test
    void skipNeverCompletesTheCurrentQuestion() {
        assertThrows(IllegalStateException.class, () -> service.skipToday("user-a"));
    }

    @Test
    void returnsPartnerAnswerAndSharedThreadAfterUnlock() {
        CoupleQuestionSession session = session(true);
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(true);

        Map<String, Object> response = service.getSession("user-a", "session-1");

        CoupleQuestionSession.Answer partnerAnswer = (CoupleQuestionSession.Answer) response.get("partnerAnswer");
        assertEquals("Câu trả lời của B", partnerAnswer.getContent());
        assertEquals(2, ((List<?>) response.get("messages")).size());
    }

    @Test
    void redactsPartnerContentAfterDisconnect() {
        CoupleQuestionSession session = session(true);
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(false);

        Map<String, Object> response = service.getSession("user-a", "session-1");

        assertNull(response.get("partnerAnswer"));
        List<?> messages = (List<?>) response.get("messages");
        assertEquals(1, messages.size());
        assertTrue(messages.stream()
                .map(CoupleQuestionSession.Message.class::cast)
                .allMatch(message -> "user-a".equals(message.getUserId())));
    }

    @Test
    void answerSessionAllowsEditingOwnAnswerAfterUnlock() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        partner.getNotificationPreferences().setCoupleQuestionEditEmailEnabled(true);
        CoupleQuestionSession session = session(true);
        CoupleQuestionSession updated = session(true);
        updated.getAnswers().get("user-a").setContent("Câu trả lời mới");

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(true);
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(CoupleQuestionSession.class)
        )).thenReturn(updated);

        Map<String, Object> response = service.answerSession("user-a", "session-1", "Câu trả lời mới");

        CoupleQuestionSession.Answer myAnswer = (CoupleQuestionSession.Answer) response.get("myAnswer");
        assertEquals("Câu trả lời mới", myAnswer.getContent());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(CoupleQuestionSession.class)
        );
        assertTrue(!queryCaptor.getValue().getQueryObject().containsKey("unlockedAt"));
        verify(notificationService, never()).createIdempotentNotification(
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(emailService).sendCoupleQuestionEditEmail(
                eq(partner.getEmail()), eq("B"), eq("A"), eq(session.getQuestionText()));
    }

    @Test
    void notificationPreferencesUseSafeCoupleEmailDefaults() {
        User.NotificationPreferences preferences = new User.NotificationPreferences();

        assertEquals(true, preferences.getCoupleQuestionAnswerEmailEnabled());
        assertEquals(false, preferences.getCoupleQuestionCommentEmailEnabled());
        assertEquals(false, preferences.getCoupleQuestionEditEmailEnabled());
    }

    @Test
    void firstAnswerSendsAnswerEmailWhenEnabled() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        CoupleQuestionSession before = session(false);
        before.setAnswers(new LinkedHashMap<>());
        CoupleQuestionSession updated = session(false);
        updated.setAnswers(new LinkedHashMap<>(Map.of(
                "user-a", answer("user-a", "Câu trả lời mới")
        )));

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(true);
        when(partnerAccessService.notificationPreferences(partner)).thenReturn(partner.getNotificationPreferences());
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(before));
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(before));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CoupleQuestionSession.class)
        )).thenReturn(updated);

        service.answerSession("user-a", "session-1", "Câu trả lời mới");

        verify(emailService).sendCoupleQuestionAnswerEmail(
                eq(partner.getEmail()), eq("B"), eq("A"), eq(before.getQuestionText()));
        verify(emailService, never()).sendCoupleQuestionEditEmail(any(), any(), any(), any());
    }

    @Test
    void commentSendsItsContentWhenEnabled() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        partner.getNotificationPreferences().setCoupleQuestionCommentEmailEnabled(true);
        CoupleQuestionSession session = session(true);

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey("user-a", "user-b")).thenReturn("user-a:user-b");
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

        service.addMessage("user-a", "session-1", "Mình cũng nghĩ vậy");

        verify(emailService).sendCoupleQuestionCommentEmail(
                eq(partner.getEmail()), eq("B"), eq("A"), eq(session.getQuestionText()), eq("Mình cũng nghĩ vậy"));
    }

    @Test
    void historyAcceptsDateRangeFilters() {
        User user = user("user-a", "A");
        User partner = user("user-b", "B");
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        when(partnerAccessService.requireUser("user-a")).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(mongoTemplate.find(any(Query.class), eq(CoupleQuestionSession.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(CoupleQuestionSession.class))).thenReturn(0L);

        Map<String, Object> response = service.history("user-a", 0, 31, from, to);

        assertEquals(0L, response.get("total"));
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(CoupleQuestionSession.class));
        org.bson.Document queryObject = queryCaptor.getValue().getQueryObject();
        assertTrue(queryObject.containsKey("questionDate"));
        org.bson.Document dateFilter = (org.bson.Document) queryObject.get("questionDate");
        assertEquals(from, dateFilter.get("$gte"));
        assertEquals(to, dateFilter.get("$lte"));
    }

    private CoupleQuestionSession session(boolean unlocked) {
        CoupleQuestionSession session = new CoupleQuestionSession();
        session.setId("session-1");
        session.setPairKey("user-a:user-b");
        session.setQuestionDate(LocalDate.of(2026, 6, 11));
        session.setQuestionText("Hai bạn hiểu nhau thế nào?");
        session.setCategory("Kết nối");
        session.setParticipantIds(List.of("user-a", "user-b"));

        Map<String, CoupleQuestionSession.Answer> answers = new LinkedHashMap<>();
        answers.put("user-a", answer("user-a", "Câu trả lời của A"));
        answers.put("user-b", answer("user-b", "Câu trả lời của B"));
        session.setAnswers(answers);
        if (unlocked) session.setUnlockedAt(Instant.now());

        CoupleQuestionSession.Message first = message("message-a", "user-a", "Tin của A");
        CoupleQuestionSession.Message second = message("message-b", "user-b", "Tin của B");
        session.setMessages(List.of(first, second));
        return session;
    }

    private User user(String id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(id + "@example.com");
        return user;
    }

    private CoupleQuestionSession.Answer answer(String userId, String content) {
        CoupleQuestionSession.Answer answer = new CoupleQuestionSession.Answer();
        answer.setUserId(userId);
        answer.setContent(content);
        answer.setAnsweredAt(Instant.now());
        answer.setUpdatedAt(Instant.now());
        return answer;
    }

    private CoupleQuestionSession.Message message(String id, String userId, String content) {
        CoupleQuestionSession.Message message = new CoupleQuestionSession.Message();
        message.setId(id);
        message.setUserId(userId);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        return message;
    }
}
