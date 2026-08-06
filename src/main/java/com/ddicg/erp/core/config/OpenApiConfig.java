package com.ddicg.erp.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.contact.name}") private String contactName;
    
    @Value("${spring.contact.url}") private String contactUrl;
    
    @Value("${spring.contact.email}") private String contactEmail;

    @Value("${server.url}") private String serverUrl;
    
@Bean
public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                        )
                )
                .info(new Info().title("ERP Experiment API")
                        .version("1.0.0")
                        .description("ERP Experiment API")
                        .termsOfService("https://swagger.io/terms/")
                        .contact(new Contact()
                                .name(contactName)
                                .url(contactUrl)
                                .email(contactEmail))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url(serverUrl).description("Development Server")
                ));
    }
}