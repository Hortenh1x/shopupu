package com.example.shopupu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.bootstrap-admin")
/**
 * describes the BootstrapAdminProperties class.
 */
public class BootstrapAdminProperties {
    private boolean enabled;
    private String email;
    private String password;
}