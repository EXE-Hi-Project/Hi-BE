package com.hi.api.controller;

import com.hi.api.dto.request.CreateSupportMessageRequest;
import com.hi.api.dto.request.CreateSupportTicketRequest;
import com.hi.api.model.SupportTicket;
import com.hi.api.model.User;
import com.hi.api.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/support/tickets")
public class SupportController {
    private final SupportService service;
    public SupportController(SupportService service) { this.service = service; }
    @PostMapping public ResponseEntity<Map<String,Object>> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateSupportTicketRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ok("Đã gửi yêu cầu hỗ trợ", service.create(user, req.getTitle(), req.getCategory(), req.getMessage())));
    }
    @GetMapping public Map<String,Object> list(@AuthenticationPrincipal User user, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int limit, @RequestParam(required=false) SupportTicket.Status status) {
        return ok("Đã tải danh sách ticket", service.listMine(user.getId(), page(page), limit(limit), status));
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@AuthenticationPrincipal User user, @PathVariable String id) { return ok("Đã tải ticket", service.detailMine(user.getId(), id)); }
    @PostMapping("/{id}/messages") public Map<String,Object> reply(@AuthenticationPrincipal User user, @PathVariable String id, @Valid @RequestBody CreateSupportMessageRequest req) { return ok("Đã gửi phản hồi", service.userReply(user.getId(), id, req.getMessage())); }
    @PatchMapping("/{id}/reopen") public Map<String,Object> reopen(@AuthenticationPrincipal User user, @PathVariable String id) { return ok("Đã mở lại ticket", service.reopen(user.getId(), id)); }
    private int page(int value) { return Math.max(value, 1) - 1; } private int limit(int value) { return Math.min(Math.max(value,1),50); }
    private Map<String,Object> ok(String message,Object data) { return Map.of("success",true,"message",message,"data",data); }
}
