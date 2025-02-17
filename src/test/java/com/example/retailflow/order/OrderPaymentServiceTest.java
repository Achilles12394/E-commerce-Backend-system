package com.example.retailflow.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.inventory.service.InventoryService;
import com.example.retailflow.order.entity.OrdersEntity;
import com.example.retailflow.order.enums.OrderStatus;
import com.example.retailflow.order.mapper.CartItemMapper;
import com.example.retailflow.order.mapper.IdempotentTokenMapper;
import com.example.retailflow.order.mapper.OrderItemMapper;
import com.example.retailflow.order.mapper.OrderOperateLogMapper;
import com.example.retailflow.order.mapper.OrdersMapper;
import com.example.retailflow.order.mapper.OutboxEventMapper;
import com.example.retailflow.order.mapper.PaymentRecordMapper;
import com.example.retailflow.order.service.impl.OrderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaymentServiceTest {

    @Test
    void testPaySuccess() {
        OrdersMapper ordersMapper = Mockito.mock(OrdersMapper.class);
        PaymentRecordMapper paymentRecordMapper = Mockito.mock(PaymentRecordMapper.class);
        InventoryService inventoryService = Mockito.mock(InventoryService.class);

        OrdersEntity order = new OrdersEntity();
        order.setOrderNo("O1");
        order.setUserId(1L);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT.name());

        when(ordersMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(paymentRecordMapper.selectOne(any())).thenReturn(new com.example.retailflow.order.entity.PaymentRecordEntity());

        OrderServiceImpl service = new OrderServiceImpl(
                Mockito.mock(CartItemMapper.class),
                ordersMapper,
                Mockito.mock(OrderItemMapper.class),
                paymentRecordMapper,
                Mockito.mock(IdempotentTokenMapper.class),
                Mockito.mock(OutboxEventMapper.class),
                Mockito.mock(OrderOperateLogMapper.class),
                inventoryService,
                Mockito.mock(StringRedisTemplate.class),
                new ObjectMapper()
        );

        service.pay("O1", 1L);
        verify(inventoryService).confirmDeduct("O1");
    }
}