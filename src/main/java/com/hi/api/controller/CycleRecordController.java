package com.hi.api.controller;

import com.hi.api.dto.request.CreateCycleRecordRequest;
import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.UpdateCycleRecordRequest;
import com.hi.api.model.CycleRecord;
import com.hi.api.model.User;
import com.hi.api.service.CycleRecordService;
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
@RequestMapping("/api/cycle-records")
public class CycleRecordController {

    private final CycleRecordService cycleRecordService;

    public CycleRecordController(CycleRecordService cycleRecordService) {
        this.cycleRecordService = cycleRecordService;
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
        return ResponseEntity.ok(Map.of("success", true, "insights", insights));
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
            @RequestBody UpdateCycleRecordRequest req) {
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