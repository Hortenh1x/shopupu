package com.example.shopupu.auth.controller;

import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.auth.dto.LoginRequest;
import com.example.shopupu.auth.dto.RefreshRequest;
import com.example.shopupu.auth.dto.RegisterRequest;
import com.example.shopupu.auth.dto.TokenPairResponse;
import com.example.shopupu.auth.service.AuthService;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/**
 * describes the AuthController class.
 */
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    // handles register.
    public ResponseEntity<TokenPairResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.registerUser(req.email(), req.password());
        var pair = authService.issueTokens(user);
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @PostMapping("/login")
    // handles login.
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );
            var user = userService.getByEmail(req.email())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            var pair = authService.issueTokens(user);
            return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Wrong login or password");
        }
    }


    @PostMapping("/refresh")
    // handles refresh.
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        var pair = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    // handles getCurrentUser.
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
