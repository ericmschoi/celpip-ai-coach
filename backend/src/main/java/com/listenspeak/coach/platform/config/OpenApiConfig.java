package com.listenspeak.coach.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI listenSpeakOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ListenSpeak AI Coach API")
                        .version("v1")
                        .description(
                                """
                                Independent, unofficial CELPIP-style Listening and Speaking practice API.
                                Not affiliated with, authorized by, or endorsed by CELPIP or Paragon \
                                Testing Enterprises. All exercises are originally generated.
                                """)
                        .license(new License().name("Personal use")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Cognito-issued access token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
