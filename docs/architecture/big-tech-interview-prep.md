# RetailFlow 大厂面试准备文档

## 1. 项目一句话介绍
RetailFlow 是一个基于 Spring Boot 的电商后台单体项目，覆盖认证鉴权、商品管理、购物车、订单、库存、商品搜索、对象存储，以及面向高并发场景的 Redis Lua 预扣库存和秒杀扩展能力。

## 2. 推荐的 1 分钟介绍
我做的是一个电商后台单体项目，核心模块包括用户认证、商品管理、订单、库存、搜索和后台运营。技术上主要用了 Spring Boot、Spring Security、MyBatis-Plus、MySQL、Redis、MinIO 和 Elasticsearch。项目里我重点实现了三条链路：第一是 Spring Security + JWT 双 token 的无状态认证，access token 负责业务访问，refresh token 负责会话续期；第二是订单提交时的 Redis 幂等 token + MySQL 二次校验，防止重复下单；第三是库存侧的 Redis Lua 原子预扣库存 + MySQL 冻结库存最终一致性校验，解决高并发下的超卖问题。另外我还扩展了秒杀场景，通过秒杀活动预热、Lua 抢购和现有订单链路复用，把热点抢购链路补完整了。

## 3. 推荐的 3 分钟介绍
这是一个模块化单体电商后端。整体按 `auth`、`product`、`inventory`、`order`、`admin`、`seckill` 六个业务域拆分。认证层采用 Spring Security + JWT 双 token，无状态处理请求身份，access token 负责业务鉴权，refresh token 负责续期，Redis 保存 refresh token 会话状态。商品主数据存储在 MySQL，搜索索引使用 Elasticsearch，搜索时优先走 ES，异常时回退 MySQL，保证可用性。订单链路里，用户进入确认页先生成幂等 token，提交订单时先消费 Redis token，再做 MySQL token 状态确认，避免重复提交。库存链路里，我使用 Redis Lua 在入口原子预扣库存，再在 MySQL 中把可用库存转成冻结库存，并通过事务失败回滚、afterCommit 校验和定时对账补偿，尽量保证 Redis 与 MySQL 最终一致。除此之外，我还补了一个秒杀扩展：活动开始前将活动配置和秒杀库存预热到 Redis，请求直接走 Lua 完成活动时间、库存和限购校验，抢购成功后再复用现有订单和库存链路，这样普通电商链路和秒杀链路都能讲清楚。

## 4. 项目亮点拆解

### 4.1 Spring Security + JWT 双 Token
- 登录成功后同时签发 `access token` 和 `refresh token`。
- `access token` 中包含 `userId`、`username`、`roles`、`sessionId`、`tokenType=access`，生命周期较短。
- `refresh token` 中包含 `userId`、`username`、`sessionId`、`tokenType=refresh`，生命周期较长。
- 业务请求统一通过 `Authorization: Bearer <accessToken>` 进入过滤器，过滤器只接收 `tokenType=access` 的 token。
- Redis 保存 `refresh token` 会话状态，刷新时会校验 token 本身和 Redis 当前会话是否一致。
- 退出登录时删除对应 `sessionId` 的 refresh 会话，从而阻断后续续期。

面试可说：
access token 负责无状态业务鉴权，refresh token 负责长期登录态续期，Redis 负责 refresh 会话失效控制，这样兼顾了安全性和用户体验。

补充理解：
双 token 和 Session 的关键区别，不在于 Redis 有没有存状态，而在于 Redis 存的是什么状态。Session 模式下，服务端保存的是每个用户完整的主会话，请求每次都要根据 sessionId 去服务端取登录态；而当前双 token 方案里，业务请求主要依赖 access token 自己携带的身份信息，过滤器校验通过后就能完成鉴权，Redis 主要保存的是 refresh token 的续期会话状态，只在续期、登出和会话失效控制时发挥作用。所以双 token 不是完全纯无状态，但也没有退回到“每次请求都强依赖服务端主会话”的 Session 模式。

续期与失效控制：
- `refresh token` 不参与普通业务接口鉴权，它的职责是续期 `access token` 和承载会话是否仍然有效。
- 登录成功后，系统会给这组 token 分配一个 `sessionId`，并把当前有效的 `refresh token` 按 `sessionId` 写入 Redis。
- 当 `access token` 过期时，前端调用 refresh 接口提交 `refresh token`，服务端先校验它的签名、过期时间和 `tokenType=refresh`，再到 Redis 校验它是否仍然是这个 `sessionId` 对应的当前有效 token。
- 校验通过后，系统不会只发一个新的 `access token`，而是会删除旧的 refresh 会话，再重新签发一组新的 `access token + refresh token`，也就是 refresh token 轮换。
- 登出时，系统会从当前 `access token` 中解析 `sessionId`，然后删除 Redis 里的 refresh 会话。这样当前会话后续无法再刷新新的 `access token`，登录态会在 access token 自然过期后彻底失效。
- 所以 Redis 在这套方案里负责的是“refresh 会话是否还能续命”，而不是像 Session 那样承载每次请求都要读取的完整主会话。

### 4.2 防重复下单
- 用户进入确认页时，后端生成一次性下单 token。
- Redis 中保存 `order:idempotent:{userId}:{token}`，数据库表中也保存 token 状态。
- 提交订单时先删除 Redis token，删除成功表示抢到执行资格。
- 然后再做 MySQL 二次校验，只允许 `used=0` 的 token 被消费。
- 校验通过后才进入真正建单逻辑。

面试可说：
Redis 负责快速拦截，MySQL 负责最终状态确认和留痕，两层结合保证幂等更稳。

### 4.3 防超卖：Redis Lua 预扣 + MySQL 最终一致性
当前项目的库存链路已经不是单纯依赖数据库条件更新，而是多了一层 Redis 预扣。

#### 入口阶段
- 下单时先调用 Redis Lua 脚本原子预扣库存。
- Lua 在 Redis 中一次性完成“读取库存、判断是否足够、扣减库存、记录订单预留”。
- 这一步避免了 Java 里先查再扣的竞态问题。

#### 持久化阶段
- Redis 预扣成功后，再进入 MySQL 事务。
- 数据库中把 `available_stock` 转成 `frozen_stock`。
- 同时写入 `inventory_reservation` 预留记录和库存流水。

#### 失败补偿
- 如果 MySQL 阶段失败，立即调用 Lua 回滚 Redis 预扣。
- 提交成功后，通过 `afterCommit` 再校验 Redis 和 MySQL 的可用库存是否一致。
- 定时任务会周期性从 MySQL 对账修正 Redis，作为兜底补偿。

面试可说：
Redis 扛高并发入口，Lua 保证原子预扣，MySQL 负责最终库存状态落库和一致性兜底。

### 4.4 秒杀扩展与防超卖
秒杀场景和普通下单场景最大的区别，不是“库存也会减少”这么简单，而是它会在极短时间内把大量请求打到同一个热点 SKU 上。因此普通商品的懒加载库存模式不适合秒杀，秒杀必须把热点数据提前准备好，并把活动校验、库存扣减和限购校验压缩到 Redis 内部一次完成。

#### 为什么秒杀场景要单独设计
- 秒杀开始时间固定，流量集中，瞬时并发远高于普通下单。
- 同一个 `activityId + skuId` 会成为热点资源，不能让每个请求都先回源 MySQL。
- 除了库存本身，还要校验活动时间、商品是否参与活动、用户是否超过限购。

#### 预热阶段
- 新增 `seckill_activity`、`seckill_activity_sku`、`seckill_order` 三张表。
- 秒杀活动发布后，由 `SeckillPreheatScheduler` 定时扫描未来短时间内即将开始的活动。
- 对每个参与秒杀的商品，将以下数据写入 Redis：
  - `seckill:activity:{activityId}`：活动状态、开始时间、结束时间
  - `seckill:sku:{activityId}:{skuId}`：秒杀商品信息、秒杀价、限购数
  - `seckill:stock:{activityId}:{skuId}`：秒杀剩余库存
- 预热时不会盲目写秒杀库存，而是取 `seckill_stock` 和当前普通库存可用数中的较小值，避免配置库存高于真实库存。

#### 抢购入口阶段
- 在真正执行业务逻辑前，系统会先按 `activityId + skuId + userId` 做 Redis 用户级固定窗口限流，例如默认 1 秒内最多允许 3 次请求。
- 如果同一用户在极短时间内持续疯狂点击秒杀按钮，请求会先在这一层被拦下，避免大量无效流量反复冲击 Redis Lua 和正式建单链路。
- 在真正执行秒杀 Lua 之前，系统会先按 `activityId + skuId + userId` 写入一个短 TTL 的 Redis 提交锁，key 类似 `seckill:submit:lock:{activityId}:{skuId}:{userId}`。
- 如果同一用户在几秒内连续疯狂点击秒杀按钮，后续请求会因为拿不到这把短期提交锁而被直接拦下，避免大量重复请求反复打进 Redis Lua 和正式建单链路。
- 这两层控制解决的是“限流 + 防重复提交 + 削减无效请求”，并不替代真正的秒杀资格判断。
- 用户发起秒杀请求后，先不创建订单，而是先执行 `seckill_reserve.lua`。
- 这段 Lua 会在 Redis 内部一次性完成：
  - 活动是否存在
  - 活动是否已发布
  - 当前时间是否在活动窗口内
  - 秒杀库存是否足够
  - 用户当前已购买数量是否超过 `limitPerUser`
  - 扣减 `seckill:stock`
  - 增加用户购买计数 `seckill:user:buy:{activityId}:{skuId}:{userId}`
- 只有 Lua 返回成功，才说明这个用户真正抢到了秒杀资格。

#### 为什么 Lua 能防超卖
- 如果把活动校验、库存校验和限购校验拆到 Java 里分别执行，并发下会出现多个请求同时通过检查的问题。
- Lua 在 Redis 单线程执行模型里一次完成所有判断和修改，没有中间竞态窗口。
- 因此秒杀场景下真正的“第一道防超卖闸门”是 `seckill_reserve.lua`，它保证秒杀库存不会在 Redis 层被扣成负数。

#### 抢到资格后为什么还要建正式订单
- Redis 中的秒杀成功，只表示用户抢到了入口资格，并不代表交易已经正式成立。
- 抢购成功后，系统会调用 `submitSeckillOrder`，继续复用现有订单、库存冻结、支付、取消、超时关单链路。
- 这样做的好处是秒杀不会绕开现有交易系统，后续订单状态流转仍然统一沉淀在 MySQL。

#### 秒杀失败补偿
- 如果 Lua 抢购成功了，但后续正式建单失败，系统会立即调用 `seckill_rollback.lua`。
- 这段回滚脚本会做两件事：
  - 把秒杀库存加回 `seckill:stock:{activityId}:{skuId}`
  - 把用户购买计数减回去，必要时删除购买标记
- 这一步很关键，因为秒杀失败时不仅要恢复库存，还要恢复用户抢购资格，否则会出现“库存恢复了，但用户被错误判定为已抢过”的问题。

#### 订单取消、超时后的补偿
- 秒杀成功建单后，如果订单还未支付就被取消或超时关闭，`SeckillOrderSyncServiceImpl` 会再次触发 Redis 回滚。
- 也就是说，秒杀场景的回滚不只发生在“建单失败”这一刻，后续订单状态变成 `CANCELED` 或 `CLOSED` 时，也会把秒杀库存和用户限购状态释放回去。
- 如果订单支付成功，则秒杀订单状态会更新为 `PAID`，此时不再回滚秒杀库存，因为这部分库存已经转化成正式成交结果。

#### 秒杀场景的一致性边界
- 当前秒杀链路已经实现了同步回滚、订单状态同步，以及秒杀 Redis 定时对账补偿。
- 定时任务会周期性扫描近期已发布的秒杀活动，根据数据库中状态为 `CREATED`、`PAID` 的有效秒杀订单，重建 Redis 中的秒杀剩余库存和用户购买计数。
- 因此即使遇到极端异常，例如进程在 Lua 成功后立刻崩溃，只要数据库中的秒杀订单状态最终正确，后续定时任务通常也能把 Redis 秒杀状态修回正确值。
- 更准确地说，当前秒杀链路已经具备“入口资格层 + 后续状态层”的最终一致性补偿能力，但它仍然不是分布式事务意义上的绝对强一致。
面试可说：
秒杀防超卖和入口治理的核心不是简单加锁，而是“活动前预热 + 用户级固定窗口限流 + 短期防重复提交锁 + Lua 原子资格校验 + 正式订单落库 + 失败回滚”。活动开始前先把秒杀库存和活动配置批量灌入 Redis，请求进来后先用用户级限流挡掉过于频繁的请求，再用 Redis 短期提交锁挡掉同一用户几秒内的连续重复点击，然后通过 Lua 一次性完成活动时间、库存和限购校验并原子扣减库存，抢购成功后再复用现有订单链路创建正式订单；如果建单失败、取消或超时，就把秒杀库存和用户购买计数一起回滚。这样做既能扛住热点流量，也能尽量保证秒杀资格状态和正式交易状态一致。

### 4.5 商品搜索：Elasticsearch + MySQL 回退
- 商品主数据存 MySQL，搜索文档存 Elasticsearch。
- 索引文档并不是单表拷贝，而是由 `sku + spu + brand + category` 重新组装而成。
- 创建、更新、上架时主动同步文档；下架时移除文档。
- 搜索时优先走 ES，异常时回退 MySQL。

#### 当前搜索条件
- 关键词 `keyword`
- 分类 `categoryId`
- 品牌 `brandId`
- 价格区间 `minPrice/maxPrice`
- 分页 `pageNum/pageSize`

#### 当前相关性搜索策略
ES 查询本质是 `bool(filter + multi_match)`：
- `filter`：`publishStatus=1`、分类、品牌、价格区间
- `must`：对 `title`、`subtitle`、`brandName`、`categoryName` 做 `multi_match`

字段权重：
- `title^3`
- `subtitle^2`
- `brandName`
- `categoryName`

也就是说，标题命中优先，副标题次之，品牌和分类作为辅助匹配字段。底层相关性排序依赖 Elasticsearch 默认的 BM25 `_score`。

#### 一个搜索例子
如果用户搜索“蓝牙耳机”，并传入：
- `categoryId=1001`
- `brandId=2001`
- `minPrice=100`
- `maxPrice=300`

那么 ES 查询会先过滤：
- 已上架商品
- 指定分类
- 指定品牌
- 价格在 100 到 300 之间

然后再根据“蓝牙耳机”在标题、副标题、品牌名、分类名上的匹配情况按 `_score` 排序。

面试可说：
当前搜索已经具备关键词相关性排序和价格区间过滤，但还没有做高亮、同义词、拼音和搜索建议，这些属于下一步增强方向。

### 4.6 MinIO 对象存储
- 图片上传时不会把二进制文件直接存数据库。
- 业务层通过存储抽象调用底层存储实现。
- MinIO 保存文件内容，MySQL 中的 `file_record` 保存元信息。
- 元信息包括业务类型、业务 id、bucket、objectKey、url、contentType、fileSize。

面试可说：
MySQL 负责结构化元数据，MinIO 负责承载媒体资源，二者解耦后更符合真实项目的存储分层。

## 5. 为什么用双 token，而不是 Session
Session 模式的核心是服务端保存每个用户的完整主会话状态，客户端只带 sessionId。当前项目采用的是 JWT 双 token 方案：业务接口只依赖短期 access token，长期登录态则通过 refresh token + Redis 会话状态管理。这样服务端不需要像 Session 一样保存每个请求都要读取的完整主会话对象，同时又能通过 Redis 控制 refresh token 的失效、轮换和续期，比单 token 更适合前后端分离场景，也比传统 Session 更容易和 Bearer Token 鉴权链路结合。

## 6. 单体项目还能怎么优化
如果继续在单体阶段打磨，这个项目最适合的优化方向有：
- 登录、搜索、下单接口限流
- 商品详情缓存的空值缓存和击穿保护
- SQL 和索引优化
- 库存链路监控与告警
- 异步任务线程池隔离
- 单元测试、集成测试和并发压测补齐

### 6.1 后台任务线程池为什么要单独配置
当前项目里已经有多类 `@Scheduled` 后台任务：
- 订单超时关闭
- 普通库存对账补偿
- 库存告警扫描
- 秒杀预热
- 秒杀定时对账补偿

如果完全沿用默认调度器，这些任务虽然也能运行，但在任务执行时间拉长、多个任务同一时间触发时，容易出现互相挤占的问题。例如秒杀预热正在批量刷新 Redis 时，库存对账和超时关单也可能同时开始扫描数据库；如果没有独立线程池，这些任务会共享默认调度资源，系统可控性会比较差。

所以我把后台任务进一步拆成了三个专用线程池，而不是继续共用一个统一调度池：
- `app.task.scheduler.order.*`：订单维护线程池，当前承载订单超时关闭
- `app.task.scheduler.inventory.*`：库存维护线程池，当前承载库存对账补偿和库存告警扫描
- `app.task.scheduler.seckill.*`：秒杀维护线程池，当前承载秒杀预热和秒杀 Redis 定时对账补偿

对应实现是三个命名的 `ThreadPoolTaskScheduler` Bean：
- `orderTaskScheduler`
- `inventoryTaskScheduler`
- `seckillTaskScheduler`

每个 `@Scheduled` 任务通过 `scheduler = "..."` 显式绑定到对应线程池。这样做的价值是：
- 订单维护、普通库存维护、秒杀维护三类任务按职责隔离
- 秒杀预热这类偏重任务不会去挤占订单超时关闭的调度线程
- 后续如果某一类任务明显变重，可以单独调它自己的池大小，而不用整体调大
- 应用关闭时每个调度池都会等待任务优雅结束，任务异常也会统一记录日志

### 6.2 配置之后，请求处理过程会怎么变化
这次配置的不是 Web 请求线程池，而是后台任务线程池，所以 HTTP 请求的主处理方式没有改变：
- 用户请求进入后，仍然由内嵌 Web 容器的请求线程处理 Controller / Service / Mapper 链路
- 普通下单、秒杀下单、商品搜索这些实时接口，仍然跑在 Web 请求线程里

变化发生在后台任务侧：
- 到达 cron 时间后，订单超时关闭会从 `retailflow-order-scheduler-*` 中取线程
- 库存对账和库存告警会从 `retailflow-inventory-scheduler-*` 中取线程
- 秒杀预热和秒杀对账会从 `retailflow-seckill-scheduler-*` 中取线程
- Web 请求线程和后台任务线程职责分离，请求线程负责实时响应用户，调度线程负责周期性维护状态

更直白一点说，配置之后系统会变成：
- 前台用户请求：由 Web 容器线程池处理
- 后台周期任务：由专门的调度线程池处理

这样做不会让请求链路变快很多，但能明显提升系统稳定性和任务隔离性，尤其是在库存对账、秒杀预热和订单超时任务同时存在的情况下更有意义。

## 7. 面试高频追问

### Q1：为什么库存不能只靠 Redis？
答：Redis 更适合抗高并发入口，但最终库存状态和订单状态还是要在 MySQL 中落库。否则系统重启、数据恢复、对账审计都会很难做，所以 Redis 负责入口原子操作，MySQL 负责最终权威状态。

### Q2：为什么 MySQL 里还保留条件更新？
答：因为 Redis 预扣成功只能说明入口拿到了库存资格，不代表数据库已经持久化成功。MySQL 条件更新是最终一致性校验的一部分，也是库存状态流转的正式落库动作。

### Q3：为什么分布式锁不作为高频扣库存主方案？
答：因为热点库存操作如果每次都先抢锁，会把并发请求串行化，吞吐量下降明显。高频库存扣减更适合用 Lua 脚本做原子校验和扣减，分布式锁更适合做缓存重建、库存初始化、对账修复这类低频操作。

### Q4：ES 文档为什么不是直接把单表同步过去？
答：因为 MySQL 的表结构是为事务和规范化存储设计的，ES 的文档结构是为搜索设计的。ES 更适合存一份为搜索场景反规范化后的文档，所以要把多表字段合并成 `ProductSearchDocument`。

### Q5：秒杀为什么要预热，而不是懒加载？
答：秒杀开始瞬间流量集中，如果等请求来了再加载库存，会引起初始化竞争和回源抖动。活动前预热可以把热点数据提前灌进 Redis，让秒杀入口更平滑。

## 8. 简历和面试表达建议
推荐你在简历和面试里优先强调这几个点：
- Spring Security + JWT 双 token + Redis refresh 会话管理
- Redis 幂等 token + MySQL 二次校验
- Redis Lua 预扣库存 + MySQL 最终一致性校验 + 定时对账补偿
- Elasticsearch 商品搜索 + BM25 相关性排序 + MySQL 回退
- MinIO 对象存储解耦媒体资源
- 秒杀活动预热 + Lua 抢购 + 复用订单链路

## 9. 当前项目边界
为了避免面试时说过头，要明确知道当前还没有完整实现这些内容：
- MQ 异步补偿闭环
- 搜索高亮、同义词、拼音、搜索建议
- 用户级长期行为画像
- 微服务拆分和分布式事务

知道边界并不减分，反而说明你对项目真实能力判断比较成熟。
