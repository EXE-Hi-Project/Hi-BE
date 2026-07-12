package com.hi.api.service;

import com.hi.api.model.SupportMessage;
import com.hi.api.model.SupportTicket;
import com.hi.api.model.User;
import com.hi.api.repository.SupportMessageRepository;
import com.hi.api.repository.SupportTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportService {
    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final NotificationService notifications;
    private final RealtimeEventService realtime;
    private final MongoTemplate mongoTemplate;

    public SupportService(SupportTicketRepository tickets, SupportMessageRepository messages,
                          NotificationService notifications, RealtimeEventService realtime,
                          MongoTemplate mongoTemplate) {
        this.tickets = tickets; this.messages = messages; this.notifications = notifications;
        this.realtime = realtime; this.mongoTemplate = mongoTemplate;
    }

    public Map<String,Object> create(User user, String title, SupportTicket.Category category, String content) {
        title = validate(title, 5, 120, "Tiêu đề");
        content = validate(content, 10, 2000, "Nội dung");
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketCode("HI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setUserId(user.getId()); ticket.setUserName(user.getName()); ticket.setUserEmail(user.getEmail());
        ticket.setTitle(clean(title)); ticket.setCategory(category); ticket.setStatus(SupportTicket.Status.OPEN);
        ticket.setLastMessageAt(Instant.now());
        ticket = tickets.save(ticket);
        saveMessage(ticket.getId(), user.getId(), SupportMessage.Actor.USER, content);
        emitAdmin(ticket, "support.ticket.created");
        return detail(ticket);
    }

    public Map<String,Object> listMine(String userId, int page, int limit, SupportTicket.Status status) {
        PageRequest pageable = PageRequest.of(page, limit);
        Page<SupportTicket> result = status == null
                ? tickets.findByUserIdOrderByLastMessageAtDesc(userId, pageable)
                : tickets.findByUserIdAndStatusOrderByLastMessageAtDesc(userId, status, pageable);
        return page(result, page, limit);
    }

    public Map<String,Object> detailMine(String userId, String id) { return detail(requireOwned(userId, id)); }

    public Map<String,Object> userReply(String userId, String id, String content) {
        SupportTicket ticket = requireOwned(userId, id);
        if (ticket.getStatus() == SupportTicket.Status.CLOSED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hãy mở lại ticket trước khi phản hồi");
        saveMessage(id, userId, SupportMessage.Actor.USER, content);
        ticket.setStatus(SupportTicket.Status.OPEN); ticket.setLastMessageAt(Instant.now()); tickets.save(ticket);
        emitAdmin(ticket, "support.ticket.user_replied");
        return detail(ticket);
    }

    public Map<String,Object> reopen(String userId, String id) {
        SupportTicket ticket = requireOwned(userId, id);
        if (ticket.getStatus() != SupportTicket.Status.CLOSED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket này chưa đóng");
        ticket.setStatus(SupportTicket.Status.OPEN); ticket.setLastMessageAt(Instant.now()); tickets.save(ticket);
        emitAdmin(ticket, "support.ticket.reopened");
        return detail(ticket);
    }

    public Map<String,Object> adminList(int page, int limit, SupportTicket.Status status,
                                        SupportTicket.Category category, String q) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        if (category != null) query.addCriteria(Criteria.where("category").is(category));
        if (q != null && !q.isBlank()) {
            String safe = java.util.regex.Pattern.quote(q.trim());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("ticketCode").regex(safe, "i"), Criteria.where("title").regex(safe, "i"),
                    Criteria.where("userName").regex(safe, "i"), Criteria.where("userEmail").regex(safe, "i")));
        }
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SupportTicket.class);
        query.with(Sort.by(Sort.Direction.DESC, "lastMessageAt")).skip((long) page * limit).limit(limit);
        List<SupportTicket> items = mongoTemplate.find(query, SupportTicket.class);
        return page(items, page, limit, total);
    }

    public Map<String,Object> adminDetail(String id) { return detail(require(id)); }

    public Map<String,Object> adminReply(User admin, String id, String content) {
        SupportTicket ticket = require(id);
        if (ticket.getStatus() == SupportTicket.Status.CLOSED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể phản hồi ticket đã đóng");
        saveMessage(id, admin.getId(), SupportMessage.Actor.ADMIN, content);
        ticket.setAssignedAdminId(admin.getId()); ticket.setStatus(SupportTicket.Status.WAITING_FOR_USER);
        ticket.setLastMessageAt(Instant.now()); tickets.save(ticket);
        notifications.createNotification(ticket.getUserId(), "SUPPORT_REPLY", "Support đã phản hồi",
                "Ticket " + ticket.getTicketCode() + " đã có phản hồi mới.", "/help?ticket=" + id,
                null, Map.of("ticketId", id));
        return detail(ticket);
    }

    public Map<String,Object> updateStatus(User admin, String id, SupportTicket.Status status) {
        SupportTicket ticket = require(id);
        ticket.setStatus(status); ticket.setAssignedAdminId(admin.getId()); tickets.save(ticket);
        if (status == SupportTicket.Status.CLOSED) {
            notifications.createNotification(ticket.getUserId(), "SUPPORT_CLOSED", "Yêu cầu hỗ trợ đã đóng",
                    "Ticket " + ticket.getTicketCode() + " đã được đóng. Bạn có thể mở lại nếu cần.",
                    "/help?ticket=" + id, null, Map.of("ticketId", id));
        }
        return detail(ticket);
    }

    private SupportTicket requireOwned(String userId, String id) {
        SupportTicket ticket = require(id);
        if (!ticket.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket");
        return ticket;
    }
    private SupportTicket require(String id) { return tickets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ticket")); }
    private void saveMessage(String ticketId, String authorId, SupportMessage.Actor actor, String content) {
        content = validate(content, 10, 2000, "Nội dung");
        SupportMessage message = new SupportMessage(); message.setTicketId(ticketId); message.setAuthorId(authorId);
        message.setActor(actor); message.setContent(clean(content)); messages.save(message);
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String validate(String value, int min, int max, String field) {
        String cleaned = clean(value);
        if (cleaned.length() < min || cleaned.length() > max)
            throw new IllegalArgumentException(field + " phải có từ " + min + " đến " + max + " ký tự");
        return cleaned;
    }
    private Map<String,Object> detail(SupportTicket ticket) { return Map.of("ticket", ticket, "messages", messages.findByTicketIdOrderByCreatedAtAsc(ticket.getId())); }
    private Map<String,Object> page(Page<SupportTicket> result, int page, int limit) { return page(result.getContent(), page, limit, result.getTotalElements()); }
    private Map<String,Object> page(List<SupportTicket> items, int page, int limit, long total) {
        Map<String,Object> data = new LinkedHashMap<>(); data.put("items", items); data.put("page", page + 1); data.put("limit", limit);
        data.put("total", total); data.put("totalPages", (total + limit - 1) / limit); return data;
    }
    private void emitAdmin(SupportTicket ticket, String type) { realtime.sendAdminOverviewUpdated(type, Map.of("ticketId", ticket.getId(), "status", ticket.getStatus())); }
}
