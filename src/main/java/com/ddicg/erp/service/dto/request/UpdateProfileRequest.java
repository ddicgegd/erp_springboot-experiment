package com.ddicg.erp.service.dto.request;

import com.ddicg.erp.model.enums.Gender;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    @Pattern(regexp = "^[\\p{L}\\s]*$", message = "Tên chỉ được chứa chữ cái và khoảng trắng!")
    String fullName;

    @Pattern(regexp = "^\\d{10}$|^$", message = "Số điện thoại chỉ được chứa 10 chữ số!")
    String phoneNumber;

    Date dateOfBirth;
    Gender gender;
    String avatarUrl;
}
