package com.ddicg.erp.modules.iam.dto.response;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import java.util.Date;

public class MyProfileResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Date dateOfBirth;
    private String avatarUrl;
    private String gender;
    private String rank;
    private ActiveStatus status;

    public MyProfileResponse() {}

    public MyProfileResponse(String id, String username, String email, String fullName, String phoneNumber, Date dateOfBirth, String avatarUrl, String gender, String rank, ActiveStatus status) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.rank = rank;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
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
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public ActiveStatus getStatus() { return status; }
    public void setStatus(ActiveStatus status) { this.status = status; }

    public static MyProfileResponseBuilder builder() { return new MyProfileResponseBuilder(); }

    public static class MyProfileResponseBuilder {
        private String id;
        private String username;
        private String email;
        private String fullName;
        private String phoneNumber;
        private Date dateOfBirth;
        private String avatarUrl;
        private String gender;
        private String rank;
        private ActiveStatus status;

        MyProfileResponseBuilder() {}

        public MyProfileResponseBuilder id(String id) { this.id = id; return this; }
        public MyProfileResponseBuilder username(String username) { this.username = username; return this; }
        public MyProfileResponseBuilder roles(java.util.Set<com.ddicg.erp.core.common.model.enums.RoleType> roles) { return this; }
        public MyProfileResponseBuilder email(String email) { this.email = email; return this; }
        public MyProfileResponseBuilder fullName(String fullName) { this.fullName = fullName; return this; }
        public MyProfileResponseBuilder phoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }
        public MyProfileResponseBuilder dateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; return this; }
        public MyProfileResponseBuilder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public MyProfileResponseBuilder gender(String gender) { this.gender = gender; return this; }
        public MyProfileResponseBuilder rank(String rank) { this.rank = rank; return this; }
        public MyProfileResponseBuilder status(ActiveStatus status) { this.status = status; return this; }

        public MyProfileResponse build() {
            return new MyProfileResponse(id, username, email, fullName, phoneNumber, dateOfBirth, avatarUrl, gender, rank, status);
        }
    }
}
