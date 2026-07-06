package com.example.shopupu.identity.service;

import com.example.shopupu.identity.dto.ConsentRequest;
import com.example.shopupu.identity.dto.ConsentResponse;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.UserConsent;
import com.example.shopupu.identity.repository.UserConsentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserConsentRepository consentRepository;

    /** Latest decision per consent type. */
    @Transactional(readOnly = true)
    public List<ConsentResponse> getCurrentConsents(User user) {
        Map<UserConsent.Type, ConsentResponse> latest = new LinkedHashMap<>();
        // repository returns newest first; keep only the first row per type
        for (UserConsent consent : consentRepository.findByUserOrderByCreatedAtDesc(user)) {
            latest.putIfAbsent(consent.getConsentType(), ConsentResponse.from(consent));
        }
        return new ArrayList<>(latest.values());
    }

    @Transactional
    public ConsentResponse recordConsent(User user, ConsentRequest request) {
        UserConsent consent = consentRepository.save(UserConsent.builder()
                .user(user)
                .consentType(request.consentType())
                .granted(request.granted())
                .policyVersion(request.policyVersion())
                .build());
        return ConsentResponse.from(consent);
    }
}
