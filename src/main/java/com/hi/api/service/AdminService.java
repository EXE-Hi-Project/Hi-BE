package com.hi.api.service;

import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.User;
import com.hi.api.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final CycleRepository cycleRepository;
    private final SymptomRepository symptomRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ChatRepository chatRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${FINANCE_PAID_USER_RATE:0.15}")
    private double paidUserRate;

    @Value("${FINANCE_ARPU_USD:4.99}")
    private double arpuUsd;

    @Value("${FINANCE_INFRA_COST_USD:30.0}")
    private double infraCostUsd;

    @Value("${FINANCE_AI_COST_PER_1K_TOKENS_USD:0.005}")
    private double aiCostPer1kTokens;

    @Value("${FINANCE_AVG_MESSAGES_PER_CONVERSATION:10.0}")
    private double avgMessagesPerConversation;

    @Value("${FINANCE_AVG_TOKENS_PER_CONVERSATION:800.0}")
    private double avgTokensPerConversation;

    @Value("${FINANCE_MONTHLY_CHURN_RATE:0.04}")
    private double monthlyChurnRate;

    public AdminService(UserRepository userRepository, CycleRepository cycleRepository,
                        SymptomRepository symptomRepository, NotificationRepository notificationRepository,
                        ChatRepository chatRepository, AdminAuditLogRepository auditLogRepository,
                        MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.cycleRepository = cycleRepository;
        this.symptomRepository = symptomRepository;
        this.notificationRepository = notificationRepository;
        this.chatRepository = chatRepository;
        this.auditLogRepository = auditLogRepository;
        this.mongoTemplate = mongoTemplate;
    }

    private record MonthInfo(int year, int month, String key, String label, Instant startDate) {}

    private List<MonthInfo> getRecentMonths(int count) {
        List<MonthInfo> months = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = count - 1; i >= 0; i--) {
            LocalDate date = now.withDayOfMonth(1).minusMonths(i);
            int year = date.getYear();
            int month = date.getMonthValue();
            String key = String.format("%d-%02d", year, month);
            String label = String.format("%02d/%d", month, year);
            Instant startDate = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            months.add(new MonthInfo(year, month, key, label, startDate));
        }
        return months;
    }

    public Map<String, Object> getOverview() {
        List<MonthInfo> months = getRecentMonths(6);
        Instant firstMonthStart = months.get(0).startDate();

        long usersTotal = userRepository.count();
        long usersFemale = userRepository.countByGender("female");
        long usersMale = userRepository.countByGender("male");
        long adminsTotal = userRepository.countByRole("admin");
        long cyclesTotal = cycleRepository.count();
        long symptomsTotal = symptomRepository.count();
        long notificationsTotal = notificationRepository.count();
        long unreadNotifications = notificationRepository.countByRead(false);
        long chatMessagesTotal = chatRepository.count();

        // Recent users
        List<User> recentUsers = userRepository.findTop5ByOrderByCreatedAtDesc();

        // Monthly aggregations
        Map<String, Long> monthlyUsersMap = aggregateByMonth("users", firstMonthStart);
        Map<String, Long> monthlyChatMap = aggregateByMonth("chats", firstMonthStart);

        // Financial calculations
        double estimatedConversations = chatMessagesTotal / avgMessagesPerConversation;
        double estimatedAiTokensMonthly = estimatedConversations * avgTokensPerConversation;
        double estimatedAiCostMonthlyUsd = (estimatedAiTokensMonthly / 1000) * aiCostPer1kTokens;
        long estimatedPaidUsers = Math.round(usersTotal * paidUserRate);
        double estimatedMrrUsd = estimatedPaidUsers * arpuUsd;
        double estimatedGrossProfitUsd = estimatedMrrUsd - estimatedAiCostMonthlyUsd - infraCostUsd;
        double estimatedGrossMarginPct = estimatedMrrUsd > 0
                ? (estimatedGrossProfitUsd / estimatedMrrUsd) * 100 : 0;
        double estimatedLtvUsd = monthlyChurnRate > 0 ? arpuUsd / monthlyChurnRate : 0;

        List<Map<String, Object>> monthlyFinancials = months.stream().map(m -> {
            long newUsers = monthlyUsersMap.getOrDefault(m.key(), 0L);
            long chatMessages = monthlyChatMap.getOrDefault(m.key(), 0L);
            long newPaidUsers = Math.round(newUsers * paidUserRate);
            double revenueUsdM = newPaidUsers * arpuUsd;
            double conversations = chatMessages / avgMessagesPerConversation;
            double aiCostUsdM = (conversations * avgTokensPerConversation / 1000) * aiCostPer1kTokens;
            Map<String, Object> mf = new LinkedHashMap<>();
            mf.put("month", m.label());
            mf.put("newUsers", newUsers);
            mf.put("chatMessages", chatMessages);
            mf.put("revenueUsd", round2(revenueUsdM));
            mf.put("aiCostUsd", round2(aiCostUsdM));
            mf.put("netUsd", round2(revenueUsdM - aiCostUsdM));
            return mf;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", Map.of(
                "usersTotal", usersTotal,
                "usersFemale", usersFemale,
                "usersMale", usersMale,
                "adminsTotal", adminsTotal,
                "cyclesTotal", cyclesTotal,
                "symptomsTotal", symptomsTotal,
                "notificationsTotal", notificationsTotal,
                "unreadNotifications", unreadNotifications,
                "chatMessagesTotal", chatMessagesTotal
        ));
        Map<String, Object> financialReport = new LinkedHashMap<>();
        financialReport.put("estimatedPaidUsers", estimatedPaidUsers);
        financialReport.put("estimatedMrrUsd", round2(estimatedMrrUsd));
        financialReport.put("estimatedAiCostMonthlyUsd", round2(estimatedAiCostMonthlyUsd));
        financialReport.put("infraCostUsd", round2(infraCostUsd));
        financialReport.put("estimatedGrossProfitUsd", round2(estimatedGrossProfitUsd));
        financialReport.put("estimatedGrossMarginPct", round2(estimatedGrossMarginPct));
        financialReport.put("arpuUsd", round2(arpuUsd));
        financialReport.put("monthlyChurnRatePct", round2(monthlyChurnRate * 100));
        financialReport.put("estimatedLtvUsd", round2(estimatedLtvUsd));
        financialReport.put("assumptions", Map.of(
                "paidUserRate", round2(paidUserRate * 100),
                "avgMessagesPerConversation", avgMessagesPerConversation,
                "avgTokensPerConversation", avgTokensPerConversation,
                "aiCostPer1kTokens", round2(aiCostPer1kTokens)
        ));
        result.put("financialReport", financialReport);
        result.put("monthlyFinancials", monthlyFinancials);
        result.put("recentUsers", recentUsers);
        return result;
    }

    private Map<String, Long> aggregateByMonth(String collection, Instant since) {
        // Use native MongoDB aggregation for $year/$month operators
        var matchStage = new org.bson.Document("$match",
                new org.bson.Document("createdAt", new org.bson.Document("$gte", java.util.Date.from(since))));
        var groupStage = new org.bson.Document("$group",
                new org.bson.Document("_id",
                        new org.bson.Document("year", new org.bson.Document("$year", "$createdAt"))
                                .append("month", new org.bson.Document("$month", "$createdAt")))
                        .append("count", new org.bson.Document("$sum", 1)));

        var pipeline = java.util.List.of(matchStage, groupStage);
        var results = mongoTemplate.getCollection(collection)
                .aggregate(pipeline, org.bson.Document.class);

        Map<String, Long> map = new HashMap<>();
        for (org.bson.Document doc : results) {
            org.bson.Document id = doc.get("_id", org.bson.Document.class);
            if (id != null) {
                int year = id.getInteger("year", 0);
                int month = id.getInteger("month", 0);
                if (year > 0 && month > 0) {
                    String key = String.format("%d-%02d", year, month);
                    Number countNum = doc.get("count", Number.class);
                    map.put(key, countNum != null ? countNum.longValue() : 0L);
                }
            }
        }
        return map;
    }

    public Map<String, Object> getUsers(int page, int limit, String q, String role, String gender) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("name").regex(q, "i"),
                    Criteria.where("email").regex(q, "i")
            ));
        }
        if (role != null && !role.isBlank()) criteriaList.add(Criteria.where("role").is(role));
        if (gender != null && !gender.isBlank()) criteriaList.add(Criteria.where("gender").is(gender));

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, User.class);

        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.skip((long) (page - 1) * limit).limit(limit);
        query.fields().exclude("password");

        List<User> users = mongoTemplate.find(query, User.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", users);
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / limit));
        result.put("pagination", pagination);
        return result;
    }

    public User updateUserRole(String actorUserId, String targetUserId, String newRole, String ipAddress) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Thao tác nguy hiểm: Bạn không thể tự thay đổi quyền của chính mình.");
        }

        String safeRole = newRole.toLowerCase().trim();
        if (!List.of("admin", "user").contains(safeRole)) {
            throw new IllegalArgumentException("Quyền (Role) không hợp lệ. Chỉ chấp nhận 'admin' hoặc 'user'.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        String oldRole = user.getRole() != null ? user.getRole() : "user";

        if (!oldRole.equals(safeRole)) {
            user.setRole(safeRole);
            userRepository.save(user);
            AdminAuditLog log = new AdminAuditLog();
            log.setActorUserId(actorUserId);
            log.setTargetUserId(targetUserId);
            log.setAction("UPDATE_USER_ROLE");
            log.setEntityType("USER");
            log.setEntityId(targetUserId);
            log.setBeforeData(oldRole);
            log.setAfterData(safeRole);
            log.setIpAddress(ipAddress);
            auditLogRepository.save(log);
        }

        return user;
    }

    public byte[] exportUsersCsv() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<User> users = mongoTemplate.find(query, User.class);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM để Excel đọc tiếng Việt không bị lỗi font
        sb.append('\ufeff');
        sb.append("ID,Họ và Tên,Email,Role,Giới tính,Ngày tham gia\n");

        for (User u : users) {
            sb.append(escapeCsv(u.getId())).append(",")
                    .append(escapeCsv(u.getName())).append(",")
                    .append(escapeCsv(u.getEmail())).append(",")
                    .append(escapeCsv(u.getRole())).append(",")
                    .append(escapeCsv(u.getGender())).append(",")
                    .append(u.getCreatedAt() != null ? u.getCreatedAt().toString() : "").append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String data) {
        if (data == null) return "";
        String escaped = data.replaceAll("\\R", " "); // Xóa dấu xuống dòng
        if (escaped.contains(",") || escaped.contains("\"")) {
            escaped = "\"" + escaped.replace("\"", "\"\"") + "\"";
        }
        return escaped;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
