package com.example.shopupu.identity.service;


import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    public List<User> getUsers() {
        return userRepository.findAll();
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