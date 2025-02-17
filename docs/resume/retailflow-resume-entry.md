# RetailFlow 简历项目描述

## 版本一：适合简历的完整描述

**项目名称：** RetailFlow 电商后台管理系统（个人项目）

**项目背景：**
基于 Spring Boot 独立设计并实现的电商后台单体应用，覆盖认证鉴权、商品管理、订单管理、库存管理、商品搜索、对象存储以及秒杀扩展等核心业务场景，重点关注认证链路、订单幂等控制、库存状态流转、高并发防超卖和搜索能力增强。

**技术栈：**
Spring Boot、Spring Security、JWT、MyBatis-Plus、MySQL、Redis、Elasticsearch、MinIO、Docker Compose

**项目亮点：**
- 使用 Spring Security + JWT 构建双 token 无状态认证体系，通过过滤器解析 access token 并写入 `SecurityContext`，结合 Redis refresh 会话管理实现续期与登出失效控制。
- 针对订单重复提交问题，设计 Redis 幂等 token + MySQL 二次校验机制，用户提交订单时先消费 Redis token，再更新数据库 token 状态，避免重复下单。
- 针对高并发库存场景，实现 Redis Lua 原子预扣库存 + MySQL 冻结库存最终一致性校验机制；数据库失败时同步回滚 Redis，提交后通过 afterCommit 校验和定时对账补偿修正 Redis/MySQL 偏差。
- 基于可用库存与冻结库存分离的库存模型，完成下单冻结、支付扣减、取消/超时释放的完整库存状态流转，避免超卖并保证订单与库存状态一致。
- 使用 Elasticsearch 构建商品搜索能力，支持关键词相关性搜索、品牌过滤、分类过滤和价格区间过滤；采用 ES 优先、MySQL 回退策略保障搜索可用性。
- 使用 MinIO 管理商品图片和对象文件，数据库仅保存文件元信息，实现业务数据与媒体资源解耦。
- 扩展秒杀场景，设计秒杀活动、秒杀商品和秒杀订单模型，通过定时任务将活动前的秒杀库存批量预热到 Redis，并使用 Lua 原子完成活动时间、库存和限购校验，成功后复用现有订单和库存链路落库。

---

## 版本二：适合简历压缩版

**RetailFlow 电商后台管理系统（个人项目）**

基于 Spring Boot 实现的电商后台单体项目，覆盖认证、商品、订单、库存、搜索与秒杀扩展等核心模块。使用 Spring Security + JWT 双 token + Redis refresh 会话管理实现无状态认证与续期；通过 Redis 幂等 token + MySQL 二次校验防止重复下单；采用 Redis Lua 原子预扣库存 + MySQL 最终一致性校验 + 定时对账补偿解决高并发下的库存超卖问题；基于 Elasticsearch 实现商品关键词相关性搜索、价格区间过滤与 MySQL 回退；通过 MinIO 管理商品图片与对象存储资源，并扩展秒杀活动预热与 Lua 抢购链路。
