package com.example.shopupu.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.identity.entity.Gender;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.PersonalDataRepository;
import com.example.shopupu.identity.repository.UserAddressRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class GdprServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserAddressRepository addressRepository;
    @Mock OrderRepository orderRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock PersonalDataRepository personalDataRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CacheManager cacheManager;
    @Mock com.example.shopupu.common.audit.AuditService auditService;
    @InjectMocks GdprService gdprService;

    @BeforeEach void transaction() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void erasureUsesLockedFreshAccountAndErasesOptionalProfileAndMfaState() {
        User stale = User.builder().id(7L).email("old@example.invalid").build();
        User current = User.builder().id(7L).email("customer@example.com").username("customer")
                .firstName("Jane").lastName("Doe").phone("123").gender(Gender.FEMALE)
                .preferredSize("S").passwordHash("hash").authVersion(8).mfaSecretCiphertext("secret")
                .mfaLastAcceptedStep(123).enabled(true).build();
        Product product = new Product();
        product.setId(2L);
        Review review = new Review();
        review.setProduct(product);
        review.setBody("Private review");
        review.setStatus(ReviewStatus.APPROVED);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(current));
        when(reviewRepository.findByUserId(7L)).thenReturn(List.of(review));
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");

        gdprService.anonymizeAccount(stale);

        assertTrue(current.getEmail().startsWith("deleted-7-"));
        assertTrue(current.getEmail().endsWith("@anonymized.invalid"));
        assertNull(current.getUsername());
        assertNull(current.getGender());
        assertNull(current.getMfaSecretCiphertext());
        assertEquals(-1, current.getMfaLastAcceptedStep());
        assertEquals(9, current.getAuthVersion());
        assertNull(current.getFirstName());
        assertNull(current.getLastName());
        assertNull(current.getPhone());
        assertNull(current.getPreferredSize());
        assertFalse(current.isEnabled());
        assertNotNull(current.getDeletedAt());
        assertEquals(ReviewStatus.DELETED, review.getStatus());
        assertEquals("[deleted]", review.getBody());
        verify(auditService).record(eq("deleted-user:7"), eq("GDPR_ACCOUNT_ERASED"), eq("user"), eq("7"), anyString());
        var ordered = inOrder(userRepository, personalDataRepository, reviewRepository);
        ordered.verify(userRepository).findByIdForUpdate(7L);
        ordered.verify(personalDataRepository).lockOrders(7L);
        ordered.verify(reviewRepository).findByUserId(7L);
        ordered.verify(reviewRepository).saveAll(List.of(review));
        ordered.verify(reviewRepository).flush();
        ordered.verify(personalDataRepository).deleteReviewSummaries(List.of(2L));
        ordered.verify(personalDataRepository).eraseRelatedData(7L, "customer@example.com", "customer");
    }

    @Test
    void erasureReplayDoesNotRewriteAuditOrRotatePseudonym() {
        User deleted = User.builder().id(7L).email("deleted-7@anonymized.invalid").deletedAt(Instant.now()).build();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(deleted));
        gdprService.anonymizeAccount(deleted);
        verifyNoInteractions(personalDataRepository, passwordEncoder, reviewRepository, auditService);
    }
}
