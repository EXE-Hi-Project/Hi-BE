package com.hi.api.controller;

import com.hi.api.model.Notification;
import com.hi.api.model.User;
import com.hi.api.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(@AuthenticationPrincipal User user) {
        List<Notification> notifications = notificationService.getNotifications(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "notifications", notifications));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã đánh dấu tất cả là đã đọc"));
    }

    // Also support PATCH /mark-all-read for new clients
    @PatchMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllReadPatch(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã đánh dấu tất cả là đã đọc"));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markReadPut(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
