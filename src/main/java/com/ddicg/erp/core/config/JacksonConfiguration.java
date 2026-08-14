package com.ddicg.erp.core.config;

import com.ddicg.erp.core.config.converter.CustomLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class JacksonConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.deserializerByType(LocalDateTime.class, new CustomLocalDateTimeDeserializer());
        };
    }
}
