package com.example.shopupu.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(unique = true)
    private String username;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(length = 32)
    private String phone;

    /** Clothing preference used for size suggestions (USER-01). */
    @Column(name = "preferred_size", length = 32)
    private String preferredSize;

    /** Self-selected gender, optional (USER-06). */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    /** Set when the account is anonymized on a GDPR erasure request (USER-05). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Confirmed via a one-time emailed token (AUTH-06). */
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    private boolean enabled = true;

    /** Identity provider that owns this login (AUTH-13). Google accounts sign in via ID token. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
