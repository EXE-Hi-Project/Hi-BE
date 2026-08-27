package com.hi.api.service;

import com.hi.api.model.PendingMediaUpload;
import com.hi.api.repository.PendingMediaUploadRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PendingMediaUploadService {
    private static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(20);

    private final PendingMediaUploadRepository repository;
    private final CloudinaryMediaService mediaService;

    public PendingMediaUploadService(PendingMediaUploadRepository repository, CloudinaryMediaService mediaService) {
        this.repository = repository;
        this.mediaService = mediaService;
    }

    public void register(String userId, String bucket, String objectKey) {
        PendingMediaUpload upload = new PendingMediaUpload();
        upload.setId(UUID.randomUUID().toString());
        upload.setUserId(userId);
        upload.setBucket(bucket);
        upload.setObjectKey(objectKey);
        upload.setExpiresAt(Instant.now().plus(CONFIRMATION_WINDOW));
        repository.save(upload);
    }

    public void confirm(String bucket, String objectKey) {
        repository.deleteByBucketAndObjectKey(bucket, objectKey);
    }

    @Scheduled(cron = "0 */10 * * * ?", zone = "Asia/Ho_Chi_Minh")
    public void cleanupExpiredUploads() {
        List<PendingMediaUpload> expired = repository.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant.now());
        for (PendingMediaUpload upload : expired) {
            if (CloudinaryMediaService.STORAGE_BUCKET.equals(upload.getBucket())) {
                mediaService.deleteImage(upload.getObjectKey());
            }
            repository.deleteById(upload.getId());
        }
    }
}
