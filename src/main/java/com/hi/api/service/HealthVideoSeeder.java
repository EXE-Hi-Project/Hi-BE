package com.hi.api.service;

import com.hi.api.model.HealthVideo;
import com.hi.api.model.HealthVideoStatus;
import com.hi.api.repository.HealthVideoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class HealthVideoSeeder implements ApplicationRunner {

    static final String SYSTEM_REVIEWER = "system-seed";

    private final HealthVideoRepository repository;
    private final SequenceService sequenceService;

    public HealthVideoSeeder(HealthVideoRepository repository, SequenceService sequenceService) {
        this.repository = repository;
        this.sequenceService = sequenceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SeedVideo item : defaultVideos()) {
            HealthVideo video = repository.findByYoutubeVideoId(item.youtubeVideoId())
                    .map(existing -> SYSTEM_REVIEWER.equals(existing.getReviewedBy()) ? apply(existing, item) : existing)
                    .orElseGet(() -> {
                        HealthVideo created = new HealthVideo();
                        created.setId(sequenceService.next("health_videos"));
                        return apply(created, item);
                    });

            if (SYSTEM_REVIEWER.equals(video.getReviewedBy())) {
                repository.save(video);
            }
        }
    }

    private HealthVideo apply(HealthVideo video, SeedVideo item) {
        video.setYoutubeVideoId(item.youtubeVideoId());
        video.setTitle(item.title());
        video.setDescription(item.description());
        video.setChannelName(item.channelName());
        video.setSourceUrl("https://www.youtube.com/watch?v=" + item.youtubeVideoId());
        video.setThumbnailUrl("https://i.ytimg.com/vi/" + item.youtubeVideoId() + "/hqdefault.jpg");
        video.setTopicTags(item.topicTags());
        video.setInterestTags(item.interestTags());
        video.setGoalTags(item.goalTags());
        video.setPhaseTags(item.phaseTags());
        video.setLanguage(item.language());
        video.setPriority(item.priority());
        video.setStatus(HealthVideoStatus.PUBLISHED);
        video.setReviewedAt(Instant.now());
        video.setReviewedBy(SYSTEM_REVIEWER);
        return video;
    }

    static List<SeedVideo> defaultVideos() {
        return List.of(
                new SeedVideo(
                        "4Zxep0PBnsM",
                        "Science-based relief from PMS",
                        "Mayo Clinic",
                        "Gợi ý dựa trên bằng chứng để giảm PMS bằng giấc ngủ, dinh dưỡng và vận động nhẹ.",
                        List.of("PMS", "lối sống", "giảm khó chịu"),
                        List.of("healthy_lifestyle", "nutrition"),
                        List.of("cycle_wellbeing", "track_cycle"),
                        List.of("hoàng thể", "kinh nguyệt"),
                        "en",
                        95
                ),
                new SeedVideo(
                        "Bipn51mxSbM",
                        "Home remedies for period cramps.",
                        "Cleveland Clinic",
                        "Các cách tự chăm sóc phổ biến khi đau bụng kinh từ nguồn bệnh viện học thuật.",
                        List.of("đau bụng kinh", "tự chăm sóc", "sức khỏe kinh nguyệt"),
                        List.of("healthy_lifestyle"),
                        List.of("track_cycle", "symptom_tracking"),
                        List.of("kinh nguyệt"),
                        "en",
                        90
                ),
                new SeedVideo(
                        "SQ5KqPCjasU",
                        "Periods: what is a period? | NHS",
                        "NHS",
                        "Giải thích cơ bản về kỳ kinh từ hệ thống y tế công của Vương quốc Anh.",
                        List.of("kiến thức cơ bản", "chu kỳ", "kinh nguyệt"),
                        List.of("education"),
                        List.of("track_cycle"),
                        List.of("kinh nguyệt"),
                        "en",
                        88
                ),
                new SeedVideo(
                        "ayzN5f3qN8g",
                        "How menstruation works - Emma Bryce",
                        "TED-Ed",
                        "Hoạt hình giáo dục dễ hiểu về cách hoạt động của kinh nguyệt.",
                        List.of("giáo dục", "chu kỳ", "cơ thể"),
                        List.of("education"),
                        List.of("track_cycle"),
                        List.of("kinh nguyệt", "rụng trứng"),
                        "en",
                        84
                ),
                new SeedVideo(
                        "gmKAuceSf-s",
                        "Reproductive cycle graph-Follicular phase | NCLEX-RN | Khan Academy",
                        "Khan Academy Medicine",
                        "Giải thích giai đoạn nang trứng trong chu kỳ sinh sản.",
                        List.of("giai đoạn nang trứng", "hormone", "giáo dục"),
                        List.of("education"),
                        List.of("track_cycle"),
                        List.of("nang trứng"),
                        "en",
                        78
                ),
                new SeedVideo(
                        "vXrQ_FhZmos",
                        "The Menstrual Cycle",
                        "Nemours KidsHealth",
                        "Video giáo dục sức khỏe giải thích chu kỳ kinh nguyệt theo ngôn ngữ dễ tiếp cận.",
                        List.of("chu kỳ", "giáo dục", "sức khỏe tuổi dậy thì"),
                        List.of("education"),
                        List.of("track_cycle"),
                        List.of("kinh nguyệt", "rụng trứng"),
                        "en",
                        76
                )
        );
    }

    record SeedVideo(String youtubeVideoId,
                     String title,
                     String channelName,
                     String description,
                     List<String> topicTags,
                     List<String> interestTags,
                     List<String> goalTags,
                     List<String> phaseTags,
                     String language,
                     int priority) {
    }
}
