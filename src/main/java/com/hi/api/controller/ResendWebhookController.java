package com.hi.api.controller;

import com.hi.api.service.ResendWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/resend")
public class ResendWebhookController {
    private final ResendWebhookService webhookService;
    public ResendWebhookController(ResendWebhookService webhookService) { this.webhookService = webhookService; }

    @PostMapping
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "svix-id", required = false) String webhookId,
                                         @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
                                         @RequestHeader(value = "svix-signature", required = false) String signature) {
        try {
            webhookService.handle(payload, webhookId, timestamp, signature);
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body("Invalid webhook");
        }
    }
}
