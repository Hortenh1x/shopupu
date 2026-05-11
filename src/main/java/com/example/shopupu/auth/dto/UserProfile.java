package com.example.shopupu.auth.dto;

import java.util.List;

/**
 * describes the UserProfile record.
 */
public record UserProfile(Long id, String email, boolean enabled, List<String> roles) {}
