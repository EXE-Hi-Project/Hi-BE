package com.hi.api.service;

import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.User;
import com.hi.api.model.Transaction;
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
    private final CycleRecordRepository cycleRecordRepository;
    private final DailyLogSymptomRepository dailyLogSymptomRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ChatRepository chatRepository;
    private final TransactionRepository transactionRepository;
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

    public AdminService(UserRepository userRepository, CycleRecordRepository cycleRecordRepository,
                        DailyLogSymptomRepository dailyLogSymptomRepository, NotificationRepository notificationRepository,
                        ChatRepository chatRepository, AdminAuditLogRepository auditLogRepository,
                        TransactionRepository transactionRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.notificationRepository = notificationRepository;
        this.chatRepository = chatRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionRepository = transactionRepository;
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
        long cyclesTotal = cycleRecordRepository.count();
        long symptomsTotal = dailyLogSymptomRepository.count();
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

        // PayOS Transactions Aggregation
        List<Transaction> allTransactions = transactionRepository.findAll();
        long totalRevenueVnd = 0;
        long completedOrdersCount = 0;
        long totalOrdersCount = allTransactions.size();

        Map<String, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put("completed", 0L);
        statusBreakdown.put("pending", 0L);
        statusBreakdown.put("canceled", 0L);

        for (Transaction tx : allTransactions) {
            String status = tx.getStatus() != null ? tx.getStatus().toLowerCase() : "pending";
            if ("completed".equals(status)) {
                totalRevenueVnd += tx.getAmount() != null ? tx.getAmount() : 0L;
                completedOrdersCount++;
                statusBreakdown.put("completed", statusBreakdown.get("completed") + 1);
            } else if ("pending".equals(status)) {
                statusBreakdown.put("pending", statusBreakdown.get("pending") + 1);
            } else {
                statusBreakdown.put("canceled", statusBreakdown.get("canceled") + 1);
            }
        }

        List<Transaction> recentTransactions = allTransactions.stream()
                .sorted((a, b) -> {
                    Instant ta = a.getCreatedAt();
                    Instant tb = b.getCreatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta); // Descending
                })
                .limit(50)
                .toList();

        Map<String, Object> payosReport = new LinkedHashMap<>();
        payosReport.put("totalRevenueVnd", totalRevenueVnd);
        payosReport.put("completedOrdersCount", completedOrdersCount);
        payosReport.put("totalOrdersCount", totalOrdersCount);
        payosReport.put("statusBreakdown", statusBreakdown);
        payosReport.put("transactions", recentTransactions);

        result.put("payosReport", payosReport);

        // 1. System Mood Index breakdown
        var groupMoodStage = new org.bson.Document("$group",
                new org.bson.Document("_id", "$moodScore")
                        .append("count", new org.bson.Document("$sum", 1)));
        var moodPipeline = java.util.List.of(groupMoodStage);
        var moodResults = mongoTemplate.getCollection("daily_logs")
                .aggregate(moodPipeline, org.bson.Document.class);

        long moodVeryBad = 0; // 1
        long moodBad = 0;     // 2
        long moodNormal = 0;  // 3
        long moodGood = 0;    // 4
        long moodVeryGood = 0;// 5

        for (org.bson.Document doc : moodResults) {
            Object idVal = doc.get("_id");
            Number countNum = doc.get("count", Number.class);
            long count = countNum != null ? countNum.longValue() : 0L;
            if (idVal instanceof Number n) {
                int val = n.intValue();
                switch (val) {
                    case 1 -> moodVeryBad += count;
                    case 2 -> moodBad += count;
                    case 3 -> moodNormal += count;
                    case 4 -> moodGood += count;
                    case 5 -> moodVeryGood += count;
                    default -> moodNormal += count;
                }
            }
        }

        List<Map<String, Object>> moodDistribution = new ArrayList<>();
        moodDistribution.add(Map.of("name", "Cáu kỉnh", "value", moodVeryBad, "color", "#f87171")); // Red/Coral
        moodDistribution.add(Map.of("name", "Mệt mỏi", "value", moodBad, "color", "#fb923c"));   // Orange
        moodDistribution.add(Map.of("name", "Bình thường", "value", moodNormal, "color", "#94a3b8")); // Slate
        moodDistribution.add(Map.of("name", "Thoải mái", "value", moodGood, "color", "#a78bfa"));   // Purple
        moodDistribution.add(Map.of("name", "Vui vẻ", "value", moodVeryGood, "color", "#e9638f"));  // Hi Pink

        result.put("moodDistribution", moodDistribution);

        // 2. Chat Hourly Traffic
        var groupHourStage = new org.bson.Document("$group",
                new org.bson.Document("_id", new org.bson.Document("$hour", "$createdAt"))
                        .append("count", new org.bson.Document("$sum", 1)));
        var sortHourStage = new org.bson.Document("$sort", new org.bson.Document("_id", 1));
        var hourlyPipeline = java.util.List.of(groupHourStage, sortHourStage);
        var hourlyResults = mongoTemplate.getCollection("chats")
                .aggregate(hourlyPipeline, org.bson.Document.class);

        long[] hourlyTraffic = new long[24];
        long totalHourlyChats = 0;
        for (org.bson.Document doc : hourlyResults) {
            Number hourNum = doc.get("_id", Number.class);
            Number countNum = doc.get("count", Number.class);
            if (hourNum != null && countNum != null) {
                int hour = hourNum.intValue();
                // MongoDB $hour outputs UTC. Adjusting to Vietnam Time (UTC+7)
                int vnHour = (hour + 7) % 24;
                hourlyTraffic[vnHour] = countNum.longValue();
                totalHourlyChats += countNum.longValue();
            }
        }

        List<Map<String, Object>> hourlyChatTraffic = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("hour", String.format("%02d:00", h));
            point.put("queries", hourlyTraffic[h]);
            hourlyChatTraffic.add(point);
        }

        result.put("hourlyChatTraffic", hourlyChatTraffic);

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
