package com.example.shopupu.identity.service;

import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the UserServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // handles getByEmail.
    @Test
    void getByEmailDelegatesToRepository() {
        User user = User.builder().email("user@example.com").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertTrue(userService.getByEmail("user@example.com").isPresent());
    }

    // handles getUsers.
    @Test
    void getUsersReturnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(User.builder().email("a@example.com").build()));

        assertEquals(1, userService.getUsers().size());
    }

    // handles registerUser.
    @Test
    void registerUserCreatesCustomerWithEncodedPassword() {
        Role customer = Role.builder().id(1L).name("CUSTOMER").build();
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customer));
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.registerUser("user@example.com", "password");

        assertEquals("user@example.com", created.getEmail());
        assertEquals("encoded", created.getPasswordHash());
        assertTrue(created.getRoles().contains(customer));
        verify(userRepository).save(any(User.class));
    }

    // handles registerUser.
    @Test
    void registerUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.registerUser("user@example.com", "password"));
    }
}
