package com.hi.api.controller;

import com.hi.api.dto.request.ForgotPasswordRequest;
import com.hi.api.dto.request.GoogleAuthRequest;
import com.hi.api.dto.request.LoginRequest;
import com.hi.api.dto.request.RegisterRequest;
import com.hi.api.dto.request.ResetPasswordRequest;
import com.hi.api.dto.request.VerifyOtpRequest;
import com.hi.api.exception.GlobalExceptionHandler;
import com.hi.api.exception.OtpDeliveryException;
import com.hi.api.model.User;
import com.hi.api.security.ClientIpResolver;
import com.hi.api.service.AuthRateLimitService;
import com.hi.api.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.auth.cookie.secure:true}")
    private boolean secureAuthCookie;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Autowired
    public AuthController(AuthService authService, AuthRateLimitService authRateLimitService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.authRateLimitService = authRateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    AuthController(AuthService authService, AuthRateLimitService authRateLimitService) {
        this(authService, authRateLimitService, new ClientIpResolver(""));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req,
                                                        HttpServletRequest request) {
        try {
            authRateLimitService.check("register", req.getEmail(), clientIp(request), 5, 15);
            Map<String, Object> payload = authService.register(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng ký thành công");
            response.put("data", payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (OtpDeliveryException e) {
            return otpDeliveryFailure(e, req.getEmail());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                                     HttpServletRequest request) {
        authRateLimitService.check("login", req.getEmail(), clientIp(request), 5, 15);
        Map<String, Object> payload = authService.login(req);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Đăng nhập thành công");
        response.put("data", payload);
        return withAuthSession(response, payload, request);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, Object>> csrf(CsrfToken csrfToken) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "CSRF token created");
        response.put("data", Map.of(
                "csrfToken", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName()
        ));
        return ResponseEntity.ok(response);
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
    public ResponseEntity<Map<String, Object>> refresh(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        Map<String, Object> payload = authService.buildAuthPayload(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Làm mới phiên đăng nhập thành công");
        response.put("data", payload);
        return withAuthSession(response, payload, request);
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(
            @RequestBody GoogleAuthRequest req,
            HttpServletRequest request) {
        try {
            Map<String, Object> payload = authService.googleAuth(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập Google thành công");
            response.put("data", payload);
            return withAuthSession(response, payload, request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return logAndReturnInternalError("GOOGLE_AUTH", e);
        }
    }

    ResponseEntity<Map<String, Object>> googleAuth(GoogleAuthRequest req) {
        return googleAuth(req, null);
    }

    @PostMapping("/facebook")
    public ResponseEntity<Map<String, Object>> facebookAuth(
            @RequestBody GoogleAuthRequest req,
            HttpServletRequest request) {
        try {
            Map<String, Object> payload = authService.facebookAuth(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập Facebook thành công");
            response.put("data", payload);
            return withAuthSession(response, payload, request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return logAndReturnInternalError("FACEBOOK_AUTH", e);
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
        } catch (OtpDeliveryException e) {
            return otpDeliveryFailure(e, req.getEmail());
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
            return withAuthSession(response, payload, request);
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
        } catch (OtpDeliveryException e) {
            return otpDeliveryFailure(e, email);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAuthCookie().toString())
                .body(Map.of("success", true, "message", "Đã đăng xuất"));
    }

    private ResponseEntity<Map<String, Object>> withAuthSession(
            Map<String, Object> body,
            Map<String, Object> payload,
            HttpServletRequest request) {
        Object user = payload.get("user");
        String tokenValue;
        if (user instanceof User authUser && authUser.getId() != null && !authUser.getId().isBlank()) {
            tokenValue = authService.issueToken(authUser.getId());
        } else if (user instanceof Map<?, ?> userMap
                && userMap.get("_id") instanceof String userIdValue
                && !userIdValue.isBlank()) {
            tokenValue = authService.issueToken(userIdValue);
        } else {
            return ResponseEntity.ok(body);
        }

        if (isNativeMobileRequest(request)) {
            Object data = body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Map<String, Object> mobileData = new LinkedHashMap<>();
                dataMap.forEach((key, value) -> {
                    if (key instanceof String stringKey) {
                        mobileData.put(stringKey, value);
                    }
                });
                mobileData.put("token", tokenValue);
                body.put("data", mobileData);
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookie(tokenValue).toString())
                .body(body);
    }

    private boolean isNativeMobileRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String platform = request.getHeader("X-Client-Platform");
        return "ios".equalsIgnoreCase(platform) || "android".equalsIgnoreCase(platform);
    }

    private ResponseEntity<Map<String, Object>> otpDeliveryFailure(OtpDeliveryException exception, String email) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", "OTP_DELIVERY_FAILED");
        data.put("email", email == null ? "" : email.trim().toLowerCase());
        data.put("trackingId", exception.getTrackingId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getMessage());
        response.put("data", data);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private ResponseEntity<Map<String, Object>> logAndReturnInternalError(String code, Exception exception) {
        String trackingId = UUID.randomUUID().toString();
        log.error("[{}:{}] Authentication request failed", code, trackingId, exception);
        return GlobalExceptionHandler.internalError(trackingId);
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
        return clientIpResolver.resolve(request);
    }
}
