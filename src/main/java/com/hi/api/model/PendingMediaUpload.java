package com.hi.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "pending_media_uploads")
@CompoundIndex(name = "pending_media_bucket_key_idx", def = "{'bucket': 1, 'objectKey': 1}", unique = true)
public class PendingMediaUpload {
    @Id
    private String id;
    private String userId;
    private String bucket;
    private String objectKey;
    @Indexed
    private Instant expiresAt;
}
