package com.example.shopupu.auth.controller;

import com.example.shopupu.auth.dto.LoginResponse;
import com.example.shopupu.auth.service.MfaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {
    private final MfaService mfa;
    public record ChallengeRequest(@NotBlank @Size(max = 128) String challengeToken) {}
    public record ConfirmRequest(@NotBlank @Size(max = 128) String challengeToken, @NotBlank @Size(max = 32) String code) {}
    public record VerifyRequest(@NotBlank @Size(max = 128) String challengeToken,
                                @Size(max = 32) String code, @Size(max = 128) String recoveryCode) {}
    @PostMapping("/enrollment/start")
    public MfaService.Enrollment start(@Valid @RequestBody ChallengeRequest request) { return mfa.startEnrollment(request.challengeToken()); }
    @PostMapping("/enrollment/confirm")
    public LoginResponse confirm(@Valid @RequestBody ConfirmRequest request) { return mfa.confirmEnrollment(request.challengeToken(), request.code()); }
    @PostMapping("/verify")
    public LoginResponse verify(@Valid @RequestBody VerifyRequest request) { return mfa.verify(request.challengeToken(), request.code(), request.recoveryCode()); }
}
