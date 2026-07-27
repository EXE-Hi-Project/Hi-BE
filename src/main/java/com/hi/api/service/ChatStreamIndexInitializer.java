package com.hi.api.service;

import com.hi.api.model.ChatStreamRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Order(210)
public class ChatStreamIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public ChatStreamIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongoTemplate.indexOps(ChatStreamRequest.class).ensureIndex(
                new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("idempotencyKey", Sort.Direction.ASC)
                        .unique()
                        .named("user_idempotency_unique")
        );
        mongoTemplate.indexOps(ChatStreamRequest.class).ensureIndex(
                new Index()
                        .on("createdAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(7))
                        .named("chat_stream_request_ttl")
        );
    }
}
