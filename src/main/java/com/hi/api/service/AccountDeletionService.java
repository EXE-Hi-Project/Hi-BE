package com.hi.api.service;

import com.hi.api.dto.request.DeleteMyAccountRequest;
import com.hi.api.model.User;
import com.hi.api.repository.UserDeviceRepository;
import com.hi.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private static final List<String> USER_ID_COLLECTIONS = List.of(
            "cycle_records", "cycles", "daily_logs", "daily_log_symptoms",
            "chat_messages", "notifications", "ai_daily_usage", "partner_care_suggestions",
            "password_reset_tokens", "otp_deliveries", "support_tickets",
            "voucher_orders", "transactions", "click_tracking", "affiliate_revenue_events",
            "analytics_events", "couple_place_photos", "couple_place_reactions",
            "couple_place_reviews", "couple_place_reports"
    );

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public void delete(String userId, DeleteMyAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));

        if (!user.getEmail().equalsIgnoreCase(request.getConfirmation().trim())) {
            throw new IllegalArgumentException("Email xác nhận không khớp");
        }
        if ("local".equalsIgnoreCase(user.getAuthProvider())) {
            if (request.getPassword() == null
                    || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Mật khẩu không chính xác");
            }
        }

        unlinkPartner(user);
        USER_ID_COLLECTIONS.forEach(collection ->
                mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), collection));
        mongoTemplate.remove(Query.query(Criteria.where("senderId").is(userId)), "support_messages");
        mongoTemplate.remove(Query.query(Criteria.where("participantIds").is(userId)), "couple_question_sessions");
        mongoTemplate.remove(Query.query(new Criteria().orOperator(
                Criteria.where("user1Id").is(userId),
                Criteria.where("user2Id").is(userId)
        )), "couple_anniversaries");
        userDeviceRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    private void unlinkPartner(User user) {
        if (user.getPartnerId() == null || user.getPartnerId().isBlank()) {
            return;
        }
        userRepository.findById(user.getPartnerId()).ifPresent(partner -> {
            if (user.getId().equals(partner.getPartnerId())) {
                partner.setPartnerId(null);
                userRepository.save(partner);
            }
        });
    }
}
