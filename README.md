1.问题一，前端如何保存session的呀，每一次后端重启什么的，前端重启，直接访问，能直接登录，管理员可能会变成普通用户的界面

2.问题二，管理员怎么能查到自己的信息的呀，在用户管理这边，还能冻结字节的账号吗

3.问题三，后端热门教材是怎么计算的，怎么感觉排行有点问题呀，为啥java也有申领的，却没显示，有预热按钮，但是为啥都不显示库存数量呀，
都不确定库存数量是否还有，或者多少，怎么预热，还有很多教材只能通过isbm查询才能预热

4.管理员界面没有退出登陆的按钮

5.后端返回给前端错了，操了，状态码怎么随便给前端返回呀，a00001是参数校验错误，搞得前端报400，md


「最初我用 Redis pending key 做异步凭证，但发现 TTL 过期与人工重试存在竞态。后来改为以数据库状态机为唯一真相源，
PROCESSING 状态既代表『MQ 待消费』，
又天然作为幂等凭证，彻底消除了 Redis 与 DB 的状态不一致风险。」

详情里面没有版次，适用年级，适合专业

个人中心的路由坏掉了吗

# 📚 Shelf — 校园教材共享循环平台

> 基于微服务架构的校园教材捐赠、申领、借阅与归还一体化系统。
> 让闲置教材流动起来，降低学生购书成本，实现资源循环利用。

---

## 一、项目简介

**Shelf** 是一个面向高校学生的教材共享平台，核心解决以下痛点：

- **教材闲置浪费**：毕业生教材丢弃或低价出售，价值未充分释放
- **购书成本高**：新生每学期购买教材花费数百至上千元
- **信息不对称**：学生不知道哪些教材可以借、去哪里借

平台通过 **"捐赠 → 入库 → 按课程检索 → 申领借阅 → 到期归还"** 的闭环，让教材在校园内高效循环。

---

## 二、技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端 (Web/App)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│              Gateway (shelf-gateway:8080)                   │
│  ├─ 统一鉴权 (Sa-Token)                                    │
│  ├─ 限流防护 (Sentinel)                                    │
│  ├─ 用户上下文透传 (X-User-Id / X-User-Role)               │
│  └─ 路由转发 (/api/auth→user, /api/borrow→borrow...)      │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌────────▼────────┐  ┌───── ▼──────┐
│  shelf-user  │  │  shelf-donate   │  │ shelf-borrow│
│   (8101)     │  │    (8102)       │  │   (8103)    │
└──────────────┘  └─────────────────┘  └─────────────┘
        │                  │                  │
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌────────▼────────┐  ┌─────▼──────┐
│   MySQL      │  │     Redis       │  │  RabbitMQ   │
│  (业务数据)   │  │  (缓存/锁/排行)  │  │  (异步解耦)  │
└──────────────┘  └─────────────────┘  └─────────────┘
```

### 2.2 技术栈

| 层级 | 技术选型 |
|---|---|
| **网关层** | Spring Cloud Gateway + Sa-Token (Reactive) |
| **服务框架** | Spring Boot 3.x + Spring Cloud OpenFeign |
| **ORM** | MyBatis-Plus |
| **认证鉴权** | Sa-Token（Token 模式：UUID，有效期 24h） |
| **服务注册** | Nacos Discovery |
| **限流熔断** | Alibaba Sentinel |
| **消息队列** | RabbitMQ（手动 ACK + 死信队列） |
| **缓存** | Redis（库存计数、热门排行、分布式锁） |
| **分布式锁** | Redisson |
| **对象存储** | 阿里云 OSS |
| **数据库** | MySQL 8.0（InnoDB + utf8mb4） |
| **工具库** | Hutool、Lombok、Fastjson2 |

---

## 三、项目结构

```
shelf/
├── shelf-gateway/          # 网关层：统一入口、鉴权、路由、上下文透传
├── shelf-user/             # 用户中心：注册/登录、用户管理、借阅额度、幂等操作
├── shelf-donate/           # 捐赠中心：捐赠提交、异步入库、库存管理、课程目录
├── shelf-borrow/           # 借阅中心：申领、领取、归还、定时任务、死信处理
├── shelf-admin/            # 管理后台：数据看板、逾期/死信管理、库存校准、热门统计
├── shelf-common/           # 公共包：统一响应、错误码、异常处理、上下文、OSS
└── shelf-api/              # API 契约包：Feign 接口、DTO、FallbackFactory
```

---

## 四、核心业务流程

### 4.1 捐赠入库流程（异步解耦）

```
前端提交捐赠
    │
    ▼
保存 PENDING 记录 ──→ 发送 MQ (donate.inbound.queue)
    │                      │
    │                      ▼
    │              异步消费：生成实体书编码（分布式锁）
    │                      │
    │              ├─ 创建 book_instance（AVAILABLE）
    │              ├─ 创建/更新 book_sku（乐观锁）
    │              ├─ 回填 donate_record（ACCEPTED）
    │              └─ Redis 预热库存
    │
    ▼
返回"提交成功，等待审核入库"
```

### 4.2 申领流程（双路径架构）

```
用户提交申领
    │
    ▼
准入校验（冻结？上限5本？重复ISBN？）
    │
    ▼
判断是否为热门教材（Redis 是否存在 stock:{isbn}）
    │
    ├─ 是（热门）────────────────────────────┐
    │   同步占额度 → Redis 预扣库存           │
    │   → 写 Redis pending（TTL=1天）        │
    │   → 发 MQ（borrow.apply.queue）         │
    │   → 返回 PROCESSING                    │
    │                                         │
    │   MQ 消费：扣 DB 库存 → 生成借阅记录    │
    │   → 删 pending → 热门排行 +1           │
    │                                         │
    └─ 否（非热门）───────────────────────────┘
        同步扣 DB 库存（FIFO 分配实体书）
        → 同步占额度
        → 直接生成 borrow_record（PENDING_PICKUP）
        → 返回完整记录
```

### 4.3 归还流程

```
管理员扫码归还 / 用户自助归还
    │
    ▼
校验状态（BORROWED / OVERDUE）
    │
    ▼
调 donate 恢复 DB 库存（returnStock）
    │
    ▼
本地实体书改 AVAILABLE
    │
    ▼
Redis 库存回滚（如存在）
    │
    ▼
释放用户额度（decreaseBorrowCount）
    │
    ▼
更新 borrow_record → RETURNED
```

---

## 五、核心设计亮点

### 5.1 全链路幂等设计

| 场景 | 幂等机制 |
|---|---|
| **前端重复提交** | `request_id` + 数据库唯一索引（`uk_request_id`） |
| **MQ 消费重复** | 消费前检查记录状态，已处理则直接 ACK |
| **Feign 重试导致重复扣减** | `idempotent_record` 表（`uk_request_biz`） |
| **定时任务重复执行** | Redis 分布式锁（`setIfAbsent` + TTL） |

### 5.2 熔断降级与故障安全

- **Sentinel 熔断**：`borrow` 调用 `donate` 扣库存时，异常比例 ≥ 50% 自动熔断 30 秒，防止 donate 服务雪崩
- **Fail-Safe 降级**：`UserFeignApiFallbackFactory` 在 user 服务不可用时，返回 `frozen=true` + `currentBorrowCount=999`，宁可拒绝所有申领也不允许数据混乱

### 5.3 死信兜底机制

```
MQ 消费失败
    │
    ▼
basicNack(requeue=false) → 进入死信队列
    │
    ▼
死信消费者持久化到 dead_letter_record
    │
    ▼
管理员后台查看 → 标记解决 / 一键重试
```

- 死信重试：自动重新走"占额度 → 扣库存 → 分配实体书 → 生成记录"完整流程
- 补偿告警：`borrow:pending:*` 超过 2 小时未处理自动告警，提醒管理员关注

### 5.4 库存一致性保障

| 机制 | 说明 |
|---|---|
| **乐观锁** | `book_sku` 表 `version` 字段，更新时 `WHERE version = oldVersion` |
| **FIFO 分配** | `SELECT ... ORDER BY create_time ASC LIMIT 1 FOR UPDATE`，保证先捐先借 |
| **分布式锁** | Redisson 按月加锁生成实体书编码，保证唯一且重启不丢号 |
| **库存校准** | 管理员可手动校准：统计实际 AVAILABLE 实体书数 → 修正 SKU → 同步 Redis |
| **状态机校验** | 释放/归还前检查实体书状态，防止重复操作 |

### 5.5 热门教材双路径优化

| 维度 | 热门路径 | 非热门路径 |
|---|---|---|
| **触发条件** | Redis 存在 `stock:{isbn}` | Redis 无该 ISBN |
| **库存扣减** | Redis 预扣（O(1)） | DB 乐观锁扣减 |
| **响应方式** | 异步 MQ，返回 PROCESSING | 同步事务，返回完整记录 |
| **适用场景** | 高频申领，削峰填谷 | 低频申领，简单可靠 |

### 5.6 空间换时间：日聚合表

- **`hot_book_daily`**：每天只保留"每个 ISBN 当天的申领次数"
- 数据量：50 种/天 × 730 天 ≈ **3.6 万条**（MySQL 小表，毫秒级查询）
- 实时榜：Redis `ZSet` 聚合近 7 天数据
- 历史回溯：直接查预聚合表，避免扫描百万级 `borrow_record` 明细

### 5.7 统一错误码体系（A-BB-CCC）

```
A00000  成功
A00001  参数校验失败
A01001  用户名/学号已存在      (用户域 01)
A02003  库存不足              (库存域 02)
A03001  借阅数量已达上限       (借阅域 03)
B00001  系统内部错误
C06001  OSS 上传失败
```

---

## 六、数据库设计要点

### 6.1 核心表结构

| 表名 | 职责 | 关键设计 |
|---|---|---|
| `sys_user` | 用户基础信息 | 逻辑删除、学院-专业-年级联合索引 |
| `user_borrow_quota` | 借阅额度 | 以 `user_id` 为主键，1:1 关联用户 |
| `course_book` | 预置课程-教材目录 | 年级 → 专业 → 课程 → 教材 四级级联 |
| `book_sku` | ISBN 逻辑库存 | `uk_isbn`、乐观锁 `version`、版次字段 |
| `book_instance` | 实体书 | `uk_instance_code`、FIFO 索引、状态机 |
| `donate_record` | 捐赠记录 | `uk_request_id` 幂等、状态流转 |
| `borrow_record` | 借阅记录 | `uk_record_no` + `uk_request_id`、覆盖索引优化统计 |
| `dead_letter_record` | 死信记录 | `idx_type_status` 管理端筛选、JSON 上下文快照 |
| `idempotent_record` | 幂等操作日志 | `uk_request_biz` 防 Feign 重试重复扣减 |
| `hot_book_daily` | 日聚合统计 | 空间换时间，支持近7天实时榜 + 历史回溯 |

### 6.2 索引优化策略

- **覆盖索引**：`borrow_record` 的 `(create_time, isbn)` 索引，支持日期范围查询 + 分组统计不回表
- **定时任务专用索引**：`(status, pickup_deadline)` 超时释放扫描、`(status, due_date)` 逾期扫描
- **业务查询索引**：`(user_id, status)` 我的借阅、`(instance_id, status)` 归还时查记录

---

## 七、快速开始

### 7.1 环境依赖

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.10+
- Nacos 2.2+

### 7.2 数据库初始化

```bash
mysql -u root -p < init.sql
```

### 7.3 配置文件

各模块 `application.yml` 中需配置以下环境变量：

| 变量 | 说明 | 示例 |
|---|---|---|
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://localhost:3306/shelf` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `your_password` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `NACOS_ADDR` | Nacos 地址 | `localhost:8848` |
| `RABBIT_HOST` | RabbitMQ 地址 | `localhost` |
| `RABBIT_USER` | RabbitMQ 用户名 | `shareshelf` |
| `RABBIT_PASSWORD` | RabbitMQ 密码 | `your_password` |
| `OSS_ENDPOINT` | 阿里云 OSS 端点 | `oss-cn-hangzhou.aliyuncs.com` |
| `OSS_ACCESS_KEY_ID` | OSS AccessKey | `your_key` |
| `OSS_ACCESS_KEY_SECRET` | OSS Secret | `your_secret` |
| `OSS_BUCKET_NAME` | OSS Bucket | `shelf-images` |

### 7.4 启动顺序

```bash
# 1. 基础设施
nacos-server
redis-server
rabbitmq-server

# 2. 网关
java -jar shelf-gateway/target/shelf-gateway.jar

# 3. 业务服务（无严格顺序，建议按依赖关系）
java -jar shelf-user/target/shelf-user.jar      # 8101
java -jar shelf-donate/target/shelf-donate.jar  # 8102
java -jar shelf-borrow/target/shelf-borrow.jar  # 8103
java -jar shelf-admin/target/shelf-admin.jar    # 8104
```

---

## 八、模块职责速查

| 模块 | 核心职责 | 对外暴露 |
|---|---|---|
| **gateway** | 统一入口、Sa-Token 鉴权、Sentinel 限流、用户上下文透传 | `8080` |
| **user** | 注册/登录、用户资料、借阅额度（增/减/逾期）、冻结/解冻 | `8101` |
| **donate** | 捐赠提交、异步入库、库存扣减/释放/归还、课程目录、图片上传 | `8102` |
| **borrow** | 申领（双路径）、领取确认、归还、超时释放/逾期扫描/到期提醒 | `8103` |
| **admin** | 数据看板、逾期/死信管理、用户管控、库存校准、扫码领还、热门统计 | `8104` |
| **common** | 统一响应、错误码、全局异常、用户上下文、OSS 模板、MQ 配置 | 被依赖 |
| **api** | Feign 接口定义、DTO、FallbackFactory | 被依赖 |

---

## 九、待办与演进方向

- [ ] 前端 Web 端实现（Vue3 / React）
- [ ] 微信小程序端（扫码领还、消息提醒）
- [ ] 引入 Seata 分布式事务，替代当前补偿式事务
- [ ] 热门教材自动预热策略（基于近7天排行自动加载到 Redis）
- [ ] 引入 Elasticsearch 实现教材全文检索
- [ ] 捐赠审核流程优化（AI 图像识别自动验书）

---

## License

MIT License

> 本项目为校园开源项目，欢迎 Fork 和 PR。
