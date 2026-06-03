package com.hi.api.service;

import com.hi.api.dto.request.CycleRecordInsightResponse;
import com.hi.api.dto.request.UpsertHealthVideoRequest;
import com.hi.api.exception.ConflictException;
import com.hi.api.model.HealthVideo;
import com.hi.api.model.HealthVideoStatus;
import com.hi.api.model.User;
import com.hi.api.repository.HealthVideoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HealthVideoService {

    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{6,20}$");
    private static final List<Pattern> YOUTUBE_URL_PATTERNS = List.of(
            Pattern.compile("(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})"),
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{6,20})")
    );

    private final HealthVideoRepository repository;
    private final SequenceService sequenceService;
    private final CycleRecordService cycleRecordService;

    public HealthVideoService(HealthVideoRepository repository,
                              SequenceService sequenceService,
                              CycleRecordService cycleRecordService) {
        this.repository = repository;
        this.sequenceService = sequenceService;
        this.cycleRecordService = cycleRecordService;
    }

    public List<HealthVideo> getRecommendations(User user, int limit) {
        List<HealthVideo> published = repository.findByStatusOrderByPriorityDescTitleAsc(HealthVideoStatus.PUBLISHED);
        if (user == null) {
            return published.stream().limit(safeLimit(limit)).toList();
        }

        CycleRecordInsightResponse insights = "female".equalsIgnoreCase(user.getGender())
                ? cycleRecordService.getInsights(user.getId())
                : null;
        String phase = insights != null ? insights.getEstimatedPhase() : null;
        Set<String> interests = normalizedSet(user.getInterests());
        Set<String> goals = normalizedSet(user.getGoals());

        return published.stream()
                .sorted(Comparator
                        .comparingInt((HealthVideo video) -> recommendationScore(video, interests, goals, phase))
                        .reversed()
                        .thenComparing(HealthVideo::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(safeLimit(limit))
                .toList();
    }

    public List<HealthVideo> getAdminVideos() {
        return repository.findAll(Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("updatedAt")));
    }

    public HealthVideo create(UpsertHealthVideoRequest request, String adminId) {
        String youtubeVideoId = extractYoutubeVideoId(request.getYoutubeVideoId());
        repository.findByYoutubeVideoId(youtubeVideoId).ifPresent(existing -> {
            throw new ConflictException("Video YouTube này đã tồn tại");
        });
        HealthVideo video = new HealthVideo();
        video.setId(sequenceService.next("health_videos"));
        apply(video, request, adminId, youtubeVideoId);
        return repository.save(video);
    }

    public HealthVideo update(Long id, UpsertHealthVideoRequest request, String adminId) {
        HealthVideo video = getById(id);
        String youtubeVideoId = extractYoutubeVideoId(request.getYoutubeVideoId());
        repository.findByYoutubeVideoId(youtubeVideoId)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> {
                    throw new ConflictException("Video YouTube này đã tồn tại");
                });
        apply(video, request, adminId, youtubeVideoId);
        return repository.save(video);
    }

    public HealthVideo archive(Long id) {
        HealthVideo video = getById(id);
        video.setStatus(HealthVideoStatus.ARCHIVED);
        return repository.save(video);
    }

    public HealthVideo getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy video sức khỏe"));
    }

    private void apply(HealthVideo video, UpsertHealthVideoRequest request, String adminId, String youtubeVideoId) {
        video.setYoutubeVideoId(youtubeVideoId);
        video.setTitle(request.getTitle().trim());
        video.setDescription(text(request.getDescription()));
        video.setChannelName(request.getChannelName().trim());
        video.setSourceUrl("https://www.youtube.com/watch?v=" + youtubeVideoId);
        video.setThumbnailUrl("https://i.ytimg.com/vi/" + youtubeVideoId + "/hqdefault.jpg");
        video.setTopicTags(cleanTags(request.getTopicTags()));
        video.setInterestTags(cleanTags(request.getInterestTags()));
        video.setGoalTags(cleanTags(request.getGoalTags()));
        video.setPhaseTags(cleanTags(request.getPhaseTags()));
        video.setLanguage(textOrDefault(request.getLanguage(), "vi"));
        video.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        video.setStatus(request.getStatus() != null ? request.getStatus() : HealthVideoStatus.DRAFT);
        if (video.getStatus() == HealthVideoStatus.PUBLISHED) {
            video.setReviewedAt(Instant.now());
            video.setReviewedBy(adminId);
        } else {
            video.setReviewedAt(null);
            video.setReviewedBy(null);
        }
    }

    private int recommendationScore(HealthVideo video, Set<String> interests, Set<String> goals, String phase) {
        int score = video.getPriority() != null ? video.getPriority() : 0;
        if ("vi".equalsIgnoreCase(video.getLanguage())) {
            score += 100;
        }
        score += overlapCount(interests, video.getInterestTags()) * 20;
        score += overlapCount(goals, video.getGoalTags()) * 20;
        if (phase != null && normalizedSet(video.getPhaseTags()).contains(normalize(phase))) {
            score += 15;
        }
        return score;
    }

    private int overlapCount(Set<String> values, List<String> tags) {
        return (int) normalizedSet(tags).stream().filter(values::contains).count();
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extractYoutubeVideoId(String value) {
        String cleaned = text(value);
        if (YOUTUBE_ID_PATTERN.matcher(cleaned).matches()) {
            return cleaned;
        }
        for (Pattern pattern : YOUTUBE_URL_PATTERNS) {
            Matcher matcher = pattern.matcher(cleaned);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalArgumentException("YouTube URL hoặc video ID không hợp lệ");
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream().map(String::trim).filter(tag -> !tag.isBlank()).distinct().toList();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String textOrDefault(String value, String defaultValue) {
        String cleaned = text(value);
        return cleaned.isBlank() ? defaultValue : cleaned;
    }

    private long safeLimit(int limit) {
        return Math.min(Math.max(limit, 1), 20);
    }
}
