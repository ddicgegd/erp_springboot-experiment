package com.ddicg.erp.modules.iam.dto.request;

import lombok.Data;

@Data
public class OAuth2TokenRequest {
    private String ticket;
}