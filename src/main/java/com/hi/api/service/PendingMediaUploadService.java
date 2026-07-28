package com.hi.api.service;

import com.hi.api.model.PendingMediaUpload;
import com.hi.api.repository.PendingMediaUploadRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PendingMediaUploadService {
    private static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(20);

    private final PendingMediaUploadRepository repository;
    private final S3Client s3Client;

    public PendingMediaUploadService(PendingMediaUploadRepository repository, S3Client s3Client) {
        this.repository = repository;
        this.s3Client = s3Client;
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
        List<PendingMediaUpload> expired =
                repository.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant.now());
        for (PendingMediaUpload upload : expired) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(upload.getBucket())
                        .key(upload.getObjectKey())
                        .build());
            } catch (S3Exception ignored) {
                continue;
            }
            repository.deleteById(upload.getId());
        }
    }
}
