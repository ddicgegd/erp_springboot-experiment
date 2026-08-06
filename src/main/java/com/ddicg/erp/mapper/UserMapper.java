package com.ddicg.erp.mapper;

import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.service.dto.UserDto;
import org.mapstruct.Mapper;

@Mapper(builder = @org.mapstruct.Builder(disableBuilder = true), config = DefaultConfigMapper.class)
public interface UserMapper extends EntityMapper<UserDto, User>{
}
