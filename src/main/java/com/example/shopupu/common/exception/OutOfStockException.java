package com.example.shopupu.common.exception;

public class OutOfStockException extends BusinessRuleException {
    public OutOfStockException(String message) {
        super(message);
    }
}
