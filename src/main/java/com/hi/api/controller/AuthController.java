package com.hi.api.controller;

import com.hi.api.dto.request.GoogleAuthRequest;
import com.hi.api.dto.request.LoginRequest;
import com.hi.api.dto.request.RegisterRequest;
import com.hi.api.model.User;
import com.hi.api.security.JwtUtil;
import com.hi.api.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.hi.api.dto.request.ForgotPasswordRequest;
import com.hi.api.dto.request.ResetPasswordRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
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
    public ResponseEntity<Map<String, Object>> refresh(@AuthenticationPrincipal User user) {
        Map<String, Object> payload = authService.buildAuthPayload(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Làm mới phiên đăng nhập thành công");
        response.put("data", payload);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(@RequestBody GoogleAuthRequest req) {
        try {
            Map<String, Object> payload = authService.googleAuth(req);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Đăng nhập Google thành công");
            response.put("data", payload);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Lỗi xác thực Google: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Nếu email của bạn tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đi.");
        return ResponseEntity.ok(response);
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
}
