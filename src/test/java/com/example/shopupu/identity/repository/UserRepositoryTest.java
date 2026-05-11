package com.example.shopupu.identity.repository;

import com.example.shopupu.support.PostgresContainerSupport;
import com.example.shopupu.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
/**
 * describes the UserRepositoryTest test class.
 */
class UserRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    // handles testSaveAndFindByUsername.
    void testSaveAndFindByUsername() {
        User user = new User();
        user.setUsername("admin");
        user.setPasswordHash("12345");
        user.setEmail("admin@example.com");
        user.setEnabled(true);
        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("admin");
        assertTrue(found.isPresent());
        assertEquals("admin", found.get().getUsername());
    }
}
