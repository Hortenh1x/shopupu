package com.example.shopupu.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {
    @AssertTrue(message = "This application supports fictional products and test payments only")
    private boolean enabled = true;
}
