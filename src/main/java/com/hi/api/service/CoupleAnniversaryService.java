package com.hi.api.service;

import com.hi.api.dto.request.UpdateCoupleStartDateRequest;
import com.hi.api.dto.request.UpsertCoupleAnniversaryEventRequest;
import com.hi.api.model.CoupleAnniversary;
import com.hi.api.model.User;
import com.hi.api.repository.CoupleAnniversaryRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoupleAnniversaryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> COLORS = List.of("pink", "rose", "violet", "sky", "emerald", "amber");
    private static final List<String> EFFECTS = List.of("none", "sparkle", "float", "glow", "confetti");
    private static final List<String> ICONS = List.of("favorite", "celebration", "cake", "local_florist", "photo_camera", "star");
    private static final List<String> STICKERS = List.of("heart", "ring", "flower", "moon", "sparkles", "ribbon");

    private final CoupleAnniversaryRepository repository;
    private final PartnerAccessService partnerAccessService;
    private final RealtimeEventService realtimeEventService;

    public CoupleAnniversaryService(CoupleAnniversaryRepository repository,
                                    PartnerAccessService partnerAccessService,
                                    RealtimeEventService realtimeEventService) {
        this.repository = repository;
        this.partnerAccessService = partnerAccessService;
        this.realtimeEventService = realtimeEventService;
    }

    public Map<String, Object> getAnniversaries(String userId) {
        Pair pair = requirePair(userId);
        CoupleAnniversary startDate = repository.findByPairKeyAndType(pair.pairKey(), CoupleAnniversary.Type.START_DATE)
                .orElse(null);
        List<CoupleAnniversary> events = repository
                .findByPairKeyAndTypeOrderByEventDateAsc(pair.pairKey(), CoupleAnniversary.Type.MEMORY);
        return response(startDate, events);
    }

    public Map<String, Object> updateStartDate(String userId, UpdateCoupleStartDateRequest request) {
        Pair pair = requirePair(userId);
        LocalDate today = LocalDate.now(APP_ZONE);
        if (request.getStartDate().isAfter(today)) {
            throw new IllegalArgumentException("Ngày bắt đầu bên nhau không được ở tương lai");
        }

        CoupleAnniversary startDate = repository.findByPairKeyAndType(pair.pairKey(), CoupleAnniversary.Type.START_DATE)
                .orElseGet(CoupleAnniversary::new);
        startDate.setPairKey(pair.pairKey());
        startDate.setType(CoupleAnniversary.Type.START_DATE);
        startDate.setEventDate(request.getStartDate());
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            startDate.setTitle(request.getTitle().trim());
        } else {
            startDate.setTitle("Ngày bên nhau");
        }
        startDate.setNote(request.getNote() == null ? "" : request.getNote().trim());
        startDate.setCreatedBy(startDate.getCreatedBy() != null ? startDate.getCreatedBy() : userId);
        startDate.setColor(normalizeOption(request.getColor(), COLORS, "pink"));
        startDate.setEffect(normalizeOption(request.getEffect(), EFFECTS, "sparkle"));
        startDate.setIcon(normalizeOption(request.getIcon(), ICONS, "favorite"));
        startDate.setSticker(normalizeOption(request.getSticker(), STICKERS, "heart"));
        CoupleAnniversary saved = repository.save(startDate);
        emitUpdated(pair);
        return response(saved, repository.findByPairKeyAndTypeOrderByEventDateAsc(pair.pairKey(), CoupleAnniversary.Type.MEMORY));
    }

    public Map<String, Object> createEvent(String userId, UpsertCoupleAnniversaryEventRequest request) {
        Pair pair = requirePair(userId);
        CoupleAnniversary event = new CoupleAnniversary();
        event.setPairKey(pair.pairKey());
        event.setType(CoupleAnniversary.Type.MEMORY);
        event.setCreatedBy(userId);
        applyEventFields(event, request);
        repository.save(event);
        emitUpdated(pair);
        return getAnniversaries(userId);
    }

    public Map<String, Object> updateEvent(String userId, String eventId, UpsertCoupleAnniversaryEventRequest request) {
        Pair pair = requirePair(userId);
        CoupleAnniversary event = repository.findByIdAndPairKeyAndType(eventId, pair.pairKey(), CoupleAnniversary.Type.MEMORY)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kỷ niệm"));
        applyEventFields(event, request);
        repository.save(event);
        emitUpdated(pair);
        return getAnniversaries(userId);
    }

    public Map<String, Object> deleteEvent(String userId, String eventId) {
        Pair pair = requirePair(userId);
        CoupleAnniversary event = repository.findByIdAndPairKeyAndType(eventId, pair.pairKey(), CoupleAnniversary.Type.MEMORY)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kỷ niệm"));
        repository.delete(event);
        emitUpdated(pair);
        return getAnniversaries(userId);
    }

    private void applyEventFields(CoupleAnniversary event, UpsertCoupleAnniversaryEventRequest request) {
        event.setEventDate(request.getEventDate());
        event.setTitle(request.getTitle().trim());
        event.setNote(request.getNote() == null ? "" : request.getNote().trim());
        event.setColor(normalizeOption(request.getColor(), COLORS, "pink"));
        event.setEffect(normalizeOption(request.getEffect(), EFFECTS, "none"));
        event.setIcon(normalizeOption(request.getIcon(), ICONS, "favorite"));
        event.setSticker(normalizeOption(request.getSticker(), STICKERS, "heart"));
    }

    private String normalizeOption(String value, List<String> allowed, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toLowerCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Tùy chọn hiển thị không hợp lệ");
        }
        return normalized;
    }

    private Pair requirePair(String userId) {
        User user = partnerAccessService.requireUser(userId);
        User partner = partnerAccessService.requireCurrentPartner(user);
        String pairKey = partnerAccessService.pairKey(user.getId(), partner.getId());
        if (!partnerAccessService.isActivePair(user.getId(), partner.getId())) {
            throw new AccessDeniedException("Liên kết Người ấy không còn hợp lệ");
        }
        return new Pair(user, partner, pairKey);
    }

    private Map<String, Object> response(CoupleAnniversary startDate, List<CoupleAnniversary> events) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", startDate);
        result.put("daysTogether", daysTogether(startDate));
        result.put("events", events);
        result.put("options", Map.of(
                "colors", COLORS,
                "effects", EFFECTS,
                "icons", ICONS,
                "stickers", STICKERS
        ));
        return result;
    }

    private Long daysTogether(CoupleAnniversary startDate) {
        if (startDate == null || startDate.getEventDate() == null) return null;
        // Tính ngày theo múi giờ Việt Nam để hai tài khoản thấy cùng một con số.
        return ChronoUnit.DAYS.between(startDate.getEventDate(), LocalDate.now(APP_ZONE)) + 1;
    }

    private void emitUpdated(Pair pair) {
        Map<String, Object> data = Map.of("pairKey", pair.pairKey());
        realtimeEventService.sendPartner(pair.user().getId(), "partner.anniversary.updated", data);
        realtimeEventService.sendPartner(pair.partner().getId(), "partner.anniversary.updated", data);
    }

    private record Pair(User user, User partner, String pairKey) {}
}
