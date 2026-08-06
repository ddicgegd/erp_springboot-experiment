package com.ddicg.erp.core.config;

import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataTypeConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module hibernate5Module() {
        Hibernate5JakartaModule module = new Hibernate5JakartaModule();
        module.configure(Hibernate5JakartaModule.Feature.FORCE_LAZY_LOADING, false);
        return module;
    }
}
