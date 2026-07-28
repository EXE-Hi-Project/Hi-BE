package com.hi.api.service;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Arrays;

final class S3ImageContentValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private S3ImageContentValidator() {
    }

    static void verify(S3Client s3Client, String bucket, String objectKey, String declaredContentType) {
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .range("bytes=0-15")
                        .build()
        );
        String detected = detectContentType(response.asByteArray());
        if (!declaredContentType.equals(detected)) {
            throw new IllegalArgumentException("Noi dung file khong khop dinh dang anh da khai bao");
        }
    }

    static String detectContentType(byte[] bytes) {
        if (bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
            return "image/png";
        }
        if (bytes.length >= 12
                && ascii(bytes, 0, "RIFF")
                && ascii(bytes, 8, "WEBP")) {
            return "image/webp";
        }
        throw new IllegalArgumentException("File khong phai anh JPG, PNG hoac WebP hop le");
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
