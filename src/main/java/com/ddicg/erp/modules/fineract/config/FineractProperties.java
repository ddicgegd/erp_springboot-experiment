package com.ddicg.erp.modules.fineract.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fineract")
public class FineractProperties {
    private String baseUrl;
    private String tenant;
    private String tenantId;
    private String username;
    private String password;
    private Long officeId;
    private Long legalFormId;
    private String dateFormat;
    private String locale;

    private boolean sslBypass = true;

    private Long cashGlAccountId;
    private Long bankGlAccountId;
    private Long salesRevenueGlAccountId;
    private Long salesReturnsGlAccountId;
    private String currencyCode;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }

    public String getTenantId() { return tenantId != null ? tenantId : tenant; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getOfficeId() { return officeId; }
    public void setOfficeId(Long officeId) { this.officeId = officeId; }

    public Long getLegalFormId() { return legalFormId; }
    public void setLegalFormId(Long legalFormId) { this.legalFormId = legalFormId; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Long getCashGlAccountId() { return cashGlAccountId; }
    public void setCashGlAccountId(Long cashGlAccountId) { this.cashGlAccountId = cashGlAccountId; }

    public Long getSalesRevenueGlAccountId() { return salesRevenueGlAccountId; }
    public void setSalesRevenueGlAccountId(Long salesRevenueGlAccountId) { this.salesRevenueGlAccountId = salesRevenueGlAccountId; }

    public Long getSalesReturnsGlAccountId() { return salesReturnsGlAccountId; }
    public void setSalesReturnsGlAccountId(Long salesReturnsGlAccountId) { this.salesReturnsGlAccountId = salesReturnsGlAccountId; }

    public Long getBankGlAccountId() { return bankGlAccountId; }
    public void setBankGlAccountId(Long bankGlAccountId) { this.bankGlAccountId = bankGlAccountId; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public boolean isSslBypass() { return sslBypass; }
    public void setSslBypass(boolean sslBypass) { this.sslBypass = sslBypass; }
}
