package com.hi.api.service;

import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.AffiliateRevenueEvent;
import com.hi.api.model.User;
import com.hi.api.model.Transaction;
import com.hi.api.model.AiCostLog;
import com.hi.api.dto.request.UpsertAiCostRequest;
import com.hi.api.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
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
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;
    private final AiCostLogRepository aiCostLogRepository;

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
                        TransactionRepository transactionRepository, MongoTemplate mongoTemplate,
                        NotificationService notificationService,
                        RealtimeEventService realtimeEventService,
                        AiCostLogRepository aiCostLogRepository) {
        this.userRepository = userRepository;
        this.cycleRecordRepository = cycleRecordRepository;
        this.dailyLogSymptomRepository = dailyLogSymptomRepository;
        this.notificationRepository = notificationRepository;
        this.chatRepository = chatRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionRepository = transactionRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationService = notificationService;
        this.realtimeEventService = realtimeEventService;
        this.aiCostLogRepository = aiCostLogRepository;
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

        String currentMonthKey = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        Optional<AiCostLog> currentMonthLog = aiCostLogRepository.findByMonth(currentMonthKey);
        if (currentMonthLog.isPresent()) {
            estimatedAiCostMonthlyUsd = currentMonthLog.get().getCostUsd();
        }

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
            Optional<AiCostLog> actualLog = aiCostLogRepository.findByMonth(m.key());
            double aiCostUsdM;
            Long actualTokens = null;
            boolean isActual = false;

            if (actualLog.isPresent()) {
                aiCostUsdM = actualLog.get().getCostUsd();
                actualTokens = actualLog.get().getTotalTokens();
                isActual = true;
            } else {
                aiCostUsdM = (conversations * avgTokensPerConversation / 1000) * aiCostPer1kTokens;
            }

            Map<String, Object> mf = new LinkedHashMap<>();
            mf.put("month", m.label());
            mf.put("newUsers", newUsers);
            mf.put("chatMessages", chatMessages);
            mf.put("revenueUsd", round2(revenueUsdM));
            mf.put("aiCostUsd", round2(aiCostUsdM));
            mf.put("actualTokens", actualTokens);
            mf.put("isActual", isActual);
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

        // PayOS totals stay in Mongo; only the latest 50 rows are materialized.
        long totalOrdersCount = transactionRepository.count();
        long completedOrdersCount = transactionRepository.countByStatusIgnoreCase("completed");
        long pendingOrdersCount = transactionRepository.countByStatusIgnoreCase("pending");
        long canceledOrdersCount = Math.max(0, totalOrdersCount - completedOrdersCount - pendingOrdersCount);
        long totalRevenueVnd = sumLong(
                Transaction.class,
                "amount",
                Criteria.where("status").regex("^completed$", "i")
        );

        Map<String, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put("completed", completedOrdersCount);
        statusBreakdown.put("pending", pendingOrdersCount);
        statusBreakdown.put("canceled", canceledOrdersCount);

        List<Transaction> recentTransactions = transactionRepository.findTop50ByOrderByCreatedAtDesc();

        Map<String, Object> payosReport = new LinkedHashMap<>();
        payosReport.put("totalRevenueVnd", totalRevenueVnd);
        payosReport.put("completedOrdersCount", completedOrdersCount);
        payosReport.put("totalOrdersCount", totalOrdersCount);
        payosReport.put("statusBreakdown", statusBreakdown);
        payosReport.put("transactions", recentTransactions);

        result.put("payosReport", payosReport);

        long affiliateOrders = mongoTemplate.count(new Query(), AffiliateRevenueEvent.class);
        BigDecimal affiliateCommission = sumDecimal(AffiliateRevenueEvent.class, "commissionAmount", null);
        BigDecimal affiliateSettledCommission = sumDecimal(
                AffiliateRevenueEvent.class,
                "commissionAmount",
                Criteria.where("status").regex("^(SETTLED|COMPLETED)$", "i")
        );
        Map<String, Object> affiliateReport = new LinkedHashMap<>();
        affiliateReport.put("orders", affiliateOrders);
        affiliateReport.put("totalCommissionVnd", affiliateCommission);
        affiliateReport.put("settledCommissionVnd", affiliateSettledCommission);
        affiliateReport.put("totalRevenueVnd", totalRevenueVnd + affiliateSettledCommission.longValue());
        result.put("affiliateReport", affiliateReport);

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

    public User updateUserAccountStatus(String actorUserId, String targetUserId, String status, String reason, String ipAddress) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Thao tác nguy hiểm: Bạn không thể tự khóa tài khoản của chính mình.");
        }

        String safeStatus = status == null ? "" : status.toUpperCase().trim();
        if (!List.of("ACTIVE", "LOCKED").contains(safeStatus)) {
            throw new IllegalArgumentException("Trạng thái tài khoản không hợp lệ. Chỉ chấp nhận ACTIVE hoặc LOCKED.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        if ("DELETED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new IllegalArgumentException("Tài khoản đã bị xóa mềm, không thể đổi trạng thái.");
        }

        String oldStatus = user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE";
        user.setAccountStatus(safeStatus);
        user.setAccountStatusReason(reason != null ? reason.trim() : null);
        user.setAccountStatusUpdatedAt(Instant.now());
        user.setAccountStatusUpdatedBy(actorUserId);
        userRepository.save(user);

        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setTargetUserId(targetUserId);
        log.setAction("UPDATE_USER_ACCOUNT_STATUS");
        log.setEntityType("USER");
        log.setEntityId(targetUserId);
        log.setBeforeData(oldStatus);
        log.setAfterData(safeStatus);
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);

        return user;
    }

    public User softDeleteUser(String actorUserId, String targetUserId, String ipAddress) {
        if (actorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Thao tác nguy hiểm: Bạn không thể tự xóa tài khoản của chính mình.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        String oldStatus = user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE";
        user.setAccountStatus("DELETED");
        user.setAccountStatusReason("Soft-deleted by admin");
        user.setAccountStatusUpdatedAt(Instant.now());
        user.setAccountStatusUpdatedBy(actorUserId);
        userRepository.save(user);

        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setTargetUserId(targetUserId);
        log.setAction("SOFT_DELETE_USER");
        log.setEntityType("USER");
        log.setEntityId(targetUserId);
        log.setBeforeData(oldStatus);
        log.setAfterData("DELETED");
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);

        return user;
    }

    public void sendUserNotification(String actorUserId, String targetUserId, String title, String message, String type, String ipAddress) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        String safeType = type == null || type.isBlank() ? "ADMIN_MESSAGE" : type.trim();
        notificationService.createNotification(user.getId(), safeType, title.trim(), message.trim());

        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setTargetUserId(targetUserId);
        log.setAction("SEND_USER_NOTIFICATION");
        log.setEntityType("NOTIFICATION");
        log.setEntityId(targetUserId);
        log.setAfterData(title.trim());
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);
    }

    public long countNotificationAudience(String target) {
        return mongoTemplate.count(buildCampaignAudienceQuery(target), User.class);
    }

    public Map<String, Object> sendNotificationCampaign(String actorUserId,
                                                        String target,
                                                        String title,
                                                        String body,
                                                        String actionUrl,
                                                        String ipAddress) {
        Query query = buildCampaignAudienceQuery(target);
        query.fields().include("_id");
        List<User> recipients = mongoTemplate.find(query, User.class);
        String campaignId = UUID.randomUUID().toString();

        for (User recipient : recipients) {
            notificationService.createIdempotentNotification(
                    recipient.getId(),
                    "ADMIN_CAMPAIGN",
                    title.trim(),
                    body.trim(),
                    actionUrl == null || actionUrl.isBlank() ? null : actionUrl.trim(),
                    campaignId + ":" + recipient.getId(),
                    Map.of("campaignId", campaignId, "target", normalizeCampaignTarget(target))
            );
        }

        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setAction("SEND_NOTIFICATION_CAMPAIGN");
        log.setEntityType("NOTIFICATION_CAMPAIGN");
        log.setEntityId(campaignId);
        log.setAfterData("target=" + normalizeCampaignTarget(target) + ", recipients=" + recipients.size() + ", title=" + title.trim());
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);

        realtimeEventService.sendAdminOverviewUpdated("admin.overview.updated", Map.of(
                "reason", "notification.campaign",
                "campaignId", campaignId,
                "recipientCount", recipients.size()
        ));
        return Map.of(
                "campaignId", campaignId,
                "target", normalizeCampaignTarget(target),
                "recipientCount", recipients.size()
        );
    }

    private Query buildCampaignAudienceQuery(String target) {
        String safeTarget = normalizeCampaignTarget(target);
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("role").ne("admin"));
        criteria.add(new Criteria().orOperator(
                Criteria.where("accountStatus").exists(false),
                Criteria.where("accountStatus").is(null),
                Criteria.where("accountStatus").is("ACTIVE")
        ));

        switch (safeTarget) {
            case "female" -> criteria.add(Criteria.where("gender").is("female"));
            case "male" -> criteria.add(Criteria.where("gender").is("male"));
            case "premium" -> criteria.add(Criteria.where("subscription.plan").nin(null, "", "free"));
            default -> {
            }
        }
        return Query.query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    private String normalizeCampaignTarget(String target) {
        String safeTarget = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "female", "male", "premium").contains(safeTarget)) {
            throw new IllegalArgumentException("Nhóm đối tượng không hợp lệ");
        }
        return safeTarget;
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
        String escaped = data.replaceAll("\\R", " ");
        if (!escaped.isBlank() && isFormulaPrefix(escaped.charAt(0))) {
            escaped = "'" + escaped;
        }
        if (escaped.contains(",") || escaped.contains("\"")) {
            escaped = "\"" + escaped.replace("\"", "\"\"") + "\"";
        }
        return escaped;
    }

    private boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@' || value == '\t' || value == '\r';
    }

    private long sumLong(Class<?> entityType, String field, Criteria criteria) {
        return aggregateSum(entityType, field, criteria).longValue();
    }

    private BigDecimal sumDecimal(Class<?> entityType, String field, Criteria criteria) {
        return aggregateSum(entityType, field, criteria);
    }

    private BigDecimal aggregateSum(Class<?> entityType, String field, Criteria criteria) {
        Aggregation aggregation = criteria == null
                ? Aggregation.newAggregation(Aggregation.group().sum(field).as("total"))
                : Aggregation.newAggregation(
                        Aggregation.match(criteria),
                        Aggregation.group().sum(field).as("total")
                );
        org.bson.Document result = mongoTemplate
                .aggregate(aggregation, entityType, org.bson.Document.class)
                .getUniqueMappedResult();
        if (result == null || result.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = result.get("total");
        if (total instanceof org.bson.types.Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (total instanceof BigDecimal decimal) {
            return decimal;
        }
        if (total instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(total.toString());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public List<AiCostLog> getAiCostLogs() {
        return aiCostLogRepository.findAll(Sort.by(Sort.Direction.DESC, "month"));
    }

    public AiCostLog saveAiCostLog(UpsertAiCostRequest req) {
        AiCostLog log = aiCostLogRepository.findByMonth(req.getMonth())
                .orElseGet(() -> {
                    AiCostLog newLog = new AiCostLog();
                    newLog.setMonth(req.getMonth());
                    return newLog;
                });
        log.setInputTokens(req.getInputTokens() != null ? req.getInputTokens() : 0L);
        log.setOutputTokens(req.getOutputTokens() != null ? req.getOutputTokens() : 0L);
        long total = req.getTotalTokens() != null ? req.getTotalTokens() :
                (log.getInputTokens() + log.getOutputTokens());
        log.setTotalTokens(total);
        log.setCostUsd(req.getCostUsd());
        log.setNotes(req.getNotes() != null ? req.getNotes() : "");
        log.setCreatedAt(Instant.now());
        return aiCostLogRepository.save(log);
    }

    public void deleteAiCostLog(String month) {
        aiCostLogRepository.findByMonth(month).ifPresent(aiCostLogRepository::delete);
    }
}
