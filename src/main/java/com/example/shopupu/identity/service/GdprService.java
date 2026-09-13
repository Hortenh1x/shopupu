package com.example.shopupu.identity.service;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.dto.AddressResponse;
import com.example.shopupu.identity.dto.UserDataExport;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local account export and erasure; retained financial records are pseudonymous, not anonymous. */
@Service
@RequiredArgsConstructor
public class GdprService {
    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final PersonalDataRepository personalDataRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final com.example.shopupu.common.audit.AuditService auditService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public UserDataExport exportData(User suppliedUser) {
        User user = userRepository.findById(suppliedUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<AddressResponse> addresses = addressRepository
                .findByUserOrderByDefaultAddressDescCreatedAtAsc(user).stream()
                .map(AddressResponse::from).toList();
        List<UserDataExport.ExportedOrder> orders = orderRepository
                .findByUser(user, Pageable.unpaged()).stream()
                .map(o -> new UserDataExport.ExportedOrder(
                        o.getOrderNumber(), o.getStatus().name(), o.getPaymentAmount(), o.getCreatedAt()))
                .toList();
        List<UserDataExport.ExportedReview> reviews = reviewRepository.findByUserId(user.getId()).stream()
                .map(r -> new UserDataExport.ExportedReview(r.getProduct().getId(), r.getRating(), r.getBody(),
                        r.getStatus().name(), r.getCreatedAt(), r.getSource().name())).toList();
        return new UserDataExport(UserProfile.from(user), addresses, orders, reviews, Instant.now(),
                personalDataRepository.exportRecords(user.getId(), user.getEmail(), user.getUsername()),
                List.of("Current local application database records linked to this account; field names in records use database naming.",
                        "Credentials, token values/hashes, MFA secrets, checkout access tokens and provider request bodies are excluded.",
                        "Other people's audit actors are excluded; your linked audit actions and details are included.",
                        "External provider records, email delivery, operational logs, backups and browser-only chat history are outside this download."));
    }

    @Transactional
    public void anonymizeAccount(User suppliedUser) {
        // User -> orders -> child/auth rows. Never reload through the original email after mutation.
        User user = userRepository.findByIdForUpdate(suppliedUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeletedAt() != null) {
            return;
        }
        personalDataRepository.lockOrders(user.getId());
        List<Review> reviews = reviewRepository.findByUserId(user.getId());
        List<Long> productIds = reviews.stream().map(r -> r.getProduct().getId()).distinct().sorted().toList();
        for (Review review : reviews) {
            review.setBody("[deleted]");
            review.setStatus(ReviewStatus.DELETED);
        }
        reviewRepository.saveAll(reviews);
        reviewRepository.flush();
        personalDataRepository.deleteReviewSummaries(productIds);
        personalDataRepository.eraseRelatedData(user.getId(), user.getEmail(), user.getUsername());
        user.setEmail("deleted-" + user.getId() + "-" + UUID.randomUUID() + "@anonymized.invalid");
        user.setUsername(null);
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhone(null);
        user.setPreferredSize(null);
        user.setGender(null);
        user.setMfaSecretCiphertext(null);
        user.setMfaLastAcceptedStep(-1);
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setEmailVerified(false);
        user.setAuthProvider(com.example.shopupu.identity.entity.AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setDeletedAt(Instant.now());
        user.getRoles().clear();
        userRepository.save(user);
        auditService.record("deleted-user:" + user.getId(), "GDPR_ACCOUNT_ERASED", "user",
                Long.toString(user.getId()), "Direct identifiers erased; financial history pseudonymized");
        productIds.forEach(id -> eventPublisher.publishEvent(new ProductReviewsChangedEvent(id)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String name : List.of("productRating", "reviewSummary")) {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) {
                        productIds.forEach(cache::evict);
                    }
                }
            }
        });
    }
}
