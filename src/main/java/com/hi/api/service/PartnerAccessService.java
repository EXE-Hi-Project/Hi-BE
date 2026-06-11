package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PartnerAccessService {

    private final UserRepository userRepository;

    public PartnerAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
    }

    public User requireCurrentPartner(User user) {
        if (user.getPartnerId() == null || user.getPartnerId().isBlank()) {
            throw new IllegalArgumentException("Bạn chưa kết nối với Người ấy");
        }
        User partner = userRepository.findById(user.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Người ấy"));
        if (!user.getId().equals(partner.getPartnerId())) {
            throw new AccessDeniedException("Liên kết Người ấy không còn hợp lệ");
        }
        return partner;
    }

    public boolean isActivePair(String firstUserId, String secondUserId) {
        if (firstUserId == null || secondUserId == null) return false;
        return userRepository.findById(firstUserId)
                .filter(first -> secondUserId.equals(first.getPartnerId()))
                .flatMap(first -> userRepository.findById(secondUserId))
                .map(second -> firstUserId.equals(second.getPartnerId()))
                .orElse(false);
    }

    public String pairKey(String firstUserId, String secondUserId) {
        return List.of(firstUserId, secondUserId).stream().sorted().reduce((a, b) -> a + ":" + b).orElseThrow();
    }

    public User.NotificationPreferences notificationPreferences(User user) {
        return user.getNotificationPreferences() != null
                ? user.getNotificationPreferences()
                : new User.NotificationPreferences();
    }

    public User.PartnerSharingPreferences sharingPreferences(User user) {
        return user.getPartnerSharingPreferences() != null
                ? user.getPartnerSharingPreferences()
                : new User.PartnerSharingPreferences();
    }
}
