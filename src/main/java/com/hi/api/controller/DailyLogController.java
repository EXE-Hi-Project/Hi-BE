package com.hi.api.controller;

import com.hi.api.dto.request.UpsertDailyLogRequest;
import com.hi.api.model.DailyLog;
import com.hi.api.model.User;
import com.hi.api.service.DailyLogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/daily-logs")
public class DailyLogController {

    private final DailyLogService dailyLogService;

    public DailyLogController(DailyLogService dailyLogService) {
        this.dailyLogService = dailyLogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLogs(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DailyLog> logs = dailyLogService.getLogs(user.getId(), from, to);
        return ResponseEntity.ok(Map.of("success", true, "dailyLogs", logs));
    }

    @GetMapping("/{logDate}")
    public ResponseEntity<Map<String, Object>> getLog(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
        DailyLog log = dailyLogService.getLog(user.getId(), logDate);
        return ResponseEntity.ok(Map.of("success", true, "dailyLog", log));
    }

    @PutMapping("/{logDate}")
    public ResponseEntity<Map<String, Object>> upsertLog(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate,
            @Valid @RequestBody UpsertDailyLogRequest req) {
        DailyLog log = dailyLogService.upsertLog(user.getId(), logDate, req);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("success", true, "dailyLog", log));
    }

    @DeleteMapping("/{logDate}")
    public ResponseEntity<Map<String, Object>> deleteLog(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate logDate) {
        dailyLogService.deleteLog(user.getId(), logDate);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa nhật ký"));
    }
}