package com.example.shopupu.common.exception;

/**
 * describes the BadRequestException class.
 */
public class BadRequestException extends RuntimeException {
    // handles BadRequestException.
    public BadRequestException(String message) {
        super(message);
    }
}