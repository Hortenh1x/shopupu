package com.example.shopupu.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.auth.service.RefreshTokenService;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserAddressRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.identity.repository.WishlistItemRepository;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class GdprServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAddressRepository addressRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private GdprService gdprService;

    @Test
    void anonymizeWipesPiiAndRevokesSessions() {
        User user = User.builder()
                .id(7L)
                .email("customer@example.com")
                .username("customer")
                .firstName("Jane")
                .lastName("Doe")
                .phone("+380001112233")
                .passwordHash("hash")
                .enabled(true)
                .build();
        Review review = new Review();
        review.setBody("Great hoodie");
        review.setStatus(ReviewStatus.APPROVED);
        when(reviewRepository.findByUserId(7L)).thenReturn(List.of(review));
        when(cartRepository.findByUser(user)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gdprService.anonymizeAccount(user);

        assertEquals("deleted-7@anonymized.invalid", user.getEmail());
        assertNull(user.getUsername());
        assertNull(user.getFirstName());
        assertNull(user.getLastName());
        assertNull(user.getPhone());
        assertFalse(user.isEnabled());
        assertNotNull(user.getDeletedAt());
        assertEquals("random-hash", user.getPasswordHash());

        assertEquals(ReviewStatus.DELETED, review.getStatus());
        assertEquals("[deleted]", review.getBody());

        verify(addressRepository).deleteByUser(user);
        verify(wishlistItemRepository).deleteByUser(user);
        verify(refreshTokenService).revokeAll(user);
        verify(auditService).record(eqActor(), any(), any(), any(), any());
    }

    private static String eqActor() {
        return org.mockito.ArgumentMatchers.eq("customer@example.com");
    }
}
