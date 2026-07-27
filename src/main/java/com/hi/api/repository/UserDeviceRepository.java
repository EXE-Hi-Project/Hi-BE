package com.hi.api.repository;

import com.hi.api.model.UserDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends MongoRepository<UserDevice, String> {
    Optional<UserDevice> findByUserIdAndDeviceId(String userId, String deviceId);
    List<UserDevice> findByUserIdAndActiveTrue(String userId);
    void deleteByUserId(String userId);
}
