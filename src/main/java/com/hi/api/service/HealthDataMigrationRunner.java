package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.DailyLog;
import com.hi.api.model.DailyLogSymptom;
import com.hi.api.model.FlowIntensity;
import com.hi.api.model.SymptomDictionary;
import com.hi.api.model.SymptomSeverity;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.DailyLogSymptomRepository;
import com.hi.api.repository.SymptomDictionaryRepository;
import com.hi.api.repository.UserRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Order(100)
public class HealthDataMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthDataMigrationRunner.class);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogRepository dailyLogRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final SymptomDictionaryRepository symptomDictionaryRepository;
    private final SequenceService sequenceService;
    private final ObjectMapper objectMapper;
    private final Set<String> predictedCycleKeys = new LinkedHashSet<>();

    @Value("${app.migration.health-data.enabled:false}")
    private boolean enabled;

    @Value("${app.migration.health-data.dry-run:true}")
    private boolean dryRun;

    @Value("${app.migration.health-data.report-path:}")
    private String reportPath;

    public HealthDataMigrationRunner(MongoTemplate mongoTemplate,
                                     UserRepository userRepository,
                                     CycleRecordRepository cycleRecordRepository,
                                     DailyLogRepository dailyLogRepository,
                                     DailyLogSymptomRepository dailyLogSymptomRepository,
                                     SymptomDictionaryRepository symptomDictionaryRepository,
                                     SequenceService sequenceService,
                                     ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.symptomDictionaryRepository = symptomDictionaryRepository;
        this.sequenceService = sequenceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            return;
        }
        MigrationReport report = new MigrationReport(dryRun);
        migrateProfiles(report);
        migrateLegacyCycles(report);
        migrateLegacySymptoms(report);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report.toMap());
        log.info("Health data migration report:\n{}", json);
        if (reportPath != null && !reportPath.isBlank()) {
            Files.writeString(Path.of(reportPath), json);
        }
    }

    private void migrateProfiles(MigrationReport report) {
        for (User user : userRepository.findAll()) {
            if (!"female".equalsIgnoreCase(user.getGender()) || isBlank(user.getLastPeriodDate())) {
                continue;
            }
            try {
                LocalDate startDate = parseDate(user.getLastPeriodDate());
                LocalDate endDate = isBlank(user.getLastPeriodEndDate()) ? null : parseDate(user.getLastPeriodEndDate());
                validateCycle(startDate, endDate, user.getDefaultCycleLength(), user.getDefaultPeriodLength());
                if (cycleExists(user.getId(), startDate)) {
                    report.profileCycles.duplicates++;
                    continue;
                }
                report.profileCycles.created++;
                if (!dryRun) {
                    CycleRecord record = new CycleRecord();
                    record.setId(sequenceService.next("cycle_records"));
                    record.setUserId(user.getId());
                    record.setStartDate(startDate);
                    record.setEndDate(endDate);
                    record.setCycleLength(valueOrDefault(user.getDefaultCycleLength(), 28));
                    record.setPeriodLength(valueOrDefault(user.getDefaultPeriodLength(), 5));
                    record.setIsIgnored(false);
                    cycleRecordRepository.save(record);
                }
            } catch (RuntimeException ex) {
                report.profileCycles.invalid++;
                report.invalidItems.add("profile:" + user.getId() + ":" + ex.getMessage());
            }
        }
    }

    private void migrateLegacyCycles(MigrationReport report) {
        for (Document raw : mongoTemplate.getCollection("cycles").find()) {
            String sourceId = stringValue(raw.get("_id"));
            try {
                String userId = requiredString(raw, "userId");
                LocalDate startDate = parseDate(requiredString(raw, "startDate"));
                LocalDate endDate = isBlank(stringValue(raw.get("endDate"))) ? null : parseDate(stringValue(raw.get("endDate")));
                Integer cycleLength = intValue(raw.get("cycleLength"), 28);
                Integer periodLength = intValue(raw.get("periodLength"), endDate == null ? 5 : null);
                if (periodLength == null) {
                    periodLength = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
                }
                validateCycle(startDate, endDate, cycleLength, periodLength);
                if (cycleExists(userId, startDate)) {
                    report.legacyCycles.duplicates++;
                    continue;
                }
                report.legacyCycles.created++;
                if (!dryRun) {
                    CycleRecord record = new CycleRecord();
                    record.setId(sequenceService.next("cycle_records"));
                    record.setUserId(userId);
                    record.setStartDate(startDate);
                    record.setEndDate(endDate);
                    record.setCycleLength(cycleLength);
                    record.setPeriodLength(periodLength);
                    record.setIsIgnored(false);
                    cycleRecordRepository.save(record);
                }
            } catch (RuntimeException ex) {
                report.legacyCycles.invalid++;
                report.invalidItems.add("cycle:" + sourceId + ":" + ex.getMessage());
            }
        }
    }

    private void migrateLegacySymptoms(MigrationReport report) {
        Map<String, SymptomDictionary> dictionaryByName = new LinkedHashMap<>();
        for (SymptomDictionary dictionary : symptomDictionaryRepository.findByActiveTrueOrderByCategoryAscNameAsc()) {
            dictionaryByName.put(normalize(dictionary.getName()), dictionary);
        }
        if (dryRun) {
            long syntheticId = -1;
            for (SymptomDictionarySeeder.SeedItem item : SymptomDictionarySeeder.defaultItems()) {
                SymptomDictionary dictionary = new SymptomDictionary();
                dictionary.setId(syntheticId--);
                dictionary.setName(item.name());
                dictionary.setCategory(item.category());
                dictionary.setActive(true);
                dictionaryByName.putIfAbsent(normalize(dictionary.getName()), dictionary);
            }
        }
        Set<String> predictedDailyLogs = new LinkedHashSet<>();
        Set<String> predictedSymptoms = new LinkedHashSet<>();
        for (Document raw : mongoTemplate.getCollection("symptoms").find()) {
            String sourceId = stringValue(raw.get("_id"));
            try {
                String userId = requiredString(raw, "userId");
                String legacyName = requiredString(raw, "name");
                SymptomDictionary dictionary = dictionaryByName.get(normalize(legacyName));
                if (dictionary == null) {
                    report.legacySymptoms.unmapped++;
                    report.unmappedSymptoms.add(legacyName);
                    continue;
                }
                LocalDate logDate = parseDate(requiredString(raw, "date"));
                DailyLog logEntry = dailyLogRepository.findByUserIdAndLogDate(userId, logDate).orElse(null);
                if (logEntry == null) {
                    String dailyLogKey = userId + ":" + logDate;
                    if (dryRun) {
                        if (predictedDailyLogs.add(dailyLogKey)) {
                            report.dailyLogs.created++;
                        } else {
                            report.dailyLogs.skipped++;
                        }
                        String symptomKey = dailyLogKey + ":" + dictionary.getName();
                        if (predictedSymptoms.add(symptomKey)) {
                            report.legacySymptoms.created++;
                        } else {
                            report.legacySymptoms.duplicates++;
                        }
                        continue;
                    } else {
                        report.dailyLogs.created++;
                        logEntry = new DailyLog();
                        logEntry.setId(sequenceService.next("daily_logs"));
                        logEntry.setUserId(userId);
                        logEntry.setLogDate(logDate);
                        logEntry.setFlowIntensity(FlowIntensity.NONE);
                        logEntry.setNotes(stringValue(raw.get("notes")));
                        logEntry = dailyLogRepository.save(logEntry);
                    }
                } else {
                    report.dailyLogs.skipped++;
                }
                if (dailyLogSymptomRepository.findByDailyLogIdAndSymptomId(logEntry.getId(), dictionary.getId()).isPresent()) {
                    report.legacySymptoms.duplicates++;
                    continue;
                }
                report.legacySymptoms.created++;
                if (!dryRun) {
                    DailyLogSymptom relation = new DailyLogSymptom();
                    relation.setId(sequenceService.next("daily_log_symptoms"));
                    relation.setDailyLogId(logEntry.getId());
                    relation.setSymptomId(dictionary.getId());
                    relation.setSeverity(toSeverity(intValue(raw.get("severity"), 1)));
                    dailyLogSymptomRepository.save(relation);
                }
            } catch (RuntimeException ex) {
                report.legacySymptoms.invalid++;
                report.invalidItems.add("symptom:" + sourceId + ":" + ex.getMessage());
            }
        }
    }

    private void validateCycle(LocalDate startDate, LocalDate endDate, Integer cycleLength, Integer periodLength) {
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("future startDate");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate before startDate");
        }
        if (cycleLength == null || cycleLength < 10 || cycleLength > 90) {
            throw new IllegalArgumentException("cycleLength outside 10-90");
        }
        if (periodLength == null || periodLength < 1 || periodLength > 30) {
            throw new IllegalArgumentException("periodLength outside 1-30");
        }
    }

    private boolean cycleExists(String userId, LocalDate startDate) {
        if (cycleRecordRepository.findByUserIdAndStartDate(userId, startDate).isPresent()) {
            return true;
        }
        return dryRun && !predictedCycleKeys.add(userId + ":" + startDate);
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.substring(0, Math.min(raw.length(), 10)));
        } catch (DateTimeParseException | IndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("invalid date");
        }
    }

    private SymptomSeverity toSeverity(int value) {
        if (value <= 2) return SymptomSeverity.MILD;
        if (value == 3) return SymptomSeverity.MODERATE;
        return SymptomSeverity.SEVERE;
    }

    private String requiredString(Document raw, String key) {
        String value = stringValue(raw.get(key));
        if (isBlank(value)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Integer intValue(Object value, Integer fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static final class MigrationReport {
        private final boolean dryRun;
        private final Stats profileCycles = new Stats();
        private final Stats legacyCycles = new Stats();
        private final Stats dailyLogs = new Stats();
        private final Stats legacySymptoms = new Stats();
        private final Set<String> unmappedSymptoms = new LinkedHashSet<>();
        private final List<String> invalidItems = new ArrayList<>();

        private MigrationReport(boolean dryRun) {
            this.dryRun = dryRun;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dryRun", dryRun);
            result.put("profileCycles", profileCycles.toMap());
            result.put("legacyCycles", legacyCycles.toMap());
            result.put("dailyLogs", dailyLogs.toMap());
            result.put("legacySymptoms", legacySymptoms.toMap());
            result.put("unmappedSymptoms", unmappedSymptoms);
            result.put("invalidItems", invalidItems);
            return result;
        }
    }

    private static final class Stats {
        private int created;
        private int updated;
        private int skipped;
        private int duplicates;
        private int invalid;
        private int unmapped;

        private Map<String, Integer> toMap() {
            Map<String, Integer> result = new LinkedHashMap<>();
            result.put("created", created);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("duplicates", duplicates);
            result.put("invalid", invalid);
            result.put("unmapped", unmapped);
            return result;
        }
    }
}
