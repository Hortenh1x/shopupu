package com.example.shopupu.common.exception;

/**
 * describes the BusinessRuleException class.
 */
public class BusinessRuleException extends RuntimeException {
    // handles BusinessRuleException.
    public BusinessRuleException(String message) {
        super(message);
    }
}