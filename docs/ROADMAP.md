# 邻里汇 · 城区互助 — 扩展开发规划

> **版本**：v1.0
> **日期**：2026-09-11
> **依据**：仓库基线 `e2aaf0a "First commit"`（2026-09-11）全量代码走查
> **范围**：工程基建规划、功能扩展规划、未来业务拓展需求

---

## 目录

- [1. 项目现状](#1-项目现状)
- [2. 问题清单](#2-问题清单)
- [3. 工程与功能扩展规划](#3-工程与功能扩展规划)
- [4. 未来业务拓展需求](#4-未来业务拓展需求)
- [5. 里程碑与排期](#5-里程碑与排期)
- [6. 技术规范约定](#6-技术规范约定)
- [7. 风险与取舍](#7-风险与取舍)
- [8. 待确认问题](#8-待确认问题)
- [9. 附录：优化实施记录（2026-09-12）](#9-附录优化实施记录2026-09-12)
- [10. 架构调整记录：Eureka → Nacos + Feign 跨服务调用](#10-架构调整记录eureka--nacos--feign-跨服务调用2026-09-12)
- [11. Redis 接入与会话治理](#11-redis-接入与会话治理2026-09-12)
- [12. 技术选型与工具引入路线](#12-技术选型与工具引入路线)
- [13. 信息架构重构记录：五大模块 + 帖子分类 + 关系链（2026-09-13）](#13-信息架构重构记录五大模块--帖子分类--关系链2026-09-13)
- [14. 数据库压力排查与治理（2026-09-13）](#14-数据库压力排查与治理2026-09-13)

---

## 1. 项目现状

### 1.1 技术栈

**后端**

| 组件 | 版本 / 选型 | 说明 |
|------|-------------|------|
| Spring Boot | 3.3.5 | `spring-boot-starter-parent` |
| Spring Cloud | 2023.0.3 | 与 Boot 3.3 匹配的 release train |
| Java | 17 | `<java.version>17</java.version>` |
| Spring Cloud Alibaba | 2023.0.3.2 | Nacos 服务发现，配 Spring Cloud 2023.0.3 |
| 注册中心 | **Nacos（192.168.198.139:8848）** | 见第 10 章；原 Netflix Eureka 已移除 |
| 服务间调用 | **OpenFeign + LoadBalancer** | 契约集中在 `api` 模块，见第 10 章 |
| 网关 | Spring Cloud Gateway | 响应式，非 Zuul |
| 持久层 | Spring Data JPA / Hibernate | `ddl-auto: validate`（建表交给 Flyway） |
| 数据库 | MySQL 8 | `192.168.198.139:3306` / 库名 `LinLiHui` |
| 数据库迁移 | Flyway | 各服务独立迁移脚本与历史表 |
| 缓存 / 分布式状态 | **Redis 7.2** | `192.168.198.139:6379`；验证码、令牌吊销、限流、热点缓存，见第 11 章 |
| 鉴权 | JJWT 0.12.6 | HS256 对称签名 |
| 工具库 | Lombok 1.18.36 | `@Getter/@Setter/@Builder/@RequiredArgsConstructor` |
| 构建 | Maven 多模块 | `huzhu-backend` 聚合 POM |

**前端**

| 组件 | 版本 |
|------|------|
| Vue | ^3.5.12（`<script setup>` 组合式 API） |
| Vite | ^5.4.10 |
| Vue Router | ^4.4.5（`createWebHistory`） |
| Pinia | ^2.2.4 |
| Axios | ^1.7.7 |
| @vitejs/plugin-vue | ^5.1.4 |

**未引入**：消息队列、API 文档工具、前端 UI 组件库、TypeScript、容器化与 CI。
**已接入**：Nacos 服务发现、OpenFeign、Redis、MinIO（`file-service` :8085）、Sentinel（Feign 熔断 + 网关限流）。

### 1.2 服务拓扑

```
                    浏览器 http://localhost:5173  (Vite dev server)
                                  │  /api/** 代理
                                  ▼
                    ┌──────────────────────────┐
                    │   Gateway :8080          │  仅做路由 + CORS，无鉴权/限流/熔断
                    └───┬───┬───┬───┬───┬───┬──┘
        Path 前缀        │   │   │   │   │   │
   /api/auth/**          │   │   │   │   │   │   /api/ad/**
   /api/admin/**         │   │   │   │   │   │   /api/notify/**
   /api/user/**          │   │   │   │   │   │   /api/file/**
                         ▼   ▼   ▼   ▼   ▼   ▼
                      auth  aid comm  ad notify file
                     :8081 :8082 :8083 :8084 :8085 :8086
                         │   │   │   │   │   │
                         └───┴───┴───┴───┴───┘
                                     ▼
                    MySQL 192.168.198.139:3306 / LinLiHui
                    Redis 192.168.198.139:6379（验证码 / 吊销 / 限流 / 缓存）

                    Nacos 192.168.198.139:8848（服务发现）
                    ↑ 六个业务进程全部注册于此，网关通过 lb:// 服务名转发

    服务间调用（OpenFeign，见第 10 章）：
      aid / community ──► auth-service      补全用户昵称与头像、关系链（关注/拉黑）互斥集
      aid / community / auth ──► notify     站内通知投递（接单、评论、关注、评价）
      auth ──► aid / community / ad         用户主页与管理台看板聚合
```

**关键事实（已于第 10 章更新）**：业务服务通过 `api` 模块中定义的 Feign 客户端互相调用。
跨服务调用一律失败即降级，冗余字段（`publisher_name`、`author_name`）退化为「下游不可用时的兜底」，
不再是主数据来源；头像也不再硬编码第三方图床地址。

**`file-service`（:8086）与 `notify-service`（:8085）为第 13 批新增**：
前者提供本地磁盘对象存取（`/api/file/upload`、`/api/file/objects/**`），是「带图内容」落地的前置条件；
后者是站内通知中心，把原先散落在各服务里「顺手写一条通知」的逻辑收成一条投递链路。

### 1.3 数据模型

**19 张表**（基线 11 张 + 第 13 批新增 8 张），统一前缀 `u_r_`：

| 表名 | 归属服务 | 说明 |
|------|----------|------|
| `u_r_sys_user` | auth | 用户主表，手机号唯一 |
| `u_r_user_rating` | auth | 用户间评分，`(target_user_id, rater_id)` 唯一 |
| `u_r_user_follow` | auth | **关注关系**，`(follower_id, followee_id)` 唯一 |
| `u_r_user_block` | auth | **拉黑关系**，`(blocker_id, blocked_id)` 唯一；拉黑同时解除互相关注 |
| `u_r_aid_request` | aid | 求助单，冗余 `publisher_name` / `helper_name` |
| `u_r_aid_rating` | aid | 求助维度评分，`(aid_id, rater_id)` 唯一 |
| `u_r_aid_helper_review` | aid | 求助方对帮助方的评价，`aid_id` 唯一，含文字内容 |
| `u_r_post` | community | 帖子，含 `kind`（10 类）/ `channel` / `like_count` / `view_count` / `heat_score` |
| `u_r_post_like` | community | 点赞，`(post_id, user_id)` 唯一 |
| `u_r_post_view` | community | 浏览去重，`(post_id, user_id)` 唯一 |
| `u_r_post_comment` | community | 评论 |
| `u_r_plaza_hot` | community | 广场每日热度榜，`(rank_date, rank_no)` 唯一 |
| `u_r_goods` | community | **集市**闲置物品，含 `status` / `contact` / `contact_type` |
| `u_r_circle` | community | **圈子**，含 `owner_id` / `status` / `member_count` |
| `u_r_circle_member` | community | 圈成员，`(circle_id, user_id)` 唯一，含 `role` |
| `u_r_circle_post` | community | 圈内帖子，关联 `circle_id` + 复用 `u_r_post` |
| `u_r_recycle_order` | community | **回收**预约单，含 `slot` / `status` / `address` |
| `u_r_notification` | notify | **站内通知**，含 `type` / `read` / `link`（落库的相对跳转路径） |
| `u_r_ad` | ad | 广告位，含 `click_count` |

> **不需要建表的字典**：`RecycleCategory`（回收品类）与 `RecycleSlot`（上门时段）是代码内常量，
> 因为它们由业务规则决定、不需要运营后台维护，落库反而多一次查询和一套管理界面。

**枚举字典**

| 枚举 | 取值 |
|------|------|
| `AidStatus` | `OPEN` / `ACCEPTED` / `DONE` / `CANCELLED` |
| `AidBoard` | `NEIGHBORHOOD` / `CAMPUS` |
| `PostChannel`（**存储层**） | `COMMUNITY` / `PLAZA` / `CAMPUS` |
| `PostModule`（**展示层**，第 13 批新增） | `DISCOVER` = `COMMUNITY` + `PLAZA` / `CAMPUS` |
| `PostKind`（10 类，第 13 批新增） | 发现：`DAILY` `MARKET` `GROUP_BUY` `EVENT` `ASK` / 校园：`DAILY` `CHAT` `RANT` `CONFESS` `PARTNER` `LOST_FOUND`（`DAILY` 为两模块共用，故枚举共 10 个值） |
| `GoodsStatus` | `ON_SALE` 在售 / `RESERVED` 已预定 / `SOLD` 已售出 / `OFF` 已下架 |
| `GoodsContactType` | `WECHAT` / `PHONE` / `QQ` / `OTHER` |
| `CircleStatus` | `ACTIVE` / `CLOSED` |
| `CircleMemberRole` | `OWNER` / `MEMBER` |
| `RecycleCategory`（代码内字典） | `PAPER` `PLASTIC` `METAL` `CLOTHES` `APPLIANCE` `OTHER`，各带基准单价、单位与说明 |
| `RecycleSlot`（代码内字典） | `MORNING` `09:00-12:00` / `AFTERNOON` `14:00-17:00` / `EVENING` `18:00-21:00` |
| `RecycleStatus` | `PENDING` / `CONFIRMED` / `DONE` / `CANCELLED` |
| `NotificationType` | `AID_ACCEPTED` `AID_COMPLETED` `AID_RATED` `POST_COMMENTED` `POST_LIKED` `FOLLOWED` `SYSTEM` |
| `PresenceStatus` | `ONLINE` / `BUSY` / `AWAY` / `STUDYING` / `OFFLINE` |
| `Gender` | `UNKNOWN` / `MALE` / `FEMALE` |
| `RoleType` | `USER` / `ADMIN` |

> **`PostChannel` 与 `PostModule` 的关系是本批最重要的一个取舍**：没有把 `PLAZA` 从
> `PostChannel` 枚举里删掉再改历史数据，而是在**查询层**引入 `PostModule` 做聚合。
> 理由是 `PostChannel` 是 `ENUM` 列，删值需要一次不可回滚的数据迁移；而「广场并入发现」
> 本质是**导航结构**的变化，不是**数据语义**的变化。见第 13.3 节。

### 1.4 功能清单

**已实现**

| 模块 | 能力 |
|------|------|
| 账号 | 手机号注册、密码登录、短信验证码登录、图形验证码、JWT 双令牌、刷新、登出、内部登录接口 |
| 用户 | 个人资料（昵称/真名/头像/简介/性别/城市/小区/学校/专业/年级/学生标记/微信号/隐私账户/在线状态）、公开主页、用户间评分 |
| **关系链** | **关注 / 取关 / 拉黑 / 取消拉黑、关注列表、粉丝列表、黑名单列表、关系查询；拉黑后双向内容互斥（信息流与评论过滤）** |
| 求助 | 发布、编辑、删除、列表（按板块/状态/分类/学生筛选、分页）、详情、接单、标记完成、取消、求助维度评分、求助方评价帮助方 |
| 社区 | 发帖、编辑、删帖、点赞、评论、评论删除、浏览去重、频道（社区/广场/校园）、**帖子类型（10 类，按模块校验）**、分页列表、按作者查询 |
| **集市** | **闲置物品发布/编辑/上下架/删除、按分类与状态筛选、详情（联系方式仅详情可见）** |
| **圈子** | **建圈、入圈/退圈、关圈、圈内帖子列表与发帖、我的圈子** |
| **回收** | **回收品类字典、上门时段字典、预约下单、我的预约、取消预约** |
| **通知** | **站内通知列表、未读数、单条已读、全部已读、删除；接单/完成/评分/点赞/评论/关注/入圈等事件自动投递** |
| **文件** | **图片上传（含扩展名与文件头双重校验）、对象读取、对象删除** |
| 广场 | 每日 6:00 定时生成热度榜（评论数优先 + 点赞数兜底），最多 16 条 |
| 广告 | 轮播查询、列表、增删改、点击计数 |
| 管理 | `/api/admin/dashboard`（欢迎语 + 用户总数）、平台看板聚合（`/internal/*/platform-stats`） |

**前端页面**（`frontend/src/views`，共 16 个）

| 模块 | 页面 |
|------|------|
| 发现 | `DiscoverView`（生活广场已并入，`?tab=plaza` 切热度榜，`?post=` 定位） |
| 邻里 | `AidsView`（列表） `AidCreateView` `AidDetailView` |
| 校园 | `CampusView`（含失物招领分区，`?post=` 定位） |
| 我的 | `MeView` `ProfileEditView` `RelationListView`（关注/粉丝/黑名单三态复用） `UserHomeView` |
| 消息 | `MessagesView` |
| 生活业务 | `MarketView` `CircleView` `RecycleView` |
| 账号 / 管理 | `LoginView` `RegisterView` `AdminView` |

> 原 `HomeView` / `CommunityView` / `PlazaView` 三个页面在第 13 批重构后**已无路由可达**
> （`/`、`/community`、`/plaza` 全部重定向到发现），文件已删除——留着它们只会让后来者改错文件。

**前端组件**（`frontend/src/components`，共 18 个）

`AdCarousel` `AidCard` `CaptchaCanvas` `CommentThread` `FollowButton` `HeartLike` `HeatBadge`
`HotRank` `ImageGallery` `ImageUploader` `PostAuthor` `PostFeed` `SkeletonList` `StarRating`
`StateBlock` `StatusTag` `StudentZone` `UserAvatar`

> `PostFeed` 的 `categoryKey` prop 让同一个信息流组件在发现页显示 `kindLabel`、
> 在个人主页显示 `category`，避免为了「同一份数据、两种标签」再复制一个组件。

### 1.5 接口清单（现有）

**auth-service — `/api/auth`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/captcha` | 生成图形验证码（Redis 存储，TTL 2 分钟） |
| POST | `/captcha/verify` | 校验图形验证码 |
| POST | `/sms/send` | 发送短信验证码（IP + 手机号双维度限流） |
| POST | `/register` | 注册 |
| POST | `/login/password` | 密码登录（按 IP 与手机号限流） |
| POST | `/login/sms` | 短信登录 |
| POST | `/login/internal` | 内部登录（供 E2E 脚本使用） |
| POST | `/refresh` | 刷新令牌（校验会话 `sid` 是否已吊销） |
| GET | `/me` | 当前用户 |
| POST | `/logout` | 登出（吊销会话；可要求退出全部设备） |

**auth-service — `/api/user`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/home` | 首页欢迎 |
| GET | `/profile` | 本人资料 |
| GET | `/{id}` | 公开主页（受隐私账户限制） |
| GET | `/{id}/home` | 他人主页聚合（资料 + 求助统计 + 帖子统计） |
| PUT | `/{id}/rating` | 评价用户 |
| PUT | `/profile` | 更新资料 |
| POST / DELETE | `/{id}/follow` | 关注 / 取关 |
| POST / DELETE | `/{id}/block` | 拉黑 / 取消拉黑（同时解除互相关注） |
| GET | `/{id}/relation` | 当前用户对该用户的关系（是否已关注/被关注/已拉黑） |
| GET | `/following` | 我关注的人 |
| GET | `/followers` | 关注我的人 |
| GET | `/blocked` | 我的黑名单 |

**auth-service — `/api/admin`**：`GET /dashboard`

**auth-service — `/internal/user`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/brief` | 批量用户摘要（昵称/头像） |
| GET | `/{id}/brief` | 单个用户摘要 |
| GET | `/exclusions` | **当前用户的互斥集（拉黑 + 拉黑我的），供下游过滤内容** |

**aid-service — `/api/aid`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 列表，支持 `status`/`category`/`student`/`board` + 分页 |
| GET | `/stats` | 统计（待接单/进行中/已完成/我发布/我帮助） |
| GET | `/mine/published` | 我发布的 |
| GET | `/mine/helping` | 我帮忙的 |
| GET | `/user/{userId}/published` | 某用户发布的 |
| GET | `/{id}` | 详情 |
| POST | `/` | 发布 |
| PUT | `/{id}` | 编辑 |
| DELETE | `/{id}` | 删除 |
| POST | `/{id}/accept` | 接单 |
| POST | `/{id}/complete` | 完成 |
| POST | `/{id}/cancel` | 取消 |
| PUT | `/{id}/rating` | 评分 |
| PUT | `/{id}/helper-review` | 评价帮助方 |

**aid-service — `/internal/aid`**：`GET /platform-stats`、`GET /user-stats/{userId}`

**community-service — `/api/community`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/posts` | 帖子列表，支持 `channel`/`kind` + 分页；`channel` 取 `DISCOVER` 时聚合 `COMMUNITY`+`PLAZA` |
| GET | `/post-kinds?module=` | **按模块返回可选帖子类型（`code` / `label` / `hint`），种类定义只存在于后端枚举里** |
| GET | `/posts/{id}` | 帖子详情 |
| GET | `/posts/author/{userId}` | 按作者查询（**已在 SQL 层排除互斥集**） |
| POST | `/posts` | 发帖（**校验 kind 与模块匹配**） |
| PUT | `/posts/{id}` | 编辑 |
| DELETE | `/posts/{id}` | 删除 |
| POST | `/posts/{id}/like` | 点赞/取消点赞 |
| POST | `/posts/{id}/view` | 记录浏览 |
| GET | `/posts/{id}/comments` | 评论列表 |
| POST | `/posts/{id}/comments` | 发评论 |
| DELETE | `/comments/{commentId}` | 删评论 |
| GET | `/plaza/hot` | 广场热度榜 |
| GET | `/stats` | 社区统计 |

**community-service — `/api/market/goods`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 列表，支持 `category`/`status`/关键词 + 分页（**不含联系方式**） |
| GET | `/mine` | 我发布的（**不含联系方式**） |
| GET | `/{id}` | 详情（**唯一返回联系方式的出口**，同时 +1 浏览量） |
| POST | `/` | 发布 |
| PUT | `/{id}` | 编辑 |
| POST | `/{id}/status` | 改状态（在售/预定/已售/下架） |
| DELETE | `/{id}` | 删除 |

**community-service — `/api/circle`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 圈子列表 |
| GET | `/mine` | 我加入/创建的圈 |
| GET | `/{id}` | 圈子详情 |
| POST | `/` | 建圈 |
| POST | `/{id}/join` | 入圈 |
| POST | `/{id}/leave` | 退圈 |
| POST | `/{id}/close` | 关圈（圈主） |
| GET | `/{id}/posts` | 圈内帖子 |
| POST | `/{id}/posts` | 圈内发帖 |

**community-service — `/api/recycle`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/categories` | 回收品类与上门时段字典（**需登录**，见第 13.5 节） |
| GET | `/orders` | 我的回收预约 |
| GET | `/orders/{id}` | 预约详情 |
| POST | `/orders` | 下单预约 |
| POST | `/orders/{id}/cancel` | 取消预约 |

**community-service — `/internal/community`**：`GET /platform-stats`、`GET /user-stats/{userId}`、`GET /recycle/stats`

**notify-service — `/api/notify`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 通知列表（分页），`link` 优先取落库值 |
| GET | `/unread-count` | 未读数（导航角标） |
| POST | `/{id}/read` | 单条已读 |
| POST | `/read-all` | 全部已读 |
| DELETE | `/{id}` | 删除通知 |

**notify-service — `/internal/notify`**：`POST /`（投递）、`GET /unread-count`

**file-service — `/api/file`**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload` | 上传图片（扩展名 + 文件头双重校验） |
| GET | `/objects/**` | 读取对象 |
| DELETE | `/objects/**` | 删除对象 |

**ad-service — `/api/ad`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/carousel` | 轮播（启用中） |
| GET | `/list` | 全部广告 |
| POST | `/` | 新建 |
| PUT | `/{id}` | 更新 |
| DELETE | `/{id}` | 删除 |
| POST | `/{id}/click` | 点击计数 |

### 1.6 关键机制

**统一响应体**（`common/ApiResponse`）

```json
{ "code": 0, "message": "成功", "data": {} }
```

`code != 0` 视为业务失败；前端 `http.js` 响应拦截器据此 reject，`code === 401` 时触发静默刷新。

**鉴权链路**

1. `auth-service` 用 HS256 签发 access token（30 分钟）与 refresh token（7 天）。
2. **六个业务服务**共用同一个对称密钥，`common/JwtAuthFilter` 各自独立解析并构建 `UserPrincipal`（id / phone / nickname / role）；
   令牌吊销状态在 Redis 里，因此任一服务都能独立判断会话是否已被登出。
3. 前端 Axios 请求拦截器注入 `Authorization: Bearer <token>`；401 时用 `refreshTokens()` 单飞（`refreshing` 去重）后重放原请求。

**求助状态机**（`AidService`）

```
        发布              接单                标记完成
  ∅ ──────────► OPEN ──────────► ACCEPTED ──────────► DONE
                 │                                     │
                 │ 发布者取消                            │ 发布者评价帮助方
                 ▼                                     ▼
             CANCELLED                          u_r_aid_helper_review
```

约束（均已在服务层实现）：仅 `OPEN` 可编辑/取消；不能接自己发布的单；仅 `ACCEPTED` 可标记完成，且限发布者或帮助者；`ACCEPTED` 状态禁止发布者删单；帮助方评价仅限发布者、且仅在 `DONE` 后。

**双轨评价**

- 求助维度：`u_r_aid_rating`，任意非发布者用户可评分。
- 用户维度：`u_r_user_rating`，任意非本人用户可评分，聚合出主页平均分。
- 帮助方维度：`u_r_aid_helper_review`，发布者对帮助者的关单评价，含 200 字文字，一单一评。

**广场热度榜**：`PlazaHotService` 每日 6:00 定时任务，取前一日 6:00 至今的广场评论数排名，不足 16 条用点赞数填充；`/plaza/hot` 在榜单为空时同步触发一次刷新。

**演示数据**：各服务自带 `*DataInitializer`——auth（账号）、aid（求助）、community（帖子 / 集市商品 / 圈子 / 回收时段）、ad（广告位），
启动时写入种子数据与演示账号（`13800000000 / Admin@123`、`13900000000 / User@123`）。
初始化器只在表为空时写入，不会覆盖已有数据。

---

## 2. 问题清单

按风险从高到低排列。**"影响"列描述的是不解决会阻塞什么**。

| # | 问题 | 证据 | 影响 | 建议 |
|---|------|------|------|------|
| 1 | 建表策略失控 | 4 份 `application.yml` 均为 `ddl-auto: update`，另有手写 `schema.sql` 未被任何流程执行 | 改实体即静默改表，无法回滚、无法审计；P1 起每项都要动表 | 引入 Flyway，`schema.sql` → `V1__init.sql`，`ddl-auto` 改 `validate` |
| 2 | ~~会话状态在内存~~ **（第 11 章已解决）** | 原：`CaptchaService.store`、`SmsCodeService` 的三张 Map | 多实例部署时验证码校验随机失败；重启丢码 | 已迁移到 Redis，TTL 自动回收 |
| 3 | 密钥与口令入库 | JWT secret、`root/123456` 明文写在 4 份 yml，且 4 个服务共用同一 secret | 拿到仓库即可伪造任意用户令牌 | 改为 `${JWT_SECRET:...}` 环境变量注入，生产强制无默认值 |
| 4 | ~~敏感数据泄露~~ **（初版判断有误，已更正）** | 经 `git ls-files` 核查：`target/`、`.idea/`、`node_modules/` **均未被提交**；`.gitignore` 规则本已生效，全仓库仅 168 个受控文件、`.git` 仅 0.2MB | 无实际泄露。原判断属主观臆测，未做验证 | 无需处理。`.idea/`、`target/` 仅存在于工作区磁盘，属正常状态 |
| 5 | 零测试 | 全模块无 `src/test` 目录 | 求助状态机、JWT 解析、热度榜算法一经改动无从验证 | 先补状态机与令牌解析单测 → **本轮已补 23 个用例** |
| 6 | 无 CI / 无容器化 | 无任何 workflow、Dockerfile、compose | 每次验证需手工在 IDEA 起 6 个进程 | `docker-compose up` 一键起全套 |
| 7 | 网关无防护 | `gateway/application.yml` 只有 routes + CORS，无过滤器链 | 无鉴权、无限流、无熔断；`discovery.locator.enabled: false` 已关（正确） | 加 Resilience4j + Redis 限流 |
| 8 | CORS 白名单写死 | 仅允许 `localhost:5173` / `127.0.0.1:5173` | 部署即失效 | 配置化 |
| 9 | 无文件上传能力 | 头像/帖子图/求助图一律为外链 URL 字符串（如 `RandomAdImages` 用 picsum，`PlazaHotService` 拼 `https://picsum.photos/seed/...`） | 用户无法上传真实图片，所有"带图内容"场景不可做 | 建 `file-service` |
| 10 | 无通知能力 | 全仓库无消息/推送相关表与接口 | 接单、评论、评价均无感知，闭环断在"对方不知道" | 建 `notify-service` |
| 11 | 管理后台近乎空白 | `AdminView.vue` 仅 42 行，展示欢迎语 + 用户数；`AdminController` 仅 1 个接口 | 无人能封禁用户、审核内容、管理广告 | 补齐管理三件套 |
| 12 | ~~服务间零调用 + 字段冗余~~ **（第 10 章已解决）** | 原：无 Feign/WebClient，`publisher_name`/`author_name` 为各表冗余快照 | 用户改昵称后列表显示旧名，头像为硬编码占位图 | 已实现 Feign 实时补全 + 降级兜底；冗余列降级为 fallback |
| 13 | 错误码不统一 | `BizException` 出现 `403`/`404`/`401` 与默认码混用；`http.js` 只对 `401` 特殊处理 | 前端难以按码分支 | 定义错误码表 |
| 14 | 前端无 lint / 无类型 | `package.json` 仅有 dev/build/preview，未配 ESLint、Prettier、TS | 已扩到 **16 个视图 + 18 个组件**，规模下更易腐化 | 加 ESLint + Prettier |
| 15 | 热度榜口径不一致 | `PlazaHotService` 主榜用"近 24 小时广场评论数"（`countPlazaCommentsSince`），不足 16 条时的补位用"全时段评论数"（`countByPostId`），同一榜单混用两种口径；`currentBoard` 还逐条 `findById` 取帖（16 次查询）；`refresh` 每次先 `deleteByRankDate` 再插入，无幂等与并发保护 | 榜单排名可解释性差；并发刷新可能产生空洞 | 统一口径 + 批量取帖 → **本轮已处理前两项**；幂等仍未做 |
| 16 | 前端依赖外网图床 | `frontend/src/utils/webImages.js` 在浏览器端实时 `fetch('https://picsum.photos/v2/list')` 取广告图；`RandomAdImages` 后端同样拼 picsum 地址 | 内网/断网/图床限流时首页广告图退化；生产环境不应依赖第三方图床 | P1-1 文件上传落地后彻底替换 |
| 17 | **内部码登录后门（严重）** | `AuthService.loginByInternal` 把管理员登录口令 `"8461"` 硬编码在源码里，校验通过即签发 ADMIN 令牌；任何看到源码的人都能直接提权 | 等同于源码泄露即管理员沦陷 | 已外置为 `app.internal-login-code`（环境变量 `INTERNAL_LOGIN_CODE`），置空即关闭；生产必须覆盖 |

**本轮新增发现（优化过程中暴露）**

| # | 问题 | 证据 | 影响 |
|---|------|------|------|
| 18 | 枚举列被映射为 MySQL 原生 `ENUM` | Hibernate 6 将 `@Enumerated(STRING)` 生成为 `enum('OPEN','ACCEPTED',...)`；`schema.sql` 却写 `VARCHAR(20)`，与现状不符 | 新增枚举值（如新板块/新频道）必须走 `ALTER TABLE`，且旧 `schema.sql` 不可再作为建表依据（已改为废弃提示） |
| 19 | 板块/频道存在 NULL，导致索引失效 | 真实库中 `u_r_aid_request.board` 5 行 NULL、`u_r_post.channel` 3 行 NULL；查询写成 `board IS NULL OR board = ?` | OR 条件使 `(board, created_at)` 索引无法使用，列表分页退化为全表扫描 + filesort |

---

## 3. 工程与功能扩展规划

### 3.1 P0 — 打地基（约 1 周）

目标：**让后续每一项扩展都不再踩雷**。全部为低风险改造，不动业务逻辑。

| # | 任务 | 具体做法 | 验收标准 |
|---|------|----------|----------|
| P0-1 | 仓库清仓 | `.gitignore` 增补 `target/`、`.idea/`、`*.iml`、`dist/`；`git rm -r --cached` 移除已入库的构建产物与 IDE 配置 | `git status` 干净，仓库体积显著下降 |
| P0-2 | 密钥外置 | 4 份 yml 改为 `${JWT_SECRET:dev-only-secret}`、`${DB_URL:...}`、`${DB_USERNAME:root}`、`${DB_PASSWORD:...}`；生产 profile 去掉默认值 | 无环境变量时本地仍可启动；`grep` 不到明文密钥 |
| P0-3 | Flyway 接管建表 | `schema.sql` 转为 `V1__init.sql` 放入 `common` 或各服务 `db/migration`；`ddl-auto: validate` | 空库启动自动建表；实体与表不一致时启动报错 |
| P0-4 | 状态外移 | ~~引入 Redis~~ **已完成（第 11 章）**：验证码、短信码、令牌吊销、限流、热点缓存全部落到 Redis `192.168.198.139:6379` | 起两个 auth-service 实例，验证码仍可校验 |
| P0-5 | 测试骨架 | 状态机单测（`OPEN→ACCEPTED→DONE`、非法跃迁、越权操作）；JWT 签发/解析/过期；热度榜排序 | `mvn test` 通过，覆盖率不是目标，关键路径覆盖即可 |
| P0-6 | 一键启动 | `docker-compose.yml`：`mysql` + `redis` + **6 个业务服务**；服务 `depends_on` + `/actuator/health` 健康检查（Nacos 为外部依赖，不在 compose 内） | `docker-compose up` 后前端可登录并使用全部现有功能 |
| P0-7 | 文档补全 | README 增补接口一览、错误码表、环境变量表；`docs/ROADMAP.md`（本文）入库 | 新人可仅凭文档启动并调通一个接口 |

> **建议**：若只能做一件事，做 **P0-3**。P1 的每一项都会动表结构，`ddl-auto: update` 在有真实数据后再改代价高得多。

### 3.2 P1 — 补核心闭环（约 3 周）

目标：**让一个陌生人能完整走完"求助 → 联系 → 完成 → 评价 → 通知"**。当前闭环断在"没有通知"和"没有沟通渠道"。

| 优先级 | 任务 | 关键设计 | 依赖 |
|--------|------|----------|------|
| P1-1 ★★★ | **文件上传服务** | 新建 `file-service`（:8085）；本地磁盘或 MinIO；`POST /api/file/upload` 返回 URL；限制类型（png/jpg/webp）与大小（≤5MB）；`u_r_sys_user.avatar`、帖子图、求助图统一走它 | P0-3 |
| P1-2 ★★★ | **通知中心** | 新建 `notify-service`（:8086）或先在 community-service 内建表；表 `u_r_notification(id, user_id, type, title, content, ref_type, ref_id, read_at, created_at)`；事件源：被接单、被评论、被点赞、被评价、求助完成待评价；接口：列表、未读数、单条已读、全部已读；前端 `AppShell` 加铃铛 + 未读红点 | P1-1（可选头像） |
| P1-3 ★★★ | **站内沟通** | 求助详情页 1v1 留言，表 `u_r_aid_message(aid_id, sender_id, content, created_at)`；先轮询（3~5s）不做 WebSocket；仅发布者与帮助者可收发 | P1-2 |
| P1-4 ★★ | **附近求助** | `u_r_aid_request` 增 `latitude` / `longitude`（DECIMAL(10,7)）；前端 `navigator.geolocation` 取点；列表支持 `lat/lng/radiusKm` 与按距离排序；先用 MySQL 经纬度范围查询（`BETWEEN` + 复合索引），不急着上 PostGIS/ES | P0-3 |
| P1-5 ★★ | **管理台补齐** | 用户列表 + 封禁/解封（`enabled` 字段已存在）；举报表 `u_r_report(target_type, target_id, reason, status, handler_id)` + 审核队列；广告 CRUD 前端页（后端 6 个接口已就绪，纯缺 UI）；数据看板（复用各服务 `/stats`） | — |
| P1-6 ★ | **图片化内容** | 帖子/求助支持配图（`u_r_post`、`u_r_aid_request` 增 `images` JSON 或独立附表）；替换现网 picsum 占位图 | P1-1 |
| P1-7 ★ | **错误码与日志规范落地** | 定义 `BizCode` 枚举，统一 `ApiResponse.code` 语义；前端按码分支 | — |

### 3.3 P2 — 平台化（约 1–2 月）

| # | 任务 | 说明 |
|---|------|------|
| P2-1 | **信用与激励体系** | 现有两套评分（`u_r_user_rating`、`u_r_aid_helper_review`）聚合出信用分；引入等级（如 Lv1–Lv5）、勋章（首次助人/助人 10 次/五星好评）；接单门槛与列表排序吃信用分；`u_r_sys_user` 增 `credit_score` / `level` / `help_count` 冗余字段（**由事件同步而非实时聚合**） |
| P2-2 | **搜索** | 统一 `/api/search`；先 MySQL 索引 + `LIKE`，量级上来再引 ES；覆盖求助、帖子、用户 |
| P2-3 | **关注与关系链** | 表 `u_r_follow(follower_id, followee_id)`；关注流（关注的人发布的求助/帖子优先） |
| P2-4 | **网关加固** | Resilience4j 熔断 + 超时；Redis 令牌桶限流（按 IP / 按用户）；统一在网关做鉴权前置校验，业务服务仅做兜底 |
| P2-5 | **跨服务调用规范** | ~~二选一~~ **已选定（第 10 章）：OpenFeign 实时查询 + 冗余字段兜底**，契约集中在 `api` 模块。后续新增跨服务调用一律沿用该模式 |
| P2-6 | **可观测** | Actuator + Micrometer + Prometheus；日志接入 `traceId`（网关生成、透传、各服务打印）；最简方案是先把 `/actuator/health` 接进 compose 健康检查 |
| P2-7 | **配置中心**（按需） | 服务数上 10+ 后再考虑启用 Nacos Config；当前 6 个业务服务用环境变量足够（Nacos 已就位，切换成本低） |
| P2-8 | **前端工程化** | ESLint + Prettier；评估是否引入 TS 与 UI 组件库（当前 `styles.css` 已是 700+ 行的 token 化设计系统，手写 CSS 的规范已经成型） |

---

## 4. 未来业务拓展需求

> 本节是**业务方向池**，不是承诺排期。每条含业务价值、功能点、数据/接口增量与优先级，供后续按资源取舍。
> 优先级标记：★★★ 建议尽快验证 / ★★ 中期价值明确 / ★ 储备方向。

### 4.1 方向一：从"免费互助"到"社区服务撮合"

**业务价值**：免费互助受限于人情与善意，天花板低且难以持续。引入**悬赏**与**技能服务**后，可与本地生活服务形成差异化——邻里小活儿（代取快递、陪诊、遛狗、修水管）比平台化家政更轻、更快、更便宜。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **悬赏互助** | 发布时设定赏金（或"酬谢"意向）；接单后资金托管；完成确认后结算；争议时冻结 | `u_r_aid_request` 增 `reward_amount` / `reward_type`；新增 `u_r_wallet`、`u_r_wallet_tx`、`u_r_escrow` | ★★★ |
| **技能服务** | 用户发布可提供的服务（家教、维修、摄影、代购）；带价目、可预约时段、服务范围 | 新服务 `skill-service`；`u_r_skill`、`u_r_skill_order`、`u_r_skill_schedule` | ★★ |
| **预约排期** | 服务方可设定可服务时间段；下单即锁定时段 | `u_r_skill_slot`，需并发锁（乐观锁 / 唯一索引） | ★★ |
| **拼单拼团** | 邻居拼水果、拼团购、拼车、拼家教；成团后统一履约 | `u_r_groupbuy`、`u_r_groupbuy_member` | ★ |
| **失物招领升级** | 现有校园专区仅 `失物招领` 一个分类且限定 `CAMPUS` 板块；扩展为独立模块（寻物/招领双向、按物品类别、按区域） | `u_r_lostfound`，或放宽 `AidBoard` 分类白名单 | ★★ |

### 4.2 方向二：邻里社交深化

**业务价值**：互助是低频行为，社交是留存的载体。没有日常活跃，求助列表会长期空置。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **私信** | 用户间 1v1 私聊（超出求助单的沟通）；会话列表 + 未读 | `u_r_conversation`、`u_r_message`；WebSocket 或轮询 | ★★★ |
| **邻里圈 / 楼栋群** | 按小区/楼栋聚合的圈子，圈内发帖、公告、成员管理 | `u_r_circle`、`u_r_circle_member`；`u_r_post` 增 `circle_id` | ★★ |
| **社区活动** | 发起线下活动（跳蚤市场、邻里节、公益清洁）；报名、人数上限、签到 | `u_r_event`、`u_r_event_signup` | ★★ |
| **二手闲置** | 邻里二手交易（比闲置平台多了"同小区自提"的天然优势） | `u_r_goods`、`u_r_goods_order`；可与悬赏共钱包 | ★★ |
| **宠物互助** | 代遛、代喂、寄养、寻宠启事 | 复用求助模块 + 新增分类；`u_r_pet` | ★ |
| **动态信息流** | 现有社区/广场/校园三频道是纯时间序；增推荐流（关注 + 热度 + 距离加权） | 改造 `listPosts` 排序；需 P2-2 搜索/排序基建 | ★★ |

### 4.3 方向三：信任与安全体系

**业务价值**：互助平台的核心风险是**人身安全与财产安全**。这是决定平台能否规模化的门槛，也是与陌生人社交平台的关键差异点。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **实名认证** | 身份证/人脸核身；未实名限制发布与接单 | `u_r_sys_user` 增 `verified` / `verified_at`；接三方核身 | ★★★ |
| **信用分体系** | 见 P2-1；叠加履约率、被举报次数、完成率 | `u_r_credit_log`（加分/扣分流水） | ★★★ |
| **内容审核** | 敏感词过滤 + 图片鉴黄 + 人工复审队列；发帖/发求助前置机审 | 接三方内容安全 API；`u_r_audit_log` | ★★★ |
| **举报与申诉** | 用户举报 → 后台处置 → 结果通知；被处置方可申诉 | `u_r_report`、`u_r_appeal` | ★★（P1-5 起步） |
| **位置隐私** | 精确坐标只对已接单方可见；列表展示模糊距离（"500m 内"） | 后端按关系裁剪 `latitude/longitude` 精度 | ★★★（P1-4 同步做） |
| **紧急求助 / 一键报警** | 独居、陪诊等高风险场景；紧急联系人、位置共享、超时未确认自动通知 | `u_r_sos`、`u_r_emergency_contact` | ★★ |
| **行为风控** | 刷单、恶意差评、多账号识别；频次限制 | Redis 计数 + 规则引擎 | ★ |
| **保险对接** | 上门服务的意外险（单次投保） | 接三方保险 API | ★ |

### 4.4 方向四：校园专区深化

**业务价值**：现有 `AidBoard.CAMPUS` 仅支持 `失物招领` 单一分类（`AidService.CAMPUS_CATEGORIES`），而学生群体的高频需求远不止于此，且校园场景封闭、密度高、传播快，是最容易做透的种子场景。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **分类扩容** | 从 `["失物招领"]` 扩到：校园跑腿、代取快递、二手教材、拼车回家、组队自习、失物招领 | 改 `CAMPUS_CATEGORIES` 白名单；前端 `StudentZone.vue` 扩 UI | ★★★（成本极低） |
| **校园跑腿** | 快递代取、食堂带饭；高峰期批量接单 | 复用求助流；增 `deadline` / `reward` 字段 | ★★ |
| **二手教材** | 按课程/专业检索教材；学期初/末高峰 | `u_r_goods` 增 `course` / `isbn` | ★★ |
| **校园认证** | 学信网/校园邮箱认证，隔离校外用户 | 复用实名认证通道，增 `student_verified` | ★★ |
| **社团与活动** | 社团主页、活动报名、招新 | 复用 4.2 活动模块 + `u_r_club` | ★ |

### 4.5 方向五：志愿服务与社区治理（G2C）

**业务价值**：这是"城区互助"区别于商业平台、也最容易获得**街道/社区/民政**支持的方向。公益属性可带来政策资源、场地与资金，同时为平台引流。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **志愿时长** | 志愿服务记录、时长累计、开具证明 | `u_r_volunteer_hour`；与信用分打通 | ★★ |
| **公益组织入驻** | 组织账号、发布志愿项目、招募志愿者 | 角色扩展 `ORGANIZATION`；`u_r_org` | ★★ |
| **重点人群关怀** | 独居老人、残障人士的定期探访；结对帮扶、定期任务提醒 | `u_r_care_pair`、`u_r_care_task` | ★★ |
| **社区公告与议事** | 街道/物业发布公告；居民投票表决（如加装电梯、停车方案） | `u_r_announcement`、`u_r_poll`、`u_r_vote` | ★★ |
| **政务数据对接** | 与街道网格系统、12345 热线对接（数据双向） | 开放 API + 数据脱敏网关 | ★ |
| **积分兑换** | 志愿服务时长兑实物/服务（商家赞助） | 与 4.6 商家体系打通 | ★ |

### 4.6 方向六：商业化

**业务价值**：现有 `u_r_ad` 是最原始的固定轮播位（含 `sort_order`、`click_count`），无计费、无定向、无素材审核。商业化是平台自我造血的路径，但必须在用户规模之后。

| 子模块 | 关键功能 | 数据 / 接口增量 | 优先级 |
|--------|----------|-----------------|--------|
| **本地商家入驻** | 商家认证、店铺主页、发布服务与优惠 | 角色 `MERCHANT`；`u_r_merchant`、`u_r_shop` | ★★ |
| **广告体系升级** | 按位置/人群定向投放、曝光+点击双计费、预算与排期、素材审核 | `u_r_ad` 增 `target_*` / `budget` / `start_at` / `end_at`；`u_r_ad_stat_daily` | ★★ |
| **信息流广告** | 帖子流中插入原生广告（当前仅首页轮播） | 社区列表接口支持混排 | ★ |
| **会员体系** | 会员免广告、求助置顶、优先展示、专属客服 | `u_r_membership`、`u_r_order` | ★ |
| **交易佣金** | 悬赏/技能服务成单抽成 | 依赖 4.1 钱包与托管 | ★ |
| **置顶与推广** | 求助/商品付费置顶 | `u_r_promotion` | ★★（成本低，先试） |
| **社区团购 / 团批** | 对接供应链做邻里团购 | 依赖 4.1 拼单 | ★ |

### 4.7 方向七：数据与智能化

| 子模块 | 关键功能 | 优先级 |
|--------|----------|--------|
| **数据看板** | 求助发布量/完成率/平均响应时长、DAU、留存、板块分布；先落 `u_r_stat_daily` 宽表 | ★★★ |
| **智能匹配** | 基于位置 + 技能标签 + 信用分主动推送"你可能能帮上忙"（现在是纯列表，靠用户自己刷） | ★★ |
| **AI 客服 / 智能问答** | 常见问题自动应答；求助内容自动分类与打标 | ★★ |
| **内容安全 AI** | 见 4.3，与审核体系合并 | ★★★ |
| **需求预测** | 预测某小区某时段的求助热点（如周末家政、开学季教材） | ★ |

### 4.8 方向八：多端与开放能力

| 子模块 | 关键功能 | 优先级 |
|--------|----------|--------|
| **微信小程序** | 邻里场景天然适合小程序（扫码进小区、微信通知触达）；需重写前端或做 uni-app 适配 | ★★★ |
| **移动 App** | iOS / Android；推送、定位、相机能力更完整 | ★★ |
| **管理后台独立化** | 现有 `AdminView.vue` 嵌在用户端路由里，应拆为独立站点（Vue3 + 后台模板） | ★★ |
| **开放平台** | 对第三方/物业/街道开放 API（OAuth2 + 限流 + 审计） | ★ |
| **多城市/多小区隔离** | 当前无任何租户概念，`city` / `neighborhood` 仅是用户资料字段；扩展多城市需引入 `community_id` 维度 | ★★（规模化的前提） |

---

## 5. 里程碑与排期

| 阶段 | 内容 | 预估 | 出口标准 |
|------|------|------|----------|
| **M0** | P0 全部 7 项 | 1 周 | `docker-compose up` 一键启动；Flyway 接管建表；密钥外置；关键路径有测试 |
| **M1** | P1-1 文件上传 + P1-2 通知中心 + P1-3 站内沟通 | 2 周 | 陌生人可完成"求助 → 沟通 → 完成 → 评价 → 收到通知"全链路 |
| **M2** | P1-4 附近求助 + P1-5 管理台 + P1-6 图片化内容 | 1.5 周 | 可按距离发现附近求助；管理员可封禁用户、审核举报、管理广告 |
| **M3** | P2-1 信用体系 + P2-2 搜索 + P2-4 网关加固 | 3 周 | 信用分影响排序与门槛；搜索可用；网关有熔断限流 |
| **M4** | 业务验证：4.4 校园分类扩容 + 4.1 悬赏互助 | 3 周 | 完成一次真实场景的付费闭环验证 |
| **M5** | 4.3 信任安全（实名 + 内容审核）+ 4.6 置顶推广 | 1 月 | 平台具备基础的合规与风控能力 |
| **M6** | 4.8 微信小程序 + 4.7 数据看板 | 1.5 月 | 多端触达；关键指标可视 |

**并行建议**：4.4 校园分类扩容（改一个常量 + 前端 UI）成本极低，建议提前穿插进 M1，作为低成本的用户验证。

---

## 6. 技术规范约定

**必须明确并写入 README 的决策**

1. **跨服务数据一致性策略**：选定「冗余字段 + 事件同步」（推荐）或「Feign 实时查询」。选定前新增功能不得引入第三种做法。
2. **建表变更流程**：一律走 Flyway 迁移脚本，禁止依赖 `ddl-auto`。
3. **响应码规范**：定义 `BizCode` 枚举；`0` 成功、`401` 未认证、`403` 无权限、`404` 不存在、`4xx` 业务校验失败、`5xx` 系统异常。
4. **日志前缀**：沿用现有约定 `[网关] [API] [鉴权] [求助] [社区] [广告]`，新增模块同步申请前缀（`[用户] [文件] [通知]` 已在用或预留）。
5. **新增服务清单**：`file-service` :8085、`notify-service` :8086、后续 `skill-service` :8087、`search-service` :8088。端口须在 README 登记。
6. **前端 API 层约定**：每个后端服务对应 `frontend/src/api/*.js` 一个模块，统一走 `http.js`，不在组件内直接用 axios。

---

## 7. 风险与取舍

| 风险 | 说明 | 应对 |
|------|------|------|
| **微服务过度设计** | 5 个进程 + 外部 Nacos 支撑当前功能量，运维成本高于收益；本地开发需手动起 5 个服务 | P0-6 用 compose 抹平开发成本；**后续（如 skill-service、search-service）新增服务前先论证能否并入现有服务** |
| **数据库共享** | 4 个服务连同一库同一账号，边界形同虚设（`aid-service` 直接读 `u_r_aid_rating`，无跨服务隔离） | 现阶段接受；若拆分，需按服务分库或至少分 schema |
| **单点数据库** | MySQL 单实例，无读写分离、无备份策略 | 上线前补定时备份 + 至少一主一从 |
| **短信通道未落地** | `app.sms.provider: mock`，`AliyunSmsSender` 存在但未启用 | 上线前切换真实通道并联调，注意 `daily-limit: 20`、`send-interval-seconds: 60` 的防刷配置 |
| **付费闭环的合规风险** | 悬赏/佣金涉及资金池、发票、涉众资金监管 | 优先接入持牌支付渠道做资金托管，避免自建钱包沉淀资金 |
| **隐私合规** | 位置、实名、身份证信息属敏感个人信息 | 上线前需完成隐私政策、单独同意、最小化收集、加密存储 |
| **信用分被刷** | 互刷好评可快速提升信用 | 需叠加履约率、举报扣分、设备指纹等反作弊维度 |

---

## 8. 待确认问题

1. **产品定位**：是校园项目（演示/毕设）还是要真实上线运营？——决定 P0 的严格程度与是否需要实名、支付、合规投入。
2. **用户规模目标**：单小区试点 / 单城市 / 多城市？——决定是否必须引入 `community_id` 租户维度（4.8）。
3. **是否走商业化**：若坚持纯公益，4.1 悬赏与 4.6 商业化整体降级，重心转向 4.5 志愿服务与社区治理。
4. **短信通道**：是否已有阿里云短信资质与模板？
5. **域名与部署环境**：是否有服务器/域名/HTTPS 证书？——决定 P0-2 CORS 配置化与部署方案。
6. **前端技术路线**：是否引入 TypeScript 与 UI 组件库？是否规划小程序（若是，是否考虑 uni-app 统一多端）？
7. **付费意愿验证**：悬赏互助（4.1）与拼单（4.1）哪个先做？

---

## 9. 附录：优化实施记录（2026-09-12）

本轮落实了「性能与代码质量」+「工程基建 P0」两部分。**所有改动均已通过编译、单元测试与真机端到端验证。**

### 9.1 性能优化

| 项 | 改动前 | 改动后 | 验证方式 |
|----|--------|--------|----------|
| 求助列表 N+1 | 每条求助 4 次查询（评分均值、评分条数、我的评分、帮助方评价），一页 10 条 = **41 次查询** | 固定 **4 次查询**（1 次分页 + 3 次批量装配） | `AidServiceTest` 断言批量方法各调用 1 次、逐条方法 `never()` |
| 动态列表 N+1 | 每条动态 2 次查询（点赞状态、评论数），一页 10 条 = **21 次查询** | 固定 **3 次查询** | `CommunityServiceTest` 同上 |
| 广场热度榜 | 逐条 `findById` 取帖，16 条榜单 = **16 次查询** | `findAllById` **1 次查询** | 接口实测返回 5 条榜单数据正常 |
| 热度榜统计口径 | 主榜用「近 24 小时评论数」，补位用「全时段评论数」，同一榜单两种口径 | 补位改用 `countByPostIdAndCreatedAtAfter`，**统一为 24 小时口径** | 代码审查 + 编译通过 |
| 板块/频道索引失效 | `board IS NULL OR board = ?` 导致 OR 条件无法命中索引；数据中确有 5 行 NULL board、3 行 NULL channel | 迁移回填 NULL → 列改 `NOT NULL` → 查询简化为 `board = ?`（显示效果不变） | `EXPLAIN` 确认走 `idx_aid_board_created` 且 **Backward index scan（无 filesort）** |
| 分页无上限 | `?size=100000` 可一次性拉全表 | `PageableConfig` 统一限制 **单页最多 50 条** | 接口实测 `?size=100000 → pageSize=50`；`PageableConfigTest` 单测 |
| 缺失索引 | 仅主键与唯一键 | 新增 **16 个** 覆盖列表/排序/统计/批量查询的索引（真实库业务索引 **18 → 34**，另有 8 个属 Flyway 历史表自身） | `EXPLAIN` 验证命中 |
| 死代码 | 6 个从未被调用的仓储方法、8 个仅用于 NULL 兜底的查询方法、3 处未使用 import | 删除；`AidService.list` 由 8 分支 if/else 收敛为 4 分支 | 编译通过 |

### 9.2 工程基建

| 项 | 内容 |
|----|------|
| **Flyway 接管建表** | 4 个服务各自维护领域内表的迁移脚本，各自独立历史表（`flyway_schema_history_{auth,aid,community,ad}`）；`ddl-auto` 由 `update` 改为 **`validate`**；用 `baseline-on-migrate` + `baseline-version: 0` 兼容存量库 |
| **迁移与真实 DDL 对齐** | V1 脚本严格按 Hibernate 实际生成的 DDL 编写（原生 `ENUM`、`BIT(1)`、`DATETIME(6)`），修正了旧 `schema.sql` 与实体不一致的问题；旧文件已改为废弃提示 |
| **配置外置** | 数据库 URL/账号/口令、`JWT_SECRET`、内部登录码、Eureka 地址、CORS 白名单全部改为 `${ENV:dev默认值}`；本地开发零配置即可启动 |
| **安全告警** | 使用源码内置 JWT 默认密钥时，启动日志输出醒目 WARN；硬编码的内部码 `8461` 外置为配置项，置空即关闭该登录方式 |
| **单元测试** | 新增 **23 个用例**：求助状态机与越权（13）、社区装配与点赞浏览语义（9）、分页上限（1）；重点为 N+1 回归保护 |
| **测试依赖** | `common` / `aid-service` / `community-service` 引入 `spring-boot-starter-test` |

### 9.3 验证记录

| 验证 | 结果 |
|------|------|
| `mvn -B -DskipTests package` | BUILD SUCCESS（7 模块） |
| `mvn -B test` | **Tests run: 23, Failures: 0, Errors: 0** || 全新空库跑全部迁移 | 9 个脚本零报错；11 张表、34 个索引；`board`/`channel` 均为 `NOT NULL` |
| 真实库副本跑迁移 | 零报错；8 求助 / 12 动态 / 8 用户行数不变；NULL 归零；中文数据完好 |
| 真实库实际执行 | 4 个服务启动成功（8081–8084 全部监听）；4 张历史表记录 V1/V2/V3 全部 `success=1` |
| `ddl-auto: validate` | 4 个服务全部通过（含原生 ENUM 列），未出现 schema 校验失败 |
| 端到端（经网关 8080） | 内部码登录 → 求助列表（评分字段正确）→ 广场列表（`liked`/`commentCount` 正确）→ 热度榜 → 分页上限生效 |
| 数据零损失 | 迁移前后 `u_r_sys_user` 8、`u_r_aid_request` 8、`u_r_post` 12、`u_r_ad` 4、评论 5、点赞 14、浏览 10，完全一致 |

### 9.4 本轮未做（仍待处理）

| 项 | 原因 |
|----|------|
| P0-4 会话状态迁移到 Redis | **本机未安装 Docker**，无法起 Redis；验证码/短信码仍在内存 `ConcurrentHashMap`，多实例部署前必须处理 |
| P0-6 `docker-compose` 一键启动 | 同上，无 Docker 环境，脚本无法验证，写了也是未验证的产物 |
| 热度榜 `refresh` 幂等 | 涉及并发语义设计，宜与 P1 通知/定时任务一起考虑 |
| P1 全部功能项 | 属新增能力而非优化，待确认优先级后再做 |
| 前端 ESLint / Prettier | 需要选定规则集，建议与「是否引入 TypeScript」一并决策 |

---

## 10. 架构调整记录：Eureka → Nacos + Feign 跨服务调用（2026-09-12）

本轮按需求做了一次基础设施级改造。**第 1、2 章中关于注册中心、数据库地址、服务间调用的描述以本章为准。**

### 10.1 变更清单

| 项 | 变更前 | 变更后 |
|----|--------|--------|
| 注册中心 | 自建 `eureka-server` 模块（:8761），需单独启动一个进程 | **Nacos `192.168.198.139:8848`**（外部已有服务），模块与全部相关代码/配置删除 |
| 版本配比 | Spring Cloud 2023.0.3 | 追加 **Spring Cloud Alibaba `2023.0.3.2`**（内置 nacos-client 2.4.2），与 2023.0.3 精确匹配 |
| 数据库 | `localhost:3306/chengqu_huzhu` | **`192.168.198.139:3306/LinLiHui`** |
| 服务间调用 | 无（各表冗余快照 + 硬编码占位头像） | 新增 **`api` 模块** + OpenFeign，4 个业务域 7 个内部接口 |
| 启动进程数 | 6（含注册中心） | **5**（注册中心改为外部依赖） |

新增模块：

```
backend/api/                     # 跨服务契约
├── client/                      # UserApiClient / AidApiClient / CommunityApiClient / AdApiClient
├── dto/                         # UserBrief、UserAidStats、UserPostStats、
│                                # PlatformAidStats、PlatformPostStats、PlatformAdStats
└── config/FeignAuthForwardConfig # 把调用方的 Authorization 透传给下游
```

### 10.2 关键设计决策

**1. 内部接口的路径与隔离**
内部接口统一为 `/internal/**`，**刻意不带 `/api` 前缀**——因为网关的路由规则只匹配 `/api/**`，
所以这些接口天然无法从外部经网关访问（已实测返回 404）。
第二道防线是 Spring Security：`/internal/**` 同样需要合法 JWT，已实测无令牌访问返回 401。

**2. 不另造服务间口令，直接透传用户令牌**
`FeignAuthForwardConfig` 把当前请求的 `Authorization` 头原样带给下游，
下游用调用方的真实身份做鉴权。这样不必引入共享密钥轮换、令牌签发等额外机制。
无请求上下文的场景（定时任务）自动跳过透传，调用本身不受影响。

**3. 冗余字段降级为兜底，而非删除**
需求是「实现跨服务调用」，但直接删掉 `publisher_name` / `author_name` 会让下游一挂列表就不可用。
最终定位：**Feign 取权威数据，取不到则回退冗余快照，头像留空由前端占位图兜底。**
这同时保持了 N+1 修复的成果——一页数据只发 **1 次**批量调用，不是每行一次。

**4. 失败即降级，且不把故障伪装成 0**
聚合接口（用户主页、管理台看板）在下游不可用时把对应字段返回 `null`，
前端显示「暂不可用」而不是 `0`——否则运维会把服务故障误读成「平台没有数据」。

**5. 跨服务调用不放在数据库事务里**
`UserHomeService` / `AdminDashboardService` 刻意不加 `@Transactional`，
避免下游抖动期间长时间占用数据库连接。

**6. `NACOS_INSTANCE_IP` 可配置**
本机存在多张网卡（`192.168.111.1`、`192.168.198.1`、`10.163.125.233`）时，
自动探测可能注册一个其他节点访问不到的地址，导致网关拿不到实例。
因此把注册 IP 做成环境变量，多网卡场景显式指定同网段地址。

### 10.3 已实现的跨服务功能

| # | 功能 | 调用链 | 接口 |
|---|------|--------|------|
| 1 | 求助列表/详情补全发布者与帮助者 | aid-service → auth-service | `GET /api/aid/list`、`/{id}`、`/mine/*` 等全部求助读接口 |
| 2 | 动态列表/详情补全作者（含广场热度榜） | community-service → auth-service | `GET /api/community/posts`、`/posts/{id}`、`/posts/author/{id}`、`/plaza/hot` |
| 3 | 用户主页聚合 | auth-service → aid + community | `GET /api/user/{id}/home`（前端由 3 次往返降为 1 次） |
| 4 | 管理台看板聚合 | auth-service → aid + community + ad | `GET /api/admin/dashboard` |

### 10.4 验证记录

全部为真机验证（连远端 Nacos `192.168.198.139:8848` 与远端 MySQL `192.168.198.139:3306/LinLiHui`）。

| 验证 | 结果 |
|------|------|
| `mvn -B clean test` | **Tests run: 27, Failures: 0, Errors: 0**（新增 4 个 Feign 补全/降级用例） |
| `npm run build` | 构建成功 |
| Flyway 对存量远端库 | `Current version of schema LinLiHui: 1` → `Schema is up to date. No migration necessary.`（校验和匹配，未重复迁移） |
| Nacos 注册 | 5 个进程全部注册且 `healthy=true`（显式指定 `192.168.198.1`） |
| 服务停止后 | 实例自动从 Nacos 摘除（各服务 `hosts=<空>`） |
| **Feign 补全（关键证据）** | 通过接口把用户昵称改为「飞哥Feign验证」、设置新头像；此时数据库冗余列仍为「系统管理员」。求助列表返回 `publisherName='飞哥Feign验证'`、`publisherAvatar='https://cdn.example.com/feign-verified.png'`；动态列表返回 `authorName='飞哥Feign验证'`。aid-service 的实体里根本没有用户表，**数据只可能来自 auth-service** |
| 管理台看板聚合 | `userCount=8`（本地）+ `aid total=8/open=4/done=3` + `post total=12/comments=5/likes=14` + `ad total=4/clicks=5`，服务端日志显示四个域都被调用 |
| 用户主页聚合 | `/api/user/2/home` → `published=3, helping=0, done=2, postCount=6`，与单独调用旧接口的结果一致 |
| **降级验证** | 停掉 auth-service 后：`/api/aid/list` 仍 `code=0`；`/api/community/posts` 仍 `code=0`，`authorName` 回退为冗余值「系统管理员」、`authorAvatar=null`；服务端日志输出 `调用 auth-service 查询用户失败，降级使用本地冗余字段` |
| 内部接口隔离 | 经网关访问 `/internal/user/brief`、`/internal/aid/platform-stats`、`/internal/ad/platform-stats` 全部 **HTTP 404**；无令牌直连内部端口返回 `code=401` |

### 10.5 遗留与后续

| 项 | 说明 |
|----|------|
| 未引入 Resilience4j / Sentinel | 当前用 try-catch 做降级，属**服务降级**而非**熔断**；下游持续故障时每次请求仍会等待 Feign 超时（已配置 connect 2s / read 4s）。P2-4 可补熔断 |
| 未使用 Nacos 配置中心 | 只用了服务发现。配置文件仍在各服务内，后续可迁移到 Nacos Config 实现动态刷新 |
| 内部接口未做调用方白名单 | 目前依赖「网关不路由 + 需合法 JWT」。更严格的场景可加服务间签名或 mTLS |
| 服务名残留 | 服务停止后 Nacos 中仍保留空的 service 条目（Nacos 自身行为，非缺陷） |
| 数据源仍为共享库 | 四个服务连同一库同一账号；跨服务调用解决的是**读一致性**，不是**数据边界** |

---

## 11. Redis 接入与会话治理（2026-09-12）

接入 Redis `192.168.198.139:6379`，落地了四类能力。**第 1、2 章中「会话状态在内存」「登出无效」的描述以本章为准。**

### 11.1 变更清单

| 项 | 变更前 | 变更后 |
|----|--------|--------|
| 图形验证码 | 进程内 `ConcurrentHashMap` + 手写 `cleanup()` | Redis Hash + TTL，多实例共享、过期自动回收 |
| 短信验证码 / 频控 | 三张进程内 Map（`codeStore`/`lastSendAt`/`dailyCounter`） | `SET NX PX`（间隔）+ `INCR` 到当天结束（日限）+ 带 TTL 的验证码 |
| 登出 | 只把 `presenceStatus` 改成 OFFLINE，**令牌依然有效 30 分钟** | 会话级吊销（sid），access + refresh 同时失效 |
| 强制下线 | 无 | 用户级令牌版本号，「退出所有设备」O(1) 生效 |
| 接口限流 | 无 | 登录 / 注册 / 短信下发按 IP 限流，另有手机号维度第二层 |
| 热点数据 | 每次请求都查库 | 广告轮播、广场热度榜缓存 + 写时失效 |

新增共用组件（位于 `common`，四个业务服务共享）：

```
common/redis/RedisKeys.java              统一 key 命名（cqh: 前缀）
common/redis/RedisCache.java             JSON 缓存原语，失败即降级
common/redis/RedisRateLimiter.java       固定窗口限流（Lua 保证原子）
common/security/TokenRevocationService.java  令牌吊销（会话级 + 版本级）
```

### 11.2 关键设计决策

**1. 令牌吊销做成两级，而不是一级**
最初只用「按 jti 吊销单个令牌」，很快发现两个洞：
① 客户端登出只上送 access token，服务端拿不到 refresh token 的 jti，
   于是「登出后仍能用刷新令牌换回可用的访问令牌」；
② 「退出所有设备」需要枚举历史 jti，不可行。
最终方案：一次登录的 access + refresh 共用 `sid` 声明（按会话吊销），
配合用户级 `ver` 版本号（按用户吊销）。两者各解决一个具体问题。

**2. 用版本号替代时间水位线（实测踩坑后修正）**
第一版「退出所有设备」记录一个时间水位线，判定条件是 `iat < 水位线`。
实测发现：一是用 `<=` 会把登出后同秒的重新登录误杀，改成 `<` 又导致
**同秒签发的其他会话不被吊销**——真机验证时确实观察到「设备 A 仍然可用」。
根因是 JWT 的 `iat` 只有秒级精度。改用版本号精确比对后，同秒歧义消失。

**3. 缓存失效要分清"必须"与"不必"**
广告轮播在增删改时立即失效，但**点击不失效**——否则每次点击都要清缓存，缓存等于白做；
代价仅是响应里的 `clickCount` 最多滞后 60 秒，而管理端走的是不缓存的 `listAll()`。
广场热度榜的**空结果不缓存**，否则会压制控制器里「榜单为空则同步重算」的兜底逻辑。

**4. 限流顺序：先查日限（只读）→ 再抢间隔锁 → 最后累加计数**
若反过来先抢间隔锁，一次被日限拒绝的请求会白白吃掉间隔配额，
用户会看到「明明没发出去，却被要求等待」。并发下越过日限时主动释放间隔锁。

**5. 固定窗口用 Lua 保证原子**
`INCR` 与 `PEXPIRE` 拆成两条命令时，进程在两条之间挂掉会留下永不过期的计数器，
把该身份**永久锁死**——这是限流实现里最常见的坑。

**6. 客户端 IP 取 `X-Forwarded-For` 最后一段（实测踩坑后修正）**
第一版取第一段。实测发现网关是**追加**语义，客户端自带伪造值会变成
`伪造IP, 真实IP`，取第一段意味着攻击者每次换一个伪造值就能完全绕过限流。
改为取最后一段（可信网关写入的真实地址）后，用「每次伪造不同 IP」的方式复测，
限流精确在第 21 次触发。

**7. 降级策略逐个能力单独定，不搞一刀切**
吊销查询 fail-open（令牌仍有 30 分钟自然过期兜底，全站登不上代价更大）、
限流放行、缓存按未命中、验证码直接不可用。
每一项都在 README 的「Redis 与会话治理」里写明，并用配置项 `JWT_REVOCATION_FAIL_CLOSED` 留出收紧开关。

### 11.3 验证记录

全部为真机验证（连远端 Redis `192.168.198.139:6379`、远端 Nacos 与 MySQL）。

| 验证 | 结果 |
|------|------|
| `mvn -B clean test` | **Tests run: 73, Failures: 0, Errors: 0**（新增 46 个用例） |
| `npm run build` | 构建成功 |
| 5 个服务启动 | 全部成功，Redis 连接正常 |
| **验证码落 Redis** | `HGETALL cqh:captcha:{id}` 返回 `code=wxkf, verified=0`，`TTL=119`（配置 120 秒） |
| **会话级登出** | 登出后 access token 在 aid / community / auth 三个服务上均返回 **401** |
| **刷新令牌** | 登出后用 refresh token 续期返回 **401「登录状态已失效」**（修复前此处可续期） |
| **退出所有设备** | 设备 2 执行后，设备 2 **与设备 1** 均 401，且设备 1 的刷新令牌同样 401；随后新登录正常（版本自增不会误杀新会话） |
| **短信频控** | 第 1 次发送成功；第 2 次立即发送返回「发送过于频繁，请 59 秒后再试」 |
| **接口限流** | 连续 30 次内部码登录：15 次成功 + 15 次 429（此前已消耗 5 次配额，合计正好 20）；验证码接口不受影响 |
| **伪造头不能绕过限流** | 25 次请求每次伪造不同的 `X-Forwarded-For`，仍精确在**第 21 次**被限流；服务端记录的是真实地址而非伪造值 |
| **广告轮播缓存** | 第一次日志「轮播查库 … 已写入缓存 60s」，第二次「命中缓存」；后台新建广告后缓存 key 被清除，再访问回到「轮播查库 count=5」 |
| **热度榜缓存** | 「热度榜查库 items=5（已写入缓存 600s）」→「命中缓存」 |
| 日志净化 | 关闭 Spring Data Redis Repository 扫描后，启动日志不再出现无关的仓库探测警告 |

### 11.4 遗留与后续

| 项 | 说明 |
|----|------|
| Redis 单点 | 未做哨兵或集群；Redis 故障时验证码不可用（无法登录），其余能力优雅降级 |
| 限流为固定窗口 | 窗口边界存在最多 2 倍突刺；需要更平滑可换滑动窗口或令牌桶 |
| 未做分布式锁 | 点赞、浏览去重目前靠数据库唯一键；高并发下可考虑 Redis 锁 |
| 未用 Redis 做排行榜 | 热度榜仍是「每日定时算好落库 + 缓存」；若要做实时榜可改用 ZSET |
| 未做在线状态心跳 | `presenceStatus` 仍由登录/登出显式改写，没有 TTL 心跳与自动离线 |

---

## 12. 技术选型与工具引入路线

> 本章回答「Kafka / Seata / Sentinel / ES 后续还会用到什么」。核心主张是：
> **中间件不是按"先进"选的，而是按"痛到什么程度"选的。** 每引入一个，就多一个要部署、
> 要监控、会挂、会拖慢本地启动的东西。下面每一项都给出**明确的引入触发条件**。

### 12.1 对已提出的四个工具的判断

| 工具 | 结论 | 引入触发条件 | 代价 / 前置条件 |
|------|------|--------------|-----------------|
| **Sentinel** | 🟢 **现在就该做** | 已经具备：跨服务调用 10 处，目前只有 try-catch 降级，**没有熔断**。下游持续故障时每个请求仍要等满 Feign 超时（2s+4s） | 需额外部署 sentinel-dashboard（一个进程）。替代方案：**Resilience4j** 无需 dashboard、与 Spring Cloud Gateway 官方集成更顺，若团队只做熔断可优先考虑 |
| **Kafka** | 🟡 **中期，别现在上** | 出现**真正的异步解耦需求**时：通知中心（接单/评论/评价事件扇出）、热度分与信用分累计、短信/图片处理削峰、向 ES 同步数据 | 需要部署、规划 topic 与消费组、处理重复消费（幂等）、死信队列、消息积压监控。**在此之前用 Spring 的 `ApplicationEvent` + `@Async` 完全够**——同一个 JVM 内的事件解耦不需要 MQ。若确定走 Alibaba 生态，BOM 里已有 RocketMQ，可一并比较 |
| **Seata** | 🔴 **暂时不需要，且可能长期不需要** | 两个条件**同时**满足才有意义：① 拆库（每服务独立 DB）；② 出现跨服务的**写**操作（如悬赏托管：扣余额 + 建订单 + 改求助状态） | 现在**六个业务服务共用一个库**，跨服务调用以只读补全为主（唯一的跨服务写是通知投递，已用「事务提交后投递 + 失败只丢通知」回避了分布式事务）。Seata 要额外部署 TC、每个库加 `undo_log` 表、AT 模式对 SQL 有约束且有性能损耗。**先做「本地消息表 + 最终一致性」或 Saga，真的扛不住再上** |
| **Elasticsearch** | 🟡 **中期，且有更轻的替代** | 数据量过万且需要**分词/高亮/拼音/纠错**；或作为**日志检索**（这往往是 ES 更早的真实价值点） | ES 是这几个里运维最重的（JVM 堆、分片规划、集群、License 需注意）。**中间方案**：MySQL 全文索引 + ngram 分词器，或 Meilisearch / Typesense（单二进制、开箱即用）。要做搜索同步，用 **Canal 订阅 binlog** 比应用双写可靠 |

### 12.2 比这四个更该先做的（按性价比排序）

现状核实：**目前连 Actuator 都没有引入**，**6 个业务服务**的日志分散在各自文件里，
跨服务调用已有 **9 个 Feign 方法（6 份契约）**——
也就是说「出了一次线上问题，只能逐个进程翻日志」。这类基础设施的投入产出比远高于上 Kafka/Seata。

| 优先级 | 工具 | 解决什么 | 为什么现在做 |
|--------|------|----------|--------------|
| **P0** | **Spring Boot Actuator** | 健康检查、指标端点、优雅停机 | 零学习成本、加依赖即可。目前 compose / K8s 健康检查、负载均衡摘除节点都缺这个 |
| **P0** | **Micrometer + Prometheus + Grafana** | QPS、P99 延迟、Feign 调用成功率、Redis/MySQL 连接池水位、JVM | 有了它才知道「慢在哪」。Feign 调用失败率是必须看的指标 |
| **P0** | **分布式链路追踪**（Micrometer Tracing + Zipkin，或 SkyWalking） | 一次求助列表请求跨了 aid→auth 两次调用，出问题无法定位是哪一跳 | **已有 9 个 Feign 方法**，加上「提交后投递通知」这条跨服务写路径，属于典型的「该有却没有」 |
| **P1** | **日志聚合**（Loki + Grafana 或 ELK） | **6 个进程**日志集中检索、按 traceId 串联 | 与链路追踪配套，否则 traceId 拿到了也搜不到 |
| **P1** | **分布式调度 / 分布式锁**（ShedLock，Spring Cloud Alibaba BOM 里已有） | **修一个真实隐患**：`PlazaHotService` 的 `@Scheduled` 在 community-service 多实例部署时会**重复执行**，热度榜被反复重算、互相覆盖 | 见 12.4，这不是"以后"，是"多部署一个实例就会发生" |
| **P1** | **Nacos Config** | 动态调整限流阈值、JWT 过期时间、功能开关，不重启 | **Nacos 已经在跑**，只用了 discovery，开启 Config 几乎零额外成本 |
| **P1** | **文件存储**（MinIO 自建 / 阿里云 OSS） | 头像、帖子配图、求助配图 | ROADMAP P1-1；目前全靠外链，是所有「带图内容」的前置条件 |
| **P2** | **API 文档**（SpringDoc OpenAPI） | **19 个 Controller / 99 个接口**（其中 11 个是集群内部接口，不经网关暴露）目前只能读代码 | 前后端联调、交接、自测都省时间 |
| **P2** | **网关加固**（限流 + 熔断 + 统一鉴权前置） | 目前限流只在 auth-service 内部，网关裸奔 | ROADMAP P2-4 |
| **P2** | **CI/CD**（GitHub Actions / Jenkins）+ Docker | 目前零 CI，全部手工验证 | 「改完不知道有没有破坏别的地方」是当前最大的交付风险 |
| **P2** | **压测**（k6 / JMeter / Gatling） | 上线前容量评估 | 有了 Prometheus 才能读懂压测结果 |

### 12.3 更后期、有明确触发条件才引入的

| 工具 | 触发条件 |
|------|----------|
| **ShardingSphere**（分库分表 / 读写分离） | 单表过千万行，或单库写入成为瓶颈。**现在 19 张表、演示级数据量，纯属过度设计** |
| **Canal** | 需要把 MySQL 变更同步到 ES / Redis，且不能接受应用双写的不一致 |
| **分布式 ID**（Leaf / 雪花算法） | 一旦分库分表，自增主键就不再全局唯一 |
| **内容审核**（敏感词 DFA / 阿里云内容安全） | 开放注册前必须做，属于合规要求而非性能优化 |
| **实名认证 / 三方核身** | 涉及线下见面与交易，是信任体系的地基（ROADMAP 4.3） |
| **实时通信**（SSE / WebSocket + STOMP） | 站内沟通、通知实时推送。**先用轮询**，量上来再换 WS |
| **分布式锁**（Redisson） | 抢单、悬赏托管等并发写场景（目前靠数据库唯一键已够用） |
| **对象存储图片处理**（imgproxy / Thumbnailator） | 用户上传原图后需要缩略图 |
| **K8s + Harbor + 灰度发布** | 服务实例数超过 10，或需要不停机发布。Nacos 权重可先做灰度 |
| **MapStruct** | DTO 转换代码继续膨胀时。目前手写 builder 尚可接受 |
| **混沌工程**（ChaosBlade） | 熔断降级链路做完之后，用来验证降级是否真的生效 |
| **密钥管理**（Vault / KMS） | 目前靠环境变量，够用；多环境多密钥时再考虑 |

### 12.4 一个现在就该修的真实隐患：多实例下的定时任务

`community-service` 的 `PlazaHotService` 用 `@Scheduled(cron = "0 0 6 * * *")` 刷新热度榜。

**问题**：`@Scheduled` 是**进程级**的。community-service 一旦部署两个实例，
6:00 会同时触发两次刷新——两次 `deleteByRankDate` + `saveAll` 相互覆盖，
可能写出残缺榜单，且日志里会出现两次「热点榜刷新」。

**修法（按成本从低到高）**：
1. **ShedLock**（Spring Cloud Alibaba BOM 已管理版本）：加一个表 + `@SchedulerLock` 注解，最小改动；
2. **XXL-JOB**：调度与业务分离、有可视化与失败重试，适合任务变多以后；
3. 把刷新逻辑改成**幂等**（先算后写、用唯一键兜底），但这解决不了"重复执行"本身。

> 这类问题在单实例开发时**完全看不出来**，一扩容就爆。属于典型的「早发现五分钟，晚发现一晚上」。

### 12.5 引入原则

1. **触发条件式引入**：每一项都写明「什么情况下才上」。没有触发条件就不上，
   避免"简历驱动开发"。
2. **能用手边的就不新增进程**：Nacos Config 比新建配置中心便宜；ShedLock 比 XXL-JOB 便宜；
   MySQL 全文索引比 ES 便宜。
3. **先降级，后熔断**：已经用 try-catch 做了服务降级，Sentinel/Resilience4j 是把它升级为
   自动熔断——**这是对已有工作的补完，不是另起炉灶**。
4. **中间件数量 = 运维负担**：每多一个进程，就多一份部署脚本、监控面板、告警规则、
   故障预案和新人上手成本。当前已有 MySQL、Nacos、Redis 三个外部依赖，
   再加 Kafka + Seata + ES + Sentinel Dashboard 就是**七个**——对这个体量的项目，
   运维成本很可能超过收益。
5. **可观测性永远优先于新中间件**：链路追踪、指标、日志是"能不能定位问题"的问题；
   Kafka、ES 是"功能能不能做"的问题。前者是后者的前提。

### 12.6 与既有里程碑的对应关系

| 阶段 | 对应工具 |
|------|----------|
| M0 打地基 | Actuator + 健康检查（原 P0-6）、ShedLock（修定时任务隐患）、SpringDoc |
| M1 补闭环 | 文件存储（MinIO/OSS）、SSE 通知推送、Sentinel（Feign 熔断） |
| M2 平台化 | Micrometer + Prometheus + Grafana、链路追踪、日志聚合、Nacos Config |
| M3 信用与搜索 | ES 或其轻量替代、Canal（数据同步）、内容审核 |
| M4+ 商业化 | Kafka（事件扇出）、分库分表、分布式 ID、实名认证、K8s |

---

## 13. 信息架构重构记录：五大模块 + 帖子分类 + 关系链（2026-09-13）

> **本轮目标**：把首页拆成 **发现 / 邻里 / 校园 / 我的 / 消息** 五大模块，
> 各模块前端重新规划布局；帖子种类至少 7 种；邻里汇补上 **集市 / 圈子 / 回收** 三个业务域；
> 增加 **关注 / 拉黑**。产品名仍为「邻里汇」，第一个模块定名 **发现**（生活广场并入其中）。

### 13.1 变更清单

**后端 — 新增模块**

| 模块 | 端口 | 内容 |
|------|------|------|
| `notify-service` | 8085 | 站内通知中心：`u_r_notification`、投递接口、未读数、已读、删除 |
| `file-service` | 8086 | 本地磁盘对象存取：上传（扩展名 + 文件头双重校验）、读取、删除 |

**后端 — auth-service**

| 变更 | 说明 |
|------|------|
| `V2__user_relation.sql` | 新增 `u_r_user_follow` / `u_r_user_block` |
| `UserRelationService` | 关注、取关、拉黑、取消拉黑、关注/粉丝/黑名单列表、关系查询 |
| 拉黑语义 | 拉黑**同时解除**互相关注（双向），并让内容在信息流里互斥 |
| `/internal/user/exclusions` | 对外暴露「当前用户的互斥集（我拉黑的 + 拉黑我的）」，供下游过滤 |
| `NotifySender` | 关注事件投递通知，同样走 `afterCommit` |

**后端 — community-service**

| 变更 | 说明 |
|------|------|
| `V5__post_kind.sql` | `u_r_post.kind`（VARCHAR，非 ENUM） |
| `V6__market_goods.sql` | `u_r_goods` |
| `V7__circle.sql` | `u_r_circle` / `u_r_circle_member` / `u_r_circle_post` |
| `V8__recycle.sql` | `u_r_recycle_order` |
| `V9__goods_contact.sql` | `u_r_goods.contact` / `contact_type` |
| `PostModule` | 展示层模块聚合：`DISCOVER` = `COMMUNITY` + `PLAZA`；`CAMPUS` |
| `PostKind` | 10 个种类，带「属于哪些模块」的约束与中文 label / hint |
| `Post.kind` | 取代原先只服务校园的 `category` 字符串（旧列保留为 `@Deprecated`） |
| 集市 / 圈子 / 回收 | 各自的 entity / repository / service / controller / 初始化数据 |
| `UserExclusionLookup` | 信息流与作者页在 **SQL 层**排除互斥集 |

**前端**

| 变更 | 说明 |
|------|------|
| 设计系统 | `styles.css` 重写为 token 化设计系统（间距/圆角/阴影/语义色/基础组件类） |
| `AppShell` | 五模块导航 + 二级入口（集市/圈子/回收）+ 未读角标 + 侧栏用户卡 |
| 新页面 | `DiscoverView` `MeView` `MessagesView` `RelationListView` `MarketView` `CircleView` `RecycleView` |
| 重写 | `CampusView`（6 个种类含失物招领分区）、`MarketView`、`AppShell` |
| 新组件 | `PostFeed` `FollowButton` `StatusTag` `StateBlock` `SkeletonList` `ImageUploader` `ImageGallery` |
| 路由 | 五模块路径 + 一组 legacy 重定向（`/`→`/discover`、`/plaza`→`/discover?tab=plaza`、`/aids*`→`/neighbor*`、`/settings`→`/me/settings`） |
| 头像 | 去掉第三方图床（picsum），改为离线 SVG data-URI 占位图 |

### 13.2 核心设计决策

**① 「广场并入发现」用查询层聚合，不动存储枚举**

`PostChannel` 是 MySQL `ENUM` 列（`COMMUNITY` / `PLAZA` / `CAMPUS`）。
把 `PLAZA` 删掉需要一次不可回滚的数据迁移，而且「广场并入发现」本质是**导航结构**的变化，
不是**数据语义**的变化——`PLAZA` 这个频道在业务上依然存在（它是热度榜的来源）。

因此新增 `PostModule` 作为**展示层**概念：

```
DISCOVER  →  查询 COMMUNITY + PLAZA
CAMPUS    →  查询 CAMPUS
```

好处：零数据迁移、零枚举变更、旧数据无需回填；代价：`listPosts` 需要在查询层把 module 展开成频道集合。

**② `PostKind` 用 VARCHAR 而不是 ENUM**

`board` / `channel` 都是 MySQL `ENUM`，每加一个取值就要 `ALTER TABLE`。
帖子种类是**最可能被业务扩展**的字段，所以这一列刻意用 `VARCHAR`，配以 Java 枚举做白名单校验。
「属于哪个模块」的约束写在枚举里（`Set<PostModule> modules`），而不是散落到各处的 if。

**③ 种类与模块的双向校验**

写操作走 `PostKind.require(code, module)`：
- 未定义种类 → `帖子种类无效`
- 种类不属于该模块 → `「闲置转让」不属于当前模块`

读操作（筛选）走 `PostKind.parseOrNull`，非法值返回 null 表示「不过滤」。
**读写用不同强度的解析**是刻意的：写错必须报错，读错不该让整个列表打不开。

**④ 拉黑同时解除关注，而不是「保留关注但屏蔽内容」**

理由：拉黑是一种**关系终止**，不是**内容过滤**。若保留关注关系，
被拉黑方仍会出现在对方粉丝列表里、仍会收到对方的关注通知，
与用户的直觉（「我不想再和这个人有关系」）不符。因此拉黑写在同一个事务里，
同时删掉双向的 `u_r_user_follow` 行。

**⑤ 互斥集在 SQL 层过滤，不在内存里筛**

`searchByChannels` / `searchByChannelsAndKind` / `searchByAuthor` 都把互斥集作为查询条件传入。
如果在 Service 拿到 `Page` 之后再 `filter`，会出现「一页 10 条被筛成 6 条」以及分页总数错乱——
**过滤必须发生在分页之前**。

**⑥ 集市联系方式只在详情接口返回**

列表一页就是几十个卖家，且是最容易被脚本批量抓取的地方，把联系方式放进去等于对外提供一份可枚举的通讯录。
因此装配方法分成两个：

- `GoodsResponse.from(goods)` —— 列表 / 我发布的 / 创建 / 更新 / 改状态，**不带联系方式**
- `GoodsResponse.fromDetail(goods)` —— 详情专用，**唯一出口**

做成两个方法而不是给 `from` 加布尔参数，是为了让「哪里会泄露联系方式」在调用处一眼可查：搜索 `fromDetail` 即可列出全部出口。

**⑦ 通知的跳转路径在创建时落库**

`NotificationType.link(refId, actorId)` 是「只看类型就能推导」的通用跳转，
但**类型无法表达频道**：一条 `POST_COMMENTED` 通知，帖子可能在发现模块也可能在校园模块。
只靠 `refType`/`refId` 推不出「该跳 `/discover?post=` 还是 `/campus?post=`」。

因此：
- `NotifyCreateCommand` 增加 `link` 字段，由业务方（只有它知道帖子在哪个频道）生成；
- `u_r_notification.link` 落库（`V3__notification_link.sql`）；
- `NotificationResponse.linkOf()` 采用「**落库值优先，缺失时按 type 现算**」——存量通知 `link` 为 NULL，
  走的正是回退分支，所以这次改动**不改变既有通知的跳转行为**；
- notify-service 只接受站内相对路径，站外地址按未传处理（防开放重定向）。

### 13.3 验证记录

**迁移**

```
community-service: Successfully validated 10 migrations，Current version of schema `LinLiHui`: 9
notify-service:    flyway_schema_history_notify 含 V3 notification link (success=1)
Hibernate ddl-auto: validate 通过（19 张业务表）
```

**单元测试**：**97 个测试全绿**（common 34 / auth 16 / aid 27 / community 18 / file 2）。

**端到端（真实起 7 个进程，经网关 :8080 调用）：57 项断言全部通过**

| 组 | 覆盖点 |
|----|--------|
| 1. 帖子种类按模块下发 | 发现 5 种 / 校园 6 种；`MARKET` 只在发现、`RANT` 只在校园、`DAILY` 两模块共用；每项带 label + hint |
| 2. 种类与模块校验 | 校园发 `MARKET` → `400「闲置转让」不属于当前模块`；未定义种类 → `400 帖子种类无效` |
| 3. 发现模块聚合 | 发现流含 `COMMUNITY` + `PLAZA`、不含 `CAMPUS`；校园流反之；列表项带 `kindLabel`；按 `kind` 过滤命中/排除正确 |
| 4. 通知 link 按频道落库 | 校园帖评论 → `/campus?post=26`；发现帖点赞 → `/discover?post=24` |
| 5. 回收品类需登录 | 匿名 → 业务码 `401 未登录或登录已过期`；登录后 → 6 个品类 + 3 个时段 + 计价说明 |
| 6. 集市联系方式 | 列表 / 我发布的 / 创建响应 `contact` 均为 `null`；仅详情返回 `e2e-wechat-***` + `WECHAT` + 中文名「微信」 |
| 7. 关注与拉黑 | 关注后粉丝列表可见；拉黑后关注自动解除、帖子从对方信息流消失、黑名单可查；**甲看不到乙的评论**；**乙评论 / 点赞 / 凭 id 读详情均 403**；甲读自己的帖子不受影响 |
| 8. 清理 | 验收数据全部删除，无残留 |

> 端到端脚本用**真实注册链路**建号：拉图形验证码 → 从 Redis 读验证码 → 校验 →
> 发短信 → 从 Redis 读短信码 → 注册。这样连验证码与限流链路一起被验证，
> 而不是绕过它们直接插库。

### 13.4 本轮未做 / 已知取舍

| 项 | 说明 |
|----|------|
| aid-service 未接入拉黑过滤 | community 的帖子列表、作者页、**评论列表**都已在 SQL 层排除互斥集，**aid-service 的求助列表还没有**。接法与 community 一致（注入 `UserExclusionLookup`），属于遗留 |
| 集市 / 圈子未接入拉黑过滤 | 同理，`/api/market/goods/list` 与 `/api/circle/*` 目前不排除互斥集 |
| 拉黑不撤销已产生的通知 | 拉黑前的点赞/评论通知仍在对方的消息列表里，没有回溯清理 |
| `CircleResponse` 缺 `statusLabel` | 前端拿 `status` 自己映射；`GoodsResponse` 已经下发 `statusLabel`，两处口径不一致 |
| 集市无「全部状态」筛选 | 前端筛选器不支持「不限状态」，后端 `status` 不传即全部，属前端能力缺口 |
| 集市无分类聚合接口 | 分类是自由文本（不设白名单），无法可靠地列出可选项 |
| `FilePurpose` 缺 `GOODS` / `CIRCLE` | 上传时无法按业务域归类，回收到 `OTHER` |
| `/internal/notify` 无调用方身份校验 | 内部接口不对网关暴露（网关路由未包含 `/internal/**`），但服务间无 mTLS，集群内理论上可被调用 |
| 通知投递仍在请求线程外同步执行 | 已用 `afterCommit` 把投递移出业务事务，但没有 outbox 表，也没有针对 notify 的熔断降级；notify 不可用时只是丢通知（降级为 WARN），不会补偿 |
| 集市编辑表单不含联系方式 | `UpdateGoodsRequest.contact` 后端已支持，前端详情页没有编辑入口 |
| 示例数据的商品无联系方式 | `MarketDataInitializer` 造的数据没编造微信号——不给演示数据塞假联系方式是有意的 |
| 联系方式会 +1 浏览量 | 详情接口既返回联系方式又记浏览量。如果以后要「看联系方式不计数」，需要拆一个不计数的 `/contact` 端点 |
| 未做浏览器可视化验收 | 前端只验证到 `npm run build` 通过（真 esbuild 流水线）+ SFC 编译校验；**页面的视觉与交互未经人眼确认** |

### 13.5 本轮修掉的一个真实缺口：屏蔽只做了「看」，没做「写」

初版屏蔽只作用在**读**路径（信息流、作者页），评论列表和**写**路径都没管。后果有两个，都是可复现的：

1. 甲把乙拉黑之后，乙在甲帖子下的**旧评论仍然显示**在甲的评论列表里——
   拉黑了人却还在自己帖子下面看他的留言；
2. 屏蔽只让帖子从信息流消失，**客户端拿到 id 就能绕过界面**：
   `POST /posts/{id}/comments`、`POST /posts/{id}/like`、`GET /posts/{id}` 全部照常成功。
   也就是说「拉黑」当时只是一层前端的视觉效果。

修法（三处，都在服务端）：

| 位置 | 改动 |
|------|------|
| `PostCommentRepository` | 新增 `findByPostIdExcludingAuthors`，**并删掉不过滤的旧方法**——留着它只会让后来者顺手用上 |
| `CommunityService.listComments` | 用互斥集过滤 |
| `CommunityService.requireInteractable` | 新增守卫，`detail` / `toggleLike` / `addComment` 共用；命中即 `403 无法对该动态进行操作` |

守卫的提示语**刻意不区分**「你拉黑了对方」与「对方拉黑了你」：
告诉被拉黑者「你被对方拉黑了」等于把对方的操作暴露出去，属于隐私泄漏。
作者本人访问自己的帖子在守卫里直接放行，不受影响（端到端已断言）。

### 13.6 一个只在沙箱/受限环境出现的假故障：Sentinel 日志目录不可写

**现象**：服务能正常启动并注册 Nacos，但 stderr 持续刷屏，且线程编号不断增长：

```
Exception in thread "sentinel-datafile-log-executor-thread-111" java.lang.NullPointerException:
  Cannot invoke "java.util.logging.FileHandler.publish(java.util.logging.LogRecord)"
  because "this.handler" is null
    at com.alibaba.csp.sentinel.log.jul.DateFileLogHandler$LogTask.run(DateFileLogHandler.java:201)
```

**根因**（抓 stderr 拿到的首条异常）：

```
java.nio.file.AccessDeniedException: C:\Users\<用户名>\logs\csp\sentinel-record.log.pid54988.2026-09-13.0.lck
    at java.logging/java.util.logging.FileHandler.openFiles(FileHandler.java:512)
    at com.alibaba.csp.sentinel.log.jul.DateFileLogHandler.rotateDate(DateFileLogHandler.java:152)
    at com.alibaba.csp.sentinel.log.RecordLog.<clinit>(RecordLog.java:39)
```

Sentinel 的日志目录默认是 `${user.home}/logs/csp/`。当该目录**不可写**（本次是 DSH 文件沙箱只允许写工作区，
`C:\Users\<用户名>\logs\csp\` 在工作区之外）时，JUL `FileHandler` 构造失败，
`DateFileLogHandler.handler` 保持 `null`，此后**每写一条日志都抛 NPE**，
而异常发生在日志线程里，既不会中断启动，也不会在 Spring 日志里留下痕迹——只有 stderr 在刷。

**这不是代码缺陷**，在不受限的终端里不会出现。但只要满足「日志目录不可写」，任何环境都会复现。

**排查过程中的两个弯路**（记下来避免重复踩）：

1. 最先怀疑「6 个进程共用同一目录、同名文件抢 `.lck` 锁」，于是加了
   `spring.cloud.sentinel.log.switch-pid: true`。该配置确实生效了
   （Sentinel 启动横幅从 `use pid is: false` 变成 `true`），但 NPE **照旧**——
   因为目录本身不可写，文件名怎么变都没用。该改动已回滚。
2. 中途用 `2>$null` 丢掉 stderr 做验证，得到「加上 PID 就没有 NPE」的**假结论**。
   NPE 恰恰只出现在 stderr 上——**验证时不能把目标信号重定向掉**。

**验证过的规避方式**：把 Sentinel 日志目录指到可写位置即可，NPE 归零、日志文件正常落盘。

```powershell
# 目录必须已存在：Sentinel 不会替你创建它（不存在时报 NoSuchFileException）
$env:JAVA_TOOL_OPTIONS = '-Dcsp.sentinel.log.dir=<可写目录>'
```

> 顺带一个观察：`use pid is: false` 时 6 个服务会往同一个 `logs/csp/` 写同名文件，
> 即使目录可写也会互相干扰、难以按服务排查。等多实例部署时，
> 建议统一打开 `spring.cloud.sentinel.log.switch-pid: true` 或给每个服务单独的 `log.dir`。

### 13.7 未纳入白名单的一处刻意选择

`/api/recycle/categories` **没有**加进 `permitAll`。

原因：前端 `/recycle` 路由是 `requiresAuth`，匿名用户根本走不到这个页面，
放一个匿名白名单等于为**不可达路径**开权限——是死代码，也是无谓的攻击面。
要真正做成匿名可访问，需要**同时**放开后端白名单 + 把前端路由改为公开 + 隐藏「我的预约」分区，
三件事一起做才有意义。当前结论与理由已写在 `SecurityConfig`、`RecycleController`、
`RecycleGuideResponse`、`recycle.js`、`RecycleView.vue` 五处注释里，避免后人「顺手补上白名单」。

### 13.8 登录 / 注册的验证码闸门：从「装饰」变成「前置条件」（2026-09-13 追加）

**改动前**：登录页与注册页把手机号、密码、短信验证码、图形验证码**平铺在一个表单里**，
`useCaptcha` 用 `watch(() => form.captchaCode, tryVerify)` 做「输满长度自动校验」。
结果是图形验证码**拦不住任何人**：校验结果与表单可见性无关，密码框一直就在那里，
用户可以先填完密码再回头处理验证码，甚至根本不管校验是否通过（提交时后端会再验一次）。

**改动后**：两步。

1. 第一步只有图形验证码输入框 + 画布 + **「确认验证码」按钮**；
2. 点按钮调用 `POST /api/auth/captcha/verify`，**由后端裁决**；
3. 通过后才渲染手机号、密码、短信验证码输入框与「获取验证码」按钮。

**几个刻意的决定**

| 决定 | 理由 |
|------|------|
| 校验必须打后端，不做本地比对 | `verified` 标记写在 Redis 里，登录 / 注册接口读的是同一份状态。前端本地比对等于把答案交给浏览器——`challenge` 本来就是下发给前端绘制的，改一行代码就能绕过 |
| 用 `v-if` 而不是 `v-show` / `disabled` | 未通过时输入框**不在 DOM 里**。CSS 隐藏只是看不见，DOM 还在，密码框依然可以被脚本填入并提交 |
| 到点收回表单，而不是只改角标 | 冻结期刚好在提交瞬间过期，后端会返回「验证码失效」；此时若前端仍显示「已验证」，用户只会反复提交失败而看不出原因 |
| 失败即换一张验证码 | 否则用户只能对着同一张图反复猜。换图同时会清空输入并重新关闭闸门 |
| 刷新验证码一律作废已验证状态 | 不然用户能拿旧验证码换一张新图，「一码一用」就漏了 |
| 「管理员内部码登录」不受闸门限制 | 它走另一套口令（`INTERNAL_LOGIN_CODE`），且管理员被卡在验证码外时仍需一个入口。该入口本来就不经过图形验证码 |
| 错误提示不区分「你拉黑」与「被拉黑」 | 见 13.5；验证码这里同理，失败只回「验证码错误」，不透露正确答案的任何信息 |

**这层闸门是 UI 约束，不是安全边界**：后端的 `requireVerified` 在登录 / 注册 / 发短信时
都会校验图形验证码，所以绕过前端并不能绕过校验——闸门改变的是**交互顺序**，
没有改变服务端的防护强度。这一点必须说清楚，避免误以为加了闸门就等于加了安全性。

**验证方式**：新增 `frontend/tests/captcha-gate.mjs`（`npm run test:gate`），**32 项断言全绿**。

- **状态机**（`useCaptcha`，真实代码 + 真实定时器 + stub 掉 HTTP）：错码确认失败且闸门保持关闭、
  失败后自动换一张、对码通过且冻结秒数取后端返回值、冻结期到点自动收回、手动换图作废已验证状态、已验证后重复确认不再打后端；
- **渲染**（SSR 渲染真实的 `LoginView` / `RegisterView`）：未确认时
  `#login-phone` `#login-password` `#login-sms` `#reg-phone` `#reg-nickname` `#reg-password` `#reg-sms`
  **全部不在 HTML 里**，同时渲染出验证码输入框、「确认验证码」按钮与禁用态的提交按钮；
- **闸门组件两态**：关闭态有确认按钮、无「已通过」；通过态反过来，且输入框禁用、显示剩余秒数。

> 唯一没被自动覆盖的是「在真实浏览器里点击确认按钮后输入框出现」这一交互——
> 它由状态机（确认后 `captchaVerified` 变 true）与渲染（未确认时不渲染）两头夹住，
> 但没有真的点过一次。用到的浏览器 API 只有 `localStorage`（`stores/auth` 初始化时读取），
> 测试里用内存实现顶替，没有为了测试改动业务代码。

---

## 14. 数据库压力排查与治理（2026-09-13）

> **起因**：消息未读数刷新过于频繁。顺着这条线做了一轮**全仓库数据库压力排查**，
> 结论是：真正的问题不是某条 SQL 写得差，而是**同一个数字被多处、反复地向数据库要**。
> 本章记录已修的部分、排查出但**未修**的部分，以及已确认没问题的部分。

### 14.1 本轮已修

| # | 问题 | 修改前 | 修改后 | 位置 |
|---|------|--------|--------|------|
| 1 | **未读数按路由变化无限刷新** | `AppShell` 监听 `route.fullPath`，**每次路由变化**打一次 `countByUserIdAndReadAtIsNull`；消息页自己又打一次（打开消息页 = 2 次）；标记已读/删除后再查一次 | 收进 `stores/notify.js` 单一来源：**30 秒最短间隔 + 并发去重 + 本地就地更新**；消息页复用列表的 `totalElements`；新增标签页切回时刷新（比定时轮询划算） | `frontend/src/stores/notify.js`、`AppShell.vue`、`MessagesView.vue` |
| 2 | **关系列表逐行查询（最大的一处）** | 一页 50 行 → 50 个 `FollowButton` 各自 `GET /user/{id}/relation`，而单条关系要跑 **6 条 SQL**；叠加 auth-service 鉴权时每请求 1 次用户行查询 → **约 51 次 HTTP、约 350 条查询** | 新增 `GET /api/user/relations?ids=`：**1 次请求、固定 6 条 SQL 覆盖整页**（4 个集合成员判断 + 2 个分组计数）；列表页由「这是哪个列表」直接推导已知位，批量接口失败也不退回逐行请求 | `UserRelationController`、`UserRelationService.relations`、`fetchRelations`、`RelationListView.vue`、`FollowButton.vue` |
| 3 | **热点榜刷新 N+1** | `for` 循环里逐条 `postRepository.findById` + 逐条 `countByPostIdAndCreatedAtAfter`，最多 32 次往返。该逻辑既被每日 6:00 定时任务调用，也被 `/plaza/hot` 冷缓存时同步调用 | 一次 `findAllById` + 一次 `GROUP BY` 批量计数，**2 次往返** | `PlazaHotService.refresh`、`PostCommentRepository.countByPostIdsAndCreatedAtAfter` |
| 4 | **`/auth/me` 每次导航一次** | `AppShell.onMounted` 无条件 `auth.loadProfile()`，而 AppShell 渲染在每个页面内 → 每次跨页导航 1 次请求，且 `/auth/me` 实际是 **2 次** `u_sys_user` 主键查询（鉴权 1 次 + `AuthService.me` 1 次）；资料设置页还额外重复一次 | 拆成两个语义：`loadProfile()` **保持「总是取最新」**（资料设置页要回填，不能被限频），`refreshProfileInBackground()` 带 **30 秒下限 + 并发去重** 供外壳使用 | `stores/auth.js`、`AppShell.vue` |
| 5 | **缺失排序索引（4 处 filesort）** | 关注列表 / 黑名单 / 未读列表 / 圈子列表均按 `created_at DESC` 排序，但没有「过滤列 + created_at」形状的索引 | 新增 `idx_follow_follower_created(follower_id, created_at)`、`idx_block_blocker_created(blocker_id, created_at)`、`idx_circle_created(created_at)`；`idx_notify_user_read` 扩展为 `(user_id, read_at, created_at)`。另补 `idx_user_role_enabled(role, enabled)`（内部码登录此前全表扫描） | `V3__relation_sort_indexes.sql`、`V10__circle_sort_index.sql`、`V4__unread_sort_index.sql` |

### 14.2 验证记录

**迁移**

```
auth-service:      V3 relation sort indexes   success=1
community-service: V10 circle sort index      success=1
notify-service:    V4 unread sort index       success=1
```

**EXPLAIN 前后对比（真机）**

| 查询 | 修改前 | 修改后 |
|------|--------|--------|
| 我关注的人 `WHERE follower_id=? ORDER BY created_at DESC` | Using filesort | `idx_follow_follower_created` + Backward index scan，**无 filesort** |
| 黑名单 `WHERE blocker_id=? ORDER BY created_at DESC` | Using filesort | `idx_block_blocker_created` + Backward index scan，**无 filesort** |
| 未读列表 `WHERE user_id=? AND read_at IS NULL ORDER BY created_at DESC` | Using filesort | `idx_notify_user_read` + Backward index scan，**无 filesort** |
| 未读角标 `COUNT(*) WHERE user_id=? AND read_at IS NULL` | — | `idx_notify_user_read`，**Using index（覆盖索引，不回表）** |
| 内部码登录 `WHERE role='ADMIN' AND enabled=1` | type=ALL 全表扫描 | `idx_user_role_enabled` |
| 圈子列表 `ORDER BY created_at DESC` | type=ALL + filesort | 表只有 3 行，优化器仍选全表扫描（此时确实更便宜）。`FORCE INDEX` 验证：`idx_circle_created` + Backward index scan、无 filesort，说明索引可用，数据量上来后会自然被选中 |

> 圈子那条要说清楚：**索引加对了，但当前数据量下优化器不选它**。这不是「没修好」，
> 而是 3 行的表全扫本来就更便宜。排查时如果只看 EXPLAIN 的 `type` 列，很容易误判成优化无效。

**单测**：**97 个后端用例全绿**。

**前端新增 3 个可执行测试**（`npm run test:gate` / `test:notify-frequency` / `test:profile-frequency`）

| 测试 | 断言数 | 覆盖 |
|------|--------|------|
| `test:notify-frequency` | 19 | 10 秒内 10 次路由变化**只打 1 次**后端；并发调用共享 1 次请求；失败也限频（服务挂掉后 5 次导航不会变成 5 次重试）；本地扣减/清零/覆盖**一次后端都不打**；退出登录后复位 |
| `test:profile-frequency` | 8 | 10 次导航只打 1 次 `/auth/me`；**间隔内 `loadProfile()` 仍然真的请求**（编辑页必须拿最新值，这是本次改动最大的风险点）；外壳刷新与编辑页加载合并成 1 次；未登录不请求；换账号不被上一个账号的时间戳挡住 |
| `test:gate` | 32 | 登录/注册验证码闸门（见 13.8） |

**批量关系接口端到端**（真机，注册 3 个真实用户）：**26 项断言全过** —— 双向标志不串
（`following` / `followedBy` 分别验证）、不存在的用户返回全 false、自己的 id 被排除、
超过 100 个 id 返回 400、`/api/user/{id}/relation` 单条接口无回归、匿名 401、清理后关系复位。

> 上述「修改前约 350 条查询」是按代码路径推算的（50 行 ×（6 条关系 + 1 条鉴权）+ 列表自身），
> **不是实测的 SQL 日志**：项目 `show-sql: false`，且演示库每表只有几行。

### 14.3 排查出但**未修**的问题（按优先级）

| 优先级 | 问题 | 触发频率 | 位置 | 建议修法 |
|--------|------|----------|------|----------|
| **高** | 计数列「读改写」且无 `@Version`：点赞数、浏览数、热度分、广告点击数、圈子成员/帖子数都是「查出来 +1 再整行 UPDATE」，并发下**静默丢更新**，且 UPDATE 会重写该行**所有列** | 每次点赞 / 浏览 / 评论 / 商品详情 / 广告点击 / 入圈 | `CommunityService.recordView/toggleLike/addComment`、`MarketService.detail`、`AdService.click`、`CircleService` | 改成原子 `UPDATE x SET c = c + 1 WHERE id = ?`（`@Modifying @Query`）。这既省一次 SELECT、又避免丢更新，是最值得做的下一项 |
| **高** | 浏览记录链路重：每个滚入视口的帖子 POST 一次 `/view`，服务端 4–5 条 SELECT + INSERT + 整行 UPDATE + **1 次 Feign 调用**（只为补作者昵称，而返回值只需要计数）；`seen` 集合在组件实例内，PostFeed 重挂载会重发 | 一屏 30 条 → 最多 30 次 POST，每次 5–6 条 SQL + 1 次跨服务调用 | `composables/useHeatView.js`、`CommunityService.recordView` | `seen` 提到模块作用域；计数改原子更新；去掉记录浏览时的 Feign 补全 |
| **中** | `我的` 页每次访问都算平台级 `COUNT(*)`，而**前端根本不渲染这些字段** | 每次进 `/me`：`/api/aid/stats` 3 个平台计数 + `/api/community/stats` 一次全表 `count()` | `AidService.stats`、`CommunityService.stats`、`MeView.vue` | 加短 TTL Redis 缓存，或干脆不下发这些字段（`RedisCache` 已在同两个服务里注入） |
| **中** | 派生删除查询逐行删：`deleteByPostId` 会先把行查出来再一条条 DELETE，且未配 `hibernate.jdbc.batch_size` | 删一条有 N 条点赞/评论/浏览记录的帖子 = 1 次 SELECT + N 次 DELETE | `CommunityService.deletePost`、`AidService`、`PlazaHotService.deleteByRankDate` | 改成 `@Modifying @Query("delete ...")`（仓库里 `UserFollowRepository.deleteBetween`、`NotificationRepository.markAllRead` 已是这个写法） |
| **中** | `relation()` 被四个写操作（关注/取关/拉黑/取消拉黑）用来拼响应，一次点击约 10 条 SQL | 每次关注/拉黑按钮 | `UserRelationService.follow/unfollow/block/unblock` | 由变更结果直接拼响应，不再回读一遍关系 |
| **中** | `UserService.toPublic()` 用 2 条查询取评分均值与条数，而批量聚合方法已存在 | 每次看他人主页 | `UserService`、`UserRatingRepository` | 改用已有的 `aggregateByTargetUserIds` |
| **低** | 创建接口在**同一事务内**为刚生成的 id 跑聚合（必然 0 行），再发一次跨服务调用只为取自己的昵称（而 `UserPrincipal` 里就有） | 每次发帖 / 发布求助 | `AidService.toResponse`、`CommunityService.toResponse` | 用已保存的实体 + 当前用户拼响应 |
| **低** | 死代码：`PostRepository.findByChannel/findByChannelAndCategory/findByAuthorId` 无调用方，而 `idx_post_channel_category_created` 只服务它们；`UserFollowRepository.findByFollowerIdAndFolloweeId` 无调用方 | — | 同上 | 删方法 + 删索引（注意 `idx_post_channel_kind_created` 已覆盖真实查询，别删错） |
| **低** | 无 `@Version` 导致重复提交触发唯一键冲突 → 500 而非幂等成功（不是丢更新，是健壮性） | 双击评分等 | `AidService`、`UserService` 的 find-or-create | 捕获 `DataIntegrityViolationException` 后重读 |
| **低** | 前端若干「操作后又整表重拉」：回收预约、圈子、集市在 POST/PUT 已返回完整对象后仍重新拉列表；发现页切换视图/种类会重复请求无法被该操作改变的数据（含热度榜） | 每次操作/切换 | `RecycleView`、`CircleView`、`MarketView`、`DiscoverView` | 用返回值就地打补丁；`if (value === view.value) return` |
| **低** | 启动期与看板的零头：各 `*DataInitializer` 为判断「是否已有数据」调了两次 `count()`；`AdminDashboardService` 串行 3 次 Feign（约 11 次 COUNT）；`CircleService` 里 `requireCircle` 取回整个 Circle 又丢弃 | 每次启动 / 管理台 | 同上 | 改用 `existsBy`；Feign 并行；`existsById` |

### 14.4 已确认**没有问题**的地方（排查过，不必再查）

- **没有无界读**：请求路径上没有任何裸 `findAll()`；分页上限由 `common/web/PageableConfig` 在代码里限制为 50（有单测断言），`?size=2000` 会被削到 50。
- **不可能有懒加载 N+1**：`backend/` 里**零** JPA 关联注解（外键都是普通 `Long`），且五个 JPA 服务都设了 `open-in-view: false`。
- **列表装配确实是批量做的**：`CommunityService.toResponses`、`CircleService`、`RecycleService`、`MarketService`、`AidService.toResponses`、`UserService.briefs` 都是「固定几次查询 + 1 次 Feign」，与页大小无关。
- **非 auth 服务的鉴权不查库**：`common/security/JwtAuthFilter` 直接从 JWT 声明构造 principal。只有 auth-service 会查用户行（已在上面的 14.1-4 处理）。
- **没有轮询**：全仓库只有 4 个重复定时器（广告轮播、短信倒计时 ×2、验证码冻结倒计时），都不发网络请求。
- **批量替代品已被正确使用**：`PostCommentRepository.countByPostIds`、`UserRatingRepository.aggregateByTargetUserIds`、`AidRatingRepository.aggregateByAidIds`、`CircleMemberRepository.findByUserIdAndCircleIdIn`、`PostLikeRepository.findLikedPostIds` 都在用（唯一例外是 `UserFollowRepository.findFollowingIds`——它写好了却没人调用，正是 14.1-2 补上的那块）。
- **401 刷新做了单飞**：`api/http.js` 用 `refreshing` + `_retry`，N 个并发 401 只会触发 1 次 `POST /auth/refresh`。
- **定时任务只有一个**：`PlazaHotService` 每日 6:00，只重写当天 ≤16 行（多实例重复执行的问题见 12.4，仍未做 ShedLock）。

---

*本文档基于仓库代码走查编写，所有"现状"结论均可回溯到对应源文件。规划部分为建议方案，最终排期需结合实际资源确认。*
*第 9、10、11、13 章的完成项均已通过编译、单测与真机验证（第 13 章另有 52 项端到端断言），未验证的事项已明确标注；第 12 章为选型建议。*
