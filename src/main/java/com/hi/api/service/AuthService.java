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
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    @Value("${app.admin.emails:}")
    private String adminEmailsStr;

    @Value("${app.google.client-id:}")
    private String googleClientId;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, PasswordResetTokenRepository tokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    private String hashToken(String plainToken) {
        return Hashing.sha256()
                .hashString(plainToken, StandardCharsets.UTF_8)
                .toString();
    }

    private List<String> getAdminEmails() {
        if (adminEmailsStr == null || adminEmailsStr.isBlank()) return List.of();
        return java.util.Arrays.stream(adminEmailsStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String generatePartnerCode() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
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

        userRepository.save(user);
        return buildAuthPayload(user);
    }

    public Map<String, Object> login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));

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

        if (req.getCredential() != null && !req.getCredential().isBlank()) {
            // ID token flow
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(req.getCredential());
            if (idToken == null) throw new IllegalArgumentException("Google token không hợp lệ");

            GoogleIdToken.Payload payload = idToken.getPayload();
            googleId = payload.getSubject();
            email = payload.getEmail();
            name = (String) payload.get("name");
            picture = (String) payload.get("picture");
        } else if (req.getAccessToken() != null && !req.getAccessToken().isBlank()) {
            // Access token flow — fetch userinfo
            String url = "https://www.googleapis.com/oauth2/v3/userinfo";
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = restTemplate.getForObject(
                    url + "?access_token=" + req.getAccessToken(), Map.class);
            if (userInfo == null) throw new IllegalArgumentException("Google token không hợp lệ");
            googleId = (String) userInfo.get("sub");
            email = (String) userInfo.get("email");
            name = (String) userInfo.get("name");
            picture = (String) userInfo.get("picture");
        } else {
            throw new IllegalArgumentException("Thiếu Google credential");
        }

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
            if (user.getGoogleId() == null) user.setGoogleId(googleId);
            if (picture != null && !picture.isBlank() && (user.getAvatar() == null || user.getAvatar().isBlank())) {
                user.setAvatar(picture);
            }
            user.setAuthProvider("google");
        }

        userRepository.save(user);
        return buildAuthPayload(user);
    }

    public Map<String, Object> buildAuthPayload(User user) {
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
        return m;
    }
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        // Không throw Exception nếu không tìm thấy email (Bảo vệ thông tin User)
        userRepository.findByEmail(email).ifPresent(user -> {

            // Chỉ hỗ trợ tài khoản đăng nhập Local
            if ("local".equals(user.getAuthProvider())) {

                // 1. Tạo chuỗi Token ngẫu nhiên (UUID)
                String plainToken = UUID.randomUUID().toString();

                // 2. Hash Token để lưu vào Database
                String hashedToken = hashToken(plainToken);

                // 3. Lưu vào DB (Hạn dùng 15 phút)
                PasswordResetToken resetToken = new PasswordResetToken();
                resetToken.setUserId(user.getId());
                resetToken.setTokenHash(hashedToken);
                resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
                tokenRepository.save(resetToken);

                // 4. Gửi email chứa Plain Token (người dùng sẽ click link này)
                String resetLink = "https://hilover.space/reset-password/" + plainToken;
                emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            }
        });
        // Hàm này luôn kết thúc êm đẹp, không trả về lỗi.
    }

    public void resetPassword(String plainToken, ResetPasswordRequest req) {
        // 1. Hash ngược cái token người dùng gửi lên để tìm trong DB
        String hashedToken = hashToken(plainToken);

        // 2. Tìm token trong DB (chưa được sử dụng)
        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashAndUsedAtIsNull(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException("Link đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng"));

        // 3. Kiểm tra hạn dùng
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Link đặt lại mật khẩu đã hết hạn");
        }

        // 4. Tìm User và đổi mật khẩu
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // 5. Đánh dấu Token đã sử dụng (Xóa bỏ logic)
        resetToken.setUsedAt(Instant.now());
        tokenRepository.save(resetToken);
    }
}
