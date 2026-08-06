package com.ddicg.erp.modules.iam.dto.response;

public class RegisterResponse {
    private String message;
    private String username;
    private String email;

    public RegisterResponse() {}

    public RegisterResponse(String message, String username, String email) {
        this.message = message;
        this.username = username;
        this.email = email;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public static RegisterResponseBuilder builder() { return new RegisterResponseBuilder(); }

    public static class RegisterResponseBuilder {
        private String message;
        private String username;
        private String email;

        RegisterResponseBuilder() {}

        public RegisterResponseBuilder message(String message) { this.message = message; return this; }
        public RegisterResponseBuilder username(String username) { this.username = username; return this; }
        public RegisterResponseBuilder email(String email) { this.email = email; return this; }
        public RegisterResponse build() { return new RegisterResponse(message, username, email); }
    }
}
