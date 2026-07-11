package com.hi.api.repository;

import com.hi.api.model.SystemMaintenance;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SystemMaintenanceRepository extends MongoRepository<SystemMaintenance, String> {
}
