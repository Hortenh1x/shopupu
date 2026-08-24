package com.example.shopupu.ai.model;

/** One turn of a stylist conversation; role is "user" or "assistant". */
public record ChatMessage(String role, String content) {
}
