package com.crispprep.reviews;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crispPrepApi() {
        return new OpenAPI().info(new Info()
                .title("CrispPrep Reviews & Ratings API")
                .version("1.0")
                .description("Star ratings, top-rated PDFs and consent-based reviews for CrispPrep's free PDFs. "
                        + "Built with Java 21, Spring Boot and MySQL."));
    }
}