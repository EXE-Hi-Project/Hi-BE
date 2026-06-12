package com.hi.api.controller;

import com.hi.api.dto.request.CreateCycleRecordRequest;
import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.UpdateCycleRecordRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.User;
import com.hi.api.service.CycleRecordService;
import com.hi.api.service.DailyLogService;
import com.hi.api.service.SubscriptionAccessService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cycle-records")
public class CycleRecordController {

    private final CycleRecordService cycleRecordService;
    private final DailyLogService dailyLogService;
    private final SubscriptionAccessService subscriptionAccessService;

    public CycleRecordController(CycleRecordService cycleRecordService,
                                 DailyLogService dailyLogService,
                                 SubscriptionAccessService subscriptionAccessService) {
        this.cycleRecordService = cycleRecordService;
        this.dailyLogService = dailyLogService;
        this.subscriptionAccessService = subscriptionAccessService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCycleRecords(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<CycleRecord> records = cycleRecordService.getCycleRecords(user.getId(), from, to);
        return ResponseEntity.ok(Map.of("success", true, "cycleRecords", records));
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> getInsights(@AuthenticationPrincipal User user) {
        CycleRecordInsightResponse insights = cycleRecordService.getInsights(user.getId());
        insights = subscriptionAccessService.filterInsights(
                insights,
                subscriptionAccessService.getAccess(user).premium()
        );
        return ResponseEntity.ok(Map.of("success", true, "insights", insights));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getCycleRecordHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Page<CycleRecord> records = cycleRecordService.getCycleRecordHistory(user.getId(), safePage, safeLimit);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "cycleRecords", records.getContent(),
                "total", records.getTotalElements(),
                "page", records.getNumber(),
                "limit", records.getSize(),
                "hasMore", records.hasNext()
        ));
    }

    @GetMapping("/{id}/symptom-history")
    public ResponseEntity<Map<String, Object>> getSymptomHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        CycleRecord record = cycleRecordService.getCycleRecord(user.getId(), id);
        LocalDate startDate = record.getStartDate();
        LocalDate endDate = record.getEndDate() != null
                ? record.getEndDate()
                : startDate.plusDays(Math.max(record.getPeriodLength() != null ? record.getPeriodLength() : 5, 1) - 1L);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "cycleRecord", record,
                "dailyLogs", dailyLogService.getLogs(user.getId(), startDate, endDate)
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCycleRecord(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCycleRecordRequest req) {
        CycleRecord record = cycleRecordService.createCycleRecord(user.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "cycleRecord", record));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCycleRecord(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCycleRecordRequest req) {
        CycleRecord record = cycleRecordService.updateCycleRecord(user.getId(), id, req);
        return ResponseEntity.ok(Map.of("success", true, "cycleRecord", record));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCycleRecord(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        cycleRecordService.deleteCycleRecord(user.getId(), id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa chu kỳ"));
    }
}
