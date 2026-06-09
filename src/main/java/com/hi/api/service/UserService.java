package com.hi.api.service;

import com.hi.api.dto.request.ConnectPartnerRequest;
import com.hi.api.dto.request.NotificationSettingsRequest;
import com.hi.api.dto.request.UpdateProfileRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.DailyLog;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRecordRepository;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.UserRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
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
    private final CacheManager cacheManager;

    public UserService(UserRepository userRepository,
                       CycleRecordRepository cycleRecordRepository,
                       DailyLogRepository dailyLogRepository,
                       CycleRecordService cycleRecordService,
                       NotificationService notificationService,
                       CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.cycleRecordService = cycleRecordService;
        this.notificationService = notificationService;
        this.cacheManager = cacheManager;
    }

    @CacheEvict(value = "ai_context", key = "#userId")
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
        syncLegacyNotificationFields(user);
        if (req.getOnboardingCompleted() != null) user.setOnboardingCompleted(req.getOnboardingCompleted());

        User saved = userRepository.save(user);
        if ("female".equalsIgnoreCase(saved.getGender()) && saved.getLastPeriodDate() != null) {
            cycleRecordService.upsertInitialFromProfile(saved);
        }
        return saved;
    }

    public User.NotificationPreferences getNotificationSettings(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        return ensureNotificationPreferences(user);
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    public User.NotificationPreferences updateNotificationSettings(String userId, NotificationSettingsRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        User.NotificationPreferences prefs = ensureNotificationPreferences(user);

        if (req.getPeriodUpcomingEnabled() != null) prefs.setPeriodUpcomingEnabled(req.getPeriodUpcomingEnabled());
        if (req.getFertilityWindowEnabled() != null) prefs.setFertilityWindowEnabled(req.getFertilityWindowEnabled());
        if (req.getDailyHealthTipsEnabled() != null) prefs.setDailyHealthTipsEnabled(req.getDailyHealthTipsEnabled());
        if (req.getPartnerPeriodAlertEnabled() != null) prefs.setPartnerPeriodAlertEnabled(req.getPartnerPeriodAlertEnabled());
        if (req.getPartnerMoodUpdatesEnabled() != null) prefs.setPartnerMoodUpdatesEnabled(req.getPartnerMoodUpdatesEnabled());
        if (req.getPartnerCareTipsEnabled() != null) prefs.setPartnerCareTipsEnabled(req.getPartnerCareTipsEnabled());
        if (req.getPushEnabled() != null) prefs.setPushEnabled(req.getPushEnabled());
        if (req.getEmailEnabled() != null) prefs.setEmailEnabled(req.getEmailEnabled());
        // SMS is intentionally disabled for the MVP until a provider is configured.
        prefs.setSmsEnabled(false);
        if (req.getReminderDaysBefore() != null) prefs.setReminderDaysBefore(req.getReminderDaysBefore());
        if (req.getSymptomDailyReminderEnabled() != null) prefs.setSymptomDailyReminderEnabled(req.getSymptomDailyReminderEnabled());
        if (req.getSymptomReminderTime() != null) prefs.setSymptomReminderTime(validTime(req.getSymptomReminderTime(), "20:00"));
        if (req.getPartnerEndOfDayNudgeEnabled() != null) prefs.setPartnerEndOfDayNudgeEnabled(req.getPartnerEndOfDayNudgeEnabled());
        if (req.getPartnerNudgeTime() != null) prefs.setPartnerNudgeTime(validTime(req.getPartnerNudgeTime(), "21:00"));
        if (req.getAiResponseStyle() != null && !req.getAiResponseStyle().isBlank()) {
            String style = req.getAiResponseStyle().trim().toUpperCase();
            prefs.setAiResponseStyle(style);
            user.setAiTone(style);
        }

        user.setNotificationPreferences(prefs);
        user.setPeriodReminder(Boolean.TRUE.equals(prefs.getPeriodUpcomingEnabled()));
        user.setPartnerNotifications(Boolean.TRUE.equals(prefs.getPartnerPeriodAlertEnabled()));
        user.setReminderDaysBefore(prefs.getReminderDaysBefore());
        userRepository.save(user);
        return prefs;
    }

    private User.NotificationPreferences ensureNotificationPreferences(User user) {
        User.NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) {
            prefs = new User.NotificationPreferences();
        }
        if (user.getPeriodReminder() != null) prefs.setPeriodUpcomingEnabled(user.getPeriodReminder());
        if (user.getPartnerNotifications() != null) prefs.setPartnerPeriodAlertEnabled(user.getPartnerNotifications());
        if (user.getReminderDaysBefore() != null) prefs.setReminderDaysBefore(user.getReminderDaysBefore());
        prefs.setSmsEnabled(false);
        if (prefs.getSymptomDailyReminderEnabled() == null) prefs.setSymptomDailyReminderEnabled(true);
        prefs.setSymptomReminderTime(validTime(prefs.getSymptomReminderTime(), "20:00"));
        if (prefs.getPartnerEndOfDayNudgeEnabled() == null) prefs.setPartnerEndOfDayNudgeEnabled(true);
        prefs.setPartnerNudgeTime(validTime(prefs.getPartnerNudgeTime(), "21:00"));
        if (prefs.getAiResponseStyle() == null || prefs.getAiResponseStyle().isBlank()) {
            prefs.setAiResponseStyle(user.getAiTone() != null && !user.getAiTone().isBlank()
                    ? user.getAiTone().toUpperCase()
                    : "FRIENDLY");
        }
        user.setNotificationPreferences(prefs);
        return prefs;
    }

    private String validTime(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalTime.parse(value.trim()).toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void syncLegacyNotificationFields(User user) {
        User.NotificationPreferences prefs = ensureNotificationPreferences(user);
        if (user.getPeriodReminder() != null) prefs.setPeriodUpcomingEnabled(user.getPeriodReminder());
        if (user.getPartnerNotifications() != null) prefs.setPartnerPeriodAlertEnabled(user.getPartnerNotifications());
        if (user.getReminderDaysBefore() != null) prefs.setReminderDaysBefore(user.getReminderDaysBefore());
        prefs.setSmsEnabled(false);
        user.setNotificationPreferences(prefs);
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

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public Map<String, Object> connectPartner(String userId, ConnectPartnerRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if (user.getPartnerId() != null) {
            throw new IllegalArgumentException("Bạn đã kết nối với một người khác. Vui lòng hủy kết nối trước.");
        }

        User partner = userRepository.findByPartnerCode(req.getPartnerCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Người ấy với mã này"));

        if (partner.getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể kết nối với chính mình");
        }

        if (partner.getPartnerId() != null) {
            throw new IllegalArgumentException("Người ấy đã kết nối với một tài khoản khác.");
        }

        user.setPartnerId(partner.getId());
        partner.setPartnerId(userId);
        User savedUser = userRepository.save(user);
        User savedPartner = userRepository.save(partner);

        // Clear partner's cached AI context
        if (cacheManager != null && cacheManager.getCache("ai_context") != null) {
            cacheManager.getCache("ai_context").evict(partner.getId());
        }

        notificationService.createNotification(
                savedUser.getId(), "PARTNER_CONNECT", "Kết nối thành công",
                "Bạn đã kết nối với " + displayName(savedPartner, "Người ấy")
        );
        notificationService.createNotification(
                savedPartner.getId(), "PARTNER_CONNECT", "Kết nối thành công",
                displayName(savedUser, "Một người") + " đã kết nối với bạn"
        );

        return Map.of(
                "id", savedPartner.getId(),
                "name", savedPartner.getName() != null ? savedPartner.getName() : "",
                "email", savedPartner.getEmail()
        );
    }

    @CacheEvict(value = "ai_context", key = "#userId")
    @Transactional
    public Map<String, Object> disconnectPartner(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        String partnerId = user.getPartnerId();
        Map<String, Object> previousPartner = new LinkedHashMap<>();

        if (partnerId != null) {
            userRepository.findById(partnerId).ifPresent(partner -> {
                previousPartner.put("id", partner.getId());
                previousPartner.put("name", partner.getName() != null ? partner.getName() : "");
                previousPartner.put("email", partner.getEmail());

                partner.setPartnerId(null);
                userRepository.save(partner);

                // Clear partner's cached AI context
                if (cacheManager != null && cacheManager.getCache("ai_context") != null) {
                    cacheManager.getCache("ai_context").evict(partner.getId());
                }

                notificationService.createNotification(
                        partner.getId(), "PARTNER_DISCONNECT", "Hủy kết nối",
                        displayName(user, "Người ấy") + " đã hủy kết nối với bạn"
                );
            });
        }

        user.setPartnerId(null);
        User saved = userRepository.save(user);
        if (partnerId != null) {
            notificationService.createNotification(
                    userId, "PARTNER_DISCONNECT", "Hủy kết nối",
                    "Bạn đã hủy kết nối thành công"
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", saved);
        response.put("previousPartner", previousPartner.isEmpty() ? null : previousPartner);
        response.put("partnerDisconnected", true);
        return response;
    }

    public Map<String, Object> getPartnerData(String userId, int historyPage, int historyLimit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        int safePage = Math.max(0, historyPage);
        int safeLimit = Math.max(1, Math.min(historyLimit, 100));

        if (user.getPartnerId() == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("partner", null);
            response.put("cycles", List.of());
            response.put("history", historyResponse(List.of(), 0, safePage, safeLimit, false));
            response.put("insights", null);
            response.put("latestMood", null);
            response.put("latestDailyLogDate", null);
            return response;
        }

        User partner = userRepository.findById(user.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dữ liệu Người ấy"));

        List<CycleRecord> cycles = cycleRecordRepository.findByUserIdOrderByStartDateDesc(partner.getId());
        Page<CycleRecord> historyPageResult = cycleRecordRepository
                .findByUserIdOrderByStartDateDesc(partner.getId(), PageRequest.of(safePage, safeLimit));
        DailyLog latestMoodLog = dailyLogRepository
                .findFirstByUserIdAndMoodScoreIsNotNullOrderByLogDateDesc(partner.getId())
                .orElse(null);

        Map<String, Object> partnerProfile = new LinkedHashMap<>();
        partnerProfile.put("id", partner.getId());
        partnerProfile.put("name", partner.getName() != null ? partner.getName() : "");
        partnerProfile.put("avatar", partner.getAvatar() != null ? partner.getAvatar() : "");
        partnerProfile.put("gender", partner.getGender() != null ? partner.getGender() : "");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("partner", partnerProfile);
        response.put("cycles", cycles);
        response.put("history", historyResponse(
                historyPageResult.getContent(),
                historyPageResult.getTotalElements(),
                safePage,
                safeLimit,
                historyPageResult.hasNext()
        ));
        response.put("insights", cycleRecordService.getInsights(partner.getId()));
        response.put("latestMood", moodResponse(latestMoodLog));
        response.put("latestDailyLogDate", latestMoodLog != null ? latestMoodLog.getLogDate() : null);
        return response;
    }

    private Map<String, Object> moodResponse(DailyLog log) {
        if (log == null || log.getMoodScore() == null) {
            return null;
        }
        Map<String, Object> mood = new LinkedHashMap<>();
        mood.put("moodScore", log.getMoodScore());
        mood.put("label", moodLabel(log.getMoodScore()));
        mood.put("logDate", log.getLogDate());
        return mood;
    }

    private String moodLabel(Integer moodScore) {
        if (moodScore == null) {
            return "Chưa rõ";
        }
        return switch (Math.max(1, Math.min(5, moodScore))) {
            case 1 -> "Bực bội";
            case 2 -> "Lo lắng hoặc mệt mỏi";
            case 4 -> "Bình tĩnh";
            case 5 -> "Vui vẻ";
            default -> "Bình thường";
        };
    }

    private Map<String, Object> historyResponse(List<CycleRecord> items, long total, int page, int limit, boolean hasMore) {
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("items", items);
        history.put("total", total);
        history.put("page", page);
        history.put("limit", limit);
        history.put("hasMore", hasMore);
        return history;
    }

    private String displayName(User user, String fallback) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : fallback;
    }
}
