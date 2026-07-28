package com.hi.api.service;

import com.hi.api.dto.request.TrackEventRequest;
import com.hi.api.model.AnalyticsEvent;
import com.hi.api.model.User;
import com.hi.api.repository.AnalyticsEventRepository;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceSecurityTest {

    @Test
    void ignoresClientSuppliedUserIdAndBoundsMetadata() {
        AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
        when(repository.save(any(AnalyticsEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AnalyticsService service = new AnalyticsService(repository, mock(MongoTemplate.class));

        TrackEventRequest request = new TrackEventRequest();
        request.setSessionId("session-1");
        request.setUserId("spoofed-user");
        request.setEventType("CLICK");
        request.setTarget("/target");
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            metadata.put("key-" + i, "x".repeat(500));
        }
        metadata.put("count", 3);
        metadata.put("enabled", true);
        request.setMetadata(metadata);
        request.setElementText("Nội dung sức khỏe riêng tư");

        User principal = new User();
        principal.setId("real-user");
        AnalyticsEvent event = service.trackEvent(request, principal, "127.0.0.1");

        assertEquals("real-user", event.getUserId());
        assertEquals(Map.of("count", 3, "enabled", true), event.getMetadata());
        assertNull(event.getElementText());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void analyticsStatsIncludesDailyActiveUsersForAuthenticatedUsersOnly() {
        AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);

        when(repository.count()).thenReturn(0L);
        when(repository.countByEventType(anyString())).thenReturn(0L);
        when(mongoTemplate.findDistinct(any(), anyString(), eq(AnalyticsEvent.class), eq(String.class))).thenReturn(List.of());
        when(mongoTemplate.getCollection("analytics_events")).thenReturn(collection);
        AggregateIterable<Document> dailyPageViews = aggregate(List.of());
        AggregateIterable<Document> dailyActiveUsers = aggregate(List.of(
                new Document("_id", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString())
                        .append("activeUsers", 2)
        ));
        AggregateIterable<Document> hourlyPageViews = aggregate(List.of());
        AggregateIterable<Document> topPages = aggregate(List.of());
        AggregateIterable<Document> clickRanking = aggregate(List.of());
        when(collection.aggregate(anyList(), eq(Document.class))).thenReturn(
                dailyPageViews,
                dailyActiveUsers,
                hourlyPageViews,
                topPages,
                clickRanking
        );

        AnalyticsService service = new AnalyticsService(repository, mongoTemplate);

        Map<String, Object> stats = service.getAnalyticsStats();
        Map<String, Object> overview = (Map<String, Object>) stats.get("overview");
        List<Map<String, Object>> trend = (List<Map<String, Object>>) stats.get("dailyActiveUsersTrend");

        assertEquals(2L, overview.get("dailyActiveUsersToday"));
        assertEquals(30, trend.size());
        assertEquals(2L, trend.get(trend.size() - 1).get("activeUsers"));
        assertEquals(0.29, overview.get("dailyActiveUsers7dAvg"));
    }

    private AggregateIterable<Document> aggregate(List<Document> docs) {
        AggregateIterable<Document> iterable = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        Boolean[] hasNext = new Boolean[docs.size() + 1];
        for (int i = 0; i < docs.size(); i++) {
            hasNext[i] = true;
        }
        hasNext[docs.size()] = false;
        when(cursor.hasNext()).thenReturn(hasNext[0], java.util.Arrays.copyOfRange(hasNext, 1, hasNext.length));
        if (!docs.isEmpty()) {
            when(cursor.next()).thenReturn(docs.get(0), docs.subList(1, docs.size()).toArray(Document[]::new));
        }
        when(iterable.iterator()).thenReturn(cursor);
        return iterable;
    }
}
