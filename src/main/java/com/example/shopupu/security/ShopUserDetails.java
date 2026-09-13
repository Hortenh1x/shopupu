package com.example.shopupu.security;

import com.example.shopupu.identity.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Current database security state, loaded for every access token authentication. */
public class ShopUserDetails extends org.springframework.security.core.userdetails.User {
    private final long authVersion;
    private final boolean mfaEnrolled;
    public ShopUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(), user.isEnabled(), true, true, true,
                user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName())).toList());
        authVersion = user.getAuthVersion();
        mfaEnrolled = user.getMfaSecretCiphertext() != null;
    }
    public long authVersion() { return authVersion; }
    public boolean mfaEnrolled() { return mfaEnrolled; }
}
