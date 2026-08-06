package com.ddicg.erp.modules.fineract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FineractClientResponseDTO {
    private Long id;
    private String accountNo;
    private String status;
    private Boolean active;
    private List<Integer> activationDate;
    private String firstname;
    private String lastname;
    private String displayName;
    private Long officeId;
    private String officeName;
}
