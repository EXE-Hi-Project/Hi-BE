package com.hi.api.service;

import com.hi.api.model.HealthVideo;
import com.hi.api.repository.HealthVideoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthVideoSeederTest {

    @Test
    void seedsDefaultPublishedVideosIdempotently() throws Exception {
        HealthVideoRepository repository = mock(HealthVideoRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        when(repository.findByYoutubeVideoId(anyString())).thenReturn(Optional.empty());
        when(sequenceService.next("health_videos")).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        new HealthVideoSeeder(repository, sequenceService).run(new DefaultApplicationArguments());

        ArgumentCaptor<HealthVideo> captor = ArgumentCaptor.forClass(HealthVideo.class);
        verify(repository, atLeast(6)).save(captor.capture());
        assertEquals(6, captor.getAllValues().size());
        assertTrue(captor.getAllValues().stream().allMatch(video -> HealthVideoSeeder.SYSTEM_REVIEWER.equals(video.getReviewedBy())));
    }

    @Test
    void doesNotOverwriteAdminManagedVideo() throws Exception {
        HealthVideoRepository repository = mock(HealthVideoRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        HealthVideo existing = new HealthVideo();
        existing.setId(99L);
        existing.setYoutubeVideoId("4Zxep0PBnsM");
        existing.setReviewedBy("admin-1");
        when(repository.findByYoutubeVideoId("4Zxep0PBnsM")).thenReturn(Optional.of(existing));
        when(repository.findByYoutubeVideoId(org.mockito.ArgumentMatchers.argThat(id -> !"4Zxep0PBnsM".equals(id)))).thenReturn(Optional.of(existing));

        new HealthVideoSeeder(repository, sequenceService).run(new DefaultApplicationArguments());

        verify(repository, never()).save(existing);
    }
}
