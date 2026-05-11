package com.example.shopupu.common.exception;

/**
 * describes the ForbiddenOperationException class.
 */
public class ForbiddenOperationException extends RuntimeException {
    // handles ForbiddenOperationException.
    public ForbiddenOperationException(String message) {
        super(message);
    }
}