package com.example.shopupu.ai.service;

import com.example.shopupu.ai.dto.StylistChatRequest;
import com.example.shopupu.ai.gateway.StubLlmClient;
import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;

/** Shopper-owned constraints. Assistant/model output can never relax these. */
record StylistConstraints(Gender gender, BigDecimal maxTotalPrice) {
    static StylistConstraints from(StylistChatRequest request) {
        Gender gender = null;
        BigDecimal budget = null;
        if (request.history() != null) {
            for (var turn : request.history()) {
                if (turn != null && "user".equals(turn.role())) {
                    var parsed = StubLlmClient.keywordParse(turn.content());
                    if (parsed.gender() != null) gender = parsed.gender();
                    if (parsed.maxPrice() != null) budget = parsed.maxPrice();
                }
            }
        }
        var current = StubLlmClient.keywordParse(request.message());
        if (current.gender() != null) gender = current.gender();
        if (current.maxPrice() != null) budget = current.maxPrice();
        return new StylistConstraints(request.gender() == null ? gender : request.gender(),
                request.maxTotalPrice() == null ? budget : request.maxTotalPrice());
    }
}
