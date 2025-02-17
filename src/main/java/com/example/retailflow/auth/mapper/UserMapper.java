package com.example.retailflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}