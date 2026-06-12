package com.hi.api.service;

import com.hi.api.exception.AiQuotaExceededException;
import com.hi.api.model.AiDailyUsage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AiDailyUsageService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MongoTemplate mongoTemplate;

    public AiDailyUsageService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Usage reserve(String userId, int limit) {
        LocalDate today = LocalDate.now(APP_ZONE);
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("usageDate").is(today)
                .and("used").lt(limit));
        Update update = new Update()
                .setOnInsert("userId", userId)
                .setOnInsert("usageDate", today)
                .inc("used", 1)
                .set("updatedAt", Instant.now());
        try {
            AiDailyUsage usage = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    AiDailyUsage.class
            );
            int used = usage != null ? usage.getUsed() : limit;
            return usage(limit, used);
        } catch (DuplicateKeyException ex) {
            Usage usage = current(userId, limit);
            throw new AiQuotaExceededException(limit, usage.used(), usage.resetsAt());
        }
    }

    public Usage current(String userId, int limit) {
        LocalDate today = LocalDate.now(APP_ZONE);
        AiDailyUsage usage = mongoTemplate.findOne(
                Query.query(Criteria.where("userId").is(userId).and("usageDate").is(today)),
                AiDailyUsage.class
        );
        return usage(limit, usage != null ? usage.getUsed() : 0);
    }

    public void release(String userId) {
        LocalDate today = LocalDate.now(APP_ZONE);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("userId").is(userId)
                        .and("usageDate").is(today)
                        .and("used").gt(0)),
                new Update().inc("used", -1).set("updatedAt", Instant.now()),
                AiDailyUsage.class
        );
    }

    private Usage usage(int limit, int used) {
        Instant resetsAt = LocalDate.now(APP_ZONE)
                .plusDays(1)
                .atStartOfDay(APP_ZONE)
                .toInstant();
        return new Usage(limit, used, Math.max(0, limit - used), resetsAt);
    }

    public record Usage(int limit, int used, int remaining, Instant resetsAt) {
    }
}
