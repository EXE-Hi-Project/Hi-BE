package com.hi.api.service;

import com.hi.api.model.Notification;
import com.hi.api.repository.NotificationRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;

    public NotificationService(NotificationRepository notificationRepository, MongoTemplate mongoTemplate) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Notification> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAllRead(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("read").is(false));
        Update update = new Update().set("read", true);
        mongoTemplate.updateMulti(query, update, Notification.class);
    }

    public void markRead(String userId, String notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
    public void createNotification(String userId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}
