package com.example.shopupu.identity.service;


import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.AuthProvider;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * describes the UserService class.
 */
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // handles getByEmail.
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // handles getUsers.
    public org.springframework.data.domain.Page<User> getUsers(org.springframework.data.domain.Pageable pageable) {
        return userRepository.findAll(pageable);
    }


    // one-time token flows own the checks; these two are storage-only helpers
    public void markEmailVerified(User user) {
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    public void setPassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    // updates profile fields only - roles/enabled are never client-writable (SEC-10)
    public User updateProfile(String email, com.example.shopupu.identity.dto.UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setPreferredSize(request.preferredSize());
        user.setGender(request.gender());
        return userRepository.save(user);
    }

    // verifies the current password and stores the new hash; caller revokes sessions
    public User changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    // handles registerUser.
    public User registerUser(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("User with this email already exists");
        }

        Role defaultRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER role not found"));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .roles(Collections.singleton(defaultRole))
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    /**
     * Resolves the account behind a verified Google login (AUTH-13): links to an
     * existing email if one exists, otherwise provisions a Google-backed account.
     * The local password is a random, unusable value, so email/password login is
     * impossible for a Google-only account until the user sets one via reset.
     * The email is trusted as verified (the caller only calls this after Google
     * asserts {@code email_verified}).
     */
    public User findOrCreateGoogleUser(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            Role defaultRole = roleRepository.findByName("CUSTOMER")
                    .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER role not found"));
            User user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .roles(Collections.singleton(defaultRole))
                    .enabled(true)
                    .emailVerified(true)
                    .authProvider(AuthProvider.GOOGLE)
                    .build();
            return userRepository.save(user);
        });
    }
}
