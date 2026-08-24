package com.example.shopupu.ai.controller;

import com.example.shopupu.ai.dto.StylistChatRequest;
import com.example.shopupu.ai.dto.StylistChatResponse;
import com.example.shopupu.ai.service.StylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public stylist chat (guest-friendly, like the other AI catalog reads).
 * POST because a conversation travels in the body; explicitly whitelisted in
 * SecurityConfig and covered by the global rate limiter.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog/stylist")
public class StylistController {

    private final StylistService stylistService;

    @PostMapping("/chat")
    public StylistChatResponse chat(@Valid @RequestBody StylistChatRequest request) {
        return stylistService.chat(request);
    }
}
