package com.hi.api.service;

import com.hi.api.dto.request.UpsertSymptomDictionaryRequest;
import com.hi.api.model.SymptomCategory;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SymptomDictionaryService {

    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final SequenceService sequenceService;

    public SymptomDictionaryService(SymptomDictionaryRepository symptomDictionaryRepository, SequenceService sequenceService) {
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.sequenceService = sequenceService;
    }

    public List<SymptomDictionary> getAll(SymptomCategory category) {
        if (category != null) {
            return symptomDictionaryRepository.findByCategoryAndActiveTrueOrderByNameAsc(category);
        }
        return symptomDictionaryRepository.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    public SymptomDictionary create(UpsertSymptomDictionaryRequest req) {
        SymptomDictionary dictionary = new SymptomDictionary();
        dictionary.setId(sequenceService.next("symptom_dictionaries"));
        apply(dictionary, req);
        return symptomDictionaryRepository.save(dictionary);
    }

    public SymptomDictionary update(Long id, UpsertSymptomDictionaryRequest req) {
        SymptomDictionary dictionary = symptomDictionaryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy triệu chứng mẫu"));
        apply(dictionary, req);
        return symptomDictionaryRepository.save(dictionary);
    }

    public void delete(Long id) {
        SymptomDictionary dictionary = symptomDictionaryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy triệu chứng mẫu"));
        dictionary.setActive(false);
        symptomDictionaryRepository.save(dictionary);
    }

    private void apply(SymptomDictionary dictionary, UpsertSymptomDictionaryRequest req) {
        dictionary.setName(req.getName().trim());
        dictionary.setCategory(req.getCategory());
        dictionary.setIconUrl(req.getIconUrl() != null ? req.getIconUrl() : "");
        if (req.getActive() != null) {
            dictionary.setActive(req.getActive());
        }
        if (dictionary.getActive() == null) {
            dictionary.setActive(true);
        }
    }
}