package com.example.shopupu.identity.controller;

import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserProfile>> getUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getUsers(pageable)
                .map(user -> new UserProfile(
                        user.getId(),
                        user.getEmail(),
                        user.isEnabled(),
                        user.getRoles().stream().map(role -> role.getName()).toList()
                )));
    }
}
