package com.example.shopupu.auth.controller;

import com.example.shopupu.auth.dto.ChangePasswordRequest;
import com.example.shopupu.auth.dto.LoginRequest;
import com.example.shopupu.auth.dto.RefreshRequest;
import com.example.shopupu.auth.dto.RegisterRequest;
import com.example.shopupu.auth.dto.TokenPairResponse;
import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.auth.service.AuthService;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<TokenPairResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.registerUser(req.email(), req.password());
        var pair = authService.issueTokens(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest req) {
        var pair = authService.login(req.email(), req.password());
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        var pair = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(authentication.getName(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfile> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        var user = userService.getByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(new UserProfile(
                user.getId(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles().stream().map(role -> role.getName()).toList()
        ));
    }
}
