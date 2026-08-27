package com.hi.api.service;

import com.hi.api.dto.request.ConfirmAvatarUploadRequest;
import com.hi.api.dto.request.PresignAvatarUploadRequest;
import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAvatarService {
    private static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final CloudinaryMediaService mediaService;
    private final RateLimitService rateLimitService;
    private final PendingMediaUploadService pendingMediaUploadService;

    public UserAvatarService(UserRepository userRepository,
                             CloudinaryMediaService mediaService,
                             RateLimitService rateLimitService,
                             PendingMediaUploadService pendingMediaUploadService) {
        this.userRepository = userRepository;
        this.mediaService = mediaService;
        this.rateLimitService = rateLimitService;
        this.pendingMediaUploadService = pendingMediaUploadService;
    }

    public Map<String, Object> presignAvatar(User user, PresignAvatarUploadRequest request) {
        mediaService.ensureConfigured();
        rateLimitService.check(
                "upload:avatar:presign", user.getId(), 20, Duration.ofHours(1),
                "Ban da tao qua nhieu yeu cau tai avatar. Vui long thu lai sau."
        );
        String contentType = normalizeContentType(request.getContentType());
        validateContentLength(request.getContentLength());

        String objectKey = mediaService.publicId("users/%s/avatar/%s".formatted(user.getId(), UUID.randomUUID()));
        Map<String, Object> data = new LinkedHashMap<>(mediaService.createSignedUpload(objectKey));
        pendingMediaUploadService.register(user.getId(), CloudinaryMediaService.STORAGE_BUCKET, objectKey);
        data.put("objectKey", objectKey);
        data.put("contentType", contentType);
        return data;
    }

    @CacheEvict(value = "ai_context", key = "#user.id")
    public User confirmAvatar(User user, ConfirmAvatarUploadRequest request) {
        String objectKey = clean(request.getObjectKey());
        String prefix = mediaService.publicId("users/" + user.getId() + "/avatar/");
        if (!objectKey.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Avatar khong thuoc ve tai khoan nay");
        }

        CloudinaryMediaService.MediaAsset asset;
        try {
            asset = mediaService.verifiedImage(objectKey, MAX_AVATAR_BYTES, ALLOWED_CONTENT_TYPES);
        } catch (IllegalArgumentException ex) {
            mediaService.deleteImage(objectKey);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        User persisted = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Nguoi dung khong ton tai"));
        String previousAvatar = persisted.getAvatar();
        persisted.setAvatar(asset.secureUrl());
        User saved = userRepository.save(persisted);
        pendingMediaUploadService.confirm(CloudinaryMediaService.STORAGE_BUCKET, objectKey);
        deletePreviousManagedAvatar(previousAvatar, user.getId(), objectKey);
        return saved;
    }

    private void deletePreviousManagedAvatar(String previousUrl, String userId, String currentObjectKey) {
        String prefix = "/" + mediaService.publicId("users/" + userId + "/avatar/");
        String previousObjectKey = publicIdFromUrl(previousUrl, prefix);
        if (previousObjectKey.isBlank() || previousObjectKey.equals(currentObjectKey)) return;
        mediaService.deleteImage(previousObjectKey);
    }

    private String publicIdFromUrl(String url, String marker) {
        String value = clean(url);
        int markerIndex = value.indexOf(marker);
        if (markerIndex < 0) return "";
        String publicId = value.substring(markerIndex + 1).split("[?#]", 2)[0];
        return publicId.replaceFirst("\\.[A-Za-z0-9]{2,5}$", "");
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

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
