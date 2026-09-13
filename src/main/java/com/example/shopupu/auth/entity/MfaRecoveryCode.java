package com.example.shopupu.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "mfa_recovery_codes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MfaRecoveryCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "code_hash", nullable = false, unique = true, length = 64) private String codeHash;
    @Column(name = "used_at") private Instant usedAt;
}
