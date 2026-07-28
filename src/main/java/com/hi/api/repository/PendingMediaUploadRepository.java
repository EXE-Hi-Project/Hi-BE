package com.hi.api.repository;

import com.hi.api.model.PendingMediaUpload;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface PendingMediaUploadRepository extends MongoRepository<PendingMediaUpload, String> {
    List<PendingMediaUpload> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant cutoff);
    void deleteByBucketAndObjectKey(String bucket, String objectKey);
}
