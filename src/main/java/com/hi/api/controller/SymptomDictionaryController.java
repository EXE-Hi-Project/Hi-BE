package com.hi.api.controller;

import com.hi.api.dto.request.UpsertSymptomDictionaryRequest;
import com.hi.api.model.SymptomCategory;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.service.SymptomDictionaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/symptom-dictionaries")
@PreAuthorize("hasRole('ADMIN')")
public class SymptomDictionaryController {

    private final SymptomDictionaryService symptomDictionaryService;

    public SymptomDictionaryController(SymptomDictionaryService symptomDictionaryService) {
        this.symptomDictionaryService = symptomDictionaryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(@RequestParam(required = false) SymptomCategory category) {
        List<SymptomDictionary> symptoms = symptomDictionaryService.getAll(category);
        return ResponseEntity.ok(Map.of("success", true, "symptoms", symptoms));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody UpsertSymptomDictionaryRequest req) {
        SymptomDictionary symptom = symptomDictionaryService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "symptom", symptom));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpsertSymptomDictionaryRequest req) {
        SymptomDictionary symptom = symptomDictionaryService.update(id, req);
        return ResponseEntity.ok(Map.of("success", true, "symptom", symptom));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        symptomDictionaryService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa triệu chứng mẫu"));
    }
}