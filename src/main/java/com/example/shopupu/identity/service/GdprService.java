package com.example.shopupu.identity.service;

import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.auth.service.RefreshTokenService;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.identity.dto.AddressResponse;
import com.example.shopupu.identity.dto.UserDataExport;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserAddressRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.identity.repository.WishlistItemRepository;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR rights (USER-05/COMPL-01): data export and right-to-be-forgotten.
 * Orders survive anonymized for bookkeeping; everything personal is wiped.
 */
@Service
@RequiredArgsConstructor
public class GdprService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserDataExport exportData(User user) {
        List<AddressResponse> addresses = addressRepository
                .findByUserOrderByDefaultAddressDescCreatedAtAsc(user).stream()
                .map(AddressResponse::from)
                .toList();

        List<UserDataExport.ExportedOrder> orders = orderRepository
                .findByUser(user, Pageable.unpaged()).stream()
                .map(o -> new UserDataExport.ExportedOrder(
                        o.getOrderNumber(), o.getStatus().name(), o.getPaymentAmount(), o.getCreatedAt()))
                .toList();

        List<UserDataExport.ExportedReview> reviews = reviewRepository
                .findByUserId(user.getId()).stream()
                .map(r -> new UserDataExport.ExportedReview(
                        r.getProduct().getId(), r.getRating(), r.getTitle(), r.getBody(),
                        r.getStatus().name(), r.getCreatedAt()))
                .toList();

        return new UserDataExport(UserProfile.from(user), addresses, orders, reviews, Instant.now());
    }

    /** Right to be forgotten: anonymize the account, keep orders for accounting. */
    @Transactional
    public void anonymizeAccount(User user) {
        String originalActor = user.getEmail();

        addressRepository.deleteByUser(user);
        wishlistItemRepository.deleteByUser(user);
        cartRepository.findByUser(user).ifPresent(cartRepository::delete);

        for (Review review : reviewRepository.findByUserId(user.getId())) {
            review.setTitle("[deleted]");
            review.setBody("[deleted]");
            review.setStatus(ReviewStatus.DELETED);
            reviewRepository.save(review);
        }

        refreshTokenService.revokeAll(user);

        user.setEmail("deleted-" + user.getId() + "@anonymized.invalid");
        user.setUsername(null);
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setPreferredSize(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        auditService.record(originalActor, "GDPR_ACCOUNT_ERASED", "user",
                String.valueOf(user.getId()), "Account anonymized on user request");
    }
}
