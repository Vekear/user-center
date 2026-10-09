# 运行与排错

## 1. 环境

| 组件 | 本机实测版本 | 备注 |
|---|---|---|
| JDK | 21（`JAVA_HOME=D:\Code_Environment\Java\jdk-21`） | 源码按 21 编译；PATH 上的 `java` 可能是别的版本，Maven 认 `JAVA_HOME` |
| Maven | 3.9.11，本地仓库 `D:/Code_Environment/maven/myRepository_3911` | 已配阿里云/华为云镜像 |
| MySQL | 8.0.46（phpstudy 的 `MySQL80` 服务，端口 3306） | 字符集 `utf8mb4 / utf8mb4_0900_ai_ci` |
| Node / pnpm | Node 24.19、pnpm 12.10.1 | npm registry 指向 `registry.npmmirror.com` |
| PowerShell | **Windows PowerShell 5.1**（本机没有 PowerShell 7） | 脚本已按 5.1 兼容编写 |

## 2. 三个端口

| 端口 | 用途 |
|---|---|
| 3306 | MySQL |
| 8080 | 后端 API（`/actuator/health`、`/swagger-ui.html`） |
| 5173 | 前端 dev server（`/api` 代理到 8080） |

## 3. 配置项与覆盖方式

`application.yml` 里的默认值都能用环境变量覆盖，配置类见 `config/UcProperties.java`。

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `UC_PROFILE` | `local` | Spring profile |
| `UC_DB_HOST` / `UC_DB_PORT` / `UC_DB_NAME` | `127.0.0.1` / `3306` / `user_center` | 数据库位置 |
| `UC_DB_USER` / `UC_DB_PASSWORD` | `uc_app` / 空 | 建议放在 `application-local.yml`（已 gitignore） |
| `UC_JWT_SECRET` | 空（必须配置） | HS256 要求 ≥ 32 字节；配置缺失时启动即失败并给出明确提示 |
| `UC_ADMIN_PASSWORD` | `Admin@12345` | 仅用于**首次**创建种子管理员；账号已存在时不会覆盖 |

其他可调项（`uc.*`）：`jwt.access-token-minutes`（30）、`jwt.refresh-token-days`（14）、
`login.max-failed-attempts`（5）、`login.lock-minutes`（10）、`avatar.max-bytes`（2MB）、
`avatar.max-dimension`（4096）、`cors.allowed-origins`（`http://localhost:5173`）。

`spring.sql.init` 每次启动都会执行 `db/schema.sql` + `db/seed.sql`，两者都是幂等的
（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`），重复启动无副作用。

## 4. 常用命令

```powershell
# 建库建账号（一次）
mysql --host=127.0.0.1 --port=3306 --user=root --password < scripts\db-init.sql

# 后端：单元测试 + 集成测试（集成测试真连 user_center_test）
cd backend; mvn clean verify

# 后端：只跑单元测试 / 只跑集成测试
mvn test
mvn verify -Dit.test=AuthApiIT

# 后端：启动
mvn spring-boot:run

# 前端
cd frontend; pnpm install; pnpm test -- run; pnpm build; pnpm dev

# 端到端冒烟（后端需已运行）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1

# 只重建表结构（危险：会丢数据）
mysql -h127.0.0.1 -uuc_app -p user_center -e "DROP DATABASE user_center; CREATE DATABASE user_center CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

## 5. 排错

### 5.1 Maven 拉不到依赖

`dependency:go-offline` 首次约 3 分钟、150–200MB。若卡住或失败：

```powershell
mvn -B -U dependency:go-offline     # 强制刷新
```
仓库位置与镜像在 `D:\Code_Environment\maven\apache-maven-3.9.11\conf\settings.xml`，
必要时换成 `https://maven.aliyun.com/repository/public`。

### 5.2 启动报 `uc.jwt.secret 未配置或长度不足 32 字节`

`application-local.yml` 没建或没填。复制模板：

```powershell
copy backend\src\main\resources\application-local.yml.example backend\src\main\resources\application-local.yml
```

### 5.3 启动报 `Access denied for user 'uc_app'`

`scripts/db-init.sql` 没执行，或 `application-local.yml` 里的密码与建库时不一致。

### 5.4 中文/emoji 变问号

- 库里乱码：确认库/表是 `utf8mb4`（`scripts/db-init.sql` 已指定），JDBC URL 带
  `characterEncoding=utf8`（默认已带）。
- **只在命令行输出里乱码**：Windows 控制台代码页问题，不是数据问题。核对请用
  `mysql --default-character-set=utf8mb4 ...` 或看接口返回。

### 5.5 脚本报 `Unexpected token` / 中文变乱码

`.ps1` 用了非 ASCII 字符时，Windows PowerShell 5.1 会按系统 ANSI 代码页读文件，必须存成
**UTF-8 with BOM**。本仓库的 `scripts/*.ps1` 已带 BOM；你新增脚本时注意：

```powershell
$c = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
[IO.File]::WriteAllText($path, $c, (New-Object System.Text.UTF8Encoding($true)))
```

### 5.6 冒烟脚本里中文断言失败（`Invoke-WebRequest` 场景）

PowerShell 5.1 的 `Invoke-WebRequest` 会按 ANSI 解码响应体，且对
`application/vnd.spring-boot.actuator.v3+json` 返回 `byte[]`。
`scripts/smoke.ps1` 因此直接用 `HttpWebRequest` + 显式 UTF-8 解码，你写新脚本时建议照做。

### 5.7 pnpm 安装后报 `ERR_PNPM_IGNORED_BUILDS`

pnpm 11+ 默认拒绝跑依赖的 postinstall。`frontend/pnpm-workspace.yaml` 里已经放行 esbuild：

```yaml
allowBuilds:
  esbuild: true
```

### 5.8 前端刷新页面掉登录页

静默续期依赖 `uc_refresh` Cookie（`Path=/api/auth`、`SameSite=Lax`）。检查：
- 后端 `/api/auth/refresh` 是否返回 200；
- 生产部署时前端与后端是否**同源**（跨站场景要 `SameSite=None; Secure`，并相应调整
  `uc.cookie.secure`、`uc.cookie.sameSite`）。

### 5.9 头像上传报 5001 / 5002

- 5001：不是 JPEG/PNG（按魔数判断，改扩展名没用）、尺寸小于 16px 或大于 4096px、空文件。
- 5002：超过 2MB，或超过 Spring 的 `multipart.max-file-size`（5MB）。
- 本版**不支持 WebP**（JDK 自带 ImageIO 无 WebP 解码器），前端裁剪后统一输出 JPEG。

### 5.10 后端 8080 被占用

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 6. 测试数据说明

- `user_center`（开发库）会被冒烟脚本反复写入 `smoke_*` 用户，介意就定期清：
  `DELETE FROM user WHERE username LIKE 'smoke\_%';`
- `user_center_test`（测试库）由集成测试自行准备数据，用户名带随机后缀，不依赖执行顺序。
