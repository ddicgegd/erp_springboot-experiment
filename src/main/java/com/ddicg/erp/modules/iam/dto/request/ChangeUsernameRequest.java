package com.ddicg.erp.modules.iam.dto.request;

public class ChangeUsernameRequest {
    private String token;
    private String newUsername;

    public ChangeUsernameRequest() {}

    public ChangeUsernameRequest(String token, String newUsername) {
        this.token = token;
        this.newUsername = newUsername;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewUsername() { return newUsername; }
    public void setNewUsername(String newUsername) { this.newUsername = newUsername; }
}
