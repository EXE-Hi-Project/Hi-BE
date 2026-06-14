package com.hi.api.service;

import com.hi.api.model.Notification;
import com.hi.api.repository.NotificationRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;
    private final RealtimeEventService realtimeEventService;

    public NotificationService(
            NotificationRepository notificationRepository,
            MongoTemplate mongoTemplate,
            RealtimeEventService realtimeEventService) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
        this.realtimeEventService = realtimeEventService;
    }

    public List<Notification> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAllRead(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("read").is(false));
        Update update = new Update().set("read", true);
        mongoTemplate.updateMulti(query, update, Notification.class);
        realtimeEventService.sendNotification(userId, "notification.read_all", Map.of("unreadCount", 0));
        emitUnreadCount(userId);
    }

    public void markRead(String userId, String notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
            realtimeEventService.sendNotification(userId, "notification.read", Map.of(
                    "notificationId", notificationId,
                    "unreadCount", getUnreadCount(userId)
            ));
            emitUnreadCount(userId);
        });
    }
    public void createNotification(String userId, String type, String title, String message) {
        createNotification(userId, type, title, message, null, null, null);
    }

    public Notification createNotification(
            String userId,
            String type,
            String title,
            String message,
            String actionUrl,
            String dedupeKey,
            Map<String, Object> metadata
    ) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setActionUrl(actionUrl);
        notification.setDedupeKey(dedupeKey);
        notification.setMetadata(metadata);
        Notification saved = notificationRepository.save(notification);
        realtimeEventService.sendNotification(userId, "notification.created", Map.of(
                "notification", saved,
                "unreadCount", getUnreadCount(userId)
        ));
        emitUnreadCount(userId);
        return saved;
    }

    public Notification createIdempotentNotification(
            String userId,
            String type,
            String title,
            String message,
            String actionUrl,
            String dedupeKey,
            Map<String, Object> metadata
    ) {
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            Optional<Notification> existing = notificationRepository.findByUserIdAndTypeAndDedupeKey(userId, type, dedupeKey);
            if (existing.isPresent()) return existing.get();
        }
        return createNotification(userId, type, title, message, actionUrl, dedupeKey, metadata);
    }

    public boolean existsByDedupeKey(String userId, String type, String dedupeKey) {
        return dedupeKey != null
                && !dedupeKey.isBlank()
                && notificationRepository.findByUserIdAndTypeAndDedupeKey(userId, type, dedupeKey).isPresent();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    private void emitUnreadCount(String userId) {
        realtimeEventService.sendNotification(userId, "notification.unread_count", Map.of(
                "unreadCount", getUnreadCount(userId)
        ));
    }
}
