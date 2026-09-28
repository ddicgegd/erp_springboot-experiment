package com.ddicg.erp.modules.iam.dto.response;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.RoleType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyProfileResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usernameCooldownUntil;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;
    private String avatarUrl;
    private String gender;
    private String rank;
    private ActiveStatus status;
    private Set<RoleType> roles;
}
