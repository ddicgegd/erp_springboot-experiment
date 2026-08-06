package com.ddicg.erp.modules.merchandise.dto;

import com.ddicg.erp.modules.iam.model.Address;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;


/**
 * DTO for {@link Address}
 */
@AllArgsConstructor
@Getter
public class AddressDto implements Serializable {
    private final Long id;
    private final String name;
    private final String address;
    private final String phoneNumber;
    private final String recipientName;
    @NotNull
    private final com.ddicg.erp.modules.iam.model.User user;

    /**
     * Chuyển đổi từ DTO sang Entity Address
     */
    public Address toEntity() {
        return Address.builder()
                .address(this.address)
                .phoneNumber(this.phoneNumber)
                .recipientName(this.recipientName)
                .build();
    }
}