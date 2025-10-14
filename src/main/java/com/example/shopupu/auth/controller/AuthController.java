package com.example.shopupu.auth.controller;

import com.example.shopupu.auth.service.AuthService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final AuthService authService;

    // ===== DTO =====
    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record TokenPairResponse(String accessToken, String refreshToken) {}

    @PostMapping("/register")
    public ResponseEntity<TokenPairResponse> register(@RequestBody RegisterRequest req) {
        User user = userService.registerUser(req.email(), req.password());
        var pair = authService.issueTokens(user);
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );
            var user = userService.getByEmail(req.email())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            var pair = authService.issueTokens(user);
            return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Wrong login or password");
        }
    }

    /** обновление access-токена по refresh-токену */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@RequestBody RefreshRequest req) {
        var pair = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken()));
    }
}