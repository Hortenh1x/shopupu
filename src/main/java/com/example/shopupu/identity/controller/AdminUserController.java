package com.example.shopupu.identity.controller;

import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
/**
 * describes the AdminUserController class.
 */
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    // handles getUsers.
    public ResponseEntity<List<UserProfile>> getUsers() {
        return ResponseEntity.ok(userService.getUsers().stream()
                .map(user -> new UserProfile(
                        user.getId(),
                        user.getEmail(),
                        user.isEnabled(),
                        user.getRoles().stream().map(role -> role.getName()).toList()
                ))
                .toList());
    }
}
