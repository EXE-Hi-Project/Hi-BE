package com.hi.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3ImageContentValidatorTest {

    @Test
    void detectsSupportedImageSignatures() {
        assertEquals("image/jpeg", S3ImageContentValidator.detectContentType(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        assertEquals("image/png", S3ImageContentValidator.detectContentType(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("image/webp", S3ImageContentValidator.detectContentType(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
    }

    @Test
    void rejectsExecutableOrUnknownContent() {
        assertThrows(IllegalArgumentException.class,
                () -> S3ImageContentValidator.detectContentType("MZ executable".getBytes()));
    }
}
