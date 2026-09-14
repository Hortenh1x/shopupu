package com.example.shopupu.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.security.JwtTokenProvider;
import com.example.shopupu.security.ShopUserDetails;
import com.example.shopupu.support.PostgresContainerSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SEC-12 / upload quotas against a real Tomcat: MockMvc bypasses the multipart
 * parser, so the 5 MB ceiling and the way it is reported can only be checked
 * over a socket. Files that fit but are not images must be refused before
 * anything is written under the uploads directory.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UploadLimitsIT extends PostgresContainerSupport {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadsDir(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.dir", () -> uploads.toString());
    }

    @LocalServerPort
    int port;

    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired JwtTokenProvider jwt;

    private final HttpClient http = HttpClient.newHttpClient();
    private String adminToken;
    private Long productId;

    @BeforeEach
    void fixtures() throws IOException {
        // The uploads dir is shared by the whole class: start every test from an empty tree.
        if (Files.exists(uploads)) {
            try (Stream<Path> files = Files.walk(uploads)) {
                files.filter(Files::isRegularFile).forEach(f -> f.toFile().delete());
            }
        }
        Role admin = roles.findByName("ADMIN").orElseGet(() -> roles.save(Role.builder().name("ADMIN").build()));
        User user = users.save(User.builder()
                .email("upload-admin-" + System.nanoTime() + "@example.com")
                .passwordHash("test-hash")
                .enabled(true)
                .mfaSecretCiphertext("enrolled-for-test")
                .roles(Set.of(admin))
                .build());
        adminToken = jwt.generateToken(new ShopUserDetails(user), Instant.now());

        Category category = categories.save(new Category("Uploads", "uploads-" + System.nanoTime(), null, null));
        Product product = new Product("Upload Tee", "upload-tee-" + System.nanoTime(), "test", new BigDecimal("20.00"), category);
        product.setEnabled(true);
        productId = products.save(product).getId();
    }

    @Test
    void anUploadOverTheLimitIsRejectedAs413WithProblemDetailsAndNothingIsStored() throws Exception {
        byte[] oversized = new byte[6 * 1024 * 1024];
        oversized[0] = (byte) 0x89; oversized[1] = 'P'; oversized[2] = 'N'; oversized[3] = 'G';
        oversized[4] = 0x0D; oversized[5] = 0x0A; oversized[6] = 0x1A; oversized[7] = 0x0A;

        HttpResponse<String> response = upload("huge.png", "image/png", oversized);

        assertEquals(413, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"code\":\"PAYLOAD_TOO_LARGE\""), response.body());
        assertTrue(response.body().contains("\"requestId\""), response.body());
        assertFalse(response.body().contains("Exception"), "no internals leak: " + response.body());
        assertEquals(0, storedFiles(), "an oversized upload must leave nothing behind");
    }

    @Test
    void aFileThatFitsButIsNotAnImageIsRefusedBeforeItIsStored() throws Exception {
        byte[] script = "<?php echo 'not an image'; ?>".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = upload("innocent.png", "image/png", script);

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("Only jpeg, png, webp, and gif images are allowed"), response.body());
        assertEquals(0, storedFiles(), "a rejected file must not reach the uploads directory");
    }

    @Test
    void aRealImageUnderTheLimitIsStoredOnceWithAGeneratedName() throws Exception {
        byte[] png = new byte[64 * 1024];
        png[0] = (byte) 0x89; png[1] = 'P'; png[2] = 'N'; png[3] = 'G';
        png[4] = 0x0D; png[5] = 0x0A; png[6] = 0x1A; png[7] = 0x0A;

        HttpResponse<String> response = upload("../../etc/passwd.png", "image/png", png);

        assertEquals(201, response.statusCode(), response.body());
        assertFalse(response.body().contains("passwd"), "client file names never reach the URL: " + response.body());
        assertEquals(1, storedFiles());
        try (Stream<Path> files = Files.walk(uploads)) {
            assertTrue(files.filter(Files::isRegularFile).allMatch(f -> f.startsWith(uploads.resolve("products"))));
        }
    }

    private HttpResponse<String> upload(String fileName, String contentType, byte[] bytes) throws IOException, InterruptedException {
        String boundary = "it-" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + bytes.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(bytes, 0, body, head.length, bytes.length);
        System.arraycopy(tail, 0, body, head.length + bytes.length, tail.length);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/catalog/products/" + productId + "/images"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long storedFiles() throws IOException {
        if (!Files.exists(uploads)) return 0;
        try (Stream<Path> files = Files.walk(uploads)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

}
