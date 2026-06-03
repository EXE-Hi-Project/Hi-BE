package com.hi.api.service;

import com.hi.api.dto.request.UpsertHealthVideoRequest;
import com.hi.api.exception.ConflictException;
import com.hi.api.model.HealthVideo;
import com.hi.api.model.HealthVideoStatus;
import com.hi.api.model.User;
import com.hi.api.repository.HealthVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthVideoServiceTest {

    private HealthVideoRepository repository;
    private SequenceService sequenceService;
    private HealthVideoService service;

    @BeforeEach
    void setUp() {
        repository = mock(HealthVideoRepository.class);
        sequenceService = mock(SequenceService.class);
        service = new HealthVideoService(repository, sequenceService, mock(CycleRecordService.class));
    }

    @Test
    void recommendationsPreferVietnamesePublishedVideo() {
        HealthVideo english = video(1L, "English", "en", 90);
        HealthVideo vietnamese = video(2L, "Tiếng Việt", "vi", 0);
        when(repository.findByStatusOrderByPriorityDescTitleAsc(HealthVideoStatus.PUBLISHED))
                .thenReturn(List.of(english, vietnamese));
        User user = new User();
        user.setId("male-1");
        user.setGender("male");

        List<HealthVideo> recommendations = service.getRecommendations(user, 2);

        assertEquals(List.of(vietnamese, english), recommendations);
    }

    @Test
    void createRejectsDuplicateYoutubeVideoId() {
        UpsertHealthVideoRequest request = request("abc123xyz89");
        when(repository.findByYoutubeVideoId(request.getYoutubeVideoId()))
                .thenReturn(Optional.of(video(1L, "Existing", "vi", 0)));

        assertThrows(ConflictException.class, () -> service.create(request, "admin-1"));
    }

    @Test
    void createBuildsOfficialYoutubeUrls() {
        UpsertHealthVideoRequest request = request("abc123xyz89");
        when(sequenceService.next("health_videos")).thenReturn(5L);
        when(repository.findByYoutubeVideoId(request.getYoutubeVideoId())).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        HealthVideo video = service.create(request, "admin-1");

        assertEquals("https://www.youtube.com/watch?v=abc123xyz89", video.getSourceUrl());
        assertEquals("https://i.ytimg.com/vi/abc123xyz89/hqdefault.jpg", video.getThumbnailUrl());
        verify(repository).save(video);
    }

    private HealthVideo video(Long id, String title, String language, int priority) {
        HealthVideo video = new HealthVideo();
        video.setId(id);
        video.setYoutubeVideoId("video-" + id);
        video.setTitle(title);
        video.setChannelName("Nguồn duyệt");
        video.setLanguage(language);
        video.setPriority(priority);
        video.setStatus(HealthVideoStatus.PUBLISHED);
        return video;
    }

    private UpsertHealthVideoRequest request(String youtubeVideoId) {
        UpsertHealthVideoRequest request = new UpsertHealthVideoRequest();
        request.setYoutubeVideoId(youtubeVideoId);
        request.setTitle("Video sức khỏe");
        request.setChannelName("Nguồn duyệt");
        request.setStatus(HealthVideoStatus.PUBLISHED);
        return request;
    }
}
