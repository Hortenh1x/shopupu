package com.example.shopupu.auth.service;

import com.example.shopupu.auth.dto.LoginResponse;
import com.example.shopupu.cart.service.CartService;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.security.JwtTokenProvider;
import com.example.shopupu.security.ShopUserDetails;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final RefreshTokenService refreshTokens;
    private final JwtTokenProvider jwt;
    private final CartService carts;
    private final AuditService audit;

    @Transactional
    public LoginResponse issue(User user, Instant verifiedAt) {
        var refresh = refreshTokens.mint(user, verifiedAt);
        return LoginResponse.authenticated(jwt.generateToken(new ShopUserDetails(refresh.entity().getUser()), verifiedAt),
                refresh.rawToken());
    }
    @Transactional
    public LoginResponse complete(User user, Instant verifiedAt, String guestCart, String method) {
        LoginResponse response = issue(user, verifiedAt);
        carts.mergeGuestCart(guestCart, user.getEmail());
        audit.record(user.getEmail(), "GOOGLE".equals(method) ? "LOGIN_SUCCEEDED_GOOGLE" : "LOGIN_SUCCEEDED",
                "user", String.valueOf(user.getId()), verifiedAt == null ? null : "MFA verified");
        return response;
    }
}
