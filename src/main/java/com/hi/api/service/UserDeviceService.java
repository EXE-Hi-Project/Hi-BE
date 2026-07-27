package com.hi.api.service;

import com.hi.api.dto.request.RegisterUserDeviceRequest;
import com.hi.api.model.UserDevice;
import com.hi.api.repository.UserDeviceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserDeviceService {
    private final UserDeviceRepository userDeviceRepository;

    public UserDeviceService(UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    public UserDevice register(String userId, RegisterUserDeviceRequest request) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, request.getDeviceId())
                .orElseGet(UserDevice::new);
        device.setUserId(userId);
        device.setDeviceId(request.getDeviceId().trim());
        device.setPlatform(request.getPlatform().toLowerCase());
        device.setExpoPushToken(request.getExpoPushToken().trim());
        device.setAppVersion(request.getAppVersion());
        device.setActive(true);
        device.setLastSeenAt(Instant.now());
        return userDeviceRepository.save(device);
    }

    public void deactivate(String userId, String deviceId) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId).ifPresent(device -> {
            device.setActive(false);
            device.setExpoPushToken(null);
            device.setLastSeenAt(Instant.now());
            userDeviceRepository.save(device);
        });
    }
}
