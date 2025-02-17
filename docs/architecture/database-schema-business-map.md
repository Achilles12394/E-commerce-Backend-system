# RetailFlow 数据库表与业务关系文档

## 1. 总体说明
RetailFlow 当前建表脚本一共包含 28 张表，按业务域可以拆成五部分：
- 认证与权限域
- 商品域
- 库存域
- 订单交易域
- 秒杀域

理解这套表设计的关键，不是死记所有字段，而是先抓住两条主链路：
- 商品链路：`category / brand / spu / sku / file_record`
- 交易链路：`user -> cart_item -> orders -> order_item / payment_record / inventory_reservation`

在此基础上，再补上权限、库存日志和秒杀活动，就能把整个项目讲清楚。

## 2. 认证与权限域

### 2.1 `user`
作用：系统用户表，存普通用户和后台用户。
核心字段：
- `id`
- `username`
- `password`
- `nickname`
- `phone`
- `email`
- `status`
- `deleted`

业务含义：
- 登录认证时按用户名查用户。
- 密码存加密值，不存明文。
- `status` 控制用户是否可用。

### 2.2 `role`
作用：角色定义表，例如 `ADMIN`、`CUSTOMER`。

### 2.3 `permission`
作用：权限点定义表，表示具体可执行动作。

### 2.4 `user_role`
作用：用户和角色的中间表。
关系：`user` 多对多 `role`。

### 2.5 `role_permission`
作用：角色和权限的中间表。
关系：`role` 多对多 `permission`。

## 3. 商品域

### 3.1 `category`
作用：商品分类表。
关系：`category 1 -> n spu`。

### 3.2 `brand`
作用：商品品牌表。
关系：`brand 1 -> n spu`。

### 3.3 `spu`
作用：商品主模型，描述一个商品抽象。
核心字段：
- `spu_code`
- `title`
- `subtitle`
- `category_id`
- `brand_id`
- `publish_status`

关系：
- `category 1 -> n spu`
- `brand 1 -> n spu`
- `spu 1 -> n sku`

### 3.4 `sku`
作用：真正可售卖的销售单元。
核心字段：
- `sku_code`
- `spu_id`
- `title`
- `price`
- `sales_count`
- `stock_status`

关系：
- `spu 1 -> n sku`
- `sku 1 -> 1 inventory`
- `sku 1 -> n order_item`
- `sku 1 -> n cart_item`

### 3.5 `sku_image`
作用：SKU 图片表，表示一个 SKU 可以挂多张图片。

### 3.6 `sku_attribute`
作用：SKU 属性表，比如颜色、版本、规格。

### 3.7 `product_publish_record`
作用：商品上下架记录表。

### 3.8 `file_record`
作用：文件元信息表。
业务场景：
- 商品图片上传成功后，文件内容在 MinIO，本表记录 bucket、objectKey、url 等信息。

## 4. 库存域

### 4.1 `inventory`
作用：库存主表。
核心字段：
- `sku_id`
- `total_stock`
- `available_stock`
- `frozen_stock`
- `version`

业务含义：
- `available_stock` 表示当前可卖库存。
- `frozen_stock` 表示已被订单占用但尚未最终扣减的库存。
- 当前项目用这张表承载 MySQL 最终库存状态。

关系：`sku 1 -> 1 inventory`

### 4.2 `inventory_reservation`
作用：库存预留表，是订单和库存之间最关键的桥梁。
核心字段：
- `order_no`
- `sku_id`
- `quantity`
- `status`

状态含义：
- `RESERVED`
- `DEDUCTED`
- `RELEASED`

关系：
- `orders 1 -> n inventory_reservation`
- `sku 1 -> n inventory_reservation`

### 4.3 `inventory_log`
作用：库存流水表，记录库存变化历史。

### 4.4 `stock_alert_record`
作用：低库存预警记录表。

## 5. 订单交易域

### 5.1 `cart_item`
作用：购物车表。
关系：
- `user 1 -> n cart_item`
- `sku 1 -> n cart_item`

### 5.2 `orders`
作用：订单主表。
核心字段：
- `order_no`
- `user_id`
- `order_status`
- `total_amount`
- `payable_amount`
- `pay_time`

关系：
- `user 1 -> n orders`
- `orders 1 -> n order_item`
- `orders 1 -> 1 payment_record`
- `orders 1 -> n order_operate_log`
- `orders 1 -> n inventory_reservation`

### 5.3 `order_item`
作用：订单明细表。
一笔订单可包含多个 SKU，所以必须拆主表和明细表。

### 5.4 `payment_record`
作用：支付记录表。
关系：通常是一笔订单一条支付记录。

### 5.5 `coupon`
作用：优惠券定义表。

### 5.6 `user_coupon`
作用：用户与优惠券关联表。

### 5.7 `order_operate_log`
作用：订单操作日志表。
业务场景：
- 创建订单
- 支付成功
- 取消订单
- 超时关单
- 发货
- 确认收货

### 5.8 `idempotent_token`
作用：订单提交幂等 token 表。
业务场景：
- 配合 Redis 防止重复下单。

## 6. 秒杀域

### 6.1 `seckill_activity`
作用：秒杀活动表。
核心字段：
- `activity_name`
- `start_time`
- `end_time`
- `status`

业务含义：
- 描述一个秒杀活动的时间窗口和发布状态。

### 6.2 `seckill_activity_sku`
作用：秒杀活动商品表。
核心字段：
- `activity_id`
- `sku_id`
- `seckill_price`
- `seckill_stock`
- `limit_per_user`
- `status`

业务含义：
- 指定某个活动中哪些商品参与秒杀。
- 定义秒杀价、秒杀库存和单用户限购数量。

关系：
- `seckill_activity 1 -> n seckill_activity_sku`
- `sku 1 -> n seckill_activity_sku`

### 6.3 `seckill_order`
作用：秒杀订单跟踪表。
核心字段：
- `order_no`
- `activity_id`
- `sku_id`
- `user_id`
- `quantity`
- `seckill_price`
- `status`

业务含义：
- 跟踪秒杀订单与活动、用户和 SKU 的关系。
- 用于支付、取消、超时后同步秒杀状态。

## 7. 表与表之间如何串成业务链路

### 7.1 登录与权限链路
`user -> user_role -> role -> role_permission -> permission`

这条链路决定：
- 用户是谁
- 拥有哪些角色
- 角色拥有哪些权限

### 7.2 商品管理链路
`category / brand -> spu -> sku -> file_record`

这条链路决定：
- 商品属于哪个分类和品牌
- 一个商品抽象下有多少个 SKU
- 每个 SKU 的图片和媒体资源如何管理

### 7.3 普通下单链路
`user -> cart_item -> orders -> order_item -> payment_record`
同时配合：
- `idempotent_token` 防重复提交
- `inventory` 和 `inventory_reservation` 做库存预留和状态流转
- `order_operate_log` 做业务留痕

### 7.4 库存链路
`sku -> inventory`
`orders -> inventory_reservation <- sku`

这条链路的核心是：
- 一个 SKU 有一条库存主记录
- 一笔订单预留了哪些 SKU 和多少数量，由 `inventory_reservation` 记录

### 7.5 秒杀链路
`seckill_activity -> seckill_activity_sku -> sku`
抢购成功后：
`seckill_order -> orders -> order_item / payment_record / inventory_reservation`

也就是说，秒杀只是多了一层活动控制和 Redis 预热，并没有另起一套订单体系。

## 8. 数据设计背后的思路
- 认证、商品、库存、订单、秒杀按职责拆表，避免混在一起。
- 多对多关系通过中间表处理，例如 `user_role`、`role_permission`、`user_coupon`。
- 订单和库存都不是只存最终结果，而是把状态流转和操作日志沉淀下来。
- 秒杀扩展是在现有交易模型上做前置增强，不破坏普通订单主链路。

## 9. 面试时最值得重点讲的表
如果面试官不想听 28 张表的全量细节，你可以优先讲这 12 张：
- `user`
- `spu`
- `sku`
- `file_record`
- `inventory`
- `inventory_reservation`
- `cart_item`
- `orders`
- `order_item`
- `payment_record`
- `idempotent_token`
- `seckill_activity_sku`

这 12 张已经足够把认证、商品、下单、库存、秒杀主链路讲清楚。
