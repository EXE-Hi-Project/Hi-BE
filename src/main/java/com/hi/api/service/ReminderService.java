package com.hi.api.service;

import com.hi.api.model.Cycle;
import com.hi.api.model.User;
import com.hi.api.repository.CycleRepository;
import com.hi.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;
    private final NotificationService notificationService;

    public ReminderService(UserRepository userRepository, CycleRepository cycleRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void generatePeriodReminders() {
        log.info("Bắt đầu chạy Cron Job tạo nhắc nhở kỳ kinh...");

        List<User> users = userRepository.findByPeriodReminderTrue();
        LocalDate today = LocalDate.now();

        int count = 0;
        for (User user : users) {
            Cycle lastCycle = cycleRepository.findFirstByUserIdOrderByStartDateDesc(user.getId()).orElse(null);
            if (lastCycle == null || lastCycle.getStartDate() == null) continue;

            try {
                LocalDate lastStartDate = LocalDate.parse(lastCycle.getStartDate().substring(0, 10));
                int cycleLength = lastCycle.getCycleLength() != null ? lastCycle.getCycleLength() : user.getDefaultCycleLength();
                LocalDate nextPeriodDate = lastStartDate.plusDays(cycleLength);

                int daysBefore = user.getReminderDaysBefore() != null ? user.getReminderDaysBefore() : 3;
                LocalDate reminderDate = nextPeriodDate.minusDays(daysBefore);

                if (today.equals(reminderDate)) {
                    notificationService.createNotification(
                            user.getId(),
                            "PERIOD_REMINDER",
                            "Sắp tới kỳ kinh nguyệt",
                            "Kỳ kinh của bạn dự kiến sẽ bắt đầu trong " + daysBefore + " ngày nữa. Hãy chuẩn bị sẵn sàng nhé!"
                    );

                    if (Boolean.TRUE.equals(user.getPartnerNotifications()) && user.getPartnerId() != null) {
                        User partner = userRepository.findById(user.getPartnerId()).orElse(null);
                        if (partner != null) {
                            notificationService.createNotification(
                                    partner.getId(),
                                    "PARTNER_PERIOD_REMINDER",
                                    "Kỳ kinh của " + user.getName() + " sắp tới",
                                    "Kỳ kinh của " + user.getName() + " dự kiến sẽ bắt đầu trong " + daysBefore + " ngày nữa. Hãy dành thêm sự quan tâm cho cô ấy nhé!"
                            );
                        }
                    }
                    count++;
                }
            } catch (Exception e) {
                log.warn("Lỗi tính toán chu kỳ cho user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("Hoàn tất tạo nhắc nhở. Đã sinh ra thông báo cho {} người dùng.", count);
    }
}