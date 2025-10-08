package com.example.shopupu.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shopupu API")
                        .description("REST API of shopupu")
                        .version("v1.0"))
                .externalDocs(new ExternalDocumentation()
                        .description("GitHub")
                        .url("https://github.com/Hortenh1x/shopupu"));
    }
}
