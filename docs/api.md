# 接口文档

所有接口以 `/api` 为前缀，服务默认跑在 `http://localhost:8080`。开发期前端通过 Vite 代理访问同一路径，
因此浏览器里不存在跨域。

## 统一响应

成功与失败都是同一个信封（HTTP 状态码同时表达语义，便于网关与前端拦截器处理）：

```json
{ "code": 0, "message": "ok", "data": { }, "traceId": "a1b2c3d4e5f60718", "success": true }
```

- `code = 0` 表示成功；非 0 见下方错误码表。
- `data` 为 `null` 时字段会被省略（Jackson `non_null`）。
- `traceId` 同时出现在响应头 `X-Trace-Id` 与错误信封里，用于对齐服务端日志。
- 分页数据形状：`{ "list": [], "total": 0, "page": 1, "size": 10 }`，`size` 上限 100。

## 鉴权

| 凭据 | 存放位置 | 有效期 | 说明 |
|---|---|---|---|
| access token（JWT HS256） | 响应体 → 前端内存 | 30 分钟 | 每次请求带 `Authorization: Bearer <token>` |
| refresh token（随机串） | httpOnly Cookie `uc_refresh`，`Path=/api/auth`，`SameSite=Lax` | 14 天（勾选"记住我"）/ 1 天 | 只在 `POST /api/auth/refresh` 使用；服务端只存 SHA-256 |

- 每次 `refresh` 都会**轮换**：旧会话作废、下发新 Cookie。旧 refresh token 再用即 401。
- 每个已认证请求都会校验 access token 所属会话是否仍然有效，所以「退出设备」「改密码」「账号停用」
  **立即生效**，不必等令牌过期。
- 前端把 access token 只放内存，页面刷新后用 `POST /api/auth/refresh` 静默续期。

## 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 1001 | 400 | 参数校验失败（`data` 是 `{字段: 提示}`） |
| 1002 | 404 | 资源不存在 |
| 2001 | 401 | 未登录 / 令牌非法 / 会话已失效 |
| 2002 | 401 | 令牌过期 |
| 3001 | 403 | 无权限 |
| 3002 | 403 | 账号已被禁用 |
| 3003 | 403 | 账号已锁定（连续 5 次密码错误，锁 10 分钟） |
| 4001 | 409 | 用户名已存在 |
| 4002 | 409 | 邮箱已被注册 |
| 4003 | 409 | 手机号已被注册 |
| 4004 | 401 | 用户名或密码错误 |
| 4005 | 400 | 原密码不正确 |
| 4006 | 400 | 密码强度不足（需 8–64 位且含字母与数字） |
| 4090 | 409 | 数据冲突 |
| 4091 | 409 | 系统至少保留一个可用管理员 |
| 4092 | 409 | 不能对自己执行该操作 |
| 5001 | 400 | 文件不合法（非 JPEG/PNG、尺寸越界、空文件） |
| 5002 | 413 | 文件过大（> 2MB） |
| 5000 | 500 | 服务器内部错误 |

## 认证 `/api/auth`

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/register` | — | 注册。`{username, email, password, nickname?}`；username 为 3–20 位字母数字下划线 |
| POST | `/login` | — | 登录。`{username, password, remember?}`，username 可填用户名或邮箱 |
| POST | `/refresh` | Cookie | 读 Cookie 轮换令牌 |
| POST | `/logout` | Cookie | 吊销当前会话并清 Cookie |
| GET | `/me` | Bearer | 当前用户 |

`register` / `login` / `refresh` 成功时 `data`：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 1800,
  "user": {
    "id": 2, "username": "alice", "email": "alice@example.com", "phone": null,
    "nickname": "阿离", "avatarUrl": null, "gender": "FEMALE", "birthday": "1996-03-18",
    "bio": "…", "status": "ACTIVE", "roles": ["USER"], 
    "createdAt": "2026-10-09T21:51:09", "lastLoginAt": "2026-10-09T21:51:09"
  }
}
```

> `roles` 是**短码**（`USER` / `ADMIN`），与请求体里 `PUT /api/admin/users/{id}/roles` 的取值一致。

## 个人资料 `/api/profile`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 当前资料 |
| PUT | `` | `{nickname, email, phone?, gender?, birthday?, bio?}`；**传空字符串表示清空**；唯一冲突返回 4001/4002/4003 |
| PUT | `/password` | `{oldPassword, newPassword}`；成功后**其他设备会话立即失效，当前设备保持登录** |
| POST | `/avatar` | `multipart/form-data`，字段名 `file`；建议前端裁剪为 512×512 JPEG |
| DELETE | `/avatar` | 删除头像并清理磁盘文件 |

头像访问路径形如 `/api/files/avatars/{userId}/{uuid}.jpg`，公开可读、带 1 小时缓存。

## 账号安全 `/api/security`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sessions` | 会话/设备列表，`current` 标记当前设备，`deviceName` 由 User-Agent 归纳（如 `Chrome · Windows`） |
| DELETE | `/sessions/{id}` | 退出指定设备（只能操作自己的会话，否则 1002） |
| DELETE | `/sessions/others` | 退出其他所有设备，返回受影响条数 |
| DELETE | `/account` | 注销账号。`{password}`；逻辑删除；最后一个可用管理员不可注销（4091） |

## 站内通知 `/api/notifications`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 分页参数 `page`、`size`、`unreadOnly` |
| GET | `/unread-count` | `{ "count": 3 }` |
| POST | `/{id}/read` | 标记单条已读（非本人的通知返回 1002） |
| POST | `/read-all` | 全部已读，返回受影响条数 |

## 管理后台 `/api/admin`（需要 `ADMIN` 角色，否则 403）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/users?keyword&status&role&page&size` | 关键字匹配用户名/邮箱/昵称；`status`=ACTIVE/DISABLED；`role`=USER/ADMIN |
| GET | `/users/active-ids?limit=200` | 活跃用户 id，用于选收件人（上限 500） |
| GET | `/users/{id}` | 用户详情 |
| PATCH | `/users/{id}/status` | `{status: "ACTIVE"｜"DISABLED"}`；不能操作自己（4092）；不能停用最后一个可用管理员（4091）；停用会吊销其全部会话并发送通知 |
| PUT | `/users/{id}/roles` | `{roles: ["USER","ADMIN"]}`；不能改自己的角色；不能移除最后一个可用管理员 |
| POST | `/users/{id}/reset-password` | 返回 `{tempPassword}`（一次性明文，不落库）；同时吊销该用户全部会话 |
| GET | `/stats` | `{userTotal, userActive, userDisabled, notificationUnread, auditToday, newUsers7d}` |
| POST | `/notifications` | `{userIds?: [1,2], all?: true, title, content}`，返回实际写入条数 |
| GET | `/audit-logs?userId&action&success&from&to&page&size` | `action` 左匹配（如 `ADMIN_`）；时间为 ISO-8601（`2026-10-09T00:00:00`），格式非法返回 1001 而不是静默忽略 |

## 审计动作码

| 动作 | 触发点 |
|---|---|
| `AUTH_REGISTER` / `AUTH_LOGIN` / `AUTH_LOGOUT` | 注册、登录（含失败与锁定）、登出 |
| `PROFILE_UPDATE` / `PASSWORD_CHANGE` / `AVATAR_UPLOAD` / `AVATAR_DELETE` / `ACCOUNT_DELETE` | 资料与账号安全 |
| `SESSION_REVOKE` / `SESSION_REVOKE_OTHERS` | 退出设备 |
| `ADMIN_USER_STATUS` / `ADMIN_USER_ROLES` / `ADMIN_USER_RESET_PASSWORD` / `ADMIN_NOTIFICATION_SEND` | 管理操作 |

审计详情只会写非敏感信息：**密码、令牌、Cookie 一律不落审计与日志**。

## 在线调试

后端启动后访问 <http://localhost:8080/swagger-ui.html>，右上角 `Authorize` 填 `Bearer <accessToken>`
即可直接调接口。
