package com.hi.api.controller;

import com.hi.api.dto.request.ConnectPartnerRequest;
import com.hi.api.dto.request.UpdateProfileRequest;
import com.hi.api.model.Cycle;
import com.hi.api.model.User;
import com.hi.api.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        User updated = userService.updateProfile(user.getId(), req);
        return ResponseEntity.ok(Map.of("success", true, "user", updated));
    }

    @PostMapping("/connect-partner")
    public ResponseEntity<Map<String, Object>> connectPartner(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ConnectPartnerRequest req) {
        Map<String, Object> partner = userService.connectPartner(user.getId(), req);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Kết nối thành công");
        response.put("partner", partner);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/disconnect-partner")
    public ResponseEntity<Map<String, Object>> disconnectPartner(@AuthenticationPrincipal User user) {
        userService.disconnectPartner(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã ngắt kết nối với đối tác"));
    }

    @GetMapping("/partner-cycles")
    public ResponseEntity<Map<String, Object>> getPartnerCycles(@AuthenticationPrincipal User user) {
        Map<String, Object> partnerData = userService.getPartnerData(user.getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("partner", partnerData.get("partner"));
        response.put("cycles", partnerData.get("cycles"));
        return ResponseEntity.ok(response);
    }
}
