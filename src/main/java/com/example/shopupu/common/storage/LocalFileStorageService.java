package com.example.shopupu.common.storage;

import com.example.shopupu.common.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadsDir;
    private final String publicBaseUrl;

    public LocalFileStorageService(
            @Value("${app.uploads.dir:uploads}") String uploadsDir,
            @Value("${app.uploads.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl
    ) {
        this.uploadsDir = Path.of(uploadsDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String storeProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required");
        }

        // The client-supplied Content-Type is untrusted; the file signature decides
        // both acceptance and the stored extension (SEC-12).
        ImageType type = detectImageType(file);

        String fileName = UUID.randomUUID() + "." + type.extension;
        Path productDir = uploadsDir.resolve("products");
        Path target = productDir.resolve(fileName).normalize();
        if (!target.startsWith(productDir)) {
            throw new BadRequestException("Invalid file name");
        }

        try {
            Files.createDirectories(productDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store product image", ex);
        }

        return publicBaseUrl + "/products/" + fileName;
    }

    private enum ImageType {
        JPEG("jpg"), PNG("png"), GIF("gif"), WEBP("webp");

        final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }
    }

    private ImageType detectImageType(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the uploaded file");
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return ImageType.PNG;
        }
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return ImageType.GIF;
        }
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageType.WEBP;
        }
        throw new BadRequestException("Only jpeg, png, webp, and gif images are allowed");
    }
}
