package com.hi.api.service;

import com.hi.api.dto.request.ConfirmAvatarUploadRequest;
import com.hi.api.dto.request.PresignAvatarUploadRequest;
import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAvatarService {
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.s3.user-media-bucket:}")
    private String bucket;

    @Value("${app.s3.public-base-url:}")
    private String publicBaseUrl;

    @Value("${app.s3.region:${aws.region:us-east-1}}")
    private String s3Region;

    public UserAvatarService(UserRepository userRepository, S3Client s3Client, S3Presigner s3Presigner) {
        this.userRepository = userRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public Map<String, Object> presignAvatar(User user, PresignAvatarUploadRequest request) {
        ensureConfigured();
        String contentType = normalizeContentType(request.getContentType());
        validateContentLength(request.getContentLength());

        String objectKey = "users/%s/avatar/%s%s".formatted(
                user.getId(),
                UUID.randomUUID(),
                extensionFor(contentType, request.getFileName())
        );
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(putObjectRequest)
                .build();

        String publicUrl = publicUrlFor(objectKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadUrl", s3Presigner.presignPutObject(presignRequest).url().toString());
        data.put("objectKey", objectKey);
        data.put("publicUrl", publicUrl);
        data.put("contentType", contentType);
        data.put("expiresInSeconds", PRESIGN_TTL.toSeconds());
        return data;
    }

    @CacheEvict(value = "ai_context", key = "#user.id")
    public User confirmAvatar(User user, ConfirmAvatarUploadRequest request) {
        ensureConfigured();
        String objectKey = clean(request.getObjectKey());
        if (!objectKey.startsWith("users/" + user.getId() + "/avatar/")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Avatar khong thuoc ve tai khoan nay");
        }

        verifyUploadedObject(objectKey);
        User persisted = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Nguoi dung khong ton tai"));
        persisted.setAvatar(publicUrlFor(objectKey));
        return userRepository.save(persisted);
    }

    private void verifyUploadedObject(String objectKey) {
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            normalizeContentType(head.contentType());
            validateContentLength(head.contentLength());
        } catch (NoSuchKeyException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chua tim thay file avatar tren S3");
        } catch (S3Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khong the xac thuc file avatar tren S3");
        }
    }

    private void ensureConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chua cau hinh S3 user media bucket");
        }
    }

    private void validateContentLength(Long contentLength) {
        if (contentLength == null || contentLength <= 0 || contentLength > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar phai nho hon 5MB");
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = clean(contentType).toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chi ho tro anh JPG, PNG hoac WebP");
        }
        return normalized;
    }

    private String publicUrlFor(String objectKey) {
        String base = clean(publicBaseUrl);
        if (!base.isBlank()) {
            return base.replaceAll("/+$", "") + "/" + objectKey;
        }
        if (bucket.contains(".")) {
            return "https://s3.%s.amazonaws.com/%s/%s".formatted(s3Region, bucket, objectKey);
        }
        if ("us-east-1".equals(s3Region)) {
            return "https://%s.s3.amazonaws.com/%s".formatted(bucket, objectKey);
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, s3Region, objectKey);
    }

    private String extensionFor(String contentType, String fileName) {
        String cleaned = clean(fileName).toLowerCase(Locale.ROOT);
        if (cleaned.endsWith(".jpg") || cleaned.endsWith(".jpeg")) return ".jpg";
        if (cleaned.endsWith(".png")) return ".png";
        if (cleaned.endsWith(".webp")) return ".webp";
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
