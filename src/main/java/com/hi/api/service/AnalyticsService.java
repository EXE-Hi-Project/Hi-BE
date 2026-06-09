package com.hi.api.service;

import com.hi.api.dto.request.TrackEventRequest;
import com.hi.api.model.AnalyticsEvent;
import com.hi.api.repository.AnalyticsEventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final MongoTemplate mongoTemplate;

    public AnalyticsService(AnalyticsEventRepository analyticsEventRepository, MongoTemplate mongoTemplate) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public AnalyticsEvent trackEvent(TrackEventRequest req) {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setSessionId(req.getSessionId());
        event.setUserId(req.getUserId() != null && !req.getUserId().isBlank() ? req.getUserId() : null);
        event.setEventType(req.getEventType());
        event.setTarget(req.getTarget());
        event.setElementText(req.getElementText());
        event.setMetadata(req.getMetadata());
        event.setCreatedAt(Instant.now());
        return analyticsEventRepository.save(event);
    }

    public Map<String, Object> getAnalyticsStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. Core Conversion Rate and Session Stats
        List<String> allSessions = mongoTemplate.findDistinct(new Query(), "sessionId", AnalyticsEvent.class, String.class);
        long totalSessions = allSessions.size();

        Query convertedQuery = new Query(new Criteria().orOperator(
                Criteria.where("userId").exists(true).ne("").ne(null),
                Criteria.where("eventType").is("REGISTER")
        ));
        List<String> convertedSessions = mongoTemplate.findDistinct(convertedQuery, "sessionId", AnalyticsEvent.class, String.class);
        long convertedSessionsCount = convertedSessions.size();

        double conversionRate = totalSessions > 0
                ? ((double) convertedSessionsCount / totalSessions) * 100.0
                : 0.0;

        long totalEvents = analyticsEventRepository.count();
        long totalClicks = analyticsEventRepository.countByEventType("CLICK");
        long totalPageViews = analyticsEventRepository.countByEventType("PAGE_VIEW");

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalSessions", totalSessions);
        overview.put("convertedSessions", convertedSessionsCount);
        overview.put("conversionRate", round2(conversionRate));
        overview.put("totalEvents", totalEvents);
        overview.put("totalClicks", totalClicks);
        overview.put("totalPageViews", totalPageViews);
        stats.put("overview", overview);

        // 2. Traffic Page Views Trend (Last 30 Days)
        Instant since30Days = Instant.now().minus(Duration.ofDays(30));
        var matchPV = new org.bson.Document("$match", new org.bson.Document("eventType", "PAGE_VIEW")
                .append("createdAt", new org.bson.Document("$gte", java.util.Date.from(since30Days))));
        var groupPVByDay = new org.bson.Document("$group", new org.bson.Document("_id",
                new org.bson.Document("$dateToString", new org.bson.Document("format", "%Y-%m-%d")
                        .append("date", "$createdAt")
                        .append("timezone", "+07:00")))
                .append("count", new org.bson.Document("$sum", 1)));
        var sortPVByDay = new org.bson.Document("$sort", new org.bson.Document("_id", 1));

        var pvPipeline = List.of(matchPV, groupPVByDay, sortPVByDay);
        var pvResults = mongoTemplate.getCollection("analytics_events")
                .aggregate(pvPipeline, org.bson.Document.class);

        Map<String, Long> dailyPvMap = new LinkedHashMap<>();
        for (org.bson.Document doc : pvResults) {
            String dateStr = doc.getString("_id");
            Number countNum = doc.get("count", Number.class);
            if (dateStr != null && countNum != null) {
                dailyPvMap.put(dateStr, countNum.longValue());
            }
        }

        // Fill missing dates with 0 values
        List<Map<String, Object>> trafficTrend = new ArrayList<>();
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(vnZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 29; i >= 0; i--) {
            LocalDate targetDate = today.minusDays(i);
            String dateKey = targetDate.format(formatter);
            String label = targetDate.format(labelFormatter);
            long count = dailyPvMap.getOrDefault(dateKey, 0L);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", dateKey);
            point.put("label", label);
            point.put("pageViews", count);
            trafficTrend.add(point);
        }
        stats.put("trafficTrend", trafficTrend);

        // 3. Hourly Page Views Traffic (24 hours)
        var groupPVByHour = new org.bson.Document("$group", new org.bson.Document("_id",
                new org.bson.Document("$hour", "$createdAt"))
                .append("count", new org.bson.Document("$sum", 1)));
        var sortPVByHour = new org.bson.Document("$sort", new org.bson.Document("_id", 1));
        var hourlyPipeline = List.of(matchPV, groupPVByHour, sortPVByHour);
        var hourlyResults = mongoTemplate.getCollection("analytics_events")
                .aggregate(hourlyPipeline, org.bson.Document.class);

        long[] hourlyTraffic = new long[24];
        for (org.bson.Document doc : hourlyResults) {
            Number hourNum = doc.get("_id", Number.class);
            Number countNum = doc.get("count", Number.class);
            if (hourNum != null && countNum != null) {
                int hour = hourNum.intValue();
                int vnHour = (hour + 7) % 24;
                hourlyTraffic[vnHour] = countNum.longValue();
            }
        }

        List<Map<String, Object>> hourlyPVTraffic = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("hour", String.format("%02d:00", h));
            point.put("pageViews", hourlyTraffic[h]);
            hourlyPVTraffic.add(point);
        }
        stats.put("hourlyTraffic", hourlyPVTraffic);

        // 4. Most Visited Pages (Top 10)
        var matchPVAll = new org.bson.Document("$match", new org.bson.Document("eventType", "PAGE_VIEW"));
        var groupPages = new org.bson.Document("$group", new org.bson.Document("_id", "$target")
                .append("count", new org.bson.Document("$sum", 1)));
        var sortPages = new org.bson.Document("$sort", new org.bson.Document("count", -1));
        var limitPages = new org.bson.Document("$limit", 10);
        var pagesPipeline = List.of(matchPVAll, groupPages, sortPages, limitPages);
        var pagesResults = mongoTemplate.getCollection("analytics_events")
                .aggregate(pagesPipeline, org.bson.Document.class);

        List<Map<String, Object>> topPages = new ArrayList<>();
        for (org.bson.Document doc : pagesResults) {
            String path = doc.getString("_id");
            Number countNum = doc.get("count", Number.class);
            if (path != null && countNum != null) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("page", path);
                p.put("views", countNum.longValue());
                topPages.add(p);
            }
        }
        stats.put("topPages", topPages);

        // 5. Top Clicked Elements (Click Ranking)
        var matchClicks = new org.bson.Document("$match", new org.bson.Document("eventType", "CLICK"));
        var groupClicks = new org.bson.Document("$group", new org.bson.Document("_id",
                new org.bson.Document("target", "$target").append("text", "$elementText"))
                .append("count", new org.bson.Document("$sum", 1)));
        var sortClicks = new org.bson.Document("$sort", new org.bson.Document("count", -1));
        var limitClicks = new org.bson.Document("$limit", 15);
        var clicksPipeline = List.of(matchClicks, groupClicks, sortClicks, limitClicks);
        var clicksResults = mongoTemplate.getCollection("analytics_events")
                .aggregate(clicksPipeline, org.bson.Document.class);

        List<Map<String, Object>> clickRanking = new ArrayList<>();
        for (org.bson.Document doc : clicksResults) {
            org.bson.Document idDoc = doc.get("_id", org.bson.Document.class);
            Number countNum = doc.get("count", Number.class);
            if (idDoc != null && countNum != null) {
                String targetId = idDoc.getString("target");
                String text = idDoc.getString("text");
                long count = countNum.longValue();
                double pct = totalClicks > 0 ? ((double) count / totalClicks) * 100.0 : 0.0;

                Map<String, Object> c = new LinkedHashMap<>();
                c.put("target", targetId != null ? targetId : "Unknown");
                c.put("text", text != null && !text.isBlank() ? text : "(Không có chữ)");
                c.put("clicks", count);
                c.put("percentage", round2(pct));
                clickRanking.add(c);
            }
        }
        stats.put("clickRanking", clickRanking);

        // 6. Conversion Funnel Statistics
        // Step 1: All sessions
        long step1All = totalSessions;

        // Step 2: Visited Landing Page ("/" or "/landing")
        Query qStep2 = new Query(new Criteria().andOperator(
                Criteria.where("eventType").is("PAGE_VIEW"),
                Criteria.where("target").in("/", "/landing")
        ));
        long step2Landing = mongoTemplate.findDistinct(qStep2, "sessionId", AnalyticsEvent.class, String.class).size();

        // Step 3: Visited Register Form ("/register")
        Query qStep3 = new Query(new Criteria().andOperator(
                Criteria.where("eventType").is("PAGE_VIEW"),
                Criteria.where("target").is("/register")
        ));
        long step3RegisterForm = mongoTemplate.findDistinct(qStep3, "sessionId", AnalyticsEvent.class, String.class).size();

        // Step 4: Registered (REGISTER event)
        Query qStep4 = new Query(Criteria.where("eventType").is("REGISTER"));
        long step4Registered = mongoTemplate.findDistinct(qStep4, "sessionId", AnalyticsEvent.class, String.class).size();

        // Step 5: Completed Onboarding (ONBOARDING_COMPLETE event or visit female/male dashboard)
        Query qStep5 = new Query(new Criteria().orOperator(
                Criteria.where("eventType").is("ONBOARDING_COMPLETE"),
                Criteria.where("target").in("/female-dashboard", "/male-dashboard")
        ));
        long step5Onboarded = mongoTemplate.findDistinct(qStep5, "sessionId", AnalyticsEvent.class, String.class).size();

        // Standardize funnel order & labels for UI
        List<Map<String, Object>> funnel = new ArrayList<>();
        funnel.add(Map.of("step", "1. Tổng truy cập", "count", step1All, "percentage", 100.0));
        funnel.add(Map.of("step", "2. Xem Landing Page", "count", step2Landing, "percentage", step1All > 0 ? round2(((double) step2Landing / step1All) * 100.0) : 0.0));
        funnel.add(Map.of("step", "3. Xem trang Đăng ký", "count", step3RegisterForm, "percentage", step1All > 0 ? round2(((double) step3RegisterForm / step1All) * 100.0) : 0.0));
        funnel.add(Map.of("step", "4. Đăng ký thành công", "count", step4Registered, "percentage", step1All > 0 ? round2(((double) step4Registered / step1All) * 100.0) : 0.0));
        funnel.add(Map.of("step", "5. Hoàn thành Onboarding", "count", step5Onboarded, "percentage", step1All > 0 ? round2(((double) step5Onboarded / step1All) * 100.0) : 0.0));
        stats.put("conversionFunnel", funnel);

        return stats;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
