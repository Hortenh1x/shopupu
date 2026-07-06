package com.example.shopupu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class BootstrapAdminProperties {
    private boolean enabled;
    private String email;
    private String password;
}
