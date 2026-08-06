package com.ddicg.erp.core.common.model.enums;

public enum ErrorCodes {
    BAD_REQUEST("BAD_REQUEST"),
    UNAUTHORIZED("UNAUTHORIZED"),
    FORBIDDEN("FORBIDDEN"),
    NOT_FOUND("NOT_FOUND"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR"),
    SERVICE_ERROR("SERVICE_ERROR");

    private final String code;

    ErrorCodes(String code) { this.code = code; }

    public String getCode() { return code; }
}
