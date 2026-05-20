package com.hi.api.controller;

import com.hi.api.dto.request.CreateCycleRequest;
import com.hi.api.dto.request.UpdateCycleRequest;
import com.hi.api.model.Cycle;
import com.hi.api.model.User;
import com.hi.api.service.CycleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cycles")
public class CycleController {

    private final CycleService cycleService;

    public CycleController(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCycles(@AuthenticationPrincipal User user) {
        List<Cycle> cycles = cycleService.getCycles(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "cycles", cycles));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCycle(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCycleRequest req) {
        Cycle cycle = cycleService.createCycle(user.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "cycle", cycle));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCycle(
            @AuthenticationPrincipal User user,
            @PathVariable String id,
            @RequestBody UpdateCycleRequest req) {
        Cycle cycle = cycleService.updateCycle(user.getId(), id, req);
        return ResponseEntity.ok(Map.of("success", true, "cycle", cycle));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCycle(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        cycleService.deleteCycle(user.getId(), id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa chu kỳ"));
    }
}
