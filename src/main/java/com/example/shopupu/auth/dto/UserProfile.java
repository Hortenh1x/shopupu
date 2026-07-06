package com.example.shopupu.auth.dto;

import com.example.shopupu.identity.entity.User;
import java.util.List;

public record UserProfile(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String preferredSize,
        boolean enabled,
        List<String> roles
) {
    public static UserProfile from(User user) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getPreferredSize(),
                user.isEnabled(),
                user.getRoles().stream().map(role -> role.getName()).toList()
        );
    }
}
