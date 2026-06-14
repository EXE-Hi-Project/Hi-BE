package com.hi.api.service;

import com.hi.api.dto.response.RealtimeEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeEventService {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeEventService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToUser(String userId, String queue, String type, Object data) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(userId, "/queue/" + queue, RealtimeEvent.of(type, data));
    }

    public void sendNotification(String userId, String type, Object data) {
        sendToUser(userId, "notifications", type, data);
    }

    public void sendChat(String userId, String type, Object data) {
        sendToUser(userId, "chat", type, data);
    }

    public void sendPartner(String userId, String type, Object data) {
        sendToUser(userId, "partner", type, data);
    }

    public void sendSubscription(String userId, String type, Object data) {
        sendToUser(userId, "subscription", type, data);
    }

    public void sendAdminOverviewUpdated(String type, Object data) {
        messagingTemplate.convertAndSend("/topic/admin/overview", RealtimeEvent.of(type, data));
    }
}
