package com.hi.api.controller;

import com.hi.api.dto.request.CreateSymptomRequest;
import com.hi.api.model.Symptom;
import com.hi.api.model.User;
import com.hi.api.service.SymptomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/symptoms")
public class SymptomController {

    private final SymptomService symptomService;

    public SymptomController(SymptomService symptomService) {
        this.symptomService = symptomService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSymptoms(@AuthenticationPrincipal User user) {
        List<Symptom> symptoms = symptomService.getSymptoms(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "symptoms", symptoms));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSymptom(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateSymptomRequest req) {
        Symptom symptom = symptomService.createSymptom(user.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "symptom", symptom));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSymptom(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        symptomService.deleteSymptom(user.getId(), id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa triệu chứng"));
    }
}
