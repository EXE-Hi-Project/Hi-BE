package com.hi.api.controller;

import com.hi.api.dto.request.UpsertDailyQuestionRequest;
import com.hi.api.model.DailyQuestion;
import com.hi.api.service.AdminDailyQuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/daily-questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDailyQuestionController {

    private final AdminDailyQuestionService service;

    public AdminDailyQuestionController(AdminDailyQuestionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active) {
        List<DailyQuestion> questions = service.list(query, category, active);
        long activeCount = questions.stream().filter(item -> Boolean.TRUE.equals(item.getActive())).count();
        long categoryCount = questions.stream().map(DailyQuestion::getCategory).distinct().count();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "questions", questions,
                "summary", Map.of(
                        "total", questions.size(),
                        "active", activeCount,
                        "categories", categoryCount
                )
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody UpsertDailyQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "question", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @Valid @RequestBody UpsertDailyQuestionRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "question", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> archive(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("success", true, "question", service.archive(id)));
    }
}
