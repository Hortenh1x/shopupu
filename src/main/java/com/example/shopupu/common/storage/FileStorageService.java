package com.example.shopupu.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * describes the FileStorageService interface.
 */
public interface FileStorageService {

    // handles storeProductImage.
    String storeProductImage(MultipartFile file);
}
