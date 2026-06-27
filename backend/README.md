# 威特仓库管理系统后端

## 手动运行

推荐从 `backend` 目录启动，让脚本先加载 `backend/.env`，避免 AI 助手拿不到 `WMS_AI_API_KEY`。

macOS / Linux：

`cd backend`

`sh dev-start.sh`

Windows PowerShell：

`cd backend`

`powershell -ExecutionPolicy Bypass -File .\dev-start.ps1`

如果不走脚本，也可以手动把密钥设进当前终端环境变量后再启动：

macOS / Linux：

`export WMS_AI_API_KEY=sk-xxx`

`mvn spring-boot:run -pl ruoyi-admin-wms`

Windows PowerShell：

`$env:WMS_AI_API_KEY="sk-xxx"`

`mvn spring-boot:run -pl ruoyi-admin-wms`

## 热重载 (Hot Reload)

项目已集成 `spring-boot-devtools`，无需额外安装。相关配置已就绪：

- 依赖：`ruoyi-admin-wms/pom.xml` 中已声明 `spring-boot-devtools`
- 开关：`application.yml` 中 `spring.devtools.restart.enabled: true`

> 原理：devtools 监控编译产物 `target/classes`，**只有 `.class` 文件变化才会触发重启**。改了 `.java` 但没重新编译是不会生效的。这是**快速重启**（重启 Spring 上下文，约 1~3 秒），不是改方法体即时生效。

根据启动方式选择对应做法：

### 1. 命令行启动 (`mvn spring-boot:run` / `dev-start.sh`)

命令行不会自动编译，改完代码后**另开一个终端**手动编译一次触发重启：

`mvn compile -pl ruoyi-admin-wms`

### 2. IntelliJ IDEA（推荐）

直接运行主启动类，并开启以下两项后，改完代码会自动重启：

1. Settings → Build, Execution, Deployment → Compiler → 勾选 **Build project automatically**
2. `Cmd/Ctrl + Shift + A` 搜索 **Registry** → 勾选 **`compiler.automake.allow.when.app.running`**

（手动 `Cmd/Ctrl + F9` 也可立即触发编译并重启。）

### 3. VS Code

Java 扩展默认保存即编译到 `target/classes`，一般保存后即可自动重启，无需额外配置。

> ⚠️ 注意：本项目要求 JDK 17，若默认 JDK 较高（如 JDK 25）会因旧版 Lombok 报错。先切换：
> `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

## 运行问题

### 基本问题

遇到无法运行的问题优先两板斧
1. `mvn clean install`
2. `mvn clean compile`

### 1. 运行时报错找不到类

> 错误: 无法初始化主类 com.ruoyi.RuoYiApplication
>
> 原因: java.lang.NoClassDefFoundError: org/springframework/core/metrics/ApplicationStartup

报错找不到核心，就是没有依赖导入，需要手动导入依赖

`cd backend/ruoyi-admin-wms`
`mvn clean install`
`mvn dependency:copy-dependencies -DoutputDirectory=target/dependency`

## 打包

`mvn clean package`
