package com.example.shopupu.identity.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.support.PostgresContainerSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

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
