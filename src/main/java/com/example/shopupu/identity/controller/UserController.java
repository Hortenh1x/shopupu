package com.example.shopupu.identity.controller;

import com.example.shopupu.auth.dto.UserProfile;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.dto.AddressRequest;
import com.example.shopupu.identity.dto.AddressResponse;
import com.example.shopupu.identity.dto.UpdateProfileRequest;
import com.example.shopupu.identity.dto.UserDataExport;
import com.example.shopupu.identity.dto.WishlistEntryResponse;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.AddressBookService;
import com.example.shopupu.identity.service.GdprService;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.identity.service.WishlistService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service profile, address book, wishlist and GDPR rights (USER-01/02/03/05). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;
    private final AddressBookService addressBookService;
    private final WishlistService wishlistService;
    private final GdprService gdprService;
    private final AccessControlService accessControlService;

    // === Profile ============================================================

    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile() {
        return ResponseEntity.ok(UserProfile.from(accessControlService.currentUser()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfile> updateProfile(Authentication authentication,
                                                     @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(UserProfile.from(
                userService.updateProfile(authentication.getName(), request)));
    }

    // === Address book =======================================================

    @GetMapping("/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses() {
        User user = accessControlService.currentUser();
        return ResponseEntity.ok(addressBookService.getAddresses(user).stream()
                .map(AddressResponse::from)
                .toList());
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(@Valid @RequestBody AddressRequest request) {
        User user = accessControlService.currentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddressResponse.from(addressBookService.addAddress(user, request)));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable Long id,
                                                         @Valid @RequestBody AddressRequest request) {
        User user = accessControlService.currentUser();
        return ResponseEntity.ok(AddressResponse.from(addressBookService.updateAddress(user, id, request)));
    }

    @PostMapping("/addresses/{id}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@PathVariable Long id) {
        User user = accessControlService.currentUser();
        return ResponseEntity.ok(AddressResponse.from(addressBookService.setDefault(user, id)));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        User user = accessControlService.currentUser();
        addressBookService.deleteAddress(user, id);
        return ResponseEntity.noContent().build();
    }

    // === Wishlist ===========================================================

    @GetMapping("/wishlist")
    public ResponseEntity<Page<WishlistEntryResponse>> getWishlist(@PageableDefault(size = 20) Pageable pageable) {
        User user = accessControlService.currentUser();
        return ResponseEntity.ok(wishlistService.getWishlist(user, pageable));
    }

    @PostMapping("/wishlist/{productId}")
    public ResponseEntity<Void> addToWishlist(@PathVariable Long productId) {
        wishlistService.add(accessControlService.currentUser(), productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/wishlist/{productId}")
    public ResponseEntity<Void> removeFromWishlist(@PathVariable Long productId) {
        wishlistService.remove(accessControlService.currentUser(), productId);
        return ResponseEntity.noContent().build();
    }

    // === GDPR ===============================================================

    @GetMapping("/export")
    public ResponseEntity<UserDataExport> exportData() {
        return ResponseEntity.ok(gdprService.exportData(accessControlService.currentUser()));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        gdprService.anonymizeAccount(accessControlService.currentUser());
        return ResponseEntity.noContent().build();
    }
}
