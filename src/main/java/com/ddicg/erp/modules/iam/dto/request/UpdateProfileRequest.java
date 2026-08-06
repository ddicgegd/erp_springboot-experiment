package com.ddicg.erp.modules.iam.dto.request;

import java.util.Date;

public class UpdateProfileRequest {
    private String fullName;
    private String phoneNumber;
    private Date dateOfBirth;
    private String avatarUrl;
    private String gender;

    public UpdateProfileRequest() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Date getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
}
