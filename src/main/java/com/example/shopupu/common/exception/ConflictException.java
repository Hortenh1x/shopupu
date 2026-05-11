package com.example.shopupu.common.exception;

/**
 * describes the ConflictException class.
 */
public class ConflictException extends RuntimeException {
    // handles ConflictException.
    public ConflictException(String message) {
        super(message);
    }
}