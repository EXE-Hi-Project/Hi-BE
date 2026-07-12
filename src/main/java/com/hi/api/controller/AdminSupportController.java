package com.hi.api.controller;
import com.hi.api.dto.request.CreateSupportMessageRequest;
import com.hi.api.dto.request.UpdateSupportStatusRequest;
import com.hi.api.model.SupportTicket;
import com.hi.api.model.User;
import com.hi.api.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/admin/support/tickets") @PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {
    private final SupportService service; public AdminSupportController(SupportService service) { this.service=service; }
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int limit,
        @RequestParam(required=false) SupportTicket.Status status,@RequestParam(required=false) SupportTicket.Category category,@RequestParam(required=false) String q) {
        return ok("Đã tải ticket", service.adminList(Math.max(page,1)-1,Math.min(Math.max(limit,1),50),status,category,q)); }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable String id) { return ok("Đã tải ticket",service.adminDetail(id)); }
    @PostMapping("/{id}/messages") public Map<String,Object> reply(@AuthenticationPrincipal User admin,@PathVariable String id,@Valid @RequestBody CreateSupportMessageRequest req) { return ok("Đã gửi phản hồi",service.adminReply(admin,id,req.getMessage())); }
    @PatchMapping("/{id}/status") public Map<String,Object> status(@AuthenticationPrincipal User admin,@PathVariable String id,@Valid @RequestBody UpdateSupportStatusRequest req) { return ok("Đã cập nhật trạng thái",service.updateStatus(admin,id,req.getStatus())); }
    private Map<String,Object> ok(String message,Object data) { return Map.of("success",true,"message",message,"data",data); }
}
