# 用户中心（User Center）

一个可以直接跑起来的前后端分离「用户中心」：注册登录、个人资料、头像裁剪上传、账号安全与会话管理、
角色权限与用户管理后台、操作审计日志、站内通知。界面刻意避开模板脸，走「个人档案册」的视觉方向。

- **后端**：Java 21 + Spring Boot 3.5.16 + Spring Security 6 + MyBatis-Plus 3.5.17 + MySQL 8
- **前端**：Vue 3.5 + Vite 7 + TypeScript 5 + Pinia 3 + Vue Router 4（不用 UI 框架，自建设计 token 与控件库）
- **鉴权**：JWT access token（只在内存）+ httpOnly Cookie 里的 refresh token（落库存哈希、每次刷新轮换）

---

## 快速开始

### 0. 前置条件

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 本仓库按 21 编译；`mvn -v` 应显示 Java 21 |
| Maven | 3.9+ | 建议配置阿里云镜像（见 `docs/run.md`） |
| MySQL | 8.0+ | 需要 root（或等价权限）账号执行一次建库脚本 |
| Node.js | 20+ | 推荐 22/24；包管理器用 pnpm |

### 1. 建库建账号（只需一次）

```bash
mysql --host=127.0.0.1 --port=3306 --user=root --password < scripts/db-init.sql
```

会创建两个库和最小权限账号（**不会动你机器上已有的库**）：

- `user_center`（开发）、`user_center_test`（测试）
- 账号 `uc_app` / `Uc_app_2026!`，只授权上面两个库

表结构不在这里创建：后端启动时由 `spring.sql.init` 执行
`backend/src/main/resources/db/schema.sql`（全 `CREATE TABLE IF NOT EXISTS`）与 `db/seed.sql`（幂等字典），
所以「代码即结构」，重复启动无副作用。

### 2. 本地配置

```bash
cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml
```

`application-local.yml` 已被 `.gitignore` 忽略，里面放数据库密码与 JWT 密钥：

```yaml
spring:
  datasource:
    password: Uc_app_2026!
uc:
  jwt:
    secret: dev-only-secret-please-change-0123456789abcdef   # HS256 要求 >= 32 字节
```

生产环境请改用环境变量覆盖：`UC_DB_PASSWORD`、`UC_JWT_SECRET`、`UC_ADMIN_PASSWORD`。

### 3. 起服务

```powershell
pwsh -File scripts/dev.ps1          # 前后端各开一个窗口
```

或者分别起：

```bash
# 后端
cd backend && mvn spring-boot:run
# 前端
cd frontend && pnpm install && pnpm dev
```

- 前端：<http://localhost:5173>
- 后端：<http://localhost:8080>，健康检查 `/actuator/health`，接口文档 `/swagger-ui.html`
- 开发期前端用 Vite 代理 `/api` → `8080`，因此不存在跨域与 Cookie 问题

### 4. 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@12345`（可用 `UC_ADMIN_PASSWORD` 覆盖） | ADMIN + USER |

管理员由后端启动时的 `DataSeeder` 创建（BCrypt 哈希必须由应用生成，所以不放进 SQL）。
**首次登录后请立刻改密码。** 账号已存在时启动不会覆盖，改过就不会被重置。

---

## 功能清单

| 模块 | 能力 |
|---|---|
| 注册 / 登录 / 登出 | 用户名或邮箱登录、BCrypt 存密码、连续 5 次失败锁定 10 分钟、refresh token 轮换 |
| 个人资料 | 昵称、邮箱、手机号、性别、生日、简介；空字符串表示清空；唯一性冲突返回 409 |
| 头像 | 前端 512×512 裁剪后上传，服务端魔数 + 像素上限校验，UUID 命名落盘，公开只读访问 |
| 账号安全 | 修改密码（踢掉其他设备）、登录设备/会话列表、退出指定设备、退出其他设备、注销账号 |
| 角色与权限 | `ROLE_USER` / `ROLE_ADMIN`，方法级 `@PreAuthorize`，前端路由守卫双保险 |
| 用户管理后台 | 用户列表（关键字/状态/角色过滤 + 分页）、启用停用、改角色、重置密码、概览统计 |
| 审计日志 | `@Audit` 切面 + 服务内显式记录，登录/改密/管理操作全留痕，可按用户/动作/时间/成败过滤 |
| 站内通知 | 未读数、列表（可只看未读）、单条已读、全部已读、管理员定向或全体群发 |

## 目录结构

```
user-center/
├─ backend/            Spring Boot 服务（包名 com.example.usercenter）
│  ├─ src/main/java/…  common / config / security / storage / audit / entity / mapper / dto / service / controller
│  ├─ src/main/resources/db/{schema.sql,seed.sql}
│  └─ src/test/java/…  单元测试 + MockMvc 集成测试（真连 user_center_test）
├─ frontend/           Vue 3 + Vite 应用（设计 token 在 src/styles/tokens.css）
├─ scripts/
│  ├─ db-init.sql      建库建账号（一次性）
│  ├─ dev.ps1          一键起前后端
│  └─ smoke.ps1        端到端冒烟（覆盖 8 条主线 + 边界）
└─ docs/{design-brief.md,api.md,run.md}
```

## 测试与验收

```bash
# 后端：单元测试（surefire）+ 集成测试（failsafe，真连 user_center_test）
cd backend && mvn clean verify

# 前端：单元测试 + 类型检查 + 构建
cd frontend && pnpm test -- run && pnpm build

# 端到端冒烟（后端需已在 8080 运行）
pwsh -File scripts/smoke.ps1
```

`scripts/smoke.ps1` 覆盖：健康检查、注册（含重复/弱密码）、登录与令牌、无/伪造令牌 401、
资料更新（含 emoji 与空值清空）、头像上传与公开访问、伪装图片被拒、会话列表与退出其他设备、
改密码后旧密码失效、通知未读/全部已读、普通用户访问管理接口 403、管理员停用/恢复用户、
被停用用户登录 403、管理员不能停用自己 409、群发通知、审计日志可查、登出。全部通过时退出码为 0。

## 文档

- [docs/run.md](docs/run.md)：环境变量、数据库、常见问题与排错
- [docs/api.md](docs/api.md)：接口清单、错误码、鉴权约定
- [docs/design-brief.md](docs/design-brief.md)：界面设计主张与 token 说明

## 已知取舍

- **头像只支持 JPEG / PNG**：WebP 需要额外 ImageIO 插件，本版有意不收（前端裁剪后统一输出 JPEG）。
- **每个已认证请求会查一次会话表**：这是「退出其他设备立即生效」的代价（否则被踢掉的设备还能用
  access token 活动到过期）。查询走主键，规模上来后可加 1–2 秒的内存缓存。
- **access token 无状态、有效期 30 分钟**，但会话撤销是即时的：会话一旦被吊销，令牌立刻失效。
- **通知为轮询式**（前端定时取未读数），未做 SSE/WebSocket 推送。
- **不做**：深色模式切换、短信/邮件验证码、第三方登录、多语言。
