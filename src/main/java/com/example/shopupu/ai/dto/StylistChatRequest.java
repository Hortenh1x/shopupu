package com.example.shopupu.ai.dto;

import com.example.shopupu.catalog.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Stylist chat turn: the new message plus the visible conversation so far. */
public record StylistChatRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 10) @Valid List<@NotNull HistoryMessage> history,
        Gender gender,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal maxTotalPrice
) {

    public StylistChatRequest(String message, List<HistoryMessage> history) {
        this(message, history, null, null);
    }

    public record HistoryMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1000) String content
    ) {
    }
}
