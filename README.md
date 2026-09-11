# 城区互助

前后端分离：`backend/`（微服务多模块）+ `frontend/`（Vue3）。

## 后端模块

```
backend/
├── common/             # 公共 JWT / API / 日志过滤器
├── eureka-server/      # 注册中心 :8761
├── gateway/            # API 网关 :8080
├── auth-service/       # 鉴权 :8081
├── aid-service/        # 求助 :8082
├── community-service/  # 社区 :8083
├── ad-service/         # 广告轮播 :8084
└── schema.sql          # 建表参考脚本
```

## 数据库

- MySQL `localhost:3306` / `chengqu_huzhu`
- 账号 `root` / `123456`
- 表前缀 `u_r_`：`u_r_sys_user`、`u_r_aid_request`、`u_r_post`、`u_r_post_like`、`u_r_ad`
- 控制台**关闭** Hibernate SQL 刷屏，只保留业务交互日志：`[网关]` `[API]` `[鉴权]` `[求助]` `[社区]` `[广告]`

## IDEA 启动顺序

打开 `backend/pom.xml` 作为 Maven 工程，依次运行：

1. `EurekaServerApplication`（8761）
2. `AuthServiceApplication`（8081）
3. `AidServiceApplication`（8082）
4. `CommunityServiceApplication`（8083）
5. `AdServiceApplication`（8084）
6. `GatewayApplication`（8080）← 前端只访问此端口

命令行打包：

```bash
cd backend
mvn -DskipTests package
```

## 前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`（代理 `/api` → Gateway `8080`）

首页含**广告轮播**（自动切换 / 点击统计）。

## 演示账号

| 手机号 | 密码 | 角色 |
|--------|------|------|
| 13800000000 | Admin@123 | ADMIN |
| 13900000000 | User@123 | USER |
