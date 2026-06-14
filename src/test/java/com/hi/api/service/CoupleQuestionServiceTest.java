package com.hi.api.service;

import com.hi.api.model.CoupleQuestionSession;
import com.hi.api.repository.CoupleQuestionSessionRepository;
import com.hi.api.repository.DailyQuestionRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoupleQuestionServiceTest {

    private CoupleQuestionSessionRepository sessionRepository;
    private PartnerAccessService partnerAccessService;
    private CoupleQuestionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(CoupleQuestionSessionRepository.class);
        partnerAccessService = mock(PartnerAccessService.class);
        service = new CoupleQuestionService(
                sessionRepository,
                mock(DailyQuestionRepository.class),
                mock(UserRepository.class),
                partnerAccessService,
                mock(NotificationService.class),
                mock(MongoTemplate.class),
                mock(SubscriptionAccessService.class),
                mock(RealtimeEventService.class)
        );
    }

    @Test
    void hidesPartnerAnswerUntilBothAnswersAreUnlocked() {
        CoupleQuestionSession session = session(false);
        when(sessionRepository.findByIdAndParticipantIdsContaining("session-1", "user-a"))
                .thenReturn(Optional.of(session));
        when(partnerAccessService.isActivePair("user-a", "user-b")).thenReturn(true);

        Map<String, Object> response = service.getSession("user-a", "session-1");

        assertNull(response.get("partnerAnswer"));
        assertEquals(false, response.get("unlocked"));
        assertEquals(true, response.get("partnerAnswered"));
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
