package com.ddicg.erp.modules.iam.dto.response;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String message;
    private String avatarUrl;
    private String gender;
    private String username;
    private String email;
    private String phoneNumber;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, String message, String avatarUrl, String gender, String username, String email, String phoneNumber) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.message = message;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public static AuthResponseBuilder builder() { return new AuthResponseBuilder(); }

    public static class AuthResponseBuilder {
        private String accessToken;
        private String refreshToken;
        private String message;
        private String avatarUrl;
        private String gender;
        private String username;
        private String email;
        private String phoneNumber;

        AuthResponseBuilder() {}

        public AuthResponseBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public AuthResponseBuilder refreshToken(String refreshToken) { this.refreshToken = refreshToken; return this; }
        public AuthResponseBuilder message(String message) { this.message = message; return this; }
        public AuthResponseBuilder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public AuthResponseBuilder gender(String gender) { this.gender = gender; return this; }
        public AuthResponseBuilder username(String username) { this.username = username; return this; }
        public AuthResponseBuilder roles(java.util.Set<com.ddicg.erp.core.common.model.enums.RoleType> roles) { return this; }
        public AuthResponseBuilder email(String email) { this.email = email; return this; }
        public AuthResponseBuilder phoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }

        public AuthResponse build() { return new AuthResponse(accessToken, refreshToken, message, avatarUrl, gender, username, email, phoneNumber); }
    }
}
