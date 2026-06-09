package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.model.User;
import com.hi.api.repository.DailyLogRepository;
import com.hi.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final UserRepository userRepository;
    private final CycleRecordService cycleRecordService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final DailyLogRepository dailyLogRepository;
    private final ChatBoxAIService chatBoxAIService;
    private final ChatContextService chatContextService;

    public ReminderService(UserRepository userRepository,
                           CycleRecordService cycleRecordService,
                           NotificationService notificationService,
                           EmailService emailService,
                           DailyLogRepository dailyLogRepository,
                           ChatBoxAIService chatBoxAIService,
                           ChatContextService chatContextService) {
        this.userRepository = userRepository;
        this.cycleRecordService = cycleRecordService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.dailyLogRepository = dailyLogRepository;
        this.chatBoxAIService = chatBoxAIService;
        this.chatContextService = chatContextService;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void generateDailyReminders() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        log.info("Bắt đầu tạo nhắc nhở hằng ngày cho {}", today);

        List<User> users = userRepository.findAll();
        int created = 0;
        for (User user : users) {
            if (!isActiveUser(user)) continue;
            try {
                created += createDailyCheckIn(user, today);
                created += createPeriodReminder(user, today);
                created += createFertilityReminder(user, today);
            } catch (Exception e) {
                log.warn("Lỗi tạo nhắc nhở cho user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Hoàn tất tạo nhắc nhở hằng ngày. Tổng xử lý: {}", created);
    }

    public void generatePeriodReminders() {
        generateDailyReminders();
    }

    @Scheduled(cron = "0 0/15 * * * ?", zone = "Asia/Ho_Chi_Minh")
    public void generateSymptomLogReminders() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone).truncatedTo(ChronoUnit.MINUTES);

        for (User user : userRepository.findAll()) {
            if (!isActiveUser(user) || !"female".equalsIgnoreCase(user.getGender())) continue;
            try {
                User.NotificationPreferences prefs = prefs(user);
                CycleRecordInsightResponse insights = cycleRecordService.getInsights(user.getId());
                if (!shouldAskForSymptomLog(insights, today)) continue;
                if (dailyLogRepository.findByUserIdAndLogDate(user.getId(), today).isPresent()) continue;

                if (Boolean.TRUE.equals(prefs.getSymptomDailyReminderEnabled()) && isDue(now, prefs.getSymptomReminderTime(), "20:00")) {
                    sendSymptomReminderToFemale(user, today, false);
                }

                if (Boolean.TRUE.equals(prefs.getPartnerEndOfDayNudgeEnabled()) && isDue(now, prefs.getPartnerNudgeTime(), "21:00")) {
                    sendSymptomReminderToFemale(user, today, true);
                    sendPartnerSymptomNudge(user, today);
                }
            } catch (Exception e) {
                log.warn("Không thể tạo nhắc ghi triệu chứng cho user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private int createDailyCheckIn(User user, LocalDate today) {
        User.NotificationPreferences prefs = prefs(user);
        if (!Boolean.TRUE.equals(prefs.getDailyHealthTipsEnabled())) return 0;

        String dedupeKey = "DAILY_CHECK_IN:" + user.getId() + ":" + today;
        boolean alreadySent = notificationService.existsByDedupeKey(user.getId(), "DAILY_CHECK_IN", dedupeKey);
        String message = getDailyTipMessage(user);

        if (!alreadySent) {
            notificationService.createIdempotentNotification(
                    user.getId(),
                    "DAILY_CHECK_IN",
                    "Hi hỏi thăm hôm nay",
                    message,
                    "/notifications",
                    dedupeKey,
                    Map.of("date", today.toString())
            );

            if (Boolean.TRUE.equals(prefs.getEmailEnabled())) {
                emailService.sendDailyCheckInEmail(
                        user.getEmail(),
                        displayName(user, "bạn"),
                        message
                );
            }
        }

        if (Boolean.TRUE.equals(prefs.getPartnerCareTipsEnabled()) && user.getPartnerId() != null) {
            userRepository.findById(user.getPartnerId()).filter(this::isActiveUser).ifPresent(partner -> {
                User.NotificationPreferences partnerPrefs = prefs(partner);
                String partnerDedupeKey = "PARTNER_DAILY_CHECK_IN:" + partner.getId() + ":" + today;
                boolean partnerAlreadySent = notificationService.existsByDedupeKey(partner.getId(), "DAILY_CHECK_IN", partnerDedupeKey);
                String partnerMessage = "Hãy gửi một lời hỏi thăm nhẹ nhàng cho " + displayName(user, "Người ấy") + " hôm nay.";
                
                if (!partnerAlreadySent) {
                    notificationService.createIdempotentNotification(
                            partner.getId(),
                            "DAILY_CHECK_IN",
                            "Nhắc quan tâm Người ấy",
                            partnerMessage,
                            "/notifications",
                            partnerDedupeKey,
                            Map.of("date", today.toString(), "partnerUserId", user.getId())
                    );

                    if (Boolean.TRUE.equals(partnerPrefs.getEmailEnabled())) {
                        emailService.sendPartnerDailyCheckInEmail(
                                partner.getEmail(),
                                displayName(partner, "bạn"),
                                displayName(user, "Người ấy"),
                                partnerMessage
                        );
                    }
                }
            });
        }
        return 1;
    }

    private int createPeriodReminder(User user, LocalDate today) {
        User.NotificationPreferences prefs = prefs(user);
        if (!Boolean.TRUE.equals(prefs.getPeriodUpcomingEnabled())) return 0;

        CycleRecordInsightResponse insights = cycleRecordService.getInsights(user.getId());
        LocalDate nextPeriodDate = insights.getEstimatedPeriodStartDate();
        if (nextPeriodDate == null) return 0;

        int daysBefore = prefs.getReminderDaysBefore() != null ? prefs.getReminderDaysBefore() : 3;
        LocalDate reminderDate = nextPeriodDate.minusDays(daysBefore);
        if (!today.equals(reminderDate)) return 0;

        String dedupeKey = "PERIOD_UPCOMING:" + user.getId() + ":" + today;
        boolean alreadySent = notificationService.existsByDedupeKey(user.getId(), "PERIOD_UPCOMING", dedupeKey);
        String message = "Kỳ kinh dự kiến sẽ bắt đầu trong " + daysBefore + " ngày nữa. Đây chỉ là dự đoán tham khảo, hãy chuẩn bị nhẹ nhàng nhé.";

        if (!alreadySent) {
            notificationService.createIdempotentNotification(
                    user.getId(),
                    "PERIOD_UPCOMING",
                    "Sắp tới kỳ kinh",
                    message,
                    "/cycles",
                    dedupeKey,
                    Map.of("estimatedPeriodStartDate", nextPeriodDate.toString(), "daysBefore", daysBefore)
            );

            if (Boolean.TRUE.equals(prefs.getEmailEnabled())) {
                emailService.sendPeriodUpcomingEmail(
                        user.getEmail(),
                        displayName(user, "bạn"),
                        daysBefore,
                        nextPeriodDate
                );
            }
        }

        if (Boolean.TRUE.equals(prefs.getPartnerPeriodAlertEnabled()) && user.getPartnerId() != null) {
            userRepository.findById(user.getPartnerId()).filter(this::isActiveUser).ifPresent(partner -> {
                User.NotificationPreferences partnerPrefs = prefs(partner);
                String partnerDedupeKey = "PARTNER_PERIOD_UPCOMING:" + partner.getId() + ":" + today;
                boolean partnerAlreadySent = notificationService.existsByDedupeKey(partner.getId(), "PARTNER_PERIOD_UPCOMING", partnerDedupeKey);
                String partnerMessage = "Kỳ kinh của " + displayName(user, "Người ấy") + " dự kiến sẽ bắt đầu trong " + daysBefore + " ngày nữa. Hãy dành thêm sự quan tâm nhé.";
                
                if (!partnerAlreadySent) {
                    notificationService.createIdempotentNotification(
                            partner.getId(),
                            "PARTNER_PERIOD_UPCOMING",
                            "Người ấy sắp tới kỳ",
                            partnerMessage,
                            "/male-dashboard",
                            partnerDedupeKey,
                            Map.of("estimatedPeriodStartDate", nextPeriodDate.toString(), "partnerUserId", user.getId(), "daysBefore", daysBefore)
                    );

                    if (Boolean.TRUE.equals(partnerPrefs.getEmailEnabled())) {
                        emailService.sendPartnerPeriodUpcomingEmail(
                                partner.getEmail(),
                                displayName(partner, "bạn"),
                                displayName(user, "Người ấy"),
                                daysBefore
                        );
                    }
                }
            });
        }
        return 1;
    }

    private int createFertilityReminder(User user, LocalDate today) {
        User.NotificationPreferences prefs = prefs(user);
        if (!Boolean.TRUE.equals(prefs.getFertilityWindowEnabled())) return 0;

        CycleRecordInsightResponse insights = cycleRecordService.getInsights(user.getId());
        LocalDate start = insights.getFertileWindowStartDate();
        LocalDate end = insights.getFertileWindowEndDate();
        if (start == null || end == null || today.isBefore(start) || today.isAfter(end)) return 0;

        String dedupeKey = "FERTILITY_WINDOW:" + user.getId() + ":" + today;
        String message = "Hôm nay nằm trong cửa sổ thụ thai ước tính. Mốc này chỉ mang tính tham khảo và không thay thế biện pháp tránh thai.";
        if (Boolean.TRUE.equals(prefs.getPushEnabled())) {
            notificationService.createIdempotentNotification(
                    user.getId(),
                    "FERTILITY_WINDOW",
                    "Cửa sổ thụ thai ước tính",
                    message,
                    "/calendar",
                    dedupeKey,
                    Map.of("date", today.toString())
            );
        }
        return 1;
    }

    private boolean shouldAskForSymptomLog(CycleRecordInsightResponse insights, LocalDate today) {
        if (insights == null) return false;
        String status = insights.getPeriodStatus();
        if ("CONFIRMED".equalsIgnoreCase(status) || "PREDICTED".equalsIgnoreCase(status) || "DELAYED".equalsIgnoreCase(status)) {
            return true;
        }
        LocalDate start = insights.getEstimatedPeriodStartDate();
        LocalDate end = insights.getEstimatedPeriodEndDate();
        return start != null && end != null && !today.isBefore(start) && !today.isAfter(end);
    }

    private void sendSymptomReminderToFemale(User user, LocalDate today, boolean endOfDay) {
        User.NotificationPreferences prefs = prefs(user);
        String type = endOfDay ? "SYMPTOM_LOG_END_OF_DAY" : "SYMPTOM_LOG_REMINDER";
        String dedupeKey = type + ":" + user.getId() + ":" + today;
        boolean alreadySent = notificationService.existsByDedupeKey(user.getId(), type, dedupeKey);
        String message = endOfDay
                ? "Ngày hôm nay sắp khép lại rồi. Nếu bạn đang trong kỳ, ghi lại lượng kinh và triệu chứng một chút thôi để Hi hiểu cơ thể bạn hơn nhé."
                : "Hi ghé nhắc nhẹ: nếu hôm nay bạn có kinh hoặc thấy triệu chứng, hãy ghi lại vài chạm để dự đoán lần sau chính xác hơn.";

        if (!alreadySent) {
            notificationService.createIdempotentNotification(
                    user.getId(),
                    type,
                    "Nhắc ghi triệu chứng hôm nay",
                    message,
                    "/cycles",
                    dedupeKey,
                    Map.of("date", today.toString())
            );

            if (Boolean.TRUE.equals(prefs.getEmailEnabled())) {
                emailService.sendSymptomReminderEmail(
                        user.getEmail(),
                        displayName(user, "bạn"),
                        message,
                        endOfDay
                );
            }
        }
    }

    private void sendPartnerSymptomNudge(User femaleUser, LocalDate today) {
        if (femaleUser.getPartnerId() == null || femaleUser.getPartnerId().isBlank()) return;
        userRepository.findById(femaleUser.getPartnerId()).filter(this::isActiveUser).ifPresent(partner -> {
            User.NotificationPreferences partnerPrefs = prefs(partner);
            String type = "PARTNER_SYMPTOM_LOG_NUDGE";
            String dedupeKey = type + ":" + partner.getId() + ":" + today;
            boolean alreadySent = notificationService.existsByDedupeKey(partner.getId(), type, dedupeKey);
            String message = displayName(femaleUser, "Người ấy")
                    + " có thể đang cần cập nhật cảm giác hôm nay. Bạn có thể nhắn một câu dịu dàng, hoặc nhắc nhẹ nếu cô ấy muốn ghi lại triệu chứng.";

            if (!alreadySent) {
                notificationService.createIdempotentNotification(
                        partner.getId(),
                        type,
                        "Gợi ý quan tâm Người ấy",
                        message,
                        "/male-dashboard",
                        dedupeKey,
                        Map.of("date", today.toString(), "partnerUserId", femaleUser.getId())
                );

                if (Boolean.TRUE.equals(partnerPrefs.getEmailEnabled())) {
                    emailService.sendPartnerSymptomNudgeEmail(
                            partner.getEmail(),
                            displayName(partner, "bạn"),
                            displayName(femaleUser, "Người ấy"),
                            message
                    );
                }
            }
        });
    }

    private boolean isDue(LocalTime now, String configuredTime, String fallback) {
        LocalTime target = parseTime(configuredTime, fallback);
        return !now.isBefore(target) && now.isBefore(target.plusMinutes(15));
    }

    private LocalTime parseTime(String value, String fallback) {
        try {
            return LocalTime.parse(value != null && !value.isBlank() ? value : fallback);
        } catch (Exception ignored) {
            return LocalTime.parse(fallback);
        }
    }

    private boolean isActiveUser(User user) {
        return user != null
                && user.getId() != null
                && user.getEmail() != null
                && !"LOCKED".equalsIgnoreCase(user.getAccountStatus())
                && !"DELETED".equalsIgnoreCase(user.getAccountStatus());
    }

    private String displayName(User user, String fallback) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : fallback;
    }

    private User.NotificationPreferences prefs(User user) {
        User.NotificationPreferences prefs = user.getNotificationPreferences();
        if (prefs == null) prefs = new User.NotificationPreferences();
        if (user.getPeriodReminder() != null) prefs.setPeriodUpcomingEnabled(user.getPeriodReminder());
        if (user.getPartnerNotifications() != null) prefs.setPartnerPeriodAlertEnabled(user.getPartnerNotifications());
        if (user.getReminderDaysBefore() != null) prefs.setReminderDaysBefore(user.getReminderDaysBefore());
        prefs.setSmsEnabled(false);
        if (prefs.getSymptomDailyReminderEnabled() == null) prefs.setSymptomDailyReminderEnabled(true);
        if (prefs.getSymptomReminderTime() == null || prefs.getSymptomReminderTime().isBlank()) prefs.setSymptomReminderTime("20:00");
        if (prefs.getPartnerEndOfDayNudgeEnabled() == null) prefs.setPartnerEndOfDayNudgeEnabled(true);
        if (prefs.getPartnerNudgeTime() == null || prefs.getPartnerNudgeTime().isBlank()) prefs.setPartnerNudgeTime("21:00");
        if (prefs.getAiResponseStyle() == null || prefs.getAiResponseStyle().isBlank()) prefs.setAiResponseStyle("FRIENDLY");
        return prefs;
    }

    private String getDailyTipMessage(User user) {
        // 1. Try AI Generation
        try {
            if (chatBoxAIService != null && chatContextService != null) {
                String context = chatContextService.buildContext(user.getId());
                String prompt = "Hãy tạo 1 lời khuyên sức khỏe hoặc lời hỏi thăm ngắn gọn, ấm áp (dưới 80 từ) cho ngày hôm nay bằng tiếng Việt. Hãy xưng là 'Hi' và gọi người dùng là 'bạn'. Tập trung vào trạng thái chu kỳ hoặc triệu chứng gần đây của họ nếu có.";
                String aiResponse = chatBoxAIService.chatOnce(prompt, user.getId(), context);
                if (aiResponse != null && !aiResponse.isBlank() && !aiResponse.contains("Hi AI đang cần cấu hình")) {
                    return aiResponse.trim();
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tạo lời khuyên AI cho user {}: {}", user.getId(), e.getMessage());
        }

        // 2. Fallback to Rule-based Tip
        return getFallbackTip(user);
    }

    private String getFallbackTip(User user) {
        if ("male".equalsIgnoreCase(user.getGender())) {
            return "Hôm nay của bạn thế nào rồi? Đừng quên gửi lời hỏi thăm nhẹ nhàng đến Người ấy và chăm sóc bản thân thật tốt nhé!";
        }
        
        // Female
        try {
            CycleRecordInsightResponse insights = cycleRecordService.getInsights(user.getId());
            if (insights != null) {
                String periodStatus = insights.getPeriodStatus(); // "CONFIRMED", "PREDICTED", "DELAYED", "NONE"
                if ("CONFIRMED".equalsIgnoreCase(periodStatus) || "PREDICTED".equalsIgnoreCase(periodStatus)) {
                    return "Bạn đang trong kỳ kinh nguyệt nè. Hãy nhớ giữ ấm bụng, uống nhiều nước ấm và nghỉ ngơi nhiều hơn nhé. Hi luôn ở bên bạn!";
                } else if ("DELAYED".equalsIgnoreCase(periodStatus)) {
                    return "Kỳ kinh của bạn đang trễ một xíu rồi đó. Hãy thư giãn đầu óc, tránh căng thẳng và ghi lại các biểu hiện để Hi theo dõi nha.";
                }
                
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                LocalDate fertileStart = insights.getFertileWindowStartDate();
                LocalDate fertileEnd = insights.getFertileWindowEndDate();
                if (fertileStart != null && fertileEnd != null && !today.isBefore(fertileStart) && !today.isAfter(fertileEnd)) {
                    return "Bạn đang trong cửa sổ thụ thai ước tính đó. Hãy lắng nghe cơ thể, ăn uống đầy đủ dinh dưỡng và giữ tinh thần vui tươi nhé!";
                }
            }
        } catch (Exception ignored) {}
        
        return "Hôm nay của bạn thế nào rồi? Hãy dành ra vài giây chạm nhẹ để ghi lại cảm xúc và triệu chứng hôm nay nhé, Hi đợi bạn!";
    }
}
