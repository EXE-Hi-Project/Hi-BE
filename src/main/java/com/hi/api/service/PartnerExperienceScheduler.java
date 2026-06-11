package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Service
public class PartnerExperienceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PartnerExperienceScheduler.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final PartnerAccessService partnerAccessService;
    private final CoupleQuestionService questionService;
    private final PartnerCareSuggestionService careSuggestionService;

    public PartnerExperienceScheduler(UserRepository userRepository,
                                      PartnerAccessService partnerAccessService,
                                      CoupleQuestionService questionService,
                                      PartnerCareSuggestionService careSuggestionService) {
        this.userRepository = userRepository;
        this.partnerAccessService = partnerAccessService;
        this.questionService = questionService;
        this.careSuggestionService = careSuggestionService;
    }

    @Scheduled(cron = "0 5 8 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void generateDailyPartnerExperience() {
        LocalDate today = LocalDate.now(APP_ZONE);
        forEachPair((first, second) -> {
            questionService.getOrCreate(first, second, today);
            if (!Boolean.FALSE.equals(partnerAccessService.notificationPreferences(first).getContextualCareSuggestionsEnabled())) {
                careSuggestionService.generate(first, second, today);
            }
            if (!Boolean.FALSE.equals(partnerAccessService.notificationPreferences(second).getContextualCareSuggestionsEnabled())) {
                careSuggestionService.generate(second, first, today);
            }
        });
    }

    private void forEachPair(PairConsumer consumer) {
        Set<String> processed = new HashSet<>();
        for (User user : userRepository.findAll()) {
            if (user.getPartnerId() == null || user.getPartnerId().isBlank()) continue;
            userRepository.findById(user.getPartnerId()).ifPresent(partner -> {
                if (!user.getId().equals(partner.getPartnerId())) return;
                String pairKey = partnerAccessService.pairKey(user.getId(), partner.getId());
                if (!processed.add(pairKey)) return;
                try {
                    consumer.accept(user, partner);
                } catch (Exception error) {
                    log.warn("Không thể tạo trải nghiệm Người ấy cho cặp {}: {}", pairKey, error.getMessage());
                }
            });
        }
    }

    @FunctionalInterface
    private interface PairConsumer {
        void accept(User first, User second);
    }
}
