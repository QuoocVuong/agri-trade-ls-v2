package com.yourcompany.agritrade.config;

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

    // Cấu hình thông tin chung cho API Docs
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth"; // Tên của security scheme
        return new OpenAPI()
                .info(new Info().title("AgriTradeLS API")
                        .version("v1.0")
                        .description("API Documentation for AgriTradeLS Application (B2B & B2C E-commerce)")
                        .termsOfService("http://swagger.io/terms/") // Thay bằng link điều khoản sử dụng nếu có
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                // Cấu hình cho JWT Authentication trong Swagger UI
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP) // Loại là HTTP
                                                .scheme("bearer") // Scheme là bearer
                                                .bearerFormat("JWT") // Định dạng là JWT
                                )
                );
    }
}