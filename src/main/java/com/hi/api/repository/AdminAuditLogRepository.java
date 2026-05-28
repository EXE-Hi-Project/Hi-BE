package com.hi.api.repository;

import com.hi.api.model.AdminAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminAuditLogRepository extends MongoRepository<AdminAuditLog, String> {
}