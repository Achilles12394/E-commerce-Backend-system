package com.example.retailflow.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.admin.dto.AdminOrderQueryRequest;
import com.example.retailflow.admin.dto.AdminOrderSummaryResponse;
import com.example.retailflow.admin.dto.AdminShipOrderRequest;
import com.example.retailflow.admin.service.AdminOrderService;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.order.dto.OrderDetailResponse;
import com.example.retailflow.order.dto.OrderItemResponse;
import com.example.retailflow.order.dto.OrderOperateLogResponse;
import com.example.retailflow.order.dto.PaymentRecordResponse;
import com.example.retailflow.order.entity.OrderItemEntity;
import com.example.retailflow.order.entity.OrderOperateLogEntity;
import com.example.retailflow.order.entity.OrdersEntity;
import com.example.retailflow.order.entity.PaymentRecordEntity;
import com.example.retailflow.order.enums.OrderStatus;
import com.example.retailflow.order.mapper.OrderItemMapper;
import com.example.retailflow.order.mapper.OrderOperateLogMapper;
import com.example.retailflow.order.mapper.OrdersMapper;
import com.example.retailflow.order.mapper.PaymentRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderOperateLogMapper orderOperateLogMapper;

    @Override
    public Page<AdminOrderSummaryResponse> page(AdminOrderQueryRequest request) {
        Page<OrdersEntity> page = ordersMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()),
                buildQuery(request).orderByDesc(OrdersEntity::getId)
        );
        Page<AdminOrderSummaryResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toSummary).toList());
        return result;
    }

    @Override
    public OrderDetailResponse detail(String orderNo) {
        OrdersEntity order = ordersMapper.selectOne(new LambdaQueryWrapper<OrdersEntity>()
                .eq(OrdersEntity::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toDetail(order);
    }

    @Override
    @Transactional
    public void ship(String orderNo, Long operatorId, AdminShipOrderRequest request) {
        OrdersEntity order = ordersMapper.selectOne(new LambdaQueryWrapper<OrdersEntity>()
                .eq(OrdersEntity::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!OrderStatus.TO_SHIP.name().equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL.getCode(), "只有待发货订单才能发货");
        }

        order.setOrderStatus(OrderStatus.SHIPPED.name());
        ordersMapper.updateById(order);

        recordOperate(orderNo, operatorId, "SHIP", buildShipRemark(request));
    }

    private LambdaQueryWrapper<OrdersEntity> buildQuery(AdminOrderQueryRequest request) {
        LambdaQueryWrapper<OrdersEntity> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getOrderNo())) {
            query.like(OrdersEntity::getOrderNo, request.getOrderNo().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            query.eq(OrdersEntity::getOrderStatus, request.getStatus().trim());
        }
        if (request.getUserId() != null) {
            query.eq(OrdersEntity::getUserId, request.getUserId());
        }
        return query;
    }

    private AdminOrderSummaryResponse toSummary(OrdersEntity order) {
        return AdminOrderSummaryResponse.builder()
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .status(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .payableAmount(order.getPayableAmount())
                .createdAt(order.getCreatedAt())
                .payTime(order.getPayTime())
                .build();
    }

    private OrderDetailResponse toDetail(OrdersEntity order) {
        List<OrderItemResponse> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderNo, order.getOrderNo())
                        .orderByAsc(OrderItemEntity::getId))
                .stream()
                .map(item -> OrderItemResponse.builder()
                        .skuId(item.getSkuId())
                        .skuTitle(item.getSkuTitle())
                        .salePrice(item.getSalePrice())
                        .quantity(item.getQuantity())
                        .amount(item.getAmount())
                        .build())
                .toList();

        PaymentRecordEntity payment = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getOrderNo, order.getOrderNo()));

        List<OrderOperateLogResponse> operateLogs = orderOperateLogMapper.selectList(new LambdaQueryWrapper<OrderOperateLogEntity>()
                        .eq(OrderOperateLogEntity::getOrderNo, order.getOrderNo())
                        .orderByDesc(OrderOperateLogEntity::getId))
                .stream()
                .map(log -> OrderOperateLogResponse.builder()
                        .operateType(log.getOperateType())
                        .operatorId(log.getOperatorId())
                        .remark(log.getRemark())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();

        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .status(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .payableAmount(order.getPayableAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .payTime(order.getPayTime())
                .items(items)
                .payment(payment == null ? null : PaymentRecordResponse.builder()
                        .payChannel(payment.getPayChannel())
                        .payStatus(payment.getPayStatus())
                        .payAmount(payment.getPayAmount())
                        .thirdTradeNo(payment.getThirdTradeNo())
                        .paidAt(payment.getPaidAt())
                        .build())
                .operateLogs(operateLogs)
                .build();
    }

    private void recordOperate(String orderNo, Long operatorId, String op, String remark) {
        OrderOperateLogEntity log = new OrderOperateLogEntity();
        log.setId(System.nanoTime());
        log.setOrderNo(orderNo);
        log.setOperatorId(operatorId);
        log.setOperateType(op);
        log.setRemark(remark);
        orderOperateLogMapper.insert(log);
    }

    private String buildShipRemark(AdminShipOrderRequest request) {
        String extraRemark = StringUtils.hasText(request.getRemark()) ? ", 备注:" + request.getRemark().trim() : "";
        return "物流公司:" + request.getLogisticsCompany().trim() + ", 运单号:" + request.getLogisticsNo().trim() + extraRemark;
    }
}
