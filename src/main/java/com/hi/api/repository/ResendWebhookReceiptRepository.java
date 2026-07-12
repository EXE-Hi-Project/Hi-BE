package com.hi.api.repository;

import com.hi.api.model.ResendWebhookReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResendWebhookReceiptRepository extends MongoRepository<ResendWebhookReceipt, String> {}
