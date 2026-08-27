package com.hi.api.service;

import com.hi.api.model.PendingMediaUpload;
import com.hi.api.repository.PendingMediaUploadRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingMediaUploadServiceTest {

    @Test
    void removesExpiredObjectAndQueueRecord() {
        PendingMediaUploadRepository repository = mock(PendingMediaUploadRepository.class);
        CloudinaryMediaService mediaService = mock(CloudinaryMediaService.class);
        PendingMediaUpload upload = new PendingMediaUpload();
        upload.setId("pending-1");
        upload.setBucket(CloudinaryMediaService.STORAGE_BUCKET);
        upload.setObjectKey("users/u/avatar/file.jpg");
        upload.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(any()))
                .thenReturn(List.of(upload), List.of());

        new PendingMediaUploadService(repository, mediaService).cleanupExpiredUploads();

        verify(mediaService).deleteImage("users/u/avatar/file.jpg");
        verify(repository).deleteById("pending-1");
    }
}
