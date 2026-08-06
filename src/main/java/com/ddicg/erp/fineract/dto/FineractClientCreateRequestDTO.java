package com.ddicg.erp.fineract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
}
