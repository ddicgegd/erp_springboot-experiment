package com.ddicg.erp.service.dto.response;

import com.ddicg.erp.model.enums.ActiveStatus;
import com.ddicg.erp.model.enums.Gender;
import com.ddicg.erp.model.enums.RoleType;
import com.ddicg.erp.model.enums.UserRank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyProfileResponse {
    String username;
    String fullName;
    String email;
    String phoneNumber;
    String avatarUrl;
    Date dateOfBirth;
    Gender gender;
    UserRank rank;
    ActiveStatus status;
    Set<RoleType> roles;
}
