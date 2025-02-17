# RetailFlow 业务链路讲解文档

## 1. 项目整体业务流
RetailFlow 是一个典型的电商后台单体系统，主链路可以概括为：

1. 用户登录并拿到 JWT
2. 前端通过 Bearer Token 访问接口
3. 用户浏览商品、查看详情、搜索商品
4. 用户将商品加入购物车或直接下单
5. 提交订单前先校验幂等 token
6. 库存侧先做 Redis Lua 原子预扣，再做 MySQL 冻结库存
7. 订单主表、订单明细、支付记录、操作日志落库
8. 支付成功后正式扣减冻结库存
9. 取消订单或超时关单时释放冻结库存
10. 定时任务对 Redis/MySQL 库存做对账修复

如果进入秒杀场景，则在这条主链路前面再增加一层“活动前 Redis 预热 + 秒杀 Lua 抢购”。

## 2. 登录鉴权链路

### 2.1 登录阶段
- 根据用户名查询用户。
- 用密码编码器校验输入密码和数据库加密密码是否匹配。
- 查询用户角色。
- 生成短期 `access token`，写入 `userId`、`username`、`roles`、`sessionId` 和 `tokenType=access`。
- 生成长期 `refresh token`，写入 `userId`、`username`、`sessionId` 和 `tokenType=refresh`。
- 把 refresh token 会话状态写入 Redis，作为后续续期和登出失效控制的依据。
- 返回给前端。

### 2.2 请求鉴权阶段
- 前端通过 `Authorization: Bearer <accessToken>` 携带 access token。
- Spring Security 过滤器读取请求头。
- 校验 token 签名、过期时间以及 `tokenType=access`。
- 解析成功后构造 `Authentication`，写入 `SecurityContext`。
- 后续 Controller 和 Service 就可以基于当前用户身份处理业务。

### 2.3 登出阶段
- 退出登录时从 access token 中解析 `sessionId`。
- 根据 `sessionId` 删除 Redis 中的 refresh token 会话。
- 这样当前会话后续无法再刷新新的 access token，登录态会在 access token 自然过期后彻底失效。

### 2.4 刷新阶段
- 当前项目提供独立的 refresh 接口，不再通过重新输入用户名密码续期。
- 前端提交 `refresh token` 后，服务端先校验签名、过期时间以及 `tokenType=refresh`。
- 再到 Redis 校验这个 refresh token 是否仍然是当前有效会话。
- 校验通过后，删除旧 refresh 会话并重新签发一组新的 access token + refresh token。
- 这样可以实现短 access token 的无感续期，同时把长期登录态控制在 Redis 会话层。

补充说明：
- refresh 阶段不是“重新登录”，而是基于已经登录成功的会话做续期。
- 当前实现里，Redis 会按 `sessionId` 保存当前有效的 refresh token，所以服务端不仅校验 token 自身是否合法，还会校验它是不是 Redis 里登记的当前会话 token。
- 这意味着即使某个 refresh token 签名还对、时间也没过，只要它不再是 Redis 中当前登记的那个 token，系统也不会接受它继续刷新。
- 刷新成功后，旧 refresh 会话会被删除，并重新签发一组新的 access token 和 refresh token，这样旧 refresh token 不能被无限复用。

### 2.5 双 token 和 Session 的区别
- Session 模式下，服务端保存的是完整主会话，客户端后续每次请求只带 `sessionId`。
- 请求进入后，服务端必须根据 `sessionId` 到 Session 容器或 Redis 中取出完整登录态，才能继续鉴权。
- 当前双 token 方案里，业务请求主要依赖 access token 自己携带的 `userId`、`roles`、`sessionId`、`tokenType` 等信息。
- 也就是说，大多数业务请求不需要先去 Redis 取完整主会话，只要 access token 合法，就能直接进入鉴权链路。
- Redis 在这里保存的主要是 refresh token 会话状态，用来做续期、登出失效和会话轮换控制。

所以更准确地说：
- Session 是“每次请求都强依赖服务端主会话”
- 双 token 是“业务访问走 access token，自身带身份；Redis 只负责 refresh 会话管理”

### 2.6 refresh token 在登出和会话失效里的作用
- 当前项目里，登出的关键动作不是拉黑 access token，而是删除 Redis 中与当前 `sessionId` 对应的 refresh 会话。
- 删除之后，这次登录会话就不能再刷新新的 access token 了，所以一旦当前 access token 自然过期，整次会话就彻底失效。
- 这也是 refresh token 在服务端最重要的价值：它让系统有了一个可以主动控制“会话还能不能继续有效”的抓手。
- 因此它控制的是长期续期能力，而不是替代 access token 去访问业务接口。

## 3. 商品链路

### 3.1 商品建模
- `spu` 表表示商品主模型。
- `sku` 表表示真正可售卖的销售单元。
- `category`、`brand` 表存商品分类和品牌。
- `file_record` 记录商品图片等媒体资源元信息。

### 3.2 商品发布链路
- 创建商品时先写 `spu` 和 `sku`。
- 默认未上架。
- 上架时修改 `spu.publishStatus=1`。
- 下架时把 `publishStatus` 改回 0。

### 3.3 图片上传链路
- 管理员上传商品图片。
- 业务层调用存储抽象。
- MinIO 或本地存储保存文件内容。
- `file_record` 表记录对象存储元信息。

## 4. 商品搜索链路

### 4.1 数据流向
- MySQL 是商品主数据源。
- Elasticsearch 存一份面向搜索场景的商品文档。
- 商品创建、更新、上架时同步 ES。
- 商品下架时从 ES 删除文档。

### 4.2 搜索请求支持的条件
- 关键词 `keyword`
- 分类 `categoryId`
- 品牌 `brandId`
- 价格区间 `minPrice/maxPrice`
- 分页 `pageNum/pageSize`

### 4.3 查询过程
- 优先调用 ES 搜索服务。
- ES 查询使用 `bool(filter + multi_match)`。
- `filter` 处理上架状态、分类、品牌、价格区间。
- `multi_match` 处理关键词相关性搜索。
- 字段权重是 `title^3`、`subtitle^2`、`brandName`、`categoryName`。
- 按 BM25 `_score` 返回相关性更高的商品。
- 如果 ES 不可用，自动回退到 MySQL。

### 4.4 结果结构
返回给前端的是统一的 `ProductResponse`，而不是 ES 原始文档。也就是说 ES 只是搜索引擎，对外仍由商品服务维持统一接口结构。

## 5. 购物车链路
- 用户加入购物车时，购物车数据写 MySQL。
- Redis 中缓存 `cart:user:{userId}`。
- 下单前读取勾选的购物车项。
- 成功下单后会清理已消费的购物车数据并刷新缓存。

## 6. 订单提交链路

### 6.1 防重复提交
- 用户进入确认页时，后端生成一次性幂等 token。
- Redis 保存 `order:idempotent:{userId}:{token}`。
- MySQL 中 `idempotent_token` 表记录 token 状态。
- 提交订单时先删除 Redis token。
- 删除成功后再更新数据库 token 状态。
- 校验通过后才进入建单流程。

### 6.2 建单流程
- 查询购物车勾选项或直接下单商品。
- 生成订单号。
- 调用库存服务预留库存。
- 写入 `orders` 主表。
- 写入 `order_item` 明细表。
- 写入 `payment_record`。
- 写入 `order_operate_log`。

## 7. 普通库存链路

### 7.1 库存模型
库存表里最关键的字段是：
- `total_stock`
- `available_stock`
- `frozen_stock`

含义：
- `available_stock`：当前可售库存
- `frozen_stock`：已经被订单占住，但还未最终售出的库存

### 7.2 预留库存
普通下单时，库存服务会先做 Redis Lua 原子预扣：
- 读取 Redis 可用库存
- 判断是否足够
- 扣减库存
- 记录订单预留快照

Lua 成功后，再进入 MySQL 事务：
- `available_stock -= quantity`
- `frozen_stock += quantity`
- 写入 `inventory_reservation`
- 写库存流水

### 7.3 为什么不会超卖
- Redis 入口用 Lua 原子执行，不存在先查再扣的竞态窗口。
- MySQL 中仍保留条件更新，防止最终落库时把库存扣穿。
- 如果 MySQL 失败，会立刻回滚 Redis 预扣。
- 提交成功后还会做 afterCommit 校验和定时对账补偿。

### 7.4 支付、取消、超时
- 支付成功：把 `frozen_stock` 正式扣减，预留状态改成 `DEDUCTED`。
- 取消订单：把冻结库存释放回可用库存，状态改成 `RELEASED`。
- 超时关单：和取消一样，释放冻结库存。

## 8. Redis/MySQL 最终一致性链路
当前库存链路不是强一致单事务，而是通过补偿实现最终一致：

1. Redis Lua 先成功预扣
2. MySQL 事务冻结库存
3. MySQL 失败时回滚 Redis
4. 数据库提交成功后，通过 `afterCommit` 校验 Redis 和 MySQL 可用库存
5. 定时任务周期性从 MySQL 修正 Redis

所以面试时应该说：
“当前项目实现的是 Redis Lua 原子预扣库存 + MySQL 最终一致性校验 + 定时对账补偿。”

## 8.1 后台任务线程池
随着库存链路和秒杀链路逐渐完整，项目里已经出现多类周期性后台任务：
- 订单超时关闭
- 普通库存对账补偿
- 库存告警扫描
- 秒杀预热
- 秒杀 Redis 定时对账补偿

如果不做额外配置，这些 `@Scheduled` 任务虽然也能执行，但它们会共用 Spring 默认调度资源。在任务耗时拉长或者多个 cron 时间重叠时，就可能出现任务之间互相影响的问题，例如秒杀预热正在批量刷新 Redis 时，库存对账和订单超时关闭又同时开始跑。

所以项目里把后台调度拆成了三个专用的 `ThreadPoolTaskScheduler`：
- `orderTaskScheduler`：订单维护线程池
- `inventoryTaskScheduler`：库存维护线程池
- `seckillTaskScheduler`：秒杀维护线程池

每个线程池都可以单独配置：
- `app.task.scheduler.order.*`
- `app.task.scheduler.inventory.*`
- `app.task.scheduler.seckill.*`

并且每个 `@Scheduled` 任务都通过 `scheduler = "..."` 显式绑定到对应线程池上。

这样做之后，系统里的线程分工会更清晰：
- Web 请求线程：继续处理用户的实时请求，比如登录、搜索、下单、秒杀提交
- 订单调度线程：负责订单超时关闭，跑在 `retailflow-order-scheduler-*`
- 库存调度线程：负责库存对账和库存告警，跑在 `retailflow-inventory-scheduler-*`
- 秒杀调度线程：负责秒杀预热和秒杀对账，跑在 `retailflow-seckill-scheduler-*`

也就是说，配置之后并不是“所有后台任务共用一个统一调度池”，而是按照任务职责拆成多个调度池。这样秒杀预热这类偏重任务不会去拖慢订单超时关闭，库存对账任务也能独立调优，整体隔离性会更好。

## 9. 秒杀链路

秒杀链路和普通下单链路最大的区别在于：普通商品可以接受按需懒加载库存，而秒杀商品在活动开始瞬间会形成极高的热点流量，所以必须把活动配置和库存提前准备好，并且把活动校验、库存扣减、限购校验压缩到 Redis 一次性原子执行。

### 9.1 秒杀活动配置
新增三张表：
- `seckill_activity`
- `seckill_activity_sku`
- `seckill_order`

它们分别承担：
- `seckill_activity`：记录活动名称、开始时间、结束时间、发布状态
- `seckill_activity_sku`：记录某个活动下哪些 SKU 参与秒杀、秒杀价、秒杀库存、每用户限购数
- `seckill_order`：记录秒杀订单与活动、SKU、用户之间的关系，便于后续支付、取消、超时状态同步

### 9.2 为什么秒杀要预热
普通库存链路里，Redis 可用库存可以在第一次访问时懒加载；但秒杀场景不能这么做，因为：
- 秒杀开始时间固定，请求会在极短时间集中爆发
- 如果等请求来了再查库并初始化 Redis，会引发缓存初始化竞争和数据库回源抖动
- 秒杀除了库存，还要同时校验活动时间和用户限购

所以秒杀必须在活动开始前，把热点活动需要的数据提前批量写入 Redis。

### 9.3 秒杀预热阶段
定时任务会扫描即将开始的秒杀活动，然后把这些数据写入 Redis：
- `seckill:activity:{activityId}`：活动状态、开始时间、结束时间
- `seckill:sku:{activityId}:{skuId}`：商品标题、原价、秒杀价、限购数
- `seckill:stock:{activityId}:{skuId}`：当前秒杀剩余库存

这里的秒杀库存不是直接照搬活动配置，而是会取：
- `seckill_stock`
- 当前普通库存可用数

两者中的较小值，避免活动配置库存大于真实库存。

### 9.4 秒杀请求处理
秒杀接口收到请求后，不会立刻创建订单，而是先争抢秒杀资格，流程是：
1. 校验登录
2. 先按 `activityId + skuId + userId` 做用户级固定窗口限流，限制同一用户在短时间内的请求频率
3. 如果用户在一个很小的时间窗口内请求次数过多，直接返回“请求过于频繁”，不会继续进入后续链路
4. 再按 `activityId + skuId + userId` 尝试写入一个短 TTL 的 Redis 提交锁，防止同一用户在极短时间内疯狂点击产生大量重复请求
5. 如果短期提交锁获取失败，直接返回“请勿重复提交”，不会继续进入 Lua 和正式建单链路
6. 读取秒杀活动配置
7. 执行 `seckill_reserve.lua`
8. Lua 在 Redis 中原子完成：
   - 活动是否存在
   - 活动是否已发布
   - 当前时间是否在秒杀窗口内
   - 秒杀库存是否足够
   - 用户购买数量是否超过限购
   - 秒杀库存扣减
   - 用户购买计数更新
9. 只有 Lua 成功，才说明用户真正抢到了秒杀资格
10. 抢购成功后调用 `submitSeckillOrder`
11. 后续继续走普通订单和库存正式落库链路

也就是说，秒杀请求分成两层：
- 第一层是 Redis 中的“用户级限流 + 短期防重复提交 + 抢资格”
- 第二层是 MySQL 中的正式交易落库

### 9.5 为什么 Lua 能防止秒杀超卖
如果把这些校验拆成多次 Java/Redis/MySQL 往返：
- 先查活动
- 再查库存
- 再查限购
- 最后扣库存

那高并发下会有多个请求同时通过检查，最后一起扣减，导致超卖或突破限购。

Lua 的优势在于：
- Redis 单线程执行脚本
- 活动时间、库存、限购和扣减在同一段脚本中一次完成
- 中间不会被别的请求插进来

所以秒杀场景下真正的第一道防超卖闸门，就是 `seckill_reserve.lua`。

### 9.6 抢到资格后为什么还要建正式订单
Redis 秒杀成功不等于正式交易成功，它只表示：
- 用户在高并发入口里抢到了名额

真正的订单、支付、库存冻结、后续状态流转仍然要回到现有交易系统，所以秒杀成功后会继续：
- 创建正式订单
- 走库存冻结
- 写支付记录
- 写订单操作日志

这样做的好处是：
- 秒杀不会绕开现有订单系统
- 支付、取消、超时逻辑仍然统一
- 最终主数据还是沉淀在 MySQL

### 9.7 秒杀失败补偿
如果 Lua 已经抢购成功，但后续正式建单失败，系统会立即执行秒杀回滚。

回滚时做的不是单纯“库存加回去”，而是两件事一起做：
- 把 `seckill:stock:{activityId}:{skuId}` 加回去
- 把用户购买计数减回去，必要时删除购买标记

这样做的原因是：
- 秒杀不仅有库存约束，还有限购约束
- 如果只恢复库存，不恢复用户购买计数，用户会被错误地判定为已经抢过

所以秒杀回滚本质上回滚的是：
- 库存状态
- 抢购资格状态

### 9.8 订单取消和超时后的补偿
秒杀订单创建成功后，如果：
- 用户取消订单
- 订单超时未支付被关闭

秒杀同步服务会再次回滚 Redis：
- 恢复秒杀库存
- 恢复用户购买计数
- 更新 `seckill_order` 状态为 `RELEASED`

也就是说，秒杀补偿不是只在“建单失败”时发生，而是贯穿订单后续生命周期：
- 建单失败：回滚
- 取消/超时：回滚
- 支付成功：确认完成，不再回滚

### 9.9 秒杀订单后续状态
- 支付成功后，秒杀订单状态改成 `PAID`
- 取消或超时后，秒杀订单状态改成 `RELEASED`
- 回滚动作会同步修正 Redis 秒杀库存和用户购买计数

### 9.10 当前秒杀链路的一致性边界
当前秒杀链路已经实现了：
- 活动前预热
- Lua 原子抢资格
- 建单失败即时回滚
- 取消/超时后的订单状态同步回滚
- 秒杀 Redis 定时对账补偿

当前的定时对账任务会周期性扫描近期已发布的秒杀活动，并根据数据库中的有效秒杀订单状态重建 Redis 秒杀快照：
- 统计 `CREATED`、`PAID` 状态的秒杀订单，重新计算每个 SKU 的已占用数量
- 用活动库存减去已占用数量，重建 `seckill:stock:{activityId}:{skuId}`
- 根据已生效订单重建 `seckill:user:buy:{activityId}:{skuId}:{userId}` 用户购买计数
- 删除 Redis 中已经过期或不再需要的旧用户购买标记

所以更准确地说：
- 当前秒杀场景已经具备“入口资格层 + 后续状态层”的防超卖和补偿能力
- 在极端异常下仍然不能说是绝对强一致，但只要数据库中的秒杀订单状态最终正确，后续定时任务通常也能把 Redis 秒杀状态修回正确值
## 10. 为什么这些设计适合单体项目
- 业务链路集中，便于统一事务和统一补偿。
- 不急着引入 MQ 和复杂分布式事务，也能把核心问题讲完整。
- Redis、ES、MinIO 都是“增强能力”，MySQL 仍然是主数据源。
- 对面试来说，这套结构足够完整，也足够贴近真实业务。
