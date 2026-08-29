# 公网部署安全加固

这份文档记录了为「上公网」做的加固：改了什么、为什么、以及**代码之外还必须由运维完成的事**。

---

## 一、代码/配置层已完成的加固

### 1. 生产不再运行 Vite 开发服务器

`start-prod.ps1` 原本用 `pnpm run dev --host 0.0.0.0 --port 80` 托管前端。

`vite dev` 带 `/@fs/` 端点，默认可读整个工作区的任意文件 —— 包括 `backend/.env` 和
`application-local.yml`（数据库密码、JWT 密钥都在里面）。项目锁的 vite 3.2.3 已经 EOL，
后续一串 `fs.deny` 绕过漏洞都不会再修。

改动：

- `start-prod.ps1` 改为先 `pnpm run build:prod`，再用 `vite preview` 托管 `dist` 静态产物。
  `preview` 只发构建产物，没有 `/@fs/`，也不暴露源码树。
- `vite.config.js` 里 `server.host` 由 `true` 改为 `127.0.0.1`：开发服务器默认只绑本机。
  需要用手机等设备访问时，命令行显式加 `--host`。
- `server.fs.deny` 扩充了 `application-local.yml`、`*.sql`、私钥类文件。
- vite 升到 5.4.21（配套升 `@vitejs/plugin-vue`、`unplugin-auto-import`、
  `unplugin-vue-setup-extend-plus`）。

> 更推荐的生产形态仍然是 nginx 托管 `dist` + 反代后端，见 `docker/nginx.conf`。
> `vite preview` 是「不装 nginx 也能安全跑起来」的兜底方案。

### 2. 打包 profile 修正，Actuator 收口

`start-prod.ps1` 执行的是 `mvn -DskipTests clean package`，**漏了 `-Pprod`**。
而父 pom 里 `dev` profile 是 `activeByDefault`，所以生产 jar 里 `spring.profiles.active=dev`：

- `application-prod.yml` 中收窄 Actuator 的配置根本没生效
- 生效的是 `include: '*'`，且 `security.excludes` 放行了 `/actuator/**`
- 结果：`GET /prod-api/actuator/heapdump` 匿名可下载整个 JVM 堆
  （数据库密码、JWT 密钥、所有在线 token、全部业务数据），一个请求即全量失守

改动：

- `start-prod.ps1` 打包命令加 `-Pprod`
- 新增 `Test-JarIsProdBuild`：启动前翻开 jar 检查 `active: prod`。
  只比时间戳发现不了「上次是用 dev profile 打的」，会导致加固静默落空。
- `application.yml` 的 Actuator 默认值由 `include: '*'` / `show-details: ALWAYS`
  改为 `include: health` / `show-details: never`（即使再次漏掉 `-Pprod` 也不会全量暴露）
- `security.excludes` 删掉 `/actuator`、`/actuator/**`
- `docker/nginx.conf` 增加 `location /prod-api/actuator { deny all; }`

> 说明：Sa-Token 的拦截器只作用于 Spring MVC 映射的 URL，Actuator 端点走的是
> `WebMvcEndpointHandlerMapping`，因此**即使从 excludes 里移除，也不会自动要求登录**。
> 实际起作用的是「只暴露 `health` 且 `show-details: never`」加上 nginx 的 `deny`。
> `/actuator/health` 仍然匿名可访问，返回的只有 `{"status":"UP"}`，可作为健康检查探针。
> 结论：**不要在生产打开 `include` 的其他端点** —— 打开即匿名可访问。

已验证（prod 包，直连后端）：

```
/actuator/health       -> {"status":"UP"}
/actuator/env          -> No static resource actuator/env.
/actuator/heapdump     -> No static resource actuator/heapdump.
/actuator/beans        -> No static resource actuator/beans.
/actuator/logfile      -> No static resource actuator/logfile.
/actuator/configprops  -> No static resource actuator/configprops.
/v3/api-docs           -> No static resource v3/api-docs.
/swagger-ui/index.html -> No static resource swagger-ui/index.html.
/tool/gen/list         -> No static resource tool/gen/list.
/tool/gen/db/list      -> No static resource tool/gen/db/list.
```

### 3. 暴力破解防护

原实现 `SysLoginService.checkLogin()` 的失败计数 key 是 `用户名 + ":" + clientIP`，
而 `ServletUtils.getClientIP()` 继承 Hutool，会**无条件采信 `X-Forwarded-For`**。
攻击者每次请求换一个头值，计数器永远停在 1，5 次锁定形同虚设。

改动：

- 新增 `ClientIpResolver`（`ruoyi-common-core`）：只有当请求的 TCP 对端落在
  可信代理网段内时才采信代理头；读 XFF 时从右往左跳过可信代理，取第一个非可信条目。
  配置见 `application.yml` 的 `security.client-ip`。
  `ServletUtils.getClientIP()` 改为委托给它，所有既有调用点（登录锁定、限流、
  操作日志）自动生效。
- `checkLogin()` 改为两级计数：
  - 来源级（账号 + 客户端 IP）：阈值 `maxRetryCount`（5），挡单点爆破
  - 账号级（仅账号）：阈值 `maxRetryCount × globalRetryMultiplier`（默认 20），
    挡换 IP 的分布式撞库。换 IP 不会重置这一级。
- 验证码由 1 位算术题改为 2 位（`captcha.numberLength`）
- `/login`、`/smsLogin`、`/emailLogin`、`/captchaImage`、`/captchaSms`、`/register`
  加 `@RateLimiter(limitType = IP)`。验证码接口尤其重要 —— 不限流的话可以无限刷，
  「每次尝试都要过验证码」的成本就降到零了。
- `docker/nginx.conf` 在边缘对这些路径再加一层 `limit_req`

### 4. JWT 密钥

`sa-token.jwt-secret-key` 原本有默认值 `abcdefghijklmnopqrstuvwxyz`。
用的是 `StpLogicJwtForSimple`（无状态 JWT），密钥可猜 = 任何人都能签发 admin token。

改动：

- 移除默认值，改为 `${JWT_SECRET_KEY}`
- 新增 `JwtSecretValidator`：启动时校验非空、不在已知弱值列表内、长度 ≥ 32，
  否则直接启动失败并给出 `openssl rand -hex 32` 的提示

### 5. 文件上传与匿名读取

`/system/oss/upload` 完全没有类型校验，`/system/oss/blob/{ossId}` 匿名可读且原样回显
上传时客户端提供的 `Content-Type`。有上传权限的账号传一个 `text/html` 上去，
就得到一个**同源**的 XSS 页面，可以偷走管理员 token（token 存在 js-cookie 里，非 HttpOnly）。

改动：

- 新增 `MimeTypeUtils.UPLOAD_ALLOWED_EXTENSION` 白名单并在 `SysOssService.upload()` 校验。
  刻意不复用 `DEFAULT_ALLOWED_EXTENSION` —— 那个列表里有 `html`/`htm`。svg 同样不放行。
- `blob` 端点：只有确定安全的 MIME 才内联渲染，其余一律
  `application/octet-stream` + `Content-Disposition: attachment`；
  统一加 `X-Content-Type-Options: nosniff`（否则限制会被 MIME sniffing 绕过）。
  这一层在读取时判断，因此**存量数据**同样受保护。

### 6. 接口文档与代码生成器

- 生产环境关闭 springdoc（`application-prod.yml`）。
  `security.excludes` 里的 `/**/*.html` + `/*/api-docs/**` 会让 Swagger 匿名可读。
- `ruoyi-generator` 改为只在 `local`/`dev` profile 引入。
  `/tool/gen/genCode` 会按用户可填的 `gen_path` 往服务器磁盘写文件 ——
  管理员账号一旦失守就等于任意文件写入。已验证 prod 包中 generator 相关类为 0。

### 7. 其他

- Token 有效期 90 天 → 30 天；活跃期 30 天 → 7 天
- XSS 过滤 `urlPatterns` 增加 `/wms/*`（此前整个业务接口不在过滤范围内）
- AI 工具按权限过滤：`AiTool` 新增 `requiredPermission()`，`ToolRegistry.specs()`
  只把当前用户有权限的工具交给模型，`execute()` 再校验一次。
  此前任何登录账号都能通过对话读到全部库存、出库价格和毛利。
- Spring Boot 3.2.6 → 3.2.12
- nginx 增加安全响应头、`server_tokens off`、隐藏文件 `deny`

---

## 二、生产环境（savo-prod / wms.savo-shen.com）

2026-08-28 起，生产从局域网 Windows `192.168.1.4` 迁到云服务器 **savo-prod**，
域名 `wms.savo-shen.com`。旧的 `start-prod.ps1` 只对已退役的 Windows 那台有意义。

**部署形态**（服务器上没有 Maven/Node，构建只能在本机做）

```
本机 mvn -Pprod + pnpm build:prod
  -> 把胖 jar 解包成目录
  -> rsync -c 增量同步到 savo-prod:~/staging/{app,dist}
  -> 硬链接落位成 /opt/wms/releases/<时间戳>/{app,dist}
  -> 原子切换 /opt/wms/current 软链接
  -> systemctl restart wms-backend
```

服务器上的目录结构：

```
/opt/wms/
  current -> releases/20260829-144302     # nginx 的 root 和 systemd 的 -cp 都指这里
  releases/<时间戳>/{app,dist}            # 保留最近 5 个，互相硬链接，几乎不额外占盘
  app.jar                                 # 迁移前的胖包，留作应急，已不再被使用
  application-prod.yml  wms.env  logs/  backups/
```

用 `./script/deploy-to-savo-prod.sh` 一条命令完成，它会：

- 强制用 JDK 17（Lombok 1.18.30 在 JDK 21+ 上编译报 `TypeTag :: UNKNOWN`）
- 强制 `-Pprod`，并在上传前校验产物确实是 prod 包、且不含 `ruoyi-generator`，否则中止
- 解包后 `rsync -c` 同步。**`-c` 不能省**：每次构建都会刷新所有 class 和资源的 mtime，
  不按内容校验的话，光 `ip2region.xdb` 那 11MB 每次都会重传。加了 `-c` 之后，
  一次典型部署实际只传约 1MB（19 个自有模块 jar，它们的 zip 内嵌时间戳每次都变），
  整个流程 1 分钟以内，其中网络部分只占几秒。
- 落位用 `cp -al` 硬链接，所以每个 release 只为「这次真正变了的文件」付磁盘：
  两个 release 各自 129M，加起来实际占盘 133M。老 release 就是备份，
  不再单独拷一份 `~/wms-backup-*`。
- 重启后从服务器本机做健康检查（actuator 在 nginx 上是 deny 的，外部打不到）

常用参数：

| 参数 | 作用 |
|---|---|
| （无） | 全量构建 + 部署 |
| `--backend-only` / `--frontend-only` | 只重建一侧，另一侧沿用服务器上现有产物 |
| `--stage` | 只同步到 `~/staging`，不落位不重启 |
| `--list` | 看有哪些 release、哪个生效、实际占盘多少 |
| `--rollback` | 切回上一个 release（改软链接 + 重启，不重新上传，秒级） |
| `--use <时间戳>` | 切到指定 release |

**启动方式**：unit 里是 `-cp /opt/wms/current/app org.springframework.boot.loader.launch.JarLauncher`，
等价于 `java -jar`，只是换成解包后的目录以便 rsync 算增量。
**`WorkingDirectory` 必须留在 `/opt/wms`**——外置的 `application-prod.yml` 和 `logs/`
都是按工作目录解析的，挪到 release 目录里会导致外置配置不再生效。

**这台机器本来就已经做好的**（不需要再动）

- HTTPS：Cloudflare + nginx 1.30.4，`*.savo-shen.com` 通配符证书，
  HTTP 301 跳 HTTPS，`Strict-Transport-Security: max-age=31536000`
- 安全响应头：`X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy` 都在 server 层
- 边缘限流：`00-limits.conf` 定义 `wms_login`(10r/m)、`wms_captcha`(60r/m)、`wms_api`(30r/s)
- 真实 IP：`snippets/ssl-params.conf` 配了 Cloudflare 网段 `set_real_ip_from` +
  `real_ip_header CF-Connecting-IP`，所以 nginx 的 `$remote_addr` 已是真实客户端 IP，
  再由 `proxy_set_header X-Real-IP $remote_addr` 权威地传给后端
- 后端 `SERVER_ADDRESS=127.0.0.1`，8080 不对外
- `JWT_SECRET_KEY` 是 64 位随机值（**不是**框架默认值）
- jar 本来就是 `-Pprod` 打的，所以 `/actuator/env`、`/actuator/heapdump` 一直是挡住的

**本次在这台机器上新增的**

- nginx 加了三条 `deny`：`/prod-api/actuator`、
  `/prod-api/(v3/api-docs|swagger-ui|swagger-resources|webjars)`、`/prod-api/tool/gen`
  （改动前已备份到 `~/nginx-backups/`）
- 部署了包含全部应用层加固的新 jar 与 dist

**部署前线上确实存在的问题（已修复）**

| 问题 | 修复前 | 修复后 |
| --- | --- | --- |
| 接口文档公网可读 | `/prod-api/v3/api-docs` 返回 267KB 全量接口定义，含「代码生成模块」分组 | nginx 403 + 应用层 springdoc 关闭 |
| 代码生成器已部署 | `ruoyi-generator-5.2.0.jar` 在包里，`/tool/gen/list` 返回 401（端点存在） | 生产包不再包含该模块，端点不存在 |
| 登录锁定可被 XFF 绕过 | 计数 key 含客户端可伪造的 IP | 两级计数 + 可信代理校验 |
| 验证码 1 位算术题 | `numberLength: 1` | 2 位 |
| 上传无类型白名单 / blob 原样回显 content-type | 可传同源 HTML | 白名单 + MIME 收口 |
| XSS 过滤不覆盖 `/wms/*` | 业务接口不过滤 | 已覆盖 |
| AI 工具不校验权限 | 任何登录账号可用全部工具 | 按用户权限过滤 |
| token 有效期 90 天 | — | 30 天 / 活跃期 7 天 |

**仍需人工处理**

- MySQL 密码轮换：`backend/.env` 里有备注「当前数据库密码曾随仓库公开」。
  云上用的是独立账号 `wms`，但如果口令与曾经公开的那个相同，仍应更换。
- `WMS_BACKUP_MIRROR_DIR` 还空着，备份与数据库在同一块盘（启动日志每次都会告警）。
- 备案接入还绑在旧服务器。

## 三、已知的、暂未处理的点

| 项 | 说明 |
| --- | --- |
| 用户名枚举 | 登录时账号不存在与密码错误返回的是不同提示。修掉会影响一线人员排查登录问题，暂未改动。 |
| Spring Boot 3.2 已 EOL | 3.2.12 是该线最后一个公开补丁版本。升到 3.5.x 需要同步升级 springdoc、mybatis-plus、dynamic-datasource 等一批依赖，属于独立的升级项目，建议单独排期并做完整回归。 |
| token 存在非 HttpOnly 的 cookie 里 | 前端用 `js-cookie` 存取，改成 HttpOnly 需要后端下发 cookie、前端改取值方式，是一次成体系的改动。当前通过堵住 XSS 入口（上传白名单 + blob 类型收口 + XSS 过滤覆盖 `/wms/*`）来降低风险。 |


---

## 四、本次改动的验证记录

均在 `-Pprod` 打出的包上、连本地 MySQL/Redis 实测。

**登录锁定 —— 固定来源**

```
第1次: 密码输入错误1次     ...     第5次: 密码输入错误5次，帐户锁定10分钟
```

**登录锁定 —— 每次请求伪造不同的 X-Forwarded-For**（修复前此场景可无限尝试）

```
第 1..19 次: 密码输入错误1次        ← 来源级计数每次都被重置（符合预期）
第 20 次:    帐户锁定10分钟          ← 账号级计数达到 5 × 4 = 20，锁定
第 21/22 次: 帐户锁定10分钟
```

**IP 解析 —— 模拟真实 nginx 拓扑**

发送 `X-Forwarded-For: 1.2.3.4, 198.51.100.77`（前者是攻击者伪造的，后者是 nginx 追加的真实 IP），
Redis 中实际生成的计数键是：

```
pwd_err_cnt:admin:198.51.100.77      ← 取到的是真实 IP，不是伪造值
```

**验证码接口限流**（配置 300 秒 30 次/IP）：连打 36 次，前 30 次成功，第 31 次起返回「访问过于频繁，请稍候再试」。

**匿名 blob 端点**

| 存储的 content_type | 响应 |
| --- | --- |
| `text/html`（内容为 `<script>alert(document.cookie)</script>`） | `Content-Type: application/octet-stream` + `Content-Disposition: attachment` + `nosniff` |
| `image/png` | `Content-Type: image/png` + `Content-Disposition: inline` + `nosniff` |

**JWT 密钥校验**：`.env` 中为旧的默认值时，应用拒绝启动并提示
「JWT_SECRET_KEY 仍然是示例值/默认值，这个值是公开的，必须更换。」

**vite preview 不暴露源码树**：以下路径全部返回 SPA 的 `index.html`，未泄露任何文件内容。

```
/@fs/<repo>/backend/.env
/@fs/<repo>/backend/ruoyi-admin-wms/src/main/resources/application-local.yml
/@fs/<repo>/backend/.env?raw??        （CVE-2025-30208 形式的绕过）
/src/main.js
```

**前端 vite 5 构建**：`pnpm run build:prod` 成功，登录页正常渲染，浏览器控制台无报错，
所有静态资源与 `/prod-api/captchaImage` 均 200。
