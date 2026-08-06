package com.ddicg.erp.modules.fineract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {
    private String baseUrl;
    private String tenant;
    private String username;
    private String password;
    private Long defaultOfficeId = 1L;
    private Long defaultLegalFormId = 1L;
    private String dateFormat = "dd MMMM yyyy";
    private String locale = "en";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getDefaultOfficeId() { return defaultOfficeId; }
    public void setDefaultOfficeId(Long defaultOfficeId) { this.defaultOfficeId = defaultOfficeId; }
    public Long getDefaultLegalFormId() { return defaultLegalFormId; }
    public void setDefaultLegalFormId(Long defaultLegalFormId) { this.defaultLegalFormId = defaultLegalFormId; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Long getCashGlAccountId() { return 1000L; }

    public Long getSalesRevenueGlAccountId() { return 4000L; }
    public Long getSalesReturnsGlAccountId() { return 4100L; }


    public String getTenantId() { return "default"; }

    public boolean isSslBypass() { return false; }
}
