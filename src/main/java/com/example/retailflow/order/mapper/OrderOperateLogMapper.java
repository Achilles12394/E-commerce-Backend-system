package com.example.retailflow.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.order.entity.OrderOperateLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderOperateLogMapper extends BaseMapper<OrderOperateLogEntity> {
}