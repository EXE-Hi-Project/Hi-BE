package com.hi.api.service;

import com.hi.api.model.DailyQuestion;
import com.hi.api.repository.DailyQuestionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyQuestionSeederTest {

    @Test
    void seedsNinetyReviewedQuestionsAcrossSixCategories() {
        DailyQuestionRepository repository = mock(DailyQuestionRepository.class);
        when(repository.count()).thenReturn(0L);
        DailyQuestionSeeder seeder = new DailyQuestionSeeder(repository);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<DailyQuestion> questions = captor.getValue();
        assertEquals(90, questions.size());
        assertEquals(6, questions.stream().map(DailyQuestion::getCategory).distinct().count());
        assertTrue(questions.stream().allMatch(item -> item.getPrompt() != null && !item.getPrompt().isBlank()));
    }

    @Test
    void doesNotOverwriteQuestionsManagedByAdmin() {
        DailyQuestionRepository repository = mock(DailyQuestionRepository.class);
        when(repository.count()).thenReturn(1L);
        DailyQuestionSeeder seeder = new DailyQuestionSeeder(repository);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
