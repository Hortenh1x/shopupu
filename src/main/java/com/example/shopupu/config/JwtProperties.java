package com.example.shopupu.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32, message = "JWT secret must be at least 32 bytes for HS256")
    private String secret;

    @Min(1)
    @Max(60)
    private long accessTokenTtlMin = 15;

    @Min(1)
    @Max(90)
    private long refreshTokenTtlDays = 7;

    @NotBlank
    private String issuer = "shopupu";

    @NotBlank
    private String audience = "shopupu-api";
}
