package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.enums.ActiveStatus;
import com.ddicg.erp.model.enums.Gender;
import com.ddicg.erp.model.enums.RoleType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.Set;


@Getter
@Setter
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDto {
    Long id;
    String username;
    String fullName;
    String email;
    String numberPhone;
    Date dateOfBirth;
    Gender gender;
    String avatarUrl;
    ActiveStatus active;
    Set<RoleType> roles;
}
