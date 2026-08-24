package com.example.shopupu.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Stylist chat turn: the new message plus the visible conversation so far. */
public record StylistChatRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 10) @Valid List<HistoryMessage> history
) {

    public record HistoryMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1000) String content
    ) {
    }
}
