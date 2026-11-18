package com.crispprep.reviews;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crispPrepApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CrispPrep Reviews & Ratings API")
                        .version("1.1")
                        .description("Accounts (email + password, JWT), star ratings, top-rated PDFs and consent-based reviews "
                                + "for CrispPrep's free PDFs. Built with Java 21, Spring Boot, Spring Security and MySQL. "
                                + "Log in with /api/auth/login, copy the token, then click Authorize."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
