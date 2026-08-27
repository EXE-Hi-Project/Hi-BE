package com.hi.api.service;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryMediaService {
    public static final String STORAGE_BUCKET = "cloudinary";

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryMediaService(
            Cloudinary cloudinary,
            @Value("${app.media.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.media.cloudinary.api-key:}") String apiKey,
            @Value("${app.media.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.media.cloudinary.folder:hi}") String folder) {
        this.cloudinary = cloudinary;
        this.cloudName = clean(cloudName);
        this.apiKey = clean(apiKey);
        this.apiSecret = clean(apiSecret);
        this.folder = clean(folder).replaceAll("^/+|/+$", "");
    }

    public String publicId(String path) {
        String cleanedPath = clean(path).replaceAll("^/+", "");
        return folder.isBlank() ? cleanedPath : folder + "/" + cleanedPath;
    }

    public Map<String, Object> createSignedUpload(String publicId) {
        ensureConfigured();
        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> signable = new LinkedHashMap<>();
        signable.put("public_id", publicId);
        signable.put("timestamp", timestamp);
        signable.put("overwrite", false);

        Map<String, Object> uploadParams = new LinkedHashMap<>(signable);
        cloudinary.signRequest(uploadParams, Map.of());

        return Map.of(
                "uploadUrl", "https://api.cloudinary.com/v1_1/%s/image/upload".formatted(cloudName),
                "uploadMethod", "POST",
                "uploadParams", uploadParams,
                "expiresInSeconds", 600
        );
    }

    public MediaAsset verifiedImage(String publicId, long maxBytes, Set<String> allowedContentTypes) {
        ensureConfigured();
        try {
            Map<?, ?> resource = cloudinary.api().resource(publicId, Map.of("resource_type", "image"));
            long bytes = number(resource.get("bytes"));
            String contentType = contentTypeFor(String.valueOf(resource.get("format")));
            if (bytes <= 0 || bytes > maxBytes) {
                throw new IllegalArgumentException("Kich thuoc file khong hop le");
            }
            if (!allowedContentTypes.contains(contentType)) {
                throw new IllegalArgumentException("Chi ho tro anh JPG, PNG hoac WebP");
            }
            String secureUrl = clean(String.valueOf(resource.get("secure_url")));
            if (secureUrl.isBlank() || "null".equals(secureUrl)) {
                throw new IllegalArgumentException("Cloudinary khong tra ve URL anh hop le");
            }
            return new MediaAsset(contentType, secureUrl);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khong the xac thuc anh da tai len", ex);
        }
    }

    public void deleteImage(String publicId) {
        if (!isConfigured() || clean(publicId).isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image", "invalidate", true));
        } catch (Exception ignored) {
            // Cleanup is best-effort. It must never undo a successfully saved record.
        }
    }

    public void ensureConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chua cau hinh Cloudinary media storage");
        }
    }

    private boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    private String contentTypeFor(String format) {
        return switch (clean(format).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new IllegalArgumentException("File khong phai anh JPG, PNG hoac WebP hop le");
        };
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record MediaAsset(String contentType, String secureUrl) {
    }
}
