package com.hi.api.service;

import com.hi.api.dto.request.CreateSymptomRequest;
import com.hi.api.model.Symptom;
import com.hi.api.repository.SymptomRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SymptomService {

    private final SymptomRepository symptomRepository;

    public SymptomService(SymptomRepository symptomRepository) {
        this.symptomRepository = symptomRepository;
    }

    public List<Symptom> getSymptoms(String userId) {
        return symptomRepository.findByUserIdOrderByDateDesc(userId);
    }

    public Symptom createSymptom(String userId, CreateSymptomRequest req) {
        Symptom symptom = new Symptom();
        symptom.setUserId(userId);
        symptom.setName(req.getName());
        symptom.setSeverity(req.getSeverity() != null ? req.getSeverity() : 1);
        symptom.setDate(req.getDate() != null ? req.getDate() : Instant.now().toString());
        symptom.setNotes(req.getNotes() != null ? req.getNotes() : "");
        return symptomRepository.save(symptom);
    }

    public void deleteSymptom(String userId, String symptomId) {
        Symptom symptom = symptomRepository.findByIdAndUserId(symptomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy triệu chứng"));
        symptomRepository.delete(symptom);
    }
}
