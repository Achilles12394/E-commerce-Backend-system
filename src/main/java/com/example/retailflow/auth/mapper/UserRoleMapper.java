package com.example.retailflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.auth.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {
}