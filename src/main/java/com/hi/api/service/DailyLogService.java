package com.hi.api.service;

import com.hi.api.dto.request.DailyLogSymptomRequest;
import com.hi.api.dto.request.UpsertDailyLogRequest;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.SymptomSeverity;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final SequenceService sequenceService;

    public DailyLogService(DailyLogRepository dailyLogRepository,
                           DailyLogSymptomRepository dailyLogSymptomRepository,
                           SymptomDictionaryRepository symptomDictionaryRepository,
                           SequenceService sequenceService) {
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.sequenceService = sequenceService;
    }

    public List<DailyLog> getLogs(String userId, LocalDate from, LocalDate to) {
        List<DailyLog> logs;
        if (from != null && to != null) {
            logs = dailyLogRepository.findByUserIdAndLogDateBetweenOrderByLogDateDesc(userId, from, to);
        } else if (from != null) {
            logs = dailyLogRepository.findByUserIdAndLogDateGreaterThanEqualOrderByLogDateDesc(userId, from);
        } else if (to != null) {
            logs = dailyLogRepository.findByUserIdAndLogDateLessThanEqualOrderByLogDateDesc(userId, to);
        } else {
            logs = dailyLogRepository.findByUserIdOrderByLogDateDesc(userId);
        }
        attachSymptoms(logs);
        return logs;
    }

    public DailyLog getLog(String userId, LocalDate logDate) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhật ký ngày này"));
        attachSymptoms(List.of(log));
        return log;
    }

    public DailyLog upsertLog(String userId, LocalDate logDate, UpsertDailyLogRequest req) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseGet(() -> {
                    DailyLog newLog = new DailyLog();
                    newLog.setId(sequenceService.next("daily_logs"));
                    newLog.setUserId(userId);
                    newLog.setLogDate(logDate);
                    return newLog;
                });

        log.setUserId(userId);
        log.setLogDate(logDate);
        log.setFlowIntensity(req.getFlowIntensity() != null ? req.getFlowIntensity() : FlowIntensity.NONE);
        log.setMoodScore(req.getMoodScore());
        log.setNotes(req.getNotes() != null ? req.getNotes() : "");

        DailyLog saved = dailyLogRepository.save(log);
        syncSymptoms(saved, req.getSymptoms());
        attachSymptoms(List.of(saved));
        return saved;
    }

    public void deleteLog(String userId, LocalDate logDate) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhật ký ngày này"));
        dailyLogSymptomRepository.deleteByDailyLogId(log.getId());
        dailyLogRepository.delete(log);
    }

    private void syncSymptoms(DailyLog log, List<DailyLogSymptomRequest> symptomRequests) {
        dailyLogSymptomRepository.deleteByDailyLogId(log.getId());

        if (symptomRequests == null || symptomRequests.isEmpty()) {
            log.setSymptoms(new ArrayList<>());
            return;
        }

        Map<Long, SymptomSeverity> deduplicated = new LinkedHashMap<>();
        for (DailyLogSymptomRequest request : symptomRequests) {
            if (request == null || request.getSymptomId() == null) {
                continue;
            }
            SymptomDictionary symptom = symptomDictionaryRepository.findByIdAndActiveTrue(request.getSymptomId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy triệu chứng mẫu hợp lệ"));
            SymptomSeverity severity = request.getSeverity() != null ? request.getSeverity() : SymptomSeverity.MILD;
            deduplicated.put(symptom.getId(), severity);
        }

        List<DailyLogSymptom> savedSymptoms = new ArrayList<>();
        for (Map.Entry<Long, SymptomSeverity> entry : deduplicated.entrySet()) {
            DailyLogSymptom relation = new DailyLogSymptom();
            relation.setId(sequenceService.next("daily_log_symptoms"));
            relation.setDailyLogId(log.getId());
            relation.setSymptomId(entry.getKey());
            relation.setSeverity(entry.getValue());
            savedSymptoms.add(dailyLogSymptomRepository.save(relation));
        }
        log.setSymptoms(savedSymptoms);
    }

    private void attachSymptoms(List<DailyLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        List<Long> logIds = logs.stream().map(DailyLog::getId).filter(Objects::nonNull).toList();
        if (logIds.isEmpty()) {
            return;
        }

        Map<Long, List<DailyLogSymptom>> relationMap = dailyLogSymptomRepository.findByDailyLogIdIn(logIds).stream()
                .collect(Collectors.groupingBy(DailyLogSymptom::getDailyLogId));

        for (DailyLog log : logs) {
            log.setSymptoms(new ArrayList<>(relationMap.getOrDefault(log.getId(), List.of())));
        }
    }
}