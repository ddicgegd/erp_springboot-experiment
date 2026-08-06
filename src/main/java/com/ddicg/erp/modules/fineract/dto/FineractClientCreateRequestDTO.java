package com.ddicg.erp.modules.fineract.dto;

public class FineractClientCreateRequestDTO {
    private String firstname;
    private String lastname;
    private Long officeId;
    private Long legalFormId;
    private boolean active = true;
    private String dateFormat = "dd MMMM yyyy";
    private String locale = "en";
    private String externalId;
    private String activationDate;

    public FineractClientCreateRequestDTO() {}

    public FineractClientCreateRequestDTO(String firstname, String lastname, Long officeId, Long legalFormId, boolean active, String dateFormat, String locale, String externalId) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.officeId = officeId;
        this.legalFormId = legalFormId;
        this.active = active;
        this.dateFormat = dateFormat;
        this.locale = locale;
        this.externalId = externalId;
    }

    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public Long getOfficeId() { return officeId; }
    public void setOfficeId(Long officeId) { this.officeId = officeId; }
    public Long getLegalFormId() { return legalFormId; }
    public void setLegalFormId(Long legalFormId) { this.legalFormId = legalFormId; }
    public boolean isActive() { return active; }
    public String getActive() { return String.valueOf(active); }
    public void setActive(boolean active) { this.active = active; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getActivationDate() { return activationDate; }
    public void setActivationDate(String activationDate) { this.activationDate = activationDate; }
}
