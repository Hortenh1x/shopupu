package com.example.shopupu.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "mfa_challenges")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MfaChallenge {
    public enum Kind { ENROLL, VERIFY }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Kind kind;
    @Column(name = "auth_version", nullable = false) private long authVersion;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "pending_secret", length = 512) private String pendingSecret;
    @Column(name = "guest_cart_token", length = 255) private String guestCartToken;
    @Column(name = "login_method", nullable = false, length = 16) private String loginMethod;
}
