package com.hi.api.service;

import com.hi.api.dto.request.ConnectPartnerRequest;
import com.hi.api.dto.request.UpdateProfileRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.DailyLog;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogRepository dailyLogRepository;
    private final CycleRecordService cycleRecordService;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository,
                       CycleRecordRepository cycleRecordRepository,
                       DailyLogRepository dailyLogRepository,
                       CycleRecordService cycleRecordService,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.cycleRecordService = cycleRecordService;
        this.notificationService = notificationService;
    }

    public User updateProfile(String userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        String effectiveGender = req.getGender() != null ? req.getGender() : user.getGender();
        validateOnboardingPayload(req, effectiveGender);

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

        User saved = userRepository.save(user);
        if ("female".equalsIgnoreCase(saved.getGender()) && saved.getLastPeriodDate() != null) {
            cycleRecordService.upsertInitialFromProfile(saved);
        }
        return saved;
    }

    private void validateOnboardingPayload(UpdateProfileRequest req, String effectiveGender) {
        if (req.getLastPeriodDate() != null && req.getLastPeriodEndDate() != null) {
            try {
                LocalDate start = LocalDate.parse(req.getLastPeriodDate());
                LocalDate end = LocalDate.parse(req.getLastPeriodEndDate());
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("Ngày kết thúc kỳ kinh phải sau hoặc bằng ngày bắt đầu");
                }
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Ngày kỳ kinh không hợp lệ");
            }
        }

        if (!Boolean.TRUE.equals(req.getOnboardingCompleted())) {
            return;
        }

        if (effectiveGender == null || effectiveGender.isBlank()) {
            throw new IllegalArgumentException("Onboarding yêu cầu chọn giới tính");
        }

        if (req.getGoals() == null || req.getGoals().isEmpty()) {
            throw new IllegalArgumentException("Onboarding yêu cầu chọn ít nhất 1 mục tiêu");
        }

        if ("female".equalsIgnoreCase(effectiveGender)) {
            if (req.getDefaultCycleLength() == null) {
                throw new IllegalArgumentException("Onboarding nữ yêu cầu độ dài chu kỳ");
            }
            if (req.getDefaultPeriodLength() == null) {
                throw new IllegalArgumentException("Onboarding nữ yêu cầu độ dài kỳ kinh");
            }
            if (req.getIrregularCycle() == null) {
                throw new IllegalArgumentException("Onboarding nữ yêu cầu thông tin chu kỳ không đều");
            }
            if (req.getPeriodReminder() == null) {
                throw new IllegalArgumentException("Onboarding nữ yêu cầu thiết lập nhắc kỳ kinh");
            }
            if (req.getReminderDaysBefore() == null) {
                throw new IllegalArgumentException("Onboarding nữ yêu cầu số ngày nhắc trước");
            }
            return;
        }

        if ("male".equalsIgnoreCase(effectiveGender)) {
            if (req.getDefaultCycleLength() != null
                    || req.getDefaultPeriodLength() != null
                    || req.getLastPeriodDate() != null
                    || req.getLastPeriodEndDate() != null
                    || req.getIrregularCycle() != null) {
                throw new IllegalArgumentException("Onboarding nam không nhận dữ liệu chu kỳ cá nhân");
            }
            return;
        }

        throw new IllegalArgumentException("Giới tính onboarding không được hỗ trợ");
    }

    @Transactional
    public Map<String, Object> connectPartner(String userId, ConnectPartnerRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (user.getPartnerId() != null) {
            throw new IllegalArgumentException("Bạn đã kết nối với một đối tác khác. Vui lòng hủy kết nối trước.");
        }

        User partner = userRepository.findByPartnerCode(req.getPartnerCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đối tác với mã này"));

        if (partner.getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể kết nối với chính mình");
        }

        if (partner.getPartnerId() != null) {
            throw new IllegalArgumentException("Đối tác này đã kết nối với một người khác.");
        }
        user.setPartnerId(partner.getId());
        partner.setPartnerId(userId);
        userRepository.save(user);
        userRepository.save(partner);

        notificationService.createNotification(
                user.getId(), "PARTNER_CONNECT", "Kết nối thành công",
                "Bạn đã kết nối bạn đời với " + (partner.getName() != null ? partner.getName() : "đối tác")
        );

        notificationService.createNotification(
                partner.getId(), "PARTNER_CONNECT", "Kết nối thành công",
                (user.getName() != null ? user.getName() : "Một người") + " đã kết nối bạn đời với bạn"
        );

        return Map.of(
                "name", partner.getName() != null ? partner.getName() : "",
                "email", partner.getEmail()
        );
    }

    @Transactional
    public void disconnectPartner(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        String partnerId = user.getPartnerId();

        if (partnerId != null) {
            userRepository.findById(partnerId).ifPresent(p -> {
                p.setPartnerId(null);
                userRepository.save(p);

                // Gửi thông báo cho partner bị ngắt kết nối
                notificationService.createNotification(
                        p.getId(), "PARTNER_DISCONNECT", "Hủy kết nối",
                        "Đối tác của bạn đã hủy kết nối bạn đời"
                );
            });
        }

        user.setPartnerId(null);
        userRepository.save(user);
        notificationService.createNotification(
                userId, "PARTNER_DISCONNECT", "Hủy kết nối",
                "Bạn đã hủy kết nối bạn đời thành công"
        );
    }

    public Map<String, Object> getPartnerData(String userId, int historyLimit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (user.getPartnerId() == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("partner", null);
            response.put("cycles", List.of());
            response.put("history", List.of());
            response.put("insights", null);
            response.put("latestMood", null);
            response.put("latestDailyLogDate", null);
            return response;
        }

        User partner = userRepository.findById(user.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dữ liệu đối tác"));

        List<CycleRecord> cycles = cycleRecordRepository.findByUserIdOrderByStartDateDesc(partner.getId());
        List<CycleRecord> history = cycleRecordRepository
                .findByUserIdOrderByStartDateDesc(partner.getId(), PageRequest.of(0, Math.max(1, Math.min(historyLimit, 100))))
                .getContent();
        DailyLog latestMoodLog = dailyLogRepository.findFirstByUserIdAndMoodScoreIsNotNullOrderByLogDateDesc(partner.getId()).orElse(null);
        Map<String, Object> partnerProfile = new LinkedHashMap<>();
        partnerProfile.put("id", partner.getId());
        partnerProfile.put("name", partner.getName() != null ? partner.getName() : "");
        partnerProfile.put("avatar", partner.getAvatar() != null ? partner.getAvatar() : "");
        partnerProfile.put("gender", partner.getGender() != null ? partner.getGender() : "");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("partner", partnerProfile);
        response.put("cycles", cycles);
        response.put("history", history);
        response.put("insights", cycleRecordService.getInsights(partner.getId()));
        response.put("latestMood", latestMoodLog != null ? latestMoodLog.getMoodScore() : null);
        response.put("latestDailyLogDate", latestMoodLog != null ? latestMoodLog.getLogDate() : null);
        return response;
    }
}
