package com.example.retailflow.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.inventory.entity.InventoryReservationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservationEntity> {
}