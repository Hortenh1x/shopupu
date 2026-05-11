package com.example.shopupu.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * redirects the short Swagger URL to the generated Swagger UI page.
 */
@Controller
public class SwaggerRedirectController {

    // redirects /swagger to the Springdoc UI.
    @GetMapping("/swagger")
    public String redirectToSwaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}
