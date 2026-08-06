package com.ddicg.erp.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCodes {
    BAD_REQUEST("BAD_REQUEST"),
    UNAUTHORIZED("UNAUTHORIZED"),
    FORBIDDEN ("FORBIDDEN"),
    NOT_FOUND ("NOT_FOUND"),
    INTERNAL_SERVER_ERROR ("INTERNAL_SERVER_ERROR"),
    SERVICE_ERROR ("SERVICE_ERROR");

    private final String description;
}
