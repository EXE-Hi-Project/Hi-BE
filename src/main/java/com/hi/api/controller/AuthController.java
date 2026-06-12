package com.hi.api.controller;

import com.hi.api.dto.request.GoogleAuthRequest;
import com.hi.api.dto.request.LoginRequest;
import com.hi.api.dto.request.RegisterRequest;
import com.hi.api.model.User;
import com.hi.api.service.AuthRateLimitService;
import com.hi.api.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.hi.api.dto.request.ForgotPasswordRequest;
import com.hi.api.dto.request.ResetPasswordRequest;
import com.hi.api.dto.request.VerifyOtpRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureAuthCookie;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public AuthController(AuthService authService, AuthRateLimitService authRateLimitService) {
        this.authService = authService;
        this.authRateLimitService = authRateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        Map<String, Object> payload = authService.register(req);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Đăng ký thành công");
        response.put("data", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, Object> payload = authService.login(req);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Đăng nhập thành công");
        response.put("data", payload);
        return withAuthCookie(response, payload);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> getMe(@AuthenticationPrincipal User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Lấy phiên đăng nhập thành công");
        response.put("data", Map.of("user", user));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> refresh(@AuthenticationPrincipal User user) {
        Map<String, Object> payload = authService.buildAuthPayload(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Làm mới phiên đăng nhập thành công");
        response.put("data", payload);
        return withAuthCookie(response, payload);
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(@RequestBody GoogleAuthRequest req) {
        try {
            Map<String, Object> payload = authService.googleAuth(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập Google thành công");
            response.put("data", payload);
            return withAuthCookie(response, payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi xác thực Google: " + e.getMessage()));
        }
    }

    @PostMapping("/facebook")
    public ResponseEntity<Map<String, Object>> facebookAuth(@RequestBody GoogleAuthRequest req) {
        try {
            Map<String, Object> payload = authService.facebookAuth(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập Facebook thành công");
            response.put("data", payload);
            return withAuthCookie(response, payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi xác thực Facebook: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req,
            HttpServletRequest request) {
        try {
            authRateLimitService.check("forgot", req.getEmail(), clientIp(request), 5, 15);
            authService.forgotPassword(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Nếu email của bạn tồn tại trong hệ thống, mã OTP đã được gửi đến email của bạn.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest req,
            HttpServletRequest request) {
        try {
            authRateLimitService.check("verify-reset", req.getEmail(), clientIp(request), 10, 15);
            String resetToken = authService.verifyOtp(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Xác minh OTP thành công");
            response.put("data", Map.of("resetToken", resetToken));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/reset-password/{token}")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable String token,
            @Valid @RequestBody ResetPasswordRequest req) {
        try {
            authService.resetPassword(token, req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify-activation")
    public ResponseEntity<Map<String, Object>> verifyActivation(
            @Valid @RequestBody VerifyOtpRequest req,
            HttpServletRequest request) {
        try {
            authRateLimitService.check("verify-activation", req.getEmail(), clientIp(request), 10, 15);
            Map<String, Object> payload = authService.verifyActivation(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Kích hoạt tài khoản thành công");
            response.put("data", payload);
            return withAuthCookie(response, payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, Object>> resendActivation(
            @RequestParam String email,
            HttpServletRequest request) {
        try {
            authRateLimitService.check("resend-activation", email, clientIp(request), 5, 15);
            authService.resendActivationOtp(email);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Mã kích hoạt OTP mới đã được gửi đến email của bạn");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAuthCookie().toString())
                .body(Map.of("success", true, "message", "ÄÃ£ Ä‘Äƒng xuáº¥t"));
    }

    private ResponseEntity<Map<String, Object>> withAuthCookie(Map<String, Object> body, Map<String, Object> payload) {
        Object token = payload.get("token");
        if (!(token instanceof String tokenValue) || tokenValue.isBlank()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookie(tokenValue).toString())
                .body(body);
    }

    private ResponseCookie authCookie(String token) {
        return ResponseCookie.from("hi_access_token", token)
                .httpOnly(true)
                .secure(secureAuthCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtExpirationMs))
                .build();
    }

    private ResponseCookie expiredAuthCookie() {
        return ResponseCookie.from("hi_access_token", "")
                .httpOnly(true)
                .secure(secureAuthCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
