package com.hi.api.service;

import com.hi.api.dto.request.UpsertDailyQuestionRequest;
import com.hi.api.model.DailyQuestion;
import com.hi.api.repository.DailyQuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDailyQuestionServiceTest {

    @Test
    void createsQuestionAfterCurrentHighestDisplayOrder() {
        DailyQuestionRepository repository = mock(DailyQuestionRepository.class);
        DailyQuestion currentLast = question("last", "Kết nối", "Câu cũ", 90, true);
        when(repository.findFirstByOrderByDisplayOrderDesc()).thenReturn(Optional.of(currentLast));
        when(repository.save(org.mockito.ArgumentMatchers.any(DailyQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AdminDailyQuestionService service = new AdminDailyQuestionService(repository);
        UpsertDailyQuestionRequest request = new UpsertDailyQuestionRequest();
        request.setCategory(" Giao tiếp ");
        request.setPrompt(" Bạn muốn được lắng nghe như thế nào? ");
        request.setActive(true);

        DailyQuestion created = service.create(request);

        assertEquals(91, created.getDisplayOrder());
        assertEquals("Giao tiếp", created.getCategory());
        assertEquals("Bạn muốn được lắng nghe như thế nào?", created.getPrompt());
    }

    @Test
    void archivesQuestionWithoutDeletingItsHistory() {
        DailyQuestionRepository repository = mock(DailyQuestionRepository.class);
        DailyQuestion existing = question("question-4", "Kết nối", "Câu hỏi", 4, true);
        when(repository.findById("question-4")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        AdminDailyQuestionService service = new AdminDailyQuestionService(repository);

        DailyQuestion archived = service.archive("question-4");

        assertFalse(archived.getActive());
        ArgumentCaptor<DailyQuestion> captor = ArgumentCaptor.forClass(DailyQuestion.class);
        verify(repository).save(captor.capture());
        assertEquals("question-4", captor.getValue().getId());
    }

    private DailyQuestion question(
            String id,
            String category,
            String prompt,
            int displayOrder,
            boolean active) {
        DailyQuestion question = new DailyQuestion();
        question.setId(id);
        question.setCategory(category);
        question.setPrompt(prompt);
        question.setDisplayOrder(displayOrder);
        question.setActive(active);
        return question;
    }
}
