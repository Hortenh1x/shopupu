package com.example.shopupu.common.storage;

import com.example.shopupu.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
/**
 * describes the LocalFileStorageService class.
 */
public class LocalFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Path uploadsDir;
    private final String publicBaseUrl;

    // handles LocalFileStorageService.
    public LocalFileStorageService(
            @Value("${app.uploads.dir:uploads}") String uploadsDir,
            @Value("${app.uploads.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl
    ) {
        this.uploadsDir = Path.of(uploadsDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    // handles storeProductImage.
    public String storeProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Only jpeg, png, webp, and gif images are allowed");
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String safeExtension = extension == null ? "bin" : extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String fileName = UUID.randomUUID() + "." + safeExtension;
        Path productDir = uploadsDir.resolve("products");
        Path target = productDir.resolve(fileName).normalize();

        try {
            Files.createDirectories(productDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store product image", ex);
        }

        return publicBaseUrl + "/products/" + fileName;
    }
}
