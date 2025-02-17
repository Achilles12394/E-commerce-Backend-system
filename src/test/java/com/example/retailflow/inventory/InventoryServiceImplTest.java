package com.example.retailflow.inventory;

import com.example.retailflow.inventory.dto.InventoryReserveItem;
import com.example.retailflow.inventory.mapper.*;
import com.example.retailflow.inventory.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {

    @Test
    void testReserveStockNotEnough() {
        InventoryMapper inventoryMapper = Mockito.mock(InventoryMapper.class);
        when(inventoryMapper.reserve(any(), any())).thenReturn(0);
        InventoryServiceImpl service = new InventoryServiceImpl(
                inventoryMapper,
                Mockito.mock(InventoryReservationMapper.class),
                Mockito.mock(InventoryLogMapper.class),
                Mockito.mock(StockAlertRecordMapper.class)
        );
        assertThrows(RuntimeException.class, () -> service.reserve("O1", List.of(new InventoryReserveItem(1L, 10))));
    }
}