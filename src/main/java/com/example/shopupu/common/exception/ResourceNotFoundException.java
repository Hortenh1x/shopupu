package com.example.shopupu.common.exception;

/**
 * describes the ResourceNotFoundException class.
 */
public class ResourceNotFoundException extends RuntimeException {
    // handles ResourceNotFoundException.
    public ResourceNotFoundException(String message) {
        super(message);
    }
}