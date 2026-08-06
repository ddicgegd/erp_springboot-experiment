package com.ddicg.erp.service.dto.response.ResponseConfig;

public class ApiStatus {
    private String message;

    private int code;

    public ApiStatus() {}

    public ApiStatus(int code) {
        this.code = code;
        this.message = "Success";
    }

    public ApiStatus(String message, int code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }

    public String name() {
        return message;
    }
}
