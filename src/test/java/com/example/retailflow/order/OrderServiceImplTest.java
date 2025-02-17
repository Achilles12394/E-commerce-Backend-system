package com.example.retailflow.order;

import com.example.retailflow.inventory.service.InventoryService;
import com.example.retailflow.order.dto.SubmitOrderRequest;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    @Test
    void testSubmitOrderRepeatToken() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        when(redis.delete(Mockito.anyString())).thenReturn(false);
        OrderServiceImpl service = new OrderServiceImpl(
                Mockito.mock(CartItemMapper.class),
                Mockito.mock(OrdersMapper.class),
                Mockito.mock(OrderItemMapper.class),
                Mockito.mock(PaymentRecordMapper.class),
                Mockito.mock(IdempotentTokenMapper.class),
                Mockito.mock(OutboxEventMapper.class),
                Mockito.mock(OrderOperateLogMapper.class),
                Mockito.mock(InventoryService.class),
                redis,
                new ObjectMapper()
        );
        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setToken("t1");
        assertThrows(RuntimeException.class, () -> service.submitOrder(1L, request));
    }
}