package com.ddicg.erp.modules.fineract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineractClientCreateRequestDTO {
    private String firstname;
    private String lastname;
    private Long officeId;
    private Long legalFormId;
    private Boolean active;
    private String activationDate;
    private String dateFormat;
    private String locale;
    private String externalId;
    private String mobileNo;
    private String emailAddress;
}
