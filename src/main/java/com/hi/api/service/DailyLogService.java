package com.hi.api.service;

import com.hi.api.dto.request.DailyLogSymptomRequest;
import com.hi.api.dto.request.UpdateDailyLogMoodRequest;
import com.hi.api.dto.request.UpsertDailyLogRequest;
import com.hi.api.dto.request.UpsertDailyLogSymptomRequest;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.SymptomSeverity;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
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
    private final CycleRecordService cycleRecordService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DailyLogService(DailyLogRepository dailyLogRepository,
                           DailyLogSymptomRepository dailyLogSymptomRepository,
                           SymptomDictionaryRepository symptomDictionaryRepository,
                           SequenceService sequenceService,
                           CycleRecordService cycleRecordService,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.sequenceService = sequenceService;
        this.cycleRecordService = cycleRecordService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<DailyLog> getLogs(String userId, LocalDate from, LocalDate to) {
        List<DailyLog> logs = dailyLogRepository.findByUserIdOrderByLogDateDesc(userId);
        if (from != null || to != null) {
            logs = logs.stream()
                    .filter(log -> {
                        LocalDate d = log.getLogDate();
                        if (d == null) return false;
                        boolean afterFrom = (from == null || !d.isBefore(from));
                        boolean beforeTo = (to == null || !d.isAfter(to));
                        return afterFrom && beforeTo;
                    })
                    .collect(Collectors.toList());
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

    @CacheEvict(value = "ai_context", key = "#userId")
    public DailyLog upsertLog(String userId, LocalDate logDate, UpsertDailyLogRequest req) {
        validateLogDate(logDate);
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
        FlowIntensity flowIntensity = req.getFlowIntensity() != null ? req.getFlowIntensity() : FlowIntensity.NONE;
        if (Boolean.TRUE.equals(req.getConfirmPeriodStart()) && FlowIntensity.NONE.equals(flowIntensity)) {
            throw new IllegalArgumentException("Cần ghi nhận lượng kinh để xác nhận ngày bắt đầu kỳ kinh");
        }
        log.setFlowIntensity(flowIntensity);
        log.setHasClots(Boolean.TRUE.equals(req.getHasClots()));
        log.setMoodScore(req.getMoodScore());
        log.setNotes(req.getNotes() != null ? req.getNotes() : "");

        DailyLog saved = dailyLogRepository.save(log);
        if (req.getSymptoms() != null) {
            syncSymptoms(saved, req.getSymptoms());
        }
        if (Boolean.TRUE.equals(req.getConfirmPeriodStart())) {
            cycleRecordService.confirmPeriodStart(userId, logDate);
        }
        attachSymptoms(List.of(saved));
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public Map<String, Object> updateMood(String userId, LocalDate logDate, UpdateDailyLogMoodRequest req) {
        validateLogDate(logDate);
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
        log.setMoodScore(req.getMoodScore());
        if (req.getNotes() != null) {
            log.setNotes(req.getNotes());
        }
        DailyLog saved = dailyLogRepository.save(log);
        attachSymptoms(List.of(saved));
        boolean partnerNotificationSent = notifyPartnerMood(userId, req.getMoodScore());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dailyLog", saved);
        response.put("partnerNotificationSent", partnerNotificationSent);
        response.put("latestMood", moodResponse(saved));
        return response;
    }

    private Map<String, Object> moodResponse(DailyLog log) {
        if (log == null || log.getMoodScore() == null) {
            return null;
        }
        Map<String, Object> mood = new LinkedHashMap<>();
        mood.put("moodScore", log.getMoodScore());
        mood.put("label", moodLabel(log.getMoodScore()));
        mood.put("logDate", log.getLogDate());
        return mood;
    }

    private boolean notifyPartnerMood(String userId, Integer moodScore) {
        if (moodScore == null) {
            return false;
        }
        return userRepository.findById(userId)
                .filter(user -> user.getPartnerId() != null && !user.getPartnerId().isBlank())
                .filter(user -> user.getNotificationPreferences() == null
                        || !Boolean.FALSE.equals(user.getNotificationPreferences().getPartnerMoodUpdatesEnabled()))
                .map(user -> {
                    String senderName = user.getName() != null && !user.getName().isBlank() ? user.getName() : "Người ấy";
                    notificationService.createNotification(
                            user.getPartnerId(),
                            "PARTNER_MOOD_UPDATE",
                            "Cảm xúc mới từ " + senderName,
                            senderName + " vừa ghi cảm xúc: " + moodLabel(moodScore)
                    );
                    return true;
                })
                .orElse(false);
    }

    private String moodLabel(Integer moodScore) {
        if (moodScore == null) {
            return "Chưa rõ";
        }
        return switch (Math.max(1, Math.min(5, moodScore))) {
            case 1 -> "Bực bội";
            case 2 -> "Lo lắng hoặc mệt mỏi";
            case 4 -> "Bình tĩnh";
            case 5 -> "Vui vẻ";
            default -> "Bình thường";
        };
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public void deleteLog(String userId, LocalDate logDate) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhật ký ngày này"));
        dailyLogSymptomRepository.deleteByDailyLogId(log.getId());
        dailyLogRepository.delete(log);
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public DailyLogSymptom upsertSymptom(String userId, LocalDate logDate, Long symptomId,
                                         UpsertDailyLogSymptomRequest req) {
        validateLogDate(logDate);
        SymptomDictionary dictionary = symptomDictionaryRepository.findByIdAndActiveTrue(symptomId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy triệu chứng mẫu hợp lệ"));
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseGet(() -> {
                    DailyLog newLog = new DailyLog();
                    newLog.setId(sequenceService.next("daily_logs"));
                    newLog.setUserId(userId);
                    newLog.setLogDate(logDate);
                    return dailyLogRepository.save(newLog);
                });
        if (req != null && req.getNotes() != null) {
            log.setNotes(req.getNotes());
            dailyLogRepository.save(log);
        }
        DailyLogSymptom symptom = dailyLogSymptomRepository.findByDailyLogIdAndSymptomId(log.getId(), symptomId)
                .orElseGet(() -> {
                    DailyLogSymptom newSymptom = new DailyLogSymptom();
                    newSymptom.setId(sequenceService.next("daily_log_symptoms"));
                    newSymptom.setDailyLogId(log.getId());
                    newSymptom.setSymptomId(symptomId);
                    return newSymptom;
                });
        symptom.setSeverity(req != null && req.getSeverity() != null ? req.getSeverity() : SymptomSeverity.MILD);
        DailyLogSymptom saved = dailyLogSymptomRepository.save(symptom);
        enrichSymptom(saved, dictionary);
        return saved;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public void deleteSymptom(String userId, LocalDate logDate, Long symptomId) {
        DailyLog log = dailyLogRepository.findByUserIdAndLogDate(userId, logDate)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhật ký ngày này"));
        dailyLogSymptomRepository.deleteByDailyLogIdAndSymptomId(log.getId(), symptomId);
    }

    private void syncSymptoms(DailyLog log, List<DailyLogSymptomRequest> symptomRequests) {
        if (symptomRequests.isEmpty()) {
            dailyLogSymptomRepository.deleteByDailyLogId(log.getId());
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

        dailyLogSymptomRepository.deleteByDailyLogId(log.getId());
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
        List<Long> symptomIds = relationMap.values().stream()
                .flatMap(List::stream)
                .map(DailyLogSymptom::getSymptomId)
                .distinct()
                .toList();
        Map<Long, SymptomDictionary> dictionaryMap = symptomDictionaryRepository.findAllById(symptomIds).stream()
                .collect(Collectors.toMap(SymptomDictionary::getId, dictionary -> dictionary));

        for (DailyLog log : logs) {
            List<DailyLogSymptom> symptoms = new ArrayList<>(relationMap.getOrDefault(log.getId(), List.of()));
            symptoms.forEach(symptom -> enrichSymptom(symptom, dictionaryMap.get(symptom.getSymptomId())));
            log.setSymptoms(symptoms);
        }
    }

    private void enrichSymptom(DailyLogSymptom symptom, SymptomDictionary dictionary) {
        if (dictionary == null) {
            return;
        }
        symptom.setSymptomName(dictionary.getName());
        symptom.setCategory(dictionary.getCategory());
        symptom.setIconUrl(dictionary.getIconUrl());
    }

    private void validateLogDate(LocalDate logDate) {
        if (logDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày ghi triệu chứng không được ở tương lai");
        }
    }
}
