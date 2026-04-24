

本项目是一个基于 Spring Boot 3 的电商后台单体项目，覆盖认证鉴权、商品管理、购物车、订单、库存、搜索、对象存储，以及面向高并发场景的库存预扣和秒杀扩展能力。

## 项目定位
- 采用模块化单体架构，电商核心业务链路完整。

## 技术栈
- Java 17
- Spring Boot 3.3.5
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8
- Redis
- Elasticsearch
- MinIO
- Spring Cache
- Spring Scheduler
- Docker Compose

## 当前核心能力

### 认证与鉴权
- 使用 Spring Security + JWT 构建双 token 认证链路。
- 登录成功后同时签发短期 `access token` 和长期 `refresh token`。
- 业务请求通过 `Authorization: Bearer <accessToken>` 携带 access token。
- 过滤器只校验 access token，并在解析成功后写入 `SecurityContext`。
- Redis 保存 refresh token 会话状态，退出登录时通过删除会话实现失效控制。

### 商品与搜索
- 商品主数据存储在 MySQL，搜索索引存储在 Elasticsearch。
- 商品创建、更新、上架、下架时，由应用层主动同步 ES 搜索文档。
- 搜索策略为 ES 优先、MySQL 回退。
- ES 支持关键词相关性搜索、分类过滤、品牌过滤、价格区间过滤。

### 下单与幂等
- 订单提交使用 Redis 幂等 token + MySQL 二次校验防止重复下单。
- 用户进入确认页时先生成一次性 token。
- 提交订单时先消费 Redis token，再更新数据库 token 状态。

### 库存与防超卖
- 库存表拆分为 `total_stock`、`available_stock`、`frozen_stock`。
- 下单时不会直接永久扣减库存，而是先做库存预留。
- 普通场景下采用 Redis Lua 原子预扣库存，再由 MySQL 冻结库存做最终一致性校验。
- MySQL 事务失败时会回滚 Redis 预扣。
- 提交成功后通过 `afterCommit` 校验 Redis 和 MySQL 一致性，并支持定时对账补偿。

### 秒杀扩展
- 新增秒杀活动、秒杀商品和秒杀订单模型。
- 活动开始前通过定时任务将秒杀库存和活动配置批量预热到 Redis。
- 秒杀提交入口增加 Redis 用户级固定窗口限流，限制同一用户在极短时间内对同一活动商品的请求频率。
- 秒杀提交入口增加 Redis 短期防重复提交锁，防止同一用户对同一活动商品疯狂点击时产生过多无效请求。
- 秒杀请求通过 Lua 一次性完成活动时间、库存、限购校验和库存扣减。
- 秒杀成功后继续复用现有订单、库存、支付、超时关单链路。
- 增加秒杀定时对账补偿任务，按数据库中的秒杀订单状态重建 Redis 秒杀库存和用户购买计数。

### 文件存储
- 图片等媒体资源通过 MinIO 或本地存储抽象管理。
- MySQL 中只记录文件元信息，媒体文件本体由对象存储承载。

## 模块划分
- `auth`：登录、注册、双 token、过滤器、refresh 会话管理。
- `product`：分类、品牌、SPU、SKU、图片、搜索索引同步。
- `inventory`：库存预留、冻结、释放、Redis 预扣、对账补偿。
- `order`：购物车、订单提交、支付、取消、超时关单。
- `admin`：后台订单、后台看板等管理能力。
- `seckill`：秒杀活动配置、预热、Lua 抢购、秒杀订单状态同步。

## 目录结构
```text
src/main/java/com/example/retailflow
├─ auth
├─ admin
├─ common
├─ config
├─ inventory
├─ order
├─ product
└─ seckill
```

## 快速启动

### 1. 启动基础依赖
项目支持通过 Docker Compose 启动 MySQL、Redis、MinIO、Elasticsearch。

### 2. 初始化数据库
执行：
- `sql/init-mysql.sql`


### 3. 启动应用
```bash
mvn spring-boot:run
```

### 4. 常用地址
- Swagger：`http://localhost:8080/swagger-ui.html`
- API 文档：`http://localhost:8080/v3/api-docs`

## 默认配置说明
`application.yml` 中的重点配置包括：
- Access Token 有效期：`jwt.access-expire-seconds`
- Refresh Token 有效期：`jwt.refresh-expire-seconds`
- 订单超时时间：`app.order.timeout-minutes=30`
- 库存对账任务：`app.inventory.reconcile-cron`
- 秒杀预热任务：`app.seckill.preheat-cron`
- 秒杀对账任务：`app.seckill.reconcile-cron`
- 秒杀防重复提交锁 TTL：`app.seckill.submit-lock-seconds`
- 秒杀用户级固定窗口限流：`app.seckill.user-rate-limit.window-seconds`、`app.seckill.user-rate-limit.max-requests`
- 订单任务线程池：`app.task.scheduler.order.*`
- 库存任务线程池：`app.task.scheduler.inventory.*`
- 秒杀任务线程池：`app.task.scheduler.seckill.*`
- ES 搜索开关：`app.search.elasticsearch.enabled`


## 当前项目特点总结
- 不是单纯的 CRUD 项目，而是把认证、搜索、下单、库存和秒杀链路串成了完整业务闭环。
- 强调“主数据在 MySQL，缓存/索引/对象存储做增强”的设计思路。
- 适合作为后端工程能力、业务建模能力和面试表达能力的综合项目。
