package com.hi.api.controller;

import com.hi.api.dto.request.ConnectPartnerRequest;
import com.hi.api.dto.request.ConfirmAvatarUploadRequest;
import com.hi.api.dto.request.NotificationSettingsRequest;
import com.hi.api.dto.request.PartnerSharingPreferencesRequest;
import com.hi.api.dto.request.PresignAvatarUploadRequest;
import com.hi.api.dto.request.UpdateProfileRequest;
import com.hi.api.dto.request.RegisterUserDeviceRequest;
import com.hi.api.dto.request.DeleteMyAccountRequest;
import com.hi.api.model.User;
import com.hi.api.model.UserDevice;
import com.hi.api.service.UserAvatarService;
import com.hi.api.service.UserDeviceService;
import com.hi.api.service.UserService;
import com.hi.api.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;
    private final UserAvatarService userAvatarService;
    private final UserDeviceService userDeviceService;
    private final AccountDeletionService accountDeletionService;

    public UserController(
            UserService userService,
            UserAvatarService userAvatarService,
            UserDeviceService userDeviceService,
            AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.userAvatarService = userAvatarService;
        this.userDeviceService = userDeviceService;
        this.accountDeletionService = accountDeletionService;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteMyAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteMyAccountRequest request) {
        accountDeletionService.delete(user.getId(), request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tài khoản và dữ liệu của bạn đã được xóa"
        ));
    }

    @PostMapping("/devices")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RegisterUserDeviceRequest request) {
        UserDevice device = userDeviceService.register(user.getId(), request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã đăng ký thiết bị",
                "data", Map.of("deviceId", device.getDeviceId(), "active", device.getActive())
        ));
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Map<String, Object>> deactivateDevice(
            @AuthenticationPrincipal User user,
            @PathVariable String deviceId) {
        userDeviceService.deactivate(user.getId(), deviceId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã gỡ thiết bị"));
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

    @PostMapping("/avatar/presign")
    public ResponseEntity<Map<String, Object>> presignAvatar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PresignAvatarUploadRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "data", userAvatarService.presignAvatar(user, request)));
    }

    @PostMapping("/avatar/confirm")
    public ResponseEntity<Map<String, Object>> confirmAvatar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ConfirmAvatarUploadRequest request) {
        User updated = userAvatarService.confirmAvatar(user, request);
        return ResponseEntity.ok(Map.of("success", true, "user", updated));
    }

    @GetMapping("/notification-settings")
    public ResponseEntity<Map<String, Object>> getNotificationSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "settings", userService.getNotificationSettings(user.getId())
        ));
    }

    @PutMapping("/notification-settings")
    public ResponseEntity<Map<String, Object>> updateNotificationSettings(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NotificationSettingsRequest req) {
        User.NotificationPreferences settings = userService.updateNotificationSettings(user.getId(), req);
        return ResponseEntity.ok(Map.of("success", true, "settings", settings));
    }

    @GetMapping("/partner-sharing-preferences")
    public ResponseEntity<Map<String, Object>> getPartnerSharingPreferences(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sharing", userService.getPartnerSharingPreferences(user.getId()),
                "notifications", userService.getNotificationSettings(user.getId())
        ));
    }

    @PutMapping("/partner-sharing-preferences")
    public ResponseEntity<Map<String, Object>> updatePartnerSharingPreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PartnerSharingPreferencesRequest req) {
        User.PartnerSharingPreferences sharing = userService.updatePartnerSharingPreferences(user.getId(), req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sharing", sharing,
                "notifications", userService.getNotificationSettings(user.getId())
        ));
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
        Map<String, Object> result = userService.disconnectPartner(user.getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Đã hủy kết nối với Người ấy");
        response.putAll(result);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/partner-cycles")
    public ResponseEntity<Map<String, Object>> getPartnerCycles(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int historyPage,
            @RequestParam(defaultValue = "20") int historyLimit) {
        Map<String, Object> partnerData = userService.getPartnerData(user.getId(), historyPage, historyLimit);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("partner", partnerData.get("partner"));
        response.put("sharing", partnerData.get("sharing"));
        response.put("cycles", partnerData.get("cycles"));
        response.put("history", partnerData.get("history"));
        response.put("insights", partnerData.get("insights"));
        response.put("latestMood", partnerData.get("latestMood"));
        response.put("latestDailyLogDate", partnerData.get("latestDailyLogDate"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/partner-cycles/history")
    public ResponseEntity<Map<String, Object>> getPartnerCycleHistory(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "items", userService.getPartnerCycleHistory(user.getId(), from, to)
        ));
    }
}
