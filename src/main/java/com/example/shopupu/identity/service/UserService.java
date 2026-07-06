package com.example.shopupu.identity.service;


import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import java.util.Collections;
import java.util.Optional;
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


    // updates profile fields only - roles/enabled are never client-writable (SEC-10)
    public User updateProfile(String email, com.example.shopupu.identity.dto.UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setPreferredSize(request.preferredSize());
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
}
