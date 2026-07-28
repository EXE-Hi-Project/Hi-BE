package com.hi.api.service;

import com.hi.api.model.CycleRecord;
import com.hi.api.model.CycleRecordStatus;
import com.hi.api.repository.CycleRecordRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;

@Component
@Order(200)
public class CycleRecordIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final CycleRecordRepository cycleRecordRepository;

    @Value("${app.migration.health-data.enabled:false}")
    private boolean migrationEnabled;

    @Value("${app.migration.health-data.dry-run:true}")
    private boolean migrationDryRun;

    @Value("${app.migration.cycle-index-normalization-enabled:false}")
    private boolean normalizationEnabled;

    public CycleRecordIndexInitializer(MongoTemplate mongoTemplate,
                                       CycleRecordRepository cycleRecordRepository) {
        this.mongoTemplate = mongoTemplate;
        this.cycleRecordRepository = cycleRecordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (migrationEnabled && migrationDryRun) {
            return;
        }
        if (normalizationEnabled) {
            normalizeLegacyRecords();
            normalizeDuplicateActiveRecords();
        }
        mongoTemplate.indexOps(CycleRecord.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("startDate", Sort.Direction.ASC)
                        .unique()
                        .named("cycle_record_user_start_idx")
        );
        mongoTemplate.indexOps(CycleRecord.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("status").in(
                                        CycleRecordStatus.ONGOING,
                                        CycleRecordStatus.NEEDS_CONFIRMATION)))
                        .named("cycle_record_one_active_user_idx")
        );
    }

    private void normalizeLegacyRecords() {
        for (CycleRecord record : cycleRecordRepository.findAll()) {
            boolean changed = false;
            if (record.getIsIgnored() == null) {
                record.setIsIgnored(false);
                changed = true;
            }
            if (record.getStatus() == null && record.getStartDate() != null) {
                LocalDate inferredEnd = record.getEndDate();
                if (inferredEnd == null) {
                    int length = Math.max(record.getPeriodLength() != null ? record.getPeriodLength() : 1, 1);
                    inferredEnd = record.getStartDate().plusDays(length - 1L);
                    if (inferredEnd.isAfter(LocalDate.now())) inferredEnd = LocalDate.now();
                    record.setEndDate(inferredEnd);
                    record.setEndDateEstimated(true);
                }
                record.setLastBleedingDate(inferredEnd);
                record.setPeriodLength((int) java.time.temporal.ChronoUnit.DAYS
                        .between(record.getStartDate(), inferredEnd) + 1);
                record.setStatus(CycleRecordStatus.COMPLETED);
                changed = true;
            }
            if (record.getEndDateEstimated() == null) {
                record.setEndDateEstimated(false);
                changed = true;
            }
            if (changed) cycleRecordRepository.save(record);
        }
    }

    private void normalizeDuplicateActiveRecords() {
        cycleRecordRepository.findAll().stream()
                .filter(record -> record.getUserId() != null)
                .filter(record -> CycleRecordStatus.ONGOING.equals(record.getStatus())
                        || CycleRecordStatus.NEEDS_CONFIRMATION.equals(record.getStatus()))
                .collect(Collectors.groupingBy(CycleRecord::getUserId))
                .values()
                .forEach(records -> {
                    records.sort(Comparator.comparing(CycleRecord::getStartDate,
                            Comparator.nullsLast(Comparator.reverseOrder())));
                    for (int index = 1; index < records.size(); index++) {
                        CycleRecord duplicate = records.get(index);
                        if (duplicate.getStartDate() == null) continue;
                        LocalDate end = duplicate.getLastBleedingDate() != null
                                ? duplicate.getLastBleedingDate()
                                : duplicate.getStartDate();
                        duplicate.setEndDate(end);
                        duplicate.setLastBleedingDate(end);
                        duplicate.setPeriodLength((int) java.time.temporal.ChronoUnit.DAYS
                                .between(duplicate.getStartDate(), end) + 1);
                        duplicate.setStatus(CycleRecordStatus.COMPLETED);
                        duplicate.setEndDateEstimated(false);
                        cycleRecordRepository.save(duplicate);
                    }
                });
    }
}
