package com.hi.api.service;

import com.hi.api.dto.request.ConnectPartnerRequest;
import com.hi.api.dto.request.UpdateProfileRequest;
import com.hi.api.model.Cycle;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;

    public UserService(UserRepository userRepository, CycleRepository cycleRepository) {
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
    }

    public User updateProfile(String userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (req.getName() != null) user.setName(req.getName());
        if (req.getGender() != null) user.setGender(req.getGender());
        if (req.getBirthDate() != null) user.setBirthDate(req.getBirthDate());
        if (req.getHeight() != null) user.setHeight(req.getHeight());
        if (req.getWeight() != null) user.setWeight(req.getWeight());
        if (req.getInterests() != null) user.setInterests(req.getInterests());
        if (req.getGoals() != null) user.setGoals(req.getGoals());
        if (req.getDefaultCycleLength() != null) user.setDefaultCycleLength(req.getDefaultCycleLength());
        if (req.getDefaultPeriodLength() != null) user.setDefaultPeriodLength(req.getDefaultPeriodLength());
        if (req.getLastPeriodDate() != null) user.setLastPeriodDate(req.getLastPeriodDate());
        if (req.getLastPeriodEndDate() != null) user.setLastPeriodEndDate(req.getLastPeriodEndDate());
        if (req.getIrregularCycle() != null) user.setIrregularCycle(req.getIrregularCycle());
        if (req.getAiPersonality() != null) user.setAiPersonality(req.getAiPersonality());
        if (req.getAiTone() != null) user.setAiTone(req.getAiTone());
        if (req.getPeriodReminder() != null) user.setPeriodReminder(req.getPeriodReminder());
        if (req.getReminderDaysBefore() != null) user.setReminderDaysBefore(req.getReminderDaysBefore());
        if (req.getPartnerNotifications() != null) user.setPartnerNotifications(req.getPartnerNotifications());
        if (req.getOnboardingCompleted() != null) user.setOnboardingCompleted(req.getOnboardingCompleted());

        return userRepository.save(user);
    }

    public Map<String, Object> connectPartner(String userId, ConnectPartnerRequest req) {
        User partner = userRepository.findByPartnerCode(req.getPartnerCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đối tác với mã này"));

        if (partner.getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể kết nối với chính mình");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        user.setPartnerId(partner.getId());
        partner.setPartnerId(userId);

        userRepository.save(user);
        userRepository.save(partner);

        return Map.of(
                "name", partner.getName() != null ? partner.getName() : "",
                "email", partner.getEmail()
        );
    }

    public void disconnectPartner(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (user.getPartnerId() != null) {
            userRepository.findById(user.getPartnerId()).ifPresent(p -> {
                p.setPartnerId(null);
                userRepository.save(p);
            });
        }
        user.setPartnerId(null);
        userRepository.save(user);
    }

    public List<Cycle> getPartnerCycles(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (user.getPartnerId() == null) {
            throw new IllegalArgumentException("Chưa kết nối với đối tác");
        }

        return cycleRepository.findByUserIdOrderByStartDateDesc(user.getPartnerId());
    }
}
