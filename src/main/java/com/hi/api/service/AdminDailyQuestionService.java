package com.hi.api.service;

import com.hi.api.dto.request.UpsertDailyQuestionRequest;
import com.hi.api.model.DailyQuestion;
import com.hi.api.repository.DailyQuestionRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDailyQuestionService {

    private final DailyQuestionRepository repository;

    public AdminDailyQuestionService(DailyQuestionRepository repository) {
        this.repository = repository;
    }

    public List<DailyQuestion> list(String query, String category, Boolean active) {
        String normalizedQuery = normalize(query);
        String normalizedCategory = normalize(category);

        return repository.findAll().stream()
                .filter(question -> active == null || active.equals(Boolean.TRUE.equals(question.getActive())))
                .filter(question -> normalizedCategory.isEmpty()
                        || normalize(question.getCategory()).equals(normalizedCategory))
                .filter(question -> normalizedQuery.isEmpty()
                        || normalize(question.getPrompt()).contains(normalizedQuery)
                        || normalize(question.getCategory()).contains(normalizedQuery))
                .sorted(Comparator.comparing(
                        DailyQuestion::getDisplayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    public DailyQuestion create(UpsertDailyQuestionRequest request) {
        int nextOrder = repository.findFirstByOrderByDisplayOrderDesc()
                .map(DailyQuestion::getDisplayOrder)
                .orElse(0) + 1;

        DailyQuestion question = new DailyQuestion();
        question.setCategory(request.getCategory().trim());
        question.setPrompt(request.getPrompt().trim());
        question.setDisplayOrder(nextOrder);
        question.setActive(request.getActive() == null || request.getActive());
        return repository.save(question);
    }

    public DailyQuestion update(String id, UpsertDailyQuestionRequest request) {
        DailyQuestion question = get(id);
        question.setCategory(request.getCategory().trim());
        question.setPrompt(request.getPrompt().trim());
        if (request.getActive() != null) {
            question.setActive(request.getActive());
        }
        return repository.save(question);
    }

    public DailyQuestion archive(String id) {
        DailyQuestion question = get(id);
        question.setActive(false);
        return repository.save(question);
    }

    private DailyQuestion get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy câu hỏi hằng ngày"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
