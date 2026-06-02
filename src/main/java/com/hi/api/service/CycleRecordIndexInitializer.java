package com.hi.api.service;

import com.hi.api.model.CycleRecord;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class CycleRecordIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Value("${app.migration.health-data.enabled:false}")
    private boolean migrationEnabled;

    @Value("${app.migration.health-data.dry-run:true}")
    private boolean migrationDryRun;

    public CycleRecordIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (migrationEnabled && migrationDryRun) {
            return;
        }
        mongoTemplate.indexOps(CycleRecord.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("startDate", Sort.Direction.ASC)
                        .unique()
                        .named("cycle_record_user_start_idx")
        );
    }
}
