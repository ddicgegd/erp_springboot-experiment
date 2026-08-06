package com.ddicg.erp.fineract.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {
    private String baseUrl = "https://localhost:8443/fineract-provider/api/v1";
    private String tenantId = "default";
    private String username = "mifos";
    private String password = "password";
    private Long defaultOfficeId = 1L;
    private Long defaultLegalFormId = 1L;
    private String dateFormat = "dd MMMM yyyy";
    private String locale = "en";
    private boolean sslBypass = false;
    
    // Accounting GL Accounts
    private Long cashGlAccountId = 1L;
    private Long salesRevenueGlAccountId = 2L;
    private Long salesReturnsGlAccountId = 3L;
}
