# 城区互助（邻里汇）

前后端分离：`backend/`（微服务多模块）+ `frontend/`（Vue3）。
服务注册与发现使用 **Nacos**，服务间调用使用 **OpenFeign**。

规划与扩展路线见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

## 后端模块

```
backend/
├── common/             # 公共 JWT / API 响应 / 日志过滤器 / 分页防护
├── api/                # 跨服务调用契约：Feign 客户端接口、共享 DTO、令牌透传
├── gateway/            # API 网关 :8080
├── auth-service/       # 鉴权与用户 :8081（同时是用户信息的提供方）
├── aid-service/        # 求助 :8082
├── community-service/  # 社区 :8083
├── ad-service/         # 广告轮播 :8084
├── notify-service/     # 消息通知 :8085
├── file-service/       # 文件上传 :8086（MinIO，浏览器不直连对象存储）
└── schema.sql          # 已废弃，见文件头说明；建表请以各服务的 Flyway 脚本为准
```

> 原先的 `eureka-server` 模块已删除，注册中心改用已有的 Nacos，不再需要单独维护注册中心进程。

## 模块与子模块（信息架构）

顶级导航只有四个模块（管理员多一个「管理台」），**每个模块与子模块都配了一个线性小图标**
（统一 24×24 视口 / 1.7 描边 / `currentColor`，定义在 `frontend/src/router/modules.js`）。
子模块不挤占顶级导航，而是落在**二级导航——一条只有子模块的 tab 条**里：

| 顶级模块 | 子模块 | 路径 |
|---|---|---|
| **发现** | 生活广场（原「发现」的内容） | `/discover` |
| | 集市 / 圈子 / 回收 | `/discover/market` `/discover/circle` `/discover/recycle` |
| | 邻里互助（原先是顶级模块「邻里」） | `/discover/neighbor` |
| **校园** | 论坛（须通过**学生认证**） | `/campus` |
| | （认证申请页，本身不受门禁限制） | `/campus/verify` |
| **我的** | 个人主页（原「我的」的内容） | `/me` |
| | 资料编辑（挂在「个人主页」这个 tab 下，不是独立子模块） | `/me/profile/edit` |
| | 设置 | `/me/settings` |
| | 广告位（须先**开通资质**） | `/me/ad` |
| **消息** | — | `/messages` |
| 管理台（仅管理员） | — | `/admin` |

二级导航**只有子模块条这一样东西**：原先它左侧还有一栏「模块名 + 副标题」，
写的是「你在大模块的哪一节」——而这句话子模块条本身用高亮就说清楚了，再重复一遍只是白占高度。
页面主操作（`#actions` 插槽）因此从粘性栏移到了内容区顶部：粘性栏的高度必须是定值
（锚点偏移与页面里的 sticky 侧栏都按它算），而「这页有没有操作按钮」是逐页变化的。

结构定义在 `frontend/src/router/modules.js`，是**唯一事实来源**：顶级导航、子模块条、路由 meta、
路由守卫、图标读的是同一份数据。分散在多处时，加一个子模块要改好几个地方，
漏掉任何一处就会出现「导航里有入口、点进去 404」或者反过来「页面能打开但没有入口」。

几个取舍：

- **URL 跟着层级走**（`/market` → `/discover/market`），并逐条保留旧路径重定向。
  通知里的链接与收藏夹不会因为改版变成 404。
- **只渲染模块名与副标题、不渲染子模块条**的模块（消息 / 管理台）没有子模块条；
  只有一个子模块的模块（校园）**照样渲染**——它现在是页面上唯一的层级提示。
- **认证页显式 opt-out**（`meta.hideTabs`）：认证没过的人点不动「论坛」。
- **「资料编辑」与关系列表**（`/me/following` 等）用 `meta.tab` 标注归属，
  而不是按路径前缀猜——它们属于某个 tab，但本身不是 tab。
- 子模块条会把粘性栏垫高 48px，因此 `--nav-stack` 由 AppShell 按当前路由**重新声明**
  （见 `styles.css` 的 `--nav-stack-tabs`）。页面里的 sticky 侧栏与 `scroll-margin-top`
  读的都是 `var(--nav-stack)`，改一处即可全部跟上；hash 锚点偏移在 `router/scroll.js` 里
  按同一规则计算。

## 深色模式与偏好设置

「设置」（`/me/settings`）目前只有两项，都是**设备级**偏好（存 localStorage，不跟账号走）：

| 设置项 | 取值 | 落地方式 |
|---|---|---|
| 国家和地区 / 语言 | 中国大陆·简体中文 / 中国香港·繁體中文 / 中国台湾·繁體中文 / 美国·English / 日本·日本語 | 写入 `<html lang>` 与 `data-locale` |
| 深色模式 | 跟随系统 / 浅色 / 深色 | 写入 `<html data-theme>`，由 `styles.css` 的令牌覆盖块接管 |

**深色模式是「成组翻转令牌」而不是逐组件写深色样式**：整套界面本来就只通过 `var(--*)` 取色，
把「画布 / 表面 / 墨色 / 描边 / 状态色」这几组值换掉，深浅两套主题自动成立。
逐个组件写深色分支的代价是每加一个页面都要记得写两遍，漏掉的那处在浅色下永远看不出问题。

层级关系保持不变：浅色下是「米白页面 + 白色卡片（卡片更亮）」，深色下翻转为
「纯黑页面 + 近黑卡片（卡片更亮）」——**卡片始终比页面亮**，所以瓦片、面板、悬浮层的层次感
不会因为换主题而消失。

主题在 `main.js` 里**先于 `mount`** 落地（`applyStoredPrefs()`）。放进组件的 `onMounted`
会让浅色界面先画一帧再跳成深色，对深色模式用户表现为「每次刷新闪一下白屏」。

> **界面文案的多语言尚未接入**：地区/语言选择会写入 `lang` 并记录偏好，
> 但不会把按钮文字换成英文。设置页上写明了这一点，避免给出「切了没反应」的错觉。

## 学生认证与广告位资质（两道管理员审核闸门）

这两条链路都是「用户提交材料 → 管理员审核 → 通过后解锁某个能力」，但**闸门的位置完全不同**，
不要混为一谈：

| | 学生认证 | 广告位资质 |
|---|---|---|
| 闸门放开的是 | 进入**校园模块**（论坛） | 提交**广告位申请** |
| 落在哪个服务 | `auth-service`（表 `u_r_student_verification`） | `ad-service`（表 `u_r_ad_qualification`） |
| 通过后写入 | `u_r_sys_user.student = true`，并写回学校/专业/年级 | 该用户具备提交广告位申请的资格 |
| 审核接口 | `/api/admin/student/verifications/**` | `/api/ad/qualifications/**` |
| 用户接口 | `/api/student/verification/**` | `/api/ad/qualification/**`（注意：管理侧是复数） |

**学生认证**

- 接口：用户侧 `/api/student/verification/**`，管理侧 `/api/admin/student/verifications/**`。
- ⚠ **新增业务前缀必须同步改网关路由。** 网关的路由是**白名单式**的
  （`discovery.locator.enabled=false`，只放行显式声明的 `Path`）。
  学生认证走独立路由 `auth-student` → `Path=/api/student/**`，不要把它
  只追加进 `/api/auth/**,/api/admin/**,/api/user/**` 那条逗号列表里。
  漏掉时表现就是 **「认证页一加载就 404」**，而服务本身、`SecurityConfig`、
  Nacos 注册全是正常的。以后 auth-service 再加前缀，在
  `gateway/src/main/resources/application.yml` 里**单独加一条**路由。
- `student` 曾经是个人资料里的一个**自选复选框**（`UpdateProfileRequest.student`），
  勾一下就展示学校与专业——等于让人自己声明一个需要凭证的身份。现在它只能由审核通过写入，
  该字段已从 `UpdateProfileRequest` 中移除。
- `V5__student_verification.sql` 会把存量 `student = 1` 的用户**重置为 0**：旧值没有任何凭证，
  不能继续当作进入校园模块的凭据。
- 演示账号 `13900000000` 也**不再是学生**（`DataInitializer` 里那个 `true` 曾是绕过门禁的后门）。
  演示完整链路：用它提交认证 → 用 `13800000000` 在管理台审核通过。
- 同一用户至多一条 `PENDING`（服务层保证，不是数据库唯一约束——`(user_id, status)` 加 UNIQUE
  会让第二条驳回历史写不进去）。
- 改/撤回他人的申请一律返回 **404 而不是 403**，避免用 id 探测别人的申请是否存在（沿用本项目既有约定）。

**广告位资质**

- 一个用户一份资质、多条广告，所以**不复用 `u_r_ad`**：`u_r_ad` 的一行是一条广告。
  合并会出现「资质被驳回导致已上线广告跟着失效」这类耦合。
- 闸门在 `AdService.apply(...)` 里：没有 `APPROVED` 资质时 `POST /api/ad/applications` 返回 403。
  只拦**新提交**，存量已通过/待审的广告不受影响。
- 前端 `/me/ad` 在未开通时**不渲染广告申请表单**，只说明原因并给入口——否则用户填完一屏
  才被一句他从没见过的规则拒掉。
- 通过资质**不会让任何广告上线**，两条队列互相独立。

## 基础设施

| 组件 | 地址 | 说明 |
|------|------|------|
| MySQL | `192.168.198.139:3306` / `LinLiHui` | 账号 `root` / `123456` |
| Nacos | `192.168.198.139:8848` | 仅使用**服务发现**，未使用配置中心；当前 `auth_enabled=false` |
| Redis | `192.168.198.139:6379` | 密码 `123456`；用于验证码、令牌吊销、限流与热点缓存 |
| MinIO | `192.168.198.139:9000` | 默认账号 `minioadmin` / `minioadmin`；bucket `linlihui`。环境变量 `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` |
| Sentinel Dashboard | `192.168.198.139:8858` | 应用通过 `SENTINEL_DASHBOARD` 接入。**不要占用 8080**（网关已用） |

## Docker Compose 部署

根目录的 [`docker-compose.yml`](docker-compose.yml) 起**7 个后端服务**，接入**已存在的外部网络 `cloud-ur`**；
[`backend/Dockerfile`](backend/Dockerfile) 是 7 个服务共用的多阶段构建文件（JDK 17）。

部署目标是 **192.168.198.139**（中间件与后端同机）。在该虚拟机上执行：

```bash
docker network inspect cloud-ur   # 确认外部网络已存在
docker compose up -d --build      # 不创建 .env 也能直接起来
docker compose ps                 # 等 7 个服务 healthcheck 全部 healthy
curl -i http://192.168.198.139:8080/api/ad/banners
```

**基础设施不在这个 compose 里**：MySQL / Redis / Nacos / Sentinel Dashboard / MinIO 已经跑在同一台虚拟机上并接入 `cloud-ur`，
compose 只通过 `networks.cloud-ur.external: true` 接入，`docker compose down` 不会误删它们。

> ⚠ **7 个后端服务必须都在 `cloud-ur` 上。** 各服务把自己的**容器 IP** 注册到 Nacos，
> 网关用 `lb://<服务名>` 取到该 IP 后**直连**。若不在同一张网络，症状不是「连不上 Nacos」，
> 而是实例明明注册上去了、网关却返回 `503`。
>
> 注意这条链路和「服务怎么访问中间件」是两回事：中间件地址默认走**宿主机已发布的端口**
> （`192.168.198.139:3306` 这类，见 `## 基础设施`）。这是项目现有配置一直在用的地址，必然可达，
> 且不依赖中间件容器叫什么名字。想改走 `cloud-ur` 的容器 DNS 名（少一次经宿主机的 NAT 回环），
> 在 `.env` 里改 `MYSQL_HOST` / `REDIS_HOST` / `NACOS_SERVER_ADDR` / `SENTINEL_DASHBOARD` / `MINIO_ENDPOINT` 即可 ——
> 名字先用 `docker network inspect cloud-ur --format '{{range .Containers}}{{.Name}} {{end}}'` 确认。

几个要点：

| 事项 | 说明 |
|------|------|
| **顶层 `name: chengqu-huzhu`** | 必需。仓库目录名是中文，Compose 拿目录名推导 project name，而 project name 只允许 `[a-z0-9_-]`，不显式指定会直接报 `invalid project name` |
| 镜像 tag | `chengqu-huzhu/<服务名>:${IMAGE_TAG:-latest}`，改 `.env` 的 `IMAGE_TAG` 即可做版本管理 |
| 端口暴露 | **只有 gateway 映射到宿主机**（默认 `8080`）。其余 6 个服务只在 `cloud-ur` 内网可达，调试用的 `ports` 已注释在文件里 |
| 健康检查 | 走 TCP 语义（`curl` 不带 `-f`，404 也算进程已监听）。项目**没有引入 actuator**，因此没有 `/actuator/health` 可用 |
| `NACOS_INSTANCE_IP` | 保持为空，由 nacos-client 探测容器 IP；出现「注册上了但网关 503」时再显式指定 |
| `MINIO_ENDPOINT` | 用内网地址是**正确的**：`file-service` 走 `getObject` 代理下载，浏览器不直连对象存储 |
| 构建加速 | `MAVEN_MIRROR_URL` 默认指向阿里云镜像；能直连中央仓库时在 `.env` 里置空 |
| 单独构建 | `docker build -t chengqu-huzhu/gateway:latest --build-arg SERVICE=gateway backend/`，上下文必须是 `backend/`，因为 `common`/`api` 是模块内依赖 |
| 构建资源 | 默认会**并发**构建 7 个 Maven 镜像。这台虚拟机还跑着 MySQL/Nacos/Redis/MinIO/Sentinel，内存紧张时用 `COMPOSE_PARALLEL_LIMIT=2 docker compose build` 降并发 |

> 网关的 CORS 白名单目前硬编码为 `localhost:5173`（见 `gateway/application.yml` 的 `#todo whiteLine配置`）。
> 前端部署到别的域名时，浏览器会先卡在预检请求上——这处需要一并调整。

## 服务注册与发现（Nacos）

每个服务与网关都通过 `spring-cloud-starter-alibaba-nacos-discovery` 注册到 Nacos：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:192.168.198.139:8848}
        ip: ${NACOS_INSTANCE_IP:}   # 留空为自动探测
```

- 版本配比：**Spring Cloud Alibaba `2023.0.3.2`** ↔ Spring Cloud `2023.0.3`（内置 nacos-client 2.4.2）。
- 网关路由仍写 `lb://aid-service` 这样的服务名，由 Spring Cloud LoadBalancer 从 Nacos 取实例，**无需改动路由配置**。
- **`NACOS_INSTANCE_IP` 什么时候要填**：本机有多张网卡时（虚拟网卡、VPN、多网段），自动探测可能选中一个 Nacos 与其他服务都访问不到的地址，导致网关拿不到实例。此时显式指定同网段的地址即可，例如 `NACOS_INSTANCE_IP=192.168.198.1`。

## 跨服务调用（api 模块 + OpenFeign）

### 契约与边界

`api` 模块只放三类东西：**Feign 客户端接口**、**跨服务共享 DTO**、**令牌透传拦截器**。
四个业务服务都依赖它，实现「提供方写接口、消费方拿接口调」的单一契约。

内部接口统一位于 `/internal/**`，**刻意不带 `/api` 前缀**：

| 提供方 | 接口 | 消费方 | 用途 |
|--------|------|--------|------|
| auth-service | `GET /internal/user/brief?ids=` | aid-service、community-service | 批量补全用户昵称与头像 |
| auth-service | `GET /internal/user/{id}/brief` | — | 单个用户精简信息 |
| aid-service | `GET /internal/aid/platform-stats` | auth-service | 管理台看板 |
| aid-service | `GET /internal/aid/user-stats/{userId}` | auth-service | 用户主页聚合 |
| community-service | `GET /internal/community/platform-stats` | auth-service | 管理台看板 |
| community-service | `GET /internal/community/user-stats/{userId}` | auth-service | 用户主页聚合 |
| ad-service | `GET /internal/ad/platform-stats` | auth-service | 管理台看板 |

**两道隔离**：

1. 网关的路由规则只匹配 `/api/**`，`/internal/**` 不在任何规则内 → 外部经网关访问一律 404（已实测）。
2. `/internal/**` 同样受 Spring Security 保护，需要合法 JWT。`FeignAuthForwardConfig` 会把当前请求的 `Authorization` 头原样透传给下游，因此下游看到的是**调用方的真实身份**，无需另造一套服务间口令。

### 已实现的三个跨服务功能

1. **求助列表 / 详情补全发布者与帮助者信息**（aid-service → auth-service）
   表里冗余的 `publisher_name` / `helper_name` 会被 auth-service 的权威数据覆盖，头像也改为取真实头像。
   一页数据只发 **1 次**批量调用，不破坏此前修好的 N+1 优化。
2. **动态列表 / 详情补全作者信息**（community-service → auth-service）
   同上，`author_name` / `author_avatar` 同理。广场热度榜的作者信息也走同一入口。
3. **用户主页聚合**（auth-service → aid-service + community-service）
   `GET /api/user/{id}/home` 一次返回「资料 + 求助统计 + 动态统计」，替代前端原先的 3 次往返。
4. **管理台看板聚合**（auth-service → aid-service + community-service + ad-service）
   `GET /api/admin/dashboard` 汇总用户数（本地）与求助 / 动态 / 广告三域统计。

### 降级策略

跨服务调用一律**失败即降级**，不向上抛异常：

| 场景 | 行为 |
|------|------|
| auth-service 不可用 | 列表照常返回，昵称回退到数据库冗余值，头像留空（前端展示占位图） |
| aid/community/ad 不可用 | 对应统计字段返回 `null`，前端显示「暂不可用」而**不是 0**，避免把故障伪装成「没有数据」 |

已实测：停掉 auth-service 后，求助与动态列表仍返回 `code=0`，服务端日志输出
`调用 auth-service 查询用户失败，降级使用本地冗余字段`。

> 提示：`publisher_name` 这类冗余列现在的定位是**下游不可用时的兜底**，不再是主数据来源。

## 文件上传（MinIO + file-service）

浏览器不直连 MinIO。上传走 `POST /api/file/upload`（需登录，`purpose` 为 `avatar` / `cover` / `post` / `aid` / `adapply` / `ad` / `student` / `qualification`，**只有 `ad` 需管理员**），展示走 `GET /api/file/objects/**`（公开读）。业务表只存相对路径 `/api/file/objects/...`。单张 png/jpg/webp ≤ 8MB，帖子/求助最多 9 张。

> **`student`**（学生证/校园卡）与 **`qualification`**（营业执照等）是本轮新增的两个用途，
> 服务于上面两道审核闸门。它们单独成目录而不是复用 `avatar`/`adapply`：
> 这两张图是**审核凭据**，与用户主动展示的图片性质不同，将来要做「仅本人与管理员可读」时
> 能按目录收敛权限，不必把用户的所有头像也一起锁掉。
>
> 目录白名单由 `FilePurpose` 枚举推导（见 `FileObjectService.OBJECT_KEY`），
> 所以新增用途只加一个枚举值即可，读写两侧自动跟上。

> **前端会在上传前等比压缩**（`ImageUploader`）：手机直出照片动辄 3–8MB、4000px 宽，
> 直接丢给后端会撞上 multipart 上限——这正是「封面图无法上传」的成因。
> 压缩按用途取长边上限（头像 512 / 帖子 1600 / 封面与广告 1920），压不动就原样上传，
> 不允许把「本来能传的文件」变成传不了。8MB 只是最后一道兜底。

> **`cover`** 是个人主页封面图；**`adapply`** 是用户申请广告位时上传的广告图，
> 与管理员直投的 `ad` 分开——合并就等于给所有人开了管理员上传通道。
> 图片能不能上线由审核状态决定，与上传权限无关。
>
> file-service 读取对象时的目录白名单**由 `FilePurpose` 枚举推导**
> （见 `FileObjectService.OBJECT_KEY`），新增用途不必再手改正则——以前是硬编码的
> `(avatar|post|aid|ad)`，漏改的后果是「上传成功、读取 404」，且只在真去读那张图时才暴露。

## Sentinel

- **Feign 熔断**：`feign.sentinel.enabled=true`，四个内部客户端带 `FallbackFactory`；下游持续故障时不再每次等满 2s+4s。列表补全仍保留 try-catch 打底。
- **网关限流**：登录约 5 QPS/IP，上传约 2 QPS/IP，其余 `/api/**` 宽松挡突发。触发后 HTTP 429，文案「请求过于频繁，请稍后再试」。
- Dashboard 地址由 `SENTINEL_DASHBOARD` 注入，默认 `192.168.198.139:8858`。

## Redis 与会话治理

五个业务服务（含 file-service）都引入了 Redis（网关不需要，它不校验 JWT）。用途分四类：

| 用途 | 位置 | 说明 |
|------|------|------|
| **登录状态** | `CaptchaService`、`SmsCodeService` | 图形验证码与短信验证码，过期由 Redis TTL 自动回收 |
| **令牌吊销** | `TokenRevocationService`（common） | 让 JWT「登出即失效」 |
| **接口限流** | `RedisRateLimiter`（common）、`AuthRateLimitFilter` | 登录 / 注册 / 短信下发的防爆破 |
| **热点缓存** | `RedisCache`（common） | 广告轮播、广场热度榜 |

所有 key 统一前缀 `cqh:`，集中定义在 `RedisKeys`——key 是跨服务共享的状态契约，
散落在各处极易出现「这边写、那边查不到」的静默故障。可用 `KEYS cqh:*` 排查。

### 令牌吊销：两级机制

JWT 是无状态的，签发后在有效期内天然无法撤回。原来的 `logout()` 只把用户状态改成 OFFLINE，
令牌照样能用满 30 分钟。现在用 Redis 补上这一环：

| 层级 | 机制 | 覆盖场景 |
|------|------|----------|
| **会话级（sid）** | 一次登录签发的 access + refresh 共用同一个 `sid` 声明；登出时吊销该 sid | 普通登出。**两个令牌同时失效**——这是必需的，客户端登出只上送 access token，服务端拿不到 refresh token，只吊销 access 会留下「登出后仍能用 refresh 续期」的口子 |
| **用户级（令牌版本）** | 签发时把当前版本写入 `ver` 声明，校验时比对；「退出所有设备」自增版本号 | 强制下线全部会话，O(1) 且精确 |

> 为什么不使用「签发时间水位线」：JWT 的 `iat` 只有秒级精度，与阈值同秒签发的令牌无法区分，
> 实测会出现「执行退出所有设备后，另一台设备的会话仍然存活」。改用版本号精确比对后问题消失。

由于四个业务服务都各自校验 JWT，`TokenRevocationService` 放在 `common` 中共享——
否则吊销在 aid-service 生效、在 community-service 不生效，只给了「已经下线」的错觉。

**接口**：`POST /api/auth/logout`（普通登出）、`POST /api/auth/logout?all=true`（退出所有设备）。
前端在「个人信息设置」页提供了「退出所有设备」入口。

### 限流规则

`AuthRateLimitFilter` 拦截以下入口，按客户端 IP 计数（固定窗口，Lua 保证 INCR 与 EXPIRE 原子）：

| 入口 | 默认阈值 | 配置项 |
|------|----------|--------|
| `/api/auth/login/password`、`/login/sms`、`/login/internal` | 20 次 / 分钟 / IP | `RATE_LIMIT_LOGIN_IP` |
| `/api/auth/register` | 10 次 / 小时 / IP | `RATE_LIMIT_REGISTER_IP` |
| `/api/auth/sms/send` | 10 次 / 分钟 / IP | `RATE_LIMIT_SMS_IP` |

另有**手机号维度**的第二层限流（`AuthService.requireLoginAttemptAllowed`，10 次/分钟/手机号，
配置项 `RATE_LIMIT_LOGIN_PHONE`）。IP 限流换 IP 即可绕过，两层叠加才有意义。

短信还有手机号维度的发送间隔（`SET NX PX`）与每日上限（`INCR` 到当天结束）。

> **客户端 IP 取 `X-Forwarded-For` 的最后一段，而不是第一段。** 网关对它是「追加」语义：
> 客户端发来的伪造值会变成 `伪造IP, 真实IP`。取第一段的话，攻击者每次换一个伪造值就能完全绕过限流。

### 缓存策略

| 数据 | TTL | 失效方式 |
|------|-----|----------|
| 广告轮播 | 60 秒 | 后台增删改立即失效；**点击不失效**（否则每次点击都清缓存，缓存形同虚设，代价仅是响应里的 `clickCount` 最多滞后 60 秒） |
| 广场热度榜 | 10 分钟 | 定时刷新与手动刷新都主动失效；**空榜单不缓存**，否则会压制控制器里「榜单为空则同步重算」的兜底逻辑 |

### 降级策略

Redis 是辅助设施，不应成为新的单点。除验证码外，所有 Redis 能力都实现了优雅降级：

| 能力 | Redis 不可用时的行为 |
|------|---------------------|
| 令牌吊销查询 | **放行**（fail-open）。令牌本身仍有 30 分钟自然过期兜底，让全站登录一起挂掉的代价更大。需要严格语义时设 `JWT_REVOCATION_FAIL_CLOSED=true` |
| 接口限流 | **一律放行**，只打一次 WARN 日志 |
| 缓存 | 按未命中处理，直接查库；缓存内容损坏时自动删除该 key |
| 图形验证码 | **不可用**（无法登录）。这是刻意的取舍：验证码必须跨实例共享才有意义，退回内存会在多实例下静默出错 |

## 数据库

- MySQL `192.168.198.139:3306` / `LinLiHui`
- 表前缀 `u_r_`：`u_r_sys_user`、`u_r_aid_request`、`u_r_post`、`u_r_post_like`、`u_r_ad` 等 11 张表
- 认证与资质各占一张表：`u_r_student_verification`（学生认证）、`u_r_ad_qualification`（广告位资质）
- 控制台**关闭** Hibernate SQL 刷屏，只保留业务交互日志：`[网关]` `[API]` `[鉴权]` `[求助]` `[社区]` `[广告]` `[用户]`

### 建表与变更（Flyway）

建表**不再由 Hibernate 负责**，`ddl-auto` 已改为 `validate`（只校验实体与表是否一致，不修改表）。
每个服务只维护自己领域的表，迁移脚本位于各自 `src/main/resources/db/migration/`：

| 服务 | 脚本 | 涉及表 |
|------|------|--------|
| auth-service | `V1__init_auth.sql`、`V2__user_relation.sql`、`V3__relation_sort_indexes.sql`、`V4__user_cover.sql`、`V5__student_verification.sql` | `u_r_sys_user`、`u_r_user_rating`、`u_r_user_follow`、`u_r_user_block`、`u_r_student_verification` |
| aid-service | `V1__init_aid.sql`、`V2__indexes_aid.sql`、`V3__normalize_aid_board.sql` | `u_r_aid_request`、`u_r_aid_rating`、`u_r_aid_helper_review` |
| community-service | `V1__init_community.sql`、`V2__indexes_community.sql`、`V3__normalize_post_channel.sql` | `u_r_post`、`u_r_post_comment`、`u_r_post_like`、`u_r_post_view`、`u_r_plaza_hot` |
| ad-service | `V1__init_ad.sql`、`V2__indexes_ad.sql`、`V3__ad_application.sql`、`V4__ad_qualification.sql` | `u_r_ad`、`u_r_ad_qualification` |

四个服务共用一个库，但各用独立的历史表（`flyway_schema_history_{auth,aid,community,ad}`），互不干扰。

**改表流程**：在对应服务下新增 `V{n}__描述.sql`，**不要修改已执行过的脚本**（否则校验和不匹配会启动失败）。
服务启动时会自动执行未应用的迁移；对已有数据的库，`baseline-on-migrate` 会把现状标记为基线 0，再从 V1 开始幂等执行。

> 注意：枚举字段（`board`、`channel`、`status`、`role` 等）被映射为 MySQL 原生 `ENUM(...)`。
> **新增枚举值必须写迁移脚本** `ALTER TABLE ... MODIFY COLUMN ...`，不能只改 Java 枚举。

## 环境变量

所有配置项都有默认值，**开箱即可启动**；部署到可被外部访问的环境前必须覆盖下列变量。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_URL` | `jdbc:mysql://192.168.198.139:3306/LinLiHui?...` | 数据库连接串 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库口令 |
| `NACOS_SERVER_ADDR` | `192.168.198.139:8848` | Nacos 地址 |
| `NACOS_INSTANCE_IP` | 空（自动探测） | 注册到 Nacos 的实例 IP，**多网卡机器建议显式指定** |
| `NACOS_NAMESPACE` / `NACOS_GROUP` | 空 / `DEFAULT_GROUP` | 命名空间与分组 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 空 | Nacos 开启鉴权时填写 |
| `REDIS_HOST` / `REDIS_PORT` | `192.168.198.139` / `6379` | Redis 地址 |
| `REDIS_PASSWORD` | `123456` | Redis 密码 |
| `REDIS_DATABASE` | `0` | Redis 库号 |
| `RATE_LIMIT_ENABLED` | `true` | 是否启用敏感接口限流 |
| `RATE_LIMIT_LOGIN_IP` / `RATE_LIMIT_LOGIN_PHONE` | `20` / `10` | 登录限流（每分钟，IP / 手机号） |
| `RATE_LIMIT_REGISTER_IP` / `RATE_LIMIT_SMS_IP` | `10` / `10` | 注册（每小时/ IP）、短信（每分钟/ IP） |
| `JWT_REVOCATION_FAIL_CLOSED` | `false` | Redis 故障时是否拒绝令牌。默认放行 |
| `JWT_SECRET` | 源码内置开发密钥 | **必须覆盖**：五个进程必须使用同一个值，否则令牌互不认。仍用默认值时启动日志会打印安全告警 |
| `JWT_ACCESS_EXPIRE_MINUTES` | `30` | 访问令牌有效期（分钟） |
| `JWT_REFRESH_EXPIRE_DAYS` | `7` | 刷新令牌有效期（天） |
| `INTERNAL_LOGIN_CODE` | `8461` | 管理员内部码登录口令。**生产必须覆盖，或置空以彻底关闭该登录方式** |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 允许的跨域来源（仅 auth-service 读取） |

## IDEA 启动顺序

打开 `backend/pom.xml` 作为 Maven 工程。**不再需要启动注册中心**（Nacos 是外部已有的服务），依次运行：

1. `AuthServiceApplication`（8081）
2. `AidServiceApplication`（8082）
3. `CommunityServiceApplication`（8083）
4. `AdServiceApplication`（8084）
5. `GatewayApplication`（8080）← 前端只访问此端口

顺序本身不严格（Nacos 是拉取式发现），但先起业务服务可以避免网关启动初期取不到实例。

命令行打包与运行：

```bash
cd backend
mvn -DskipTests package
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar   # 其余服务同理
```

多网卡机器上建议：

```bash
java -jar aid-service/target/aid-service-1.0.0-SNAPSHOT.jar --spring.cloud.nacos.discovery.ip=192.168.198.1
```

## 测试

### 后端单测

```bash
cd backend
mvn test
```

现有 **122 个用例**，集中在最高风险的几块逻辑：

| 模块 | 用例数 | 覆盖重点 |
|------|--------|----------|
| common | 34 | 令牌吊销（会话 + 令牌版本）、Redis 限流边界与降级、缓存脏数据自愈、单页条数上限、文件地址规范化 |
| auth | 41 | **图形验证码冻结期状态机与一次性消费**、客户端 IP 提取（伪造 `X-Forwarded-For` 不能绕过限流）、**学生认证状态机**（重复提交被拒、驳回意见写入、非本人 404、通过后写回 student/学校） |
| aid | 27 | 求助状态机与越权校验、列表装配的 N+1 回归保护、通知投递契约与降级 |
| community | 18 | 动态列表批量装配、点赞翻转、浏览去重、帖子种类与模块校验、Feign 补全与降级 |
| ad | 23 | **广告位资质状态机**（重复提交、驳回与重新提交、非本人 404、整页只发一次 Feign 与降级）与**申请闸门**（无资质 403 且不落库、有资质放行、审核存量申请不查资质） |
| file | 2 | 图片文件头魔数校验 |

> 用例数按 `mvn -pl <模块> -am test` 的实测结果记。注意 `-am` 不能省：`common` 与 `api`
> 是模块内依赖，单独构建某个模块时本地仓库里没有它们的 SNAPSHOT，会直接报
> `Could not find artifact com.chengqu:api:jar:1.0.0-SNAPSHOT`——这是构建方式问题，不是测试失败。

> 涉及数据库的改动（尤其是迁移脚本）请在**空库**与**存量库副本**上各验证一次再合入。

测试配置说明：`aid-service` 与 `community-service` 的 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
指定了 `mock-maker-subclass`。Mockito 5 默认的 inline mock maker 需要向 JVM 自附加 agent，
在加固过的容器 / 受限沙箱中会报 `Could not initialize inline Byte Buddy mock maker`；
本项目的测试只 mock 接口，改用 subclass maker 即可在受限环境正常执行。

### 前端：可执行测试（不需要浏览器）

```bash
cd frontend
npm run test:gate               # 登录/注册验证码闸门，32 项断言
npm run test:notify-frequency   # 未读数刷新频率，19 项断言
npm run test:profile-frequency  # 用户资料刷新频率，8 项断言
npm run test:design             # 设计系统体检（SFC 编译 + 设计语言硬规则）
npm run test:shell              # 页面骨架：SSR 渲染全部 17 个页面 + 子模块条 + 问候语 + 封面，195 项断言
npm run test:scroll             # 路由滚动策略 + 锚点偏移 + 信息架构与路由表一致性，50 项断言
npm run test:relation           # 关注 / 拉黑业务（状态机 + 按钮渲染 + 入口存在），30 项断言
npm run test:ad                 # 广告位：申请 / 审核 / 通过后管理 / 修改申请，45 项断言
```

`test:design` 与 `test:shell` 是 UI 改版时补的两道网：

- **`test:design`** 把设计语言写成断言：不出现 `font-weight: 500`、不出现装饰性
  `radial/conic-gradient`、`box-shadow` 只能是 `none` / 发丝环 / `var(--product-shadow)`，
  以及**模板里用到的组件必须在本文件 import 过**。最后一条不是洁癖：编译器对未解析的
  组件只报警告不报错，构建照样通过，结果是页面「少了一整块」却没人发现——真发生过
  （四个页面用了 `<PageHero>` 却没 import，大标题直接消失）。
- **`test:shell`** 用 Vite SSR 把每个页面真渲染一遍，断言骨架契约：渲染不抛错、
  纯黑全局导航 / 子模块条 / `<main>` / 页脚各一份、**每页最多一个 `<h1>`**
  （页面大标题属于 `#hero` 的 PageHero）、子模块条只在有子模块的模块上出现且高亮了当前子模块、
  顶级导航只剩模块且每项都带图标、通栏瓦片的 tone 映射到正确的表面类。SSR 不执行 `onMounted`，
  因此不产生网络请求。
  >
  > 路由 meta 是用 `routeLocationKey` **覆盖注入**的，不是「真导航一次再读 currentRoute」——
  > 后者要 `await router.isReady()`，在 SSR 单进程渲染 17 个页面的场景下会把整个测试卡死
  > （实测跑满 7 分钟零输出）。现在 `useRoute()` 拿注入的假路由、`RouterLink` 仍走真 router，
  > 两边互不干扰，也不依赖导航时序。
- **`test:scroll`** 钉住路由滚动策略。原来的 `scrollBehavior: () => ({ top: 0 })` 让
  **任何**导航都回到顶部，于是「校园页切一个帖子分类」「广场页切内容视角」这类
  只换 query 的筛选会把人从列表中间弹回顶端——这是实测报出来的 bug。
  现在策略是纯函数（`src/router/scroll.js`）：前进/后退回原位 → 带 hash 滚锚点 →
  **同路由只换 query 保持不动** → 真正换页面才回到顶部。
  它还校验 `STICKY_OFFSET` / `MODTABS_OFFSET` 与 `styles.css` 里的
  `--nav-h` / `--subnav-h` / `--modtabs-h` 一致——CSS 与 JS 无法互相引用，
  改了一处忘了另一处只能靠断言拦住。
- **`test:relation`** 覆盖关注 / 拉黑业务。后端与网关本来就是通的，但前端只有关系列表页
  能用这组操作，**个人主页上根本没有关注 / 拉黑按钮**（而列表空态却写着「在个人主页点
  『关注』」）。现在行为收敛在 `src/composables/useRelation.js`，测试直接驱动它：
  关注↔取关的状态翻转、拉黑必须先确认、失败要有提示且状态不乱跳、
  连点两次只发一次请求、批量给了 `initialRelation` 就不再各自请求；
  外加 SSR 渲染 FollowButton 断言按钮文案与「被对方拉黑」的说明，并检查个人主页确实挂了这组入口。

三个测试都用 Vite 的 SSR 模块图加载**真实组件/真实 store**，只把网络层
（`src/api/auth`、`src/api/notify`）通过 Vite alias 换成带计数的 stub，
因此断言的是**实际发出的请求次数**，而不是「看起来应该不会请求」。

- `test:gate`：错码确认失败且闸门保持关闭、失败后自动换一张、对码通过并取后端返回的冻结秒数、
  冻结期到点自动收回；登录页与注册页在未确认时，手机号/密码/短信验证码输入框**根本不在 HTML 里**。
- `test:notify-frequency`：10 秒内 10 次路由变化**只打 1 次**后端；并发调用共享同一请求；
  通知服务挂掉后 5 次导航不会变成 5 次重试；标记已读/删除就地改数字、不打后端。
- `test:profile-frequency`：10 次页面导航只打 1 次 `/auth/me`；同时断言
  **间隔内 `loadProfile()` 仍然真的请求**——资料设置页必须拿到最新值，不能被限频。

唯一用到的浏览器 API 是 `localStorage`（`stores/auth` 初始化时读取），测试里用内存实现顶替，
没有为了测试改动业务代码。

## 前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`（代理 `/api` → Gateway `8080`）

### 界面设计系统

前端界面按根目录 [`DESIGN.md`](DESIGN.md)（Apple 风格设计系统）重构，落地契约见
[`docs/UI-REDESIGN.md`](docs/UI-REDESIGN.md)。三条硬规则：

1. **单一强调色** `#0066cc`（深色瓦片上换 `#2997ff`），没有第二个品牌色。
2. **层级靠表面色差**（白 `#ffffff` / 米白 `#f5f5f7` / 近黑 `#272729`）与留白，
   卡片、按钮、文字一律不加阴影；全站唯一阴影只留给「躺在表面上的图片」。
3. **体例只有 300 / 400 / 600 / 700**，正文固定 17px / 1.47，展示标题 600 + 负字距。

令牌与基础类集中在 `src/styles.css`（页面只写「布局差异」）；
骨架是 `src/layouts/AppShell.vue`（纯黑全局导航 64px + 磨砂二级导航 56px + 站尾页脚），
每个页面用 `#hero` 插槽放一个通栏 `PageHero` 瓦片作为大标题，
内容之后还可以用 `#band` 插槽放一条真正通栏的收口瓦片（近黑叙事 / 二次行动）。

顶部导航的模块组吃掉品牌与账号区之间的全部宽度，用 `justify-content: space-evenly`
均匀分布；两侧区块宽度接近，整组因此仍是视觉居中的。

入场动画分三层：整页淡入（AppShell 的 `.main`，**只动 opacity**，否则会破坏吸顶侧栏）、
卡片错峰（令牌层对 `.aid-grid` / `.feed` / `.msg-list` / `.stat-strip` 等容器统一挂载，
新页面用了这些容器就自带）、以及显式编排用的 `.enter-stagger`。
fill-mode 只能用 `backwards`——`both` 会永久压掉元素自身的 `:active scale(0.95)`。

「个人主页」是「我的」模块的第一个子模块：`/me`（资料封面 + 我的动态/求助 + 常用入口），
访问自己的 `/users/{id}` 会重定向过去（`src/router/guards.js`），
右上角头像与顶级导航的「我的」都指向 `/me`；看别人的主页仍是 `/users/{别人的 id}`。

同模块的另外两个子模块是 `/me/settings`（设置）与 `/me/ad`（广告位），
关注 / 粉丝 / 黑名单（`/me/following` 等）是「个人主页」下的子页而不是子模块——
它们复用同一个 tab 高亮，由路由 `meta.tab` 标注（见 `src/router/modules.js`）。

### 登录 / 注册的验证码闸门

登录与注册都改成**两步**：先手动点「确认验证码」，通过后端 `/api/auth/captcha/verify`
校验后，才渲染手机号、密码与短信验证码输入框。未通过时这些输入框不存在，提交按钮也是禁用的。

- 校验结果只能由后端裁决：`verified` 标记写在 Redis 里，登录与注册读的是同一份状态；
  前端本地比对等于把答案交给浏览器，改一行代码就能绕过。
- 通过后有 **60 秒冻结期**（后端 `holdSeconds`），期内不必重复确认；到点或手动换图都会
  立即收回表单，而不是只改个角标文字——否则用户填到一半提交失败却看不出原因。
- 「使用管理员内部码登录」不受此限制：它走另一套口令，且管理员被卡在验证码外时仍需入口。

首页含**广告轮播**（自动切换 / 点击统计）；用户主页通过聚合接口一次性拿到资料与统计。

### 广告位：开通资质 → 申请 → 审核 → 上线

广告位在「我的」模块下（`/me/ad`）。**用户申请前必须先开通资质**（见
[学生认证与广告位资质](#学生认证与广告位资质两道管理员审核闸门)）；
管理员直投不受此限制，它是后台行为。

轮播里的广告有两条来源，**走同一张表、同一行状态**（`u_r_ad`）：

| 来源 | 进入方式 | 状态 |
|---|---|---|
| 管理员直投 | 后台创建（`POST /api/ad`，ADMIN） | 直接 `APPROVED` |
| 用户申请 | `/ad/apply` 提交（任何登录用户） | `PENDING` → 审核 |

- **审核是唯一的闸门**：轮播只查 `status = 'APPROVED' AND enabled = 1`
  （`AdBannerRepository.findByStatusAndEnabledTrue...`），所以待审与已驳回的申请
  即便 `enabled` 为真也进不了首页——不是靠前端不渲染。
- **通过即上线**：`approve` 会立刻清掉轮播缓存，不必等 60 秒 TTL。
- **驳回要写原因**：审核意见会出现在用户的「我的申请」里；未填原因时管理端会二次确认。
- **待审可撤回，已审核不可改**：撤回只能撤回自己的、且仍在 `PENDING` 的申请
  （用 404 而不是 403，避免用 id 探测别人的申请）。

#### 审核通过之后：日常管理（管理员）

`GET /api/ad/applications/all` 给出**全部广告**（含已通过），管理台据此提供：

| 动作 | 走哪个接口 | 要不要重新审核 |
|---|---|---|
| 下架 / 上架 | `PUT /api/ad/{id}`（只改 `enabled`） | **不用**——审核结论没变，只是「现在播不播」 |
| 调整排序 | `PUT /api/ad/{id}`（改 `sortOrder`） | 不用 |
| 改标题 / 说明 / 链接 | `PUT /api/ad/{id}` | 不用（管理员本来就有编辑权） |
| 删除 | `DELETE /api/ad/{id}` | — |

> `PUT /api/ad/{id}` 是**整体覆盖**语义：必须回传完整对象（含 `imageUrl`），
> 漏字段会把标题清空。管理台的每个动作都按这个约定构造请求。

#### 修改广告：用户侧的「修改申请」

申请人可以改自己的广告（`PUT /api/ad/applications/{id}`），规则是**改了就要重新审**：

| 原状态 | 修改后 | 首页轮播 |
|---|---|---|
| `APPROVED` | `PENDING` | **立刻下线**（清缓存），重新通过后再上 |
| `PENDING` | `PENDING` | 本来就没上 |
| `REJECTED` | `PENDING` | 改完重试的正常路径 |

三种情况都会清掉上一次的审核意见与时间——它们是针对旧内容的。
如果允许「通过后随便改」，审核就形同虚设：先提交一个好图过审，再换成任意内容即可。
用户端在提交前就会看到「这条广告正在首页轮播，提交修改后会先下线」这句提示。
- **申请人昵称由 auth-service 提供**：待审列表整页只发一次 Feign（`UserApiClient`），
  依赖不可用时降级为只显示 id，不阻塞审核。
- **上传用途分开**：申请用 `adapply`（普通用户可上传），管理员直投用 `ad`（需 ADMIN）。
  合并成一个用途就等于给所有人开了管理员上传通道。

> 待审列表要显示申请人昵称，因此 ad-service 也开启了 `@EnableFeignClients(UserApiClient)`。
> 注意 `AdController` 的安全规则**顺序**：`/api/ad/applications` 的用户规则必须排在
> `"/api/ad/**".hasRole("ADMIN")` 之前，否则普通用户提交申请会被判 403，
> 表现成「功能没实现」——`npm run test:ad` 把这条顺序也断言上了。

## 演示账号

| 手机号 | 密码 | 角色 |
|--------|------|------|
| 13800000000 | Admin@123 | ADMIN |
| 13900000000 | User@123 | USER（**默认不是学生**） |

管理员也可在登录页使用「内部码」登录（默认 `8461`，见 `INTERNAL_LOGIN_CODE`）。

> `13900000000` 默认**没有**学生认证、也没有广告位资质——它曾经带着 `student = true` 落库，
> 那等于在演示环境里直接绕过校园门禁。现在要演示这两条链路就正常走一遍：
>
> 1. 用 `13900000000` 登录 → 顶级导航「校园」会被门禁拦到认证页 → 提交材料；
> 2. 用 `13800000000` 登录 → 管理台「学生认证」→ 通过；
> 3. 回到 `13900000000`，认证页会提示已通过，即可进入校园论坛。
>
> 广告位同理：`我的 → 广告位` 先提交资质，管理员在管理台「广告位资质」通过后才能申请广告位。
