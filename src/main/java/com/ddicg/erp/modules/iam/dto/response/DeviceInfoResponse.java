package com.ddicg.erp.modules.iam.dto.response;

public class DeviceInfoResponse {
    private String finalRefreshTokenString;
    private String accessToken;
    private String message;

    public DeviceInfoResponse() {}

    public DeviceInfoResponse(String finalRefreshTokenString, String accessToken, String message) {
        this.finalRefreshTokenString = finalRefreshTokenString;
        this.accessToken = accessToken;
        this.message = message;
    }

    public String getFinalRefreshTokenString() { return finalRefreshTokenString; }
    public void setFinalRefreshTokenString(String finalRefreshTokenString) { this.finalRefreshTokenString = finalRefreshTokenString; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static DeviceInfoResponseBuilder builder() { return new DeviceInfoResponseBuilder(); }

    public static class DeviceInfoResponseBuilder {
        private String finalRefreshTokenString;
        private String accessToken;
        private String message;

        DeviceInfoResponseBuilder() {}

        public DeviceInfoResponseBuilder finalRefreshTokenString(String finalRefreshTokenString) { this.finalRefreshTokenString = finalRefreshTokenString; return this; }
        public DeviceInfoResponseBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public DeviceInfoResponseBuilder message(String message) { this.message = message; return this; }

        public DeviceInfoResponse build() {
            return new DeviceInfoResponse(finalRefreshTokenString, accessToken, message);
        }
    }
}
