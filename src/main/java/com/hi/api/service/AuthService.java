package com.hi.api.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.hash.Hashing;
import com.hi.api.dto.request.*;
import com.hi.api.model.PasswordResetToken;
import com.hi.api.model.User;
import com.hi.api.repository.PasswordResetTokenRepository;
import com.hi.api.repository.UserRepository;
import com.hi.api.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int OTP_LOCK_MINUTES = 15;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    @Value("${app.admin.emails:}")
    private String adminEmailsStr;

    @Value("${app.google.client-id:}")
    private String googleClientId;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil, PasswordResetTokenRepository tokenRepository,
            RestTemplate restTemplate,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenRepository = tokenRepository;
        this.restTemplate = restTemplate;
        this.emailService = emailService;
    }

    private String hashToken(String plainToken) {
        return Hashing.sha256()
                .hashString(plainToken, StandardCharsets.UTF_8)
                .toString();
    }

    private List<String> getAdminEmails() {
        if (adminEmailsStr == null || adminEmailsStr.isBlank())
            return List.of();
        return java.util.Arrays.stream(adminEmailsStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String generatePartnerCode() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private void assertAccountCanAuthenticate(User user) {
        String status = user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE";
        if ("PENDING_ACTIVATION".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("PENDING_ACTIVATION");
        }
        if ("LOCKED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Tài khoản của bạn đang bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        if ("DELETED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Tài khoản không còn hoạt động.");
        }
    }

    public Map<String, Object> register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        User user = new User();
        user.setName(req.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setGender(req.getGender());
        user.setAuthProvider("local");
        user.setRole(getAdminEmails().contains(email) ? "admin" : "user");
        user.setPartnerCode(generatePartnerCode());
        user.setAccountStatus("PENDING_ACTIVATION");

        userRepository.save(user);

        // Sinh mã OTP 6 số ngẫu nhiên
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        String otpHash = hashToken(otp);

        tokenRepository.save(newOtpToken(user.getId(), otpHash));

        try {
            emailService.sendRegistrationOtpEmail(user.getEmail(), user.getName(), otp);
        } catch (Exception ex) {
            log.error("[REGISTER] Gửi email kích hoạt OTP thất bại cho {}: {}", email, ex.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("email", email);
        response.put("pendingActivation", true);
        return response;
    }

    public Map<String, Object> login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));

        assertAccountCanAuthenticate(user);

        if (user.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng");
        }

        // Fix role if missing
        if (user.getRole() == null) {
            user.setRole(getAdminEmails().contains(user.getEmail()) ? "admin" : "user");
            userRepository.save(user);
        }

        return buildAuthPayload(user);
    }

    public Map<String, Object> googleAuth(GoogleAuthRequest req) throws Exception {
        String googleId, email, name, picture;

        if (req.getCredential() == null || req.getCredential().isBlank()) {
            throw new IllegalArgumentException("Thiếu Google credential");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(req.getCredential());
        if (idToken == null) {
            throw new IllegalArgumentException("Google token không hợp lệ");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        googleId = payload.getSubject();
        email = payload.getEmail();
        name = (String) payload.get("name");
        picture = (String) payload.get("picture");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tài khoản Google chưa có email hợp lệ");
        }
        email = email.trim().toLowerCase();

        final String finalGoogleId = googleId;
        final String finalEmail = email;
        User user = userRepository.findByGoogleIdOrEmail(finalGoogleId, finalEmail).orElse(null);

        if (user == null) {
            user = new User();
            user.setGoogleId(googleId);
            user.setEmail(email);
            user.setName(name);
            user.setAvatar(picture != null ? picture : "");
            user.setAuthProvider("google");
            user.setRole(getAdminEmails().contains(email) ? "admin" : "user");
            user.setPartnerCode(generatePartnerCode());
        } else {
            assertAccountCanAuthenticate(user);
            if (user.getGoogleId() == null)
                user.setGoogleId(googleId);
            if (picture != null && !picture.isBlank() && (user.getAvatar() == null || user.getAvatar().isBlank())) {
                user.setAvatar(picture);
            }
            user.setAuthProvider("google");
        }

        userRepository.save(user);
        return buildAuthPayload(user);
    }

    public Map<String, Object> facebookAuth(GoogleAuthRequest req) {
        if (req.getAccessToken() == null || req.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("Thiếu Facebook access token");
        }

        String url = "https://graph.facebook.com/me?fields=id,name,email,picture.type(large)&access_token="
                + req.getAccessToken();
        Map<String, Object> userInfo;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            userInfo = response;
        } catch (RestClientResponseException e) {
            throw new IllegalArgumentException("Facebook token không hợp lệ");
        }
        if (userInfo == null) {
            throw new IllegalArgumentException("Facebook token không hợp lệ");
        }

        String facebookId = (String) userInfo.get("id");
        if (facebookId == null || facebookId.isBlank()) {
            throw new IllegalArgumentException("Facebook token không hợp lệ");
        }
        if (req.getUserID() != null && !req.getUserID().isBlank() && !facebookId.equals(req.getUserID())) {
            throw new IllegalArgumentException("Facebook user không khớp với access token");
        }

        String email = (String) userInfo.get("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tài khoản Facebook chưa chia sẻ email hợp lệ");
        }
        email = email.trim().toLowerCase();

        String name = (String) userInfo.get("name");
        String picture = "";
        Object pictureObj = userInfo.get("picture");
        if (pictureObj instanceof Map<?, ?> pictureMap) {
            Object dataObj = pictureMap.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                Object urlObj = dataMap.get("url");
                if (urlObj instanceof String urlStr) {
                    picture = urlStr;
                }
            }
        }

        final String finalFacebookId = facebookId;
        final String finalEmail = email;
        User user = userRepository.findByFacebookIdOrEmail(finalFacebookId, finalEmail).orElse(null);

        if (user == null) {
            user = new User();
            user.setFacebookId(facebookId);
            user.setEmail(email);
            user.setName(name);
            user.setAvatar(picture);
            user.setAuthProvider("facebook");
            user.setRole(getAdminEmails().contains(email) ? "admin" : "user");
            user.setPartnerCode(generatePartnerCode());
        } else {
            assertAccountCanAuthenticate(user);
            if (user.getFacebookId() == null)
                user.setFacebookId(facebookId);
            if (!picture.isBlank() && (user.getAvatar() == null || user.getAvatar().isBlank())) {
                user.setAvatar(picture);
            }
            user.setAuthProvider("facebook");
        }

        userRepository.save(user);
        return buildAuthPayload(user);
    }

    public Map<String, Object> buildAuthPayload(User user) {
        assertAccountCanAuthenticate(user);
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", jwtUtil.generateToken(user.getId()));
        payload.put("user", sanitizeUser(user));
        return payload;
    }

    private Map<String, Object> sanitizeUser(User user) {
        // Use LinkedHashMap to allow null values (Map.of() rejects nulls)
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("_id", user.getId() != null ? user.getId() : "");
        m.put("name", user.getName() != null ? user.getName() : "");
        m.put("email", user.getEmail() != null ? user.getEmail() : "");
        m.put("role", user.getRole() != null ? user.getRole() : "user");
        m.put("gender", user.getGender() != null ? user.getGender() : "");
        m.put("avatar", user.getAvatar());
        m.put("authProvider", user.getAuthProvider() != null ? user.getAuthProvider() : "local");
        m.put("onboardingCompleted", user.getOnboardingCompleted() != null ? user.getOnboardingCompleted() : false);
        m.put("partnerCode", user.getPartnerCode() != null ? user.getPartnerCode() : "");
        m.put("partnerId", user.getPartnerId());
        m.put("accountStatus", user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE");
        m.put("accountStatusReason", user.getAccountStatusReason());
        return m;
    }

    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        log.info("[FORGOT-PASSWORD] Yêu cầu reset password cho email: {}", email);

        java.util.Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("[FORGOT-PASSWORD] Không tìm thấy user với email: {}", email);
            return;
        }

        User user = userOpt.get();
        if (!"local".equals(user.getAuthProvider())) {
            String provider = "google".equals(user.getAuthProvider()) ? "Google" : "Facebook";
            log.warn("[FORGOT-PASSWORD] User {} đăng ký qua {} (không phải local) — không gửi OTP",
                    email, user.getAuthProvider());
            throw new IllegalArgumentException(
                    "Tài khoản này đăng ký bằng " + provider + ". Vui lòng đăng nhập bằng nút \"Đăng nhập với "
                            + provider + "\" — không cần mật khẩu.");
        }

        // Tạo OTP 6 chữ số an toàn
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        String otpHash = hashToken(otp);

        enforceResendCooldown(user.getId());
        invalidateOpenTokens(user.getId());
        tokenRepository.save(newOtpToken(user.getId(), otpHash));

        log.info("[FORGOT-PASSWORD] Đã lưu OTP token cho user: {}, gửi email...", email);
        try {
            emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);
        } catch (Exception ex) {
            log.error("[FORGOT-PASSWORD] Gửi email OTP thất bại cho {}: {}", email, ex.getMessage(), ex);
            throw new IllegalArgumentException(
                    "Hệ thống gửi Email gặp sự cố (Chưa cấu hình Gmail App Password hoặc bị chặn). Vui lòng cấu hình MAIL_PASSWORD trong .env. Chi tiết lỗi: "
                            + ex.getMessage());
        }
    }

    public String verifyOtp(VerifyOtpRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không đúng hoặc đã hết hạn"));

        PasswordResetToken token = verifyOtpToken(user.getId(), req.getOtp());

        // Tạo UUID reset token sau khi OTP hợp lệ
        String plainToken = UUID.randomUUID().toString();
        token.setTokenHash(hashToken(plainToken));
        token.setOtpVerified(true);
        tokenRepository.save(token);

        return plainToken;
    }

    public void resetPassword(String plainToken, ResetPasswordRequest req) {
        String hashedToken = hashToken(plainToken);
        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndOtpVerifiedTrue(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng"));
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Đường dẫn đặt lại mật khẩu đã hết hạn");
        }
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        resetToken.setUsedAt(Instant.now());
        tokenRepository.save(resetToken);
    }

    public Map<String, Object> verifyActivation(VerifyOtpRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        if ("ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
            throw new IllegalArgumentException("Tài khoản này đã được kích hoạt từ trước.");
        }

        PasswordResetToken token = verifyOtpToken(user.getId(), req.getOtp());

        user.setAccountStatus("ACTIVE");
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        token.setOtpVerified(true);
        tokenRepository.save(token);

        return buildAuthPayload(user);
    }

    public void resendActivationOtp(String email) {
        String cleanEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        if ("ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
            throw new IllegalArgumentException("Tài khoản đã được kích hoạt.");
        }

        enforceResendCooldown(user.getId());
        invalidateOpenTokens(user.getId());

        // Sinh mã OTP mới
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        String otpHash = hashToken(otp);

        tokenRepository.save(newOtpToken(user.getId(), otpHash));

        emailService.sendRegistrationOtpEmail(user.getEmail(), user.getName(), otp);
    }

    private PasswordResetToken newOtpToken(String userId, String otpHash) {
        Instant now = Instant.now();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setOtpHash(otpHash);
        token.setOtpVerified(false);
        token.setFailedAttempts(0);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(15, ChronoUnit.MINUTES));
        return token;
    }

    private PasswordResetToken verifyOtpToken(String userId, String plainOtp) {
        Instant now = Instant.now();
        PasswordResetToken token = tokenRepository
                .findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không đúng hoặc đã hết hạn"));

        if (token.getLockedUntil() != null && token.getLockedUntil().isAfter(now)) {
            throw new IllegalArgumentException("Mã OTP đang tạm khóa do nhập sai quá nhiều lần. Vui lòng thử lại sau.");
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            token.setUsedAt(now);
            tokenRepository.save(token);
            throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        String otpHash = hashToken(plainOtp);
        if (!otpHash.equals(token.getOtpHash())) {
            int attempts = (token.getFailedAttempts() != null ? token.getFailedAttempts() : 0) + 1;
            token.setFailedAttempts(attempts);
            token.setLastAttemptAt(now);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                token.setLockedUntil(now.plus(OTP_LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            tokenRepository.save(token);
            throw new IllegalArgumentException("Mã OTP không đúng hoặc đã hết hạn");
        }

        token.setLastAttemptAt(now);
        return token;
    }

    private void enforceResendCooldown(String userId) {
        tokenRepository.findTopByUserIdAndUsedAtIsNullAndOtpVerifiedFalseOrderByCreatedAtDesc(userId)
                .ifPresent(token -> {
                    Instant createdAt = token.getCreatedAt();
                    if (createdAt != null && createdAt.plus(OTP_RESEND_COOLDOWN_SECONDS, ChronoUnit.SECONDS).isAfter(Instant.now())) {
                        throw new IllegalArgumentException("Vui lòng chờ ít nhất 60 giây trước khi gửi lại OTP.");
                    }
                });
    }

    private void invalidateOpenTokens(String userId) {
        Instant now = Instant.now();
        List<PasswordResetToken> oldTokens = tokenRepository.findByUserIdAndUsedAtIsNull(userId);
        for (PasswordResetToken t : oldTokens) {
            t.setUsedAt(now);
            tokenRepository.save(t);
        }
    }

}
