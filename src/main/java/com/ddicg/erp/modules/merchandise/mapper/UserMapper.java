package com.ddicg.erp.modules.merchandise.mapper;

import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.dto.UserDto;
import org.mapstruct.Mapper;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), config = DefaultConfigMapper.class)
public interface UserMapper extends EntityMapper<UserDto, User>{
}
