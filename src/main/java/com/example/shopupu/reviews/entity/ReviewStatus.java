package com.example.shopupu.reviews.entity;

/** Moderation lifecycle (REV-02): new reviews wait for approval. */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    DELETED
}
