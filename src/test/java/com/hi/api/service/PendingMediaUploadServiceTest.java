package com.hi.api.service;

import com.hi.api.model.PendingMediaUpload;
import com.hi.api.repository.PendingMediaUploadRepository;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

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
        S3Client s3Client = mock(S3Client.class);
        PendingMediaUpload upload = new PendingMediaUpload();
        upload.setId("pending-1");
        upload.setBucket("media");
        upload.setObjectKey("users/u/avatar/file.jpg");
        upload.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(any()))
                .thenReturn(List.of(upload), List.of());

        new PendingMediaUploadService(repository, s3Client).cleanupExpiredUploads();

        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        verify(repository).deleteById("pending-1");
    }
}
