package com.example.retailflow.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.inventory.entity.InventoryLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLogEntity> {
}