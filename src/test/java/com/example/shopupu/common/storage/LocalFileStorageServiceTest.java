package com.example.shopupu.common.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.common.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Signature-level fixtures only; these tests do not claim full image decoding or malware detection. */
class LocalFileStorageServiceTest {
    @TempDir Path uploads;

    @Test
    void missingAndEmptyFilesAreRejectedWithoutCreatingPublicArtifacts() {
        var storage = storage();
        assertThrows(BadRequestException.class, () -> storage.storeProductImage(null));
        assertThrows(BadRequestException.class, () -> storage.storeProductImage(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])));
        assertFalse(Files.exists(uploads.resolve("products")));
    }

    @ParameterizedTest
    @MethodSource("allowedSignatures")
    void signatureDeterminesGeneratedExtensionDespiteUntrustedFilenameAndContentType(String hex, String extension)
            throws IOException {
        byte[] content = bytes(hex);
        String url = storage().storeProductImage(new MockMultipartFile("file", "../../outside.svg",
                "text/html", content));
        assertTrue(url.startsWith("https://assets.example.test/uploads/products/"));
        String name = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(name.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\." + extension));
        assertArrayEquals(content, Files.readAllBytes(uploads.resolve("products").resolve(name)));
        assertFalse(Files.exists(uploads.resolve("outside.svg")));
    }

    @ParameterizedTest
    @MethodSource("rejectedSignatures")
    void falseOrTruncatedImageSignaturesAreRejectedRegardlessOfDeclaredMime(String hex) {
        assertThrows(BadRequestException.class, () -> storage().storeProductImage(
                new MockMultipartFile("file", "trusted.png", "image/png", bytes(hex))));
        assertFalse(Files.exists(uploads.resolve("products")));
    }

    @Test
    void completeHeaderCanArriveInSmallReads() throws IOException {
        byte[] content = bytes("89504e470d0a1a0a00000000");
        var file = new MockMultipartFile("file", "ignored", "application/octet-stream", content) {
            @Override public java.io.InputStream getInputStream() {
                return new ByteArrayInputStream(content) {
                    @Override public synchronized int read(byte[] destination, int offset, int length) {
                        return super.read(destination, offset, Math.min(length, 1));
                    }
                };
            }
        };
        assertTrue(storage().storeProductImage(file).endsWith(".png"));
    }

    @Test
    void unreadableUploadReturnsSafeValidationFailureAndCreatesNoArtifact() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("private-path-fixture"));
        BadRequestException failure = assertThrows(BadRequestException.class, () -> storage().storeProductImage(file));
        assertEquals("Could not read the uploaded file", failure.getMessage());
        assertFalse(Files.exists(uploads.resolve("products")));
    }

    private LocalFileStorageService storage() {
        return new LocalFileStorageService(uploads.toString(), "https://assets.example.test/uploads///");
    }

    static Stream<Arguments> allowedSignatures() {
        return Stream.of(
                Arguments.of("ffd8ffdb00000000", "jpg"),
                Arguments.of("89504e470d0a1a0a00000000", "png"),
                Arguments.of("4749463837610000", "gif"),
                Arguments.of("4749463839610000", "gif"),
                Arguments.of("524946460400000057454250", "webp"));
    }

    static Stream<String> rejectedSignatures() {
        return Stream.of(
                "3c68746d6c3e",                  // HTML with an image MIME type
                "3c7376673e",                    // SVG is deliberately outside the allowlist
                "504b03040000000000000000",      // ZIP
                "ffd8",                          // JPEG prefix truncated
                "89504e470d0a1a",                // PNG signature truncated
                "89504e4700000000",              // PNG prefix with invalid trailing signature bytes
                "89504e470d0a1a00",              // PNG wrong final signature byte
                "4749463837",                    // GIF signature truncated
                "474946383078",                  // GIF80x is not GIF87a or GIF89a
                "5249464604000000574542",        // WebP signature truncated
                "524946460400000057415645");     // RIFF/WAVE is not WebP
    }

    private static byte[] bytes(String hex) { return HexFormat.of().parseHex(hex); }
}
