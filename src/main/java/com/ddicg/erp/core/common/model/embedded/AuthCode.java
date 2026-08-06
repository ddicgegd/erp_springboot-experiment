package com.ddicg.erp.core.common.model.embedded;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthCode {

    @Column(name = "code")
    String code;

    @Column(name = "purpose")
    @Enumerated(EnumType.STRING)
    ActiveStatus purpose;

    @Column(name = "expiry_date")
    LocalDateTime expiryDate;

}