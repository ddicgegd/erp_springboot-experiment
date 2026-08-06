package com.ddicg.erp.service.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSearchRequest {
    String fullName;
    String email;
    String numberPhone;


}
