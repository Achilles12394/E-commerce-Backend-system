package com.example.retailflow.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retailflow.inventory.entity.InventoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {
    int reserve(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    int confirmDeduct(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    int release(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}