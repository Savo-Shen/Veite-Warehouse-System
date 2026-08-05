# 数据库备份

系统内置了自动备份，由后端应用自己执行，不依赖 Docker、不依赖 Windows 计划任务，
**MySQL 是宿主机上的独立服务还是 Docker 容器都一样支持**——后端只是起一个 `mysqldump` 子进程去连数据库。

后台入口：**基础资料 → 数据库备份**。在这个页面上可以：

- 点「立即备份」马上备份一次
- 设置备份计划（每天几点 / 每隔几小时、保留多久、备份目录、离机副本目录），保存即生效，不用重启
- 点「环境自检」逐项检查 mysqldump、目录权限、数据库连接
- 看到上次备份的结果，失败时整条飘红并显示原因
- 下载、删除历史备份

## 备份计划

页面上有两种模式：

- **每天定时**（推荐）：指定时刻和星期，例如「每天 03:00」或「只在周一、周五 22:00」。
- **按间隔时间**：距上次备份超过 N 小时就备份一次。

后端每 5 分钟检查一次「按计划本该备份的那个时间点过去了没、之后有没有备份过」，缺了就立刻补。
所以**定时模式下 3 点没开机也不会漏备份**：早上开机几分钟后就会自动补上当天这一次。
这也是这里不用系统 cron 的原因——店里的电脑晚上关机，固定时刻的定时任务很可能永远不触发。

设置保存在数据库的 `sys_config` 表里（键名前缀 `wms.backup.`），升级上来不需要做任何迁移。

备份文件名形如 `wms-20260730-031500.sql.gz`，内容是 `mysqldump` 的完整导出（含存储过程、事件、触发器）后 gzip 压缩。

## 前置条件：mysqldump

`mysqldump` 随 MySQL 一起安装，Windows 上通常位于：

```
C:\Program Files\MySQL\MySQL Server 8.4\bin
```

系统会**自动**按这个顺序找：PATH → `MYSQL_HOME` → MySQL / MariaDB / XAMPP 的常见安装目录。
自检里如果仍提示「找不到 mysqldump」，在备份页面的「mysqldump 路径」里填完整路径即可（填 bin 目录也认），
不需要改系统 PATH、也不用重启。

## 首次部署后一定要做的两件事

1. 打开备份页面点一次「环境自检」，四项都是「正常」才算装好。
2. 配置「离机副本目录」，见下一节。

后端每次启动也会自动做一次同样的自检并写进日志（搜 `备份自检`）。

## 环境变量（仅作为初始值）

在 `backend/.env` 中设置（原生启动时由 `start-prod.ps1` 自动加载），可参考 `backend/.env.example`。
**这些值只在数据库里还没有对应设置时生效**；页面上保存过一次之后，就以页面上的为准。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `WMS_BACKUP_ENABLED` | `true` | 是否开启自动备份 |
| `WMS_BACKUP_DIR` | `./backups` | 备份目录。相对路径基于启动目录（原生启动是 `backend\`），建议改成绝对路径 |
| `WMS_BACKUP_MIRROR_DIR` | 空 | 离机副本目录，留空表示不做副本 |
| `WMS_BACKUP_INTERVAL_HOURS` | `24` | 距上次备份超过多少小时就自动备份 |
| `WMS_BACKUP_RETENTION_DAYS` | `14` | 保留天数，`0` 表示不清理 |
| `WMS_BACKUP_MIN_KEEP` | `3` | 无论多旧都至少保留的份数 |
| `WMS_BACKUP_MYSQLDUMP` | 空 | mysqldump 路径，留空表示自动查找 |

`WMS_BACKUP_MIN_KEEP` 是防呆用的：假如机器停用了一个月，回来开机时所有备份都已超过保留期，
没有这个下限就会被一次性清空。

## 一定要配 `WMS_BACKUP_MIRROR_DIR`

备份文件默认和数据库在同一台机器、往往还是同一块硬盘上。硬盘坏了，数据库和备份会一起没。
把 `WMS_BACKUP_MIRROR_DIR` 指向一个会自动同步到别处的目录，每次备份完成后系统会自动复制一份过去：

```
WMS_BACKUP_MIRROR_DIR=C:\Users\你的用户名\OneDrive\wms-backups
```

OneDrive、坚果云等网盘的本地同步目录都可以，也可以指向常插着的移动硬盘或网络共享盘。
副本写入失败只会在日志里告警，不影响正本备份。

副本目录同样会按保留期清理。

## 恢复

```bash
gunzip -c wms-20260730-031500.sql.gz | mysql -h 主机 -u 用户名 -p 数据库名
```

Windows 上如果没有 `gunzip`，先用 7-Zip 解压出 `.sql` 再导入。

**没验证过的备份不算备份。** 建议偶尔恢复到一个临时库里确认一次，比如：

```sql
CREATE DATABASE wms_restore_test;
```

然后导入这个库，确认表和数据都在，再 `DROP DATABASE wms_restore_test;`。

## Docker 部署的情况

Docker Compose 里另有一个 `wms-db-backup` 容器做同样的事（见 `docker/db-backup-entrypoint.sh`）。
**两套只应启用一套**，否则会重复备份并互相清理。

Docker 下 `.env.docker` 默认 `WMS_BACKUP_ENABLED=false`，沿用 `wms-db-backup` 容器。
**建议改用应用内置的这套**：备份计划能在页面上改、失败在页面上看得到，容器那套只能改文件重启。
做法是把 `wms-db-backup` 服务停掉，再设 `WMS_BACKUP_ENABLED=true`。

另外注意 `MYSQL_HOST`：**MySQL 装在宿主机上时不能填 `localhost`**，容器里的 localhost 是容器自己。
Docker Desktop（Windows / macOS）填 `host.docker.internal`，Linux 填宿主机内网 IP。
两套备份读的是同一个 `MYSQL_HOST`，填错了会一起失败——这也是「看着开了备份其实一份都没有」的常见原因。

## 手动同步到云端数据库

`script/sync-to-cloud.ps1`（Windows）和 `script/sync-to-cloud.sh`（macOS/Linux）可以把本地数据库
**单向覆盖**到云端数据库，用来在云服务器上留一份可直接查询的副本。

```powershell
powershell -ExecutionPolicy Bypass -File script\sync-to-cloud.ps1
```

```bash
script/sync-to-cloud.sh
```

流程是：检查两端连接 → 交互确认 → 把云端现有数据导出到本地作为回退备份 → 导出本地数据 → 写入云端 → 比对表数量。
加 `-Force` / `--force` 可跳过交互确认。

先在 `backend/.env` 中配置：

```
CLOUD_MYSQL_HOST=你的云服务器地址
CLOUD_MYSQL_PORT=3306
CLOUD_MYSQL_USERNAME=wms
CLOUD_MYSQL_PASSWORD=云端数据库密码
CLOUD_MYSQL_SSL_MODE=REQUIRED
```

### 需要注意

- **方向是单向的，且会完整覆盖云端。** 云端上做的任何修改都会在下次同步时被抹掉。
  所以云端只应该用来查看和灾备，不要在上面改数据。
- **不要把系统连到云端库上用。** 系统登录时就会写登录日志、更新 `sys_user.login_date`，
  这些写入既会被下次同步抹掉，也会让两边数据分叉。人不在店里又要用系统时，
  正确做法是让电脑连回店里那台数据库，而不是连云端副本。
- **数据要过公网，默认强制 TLS**（`CLOUD_MYSQL_SSL_MODE=REQUIRED`）。
  云端 MySQL 需要配好证书，否则连不上。确实没条件配证书才改成 `PREFERRED`，
  但那样整个数据库是明文传输的。
- 云端 MySQL 需要允许从店里的 IP 连入：`bind-address` 不能只绑 `127.0.0.1`，
  防火墙/安全组要放行 3306，账号授权要用 `'wms'@'%'` 或指定的来源 IP。
  **不要把 3306 无限制地开放到公网**，安全组里限定来源 IP。
- 同步期间店里的系统可以照常使用（用了 `--single-transaction`，导出的是一致性快照）。

## 备份没跑起来时怎么查

先在备份页面点「环境自检」，四项检查基本能定位所有常见问题：

| 自检提示 | 原因和处理 |
| --- | --- |
| 找不到 mysqldump | MySQL 的 bin 目录不在 PATH，且不在常见安装位置。在页面「mysqldump 路径」里填绝对路径 |
| 备份目录无法写入 | 路径不存在、盘符错了或没有写权限。换一个目录，或给服务账号授权 |
| 数据库连接失败 `Access denied` | 账号密码不对，或该账号没有导出权限（需要 SELECT、SHOW VIEW、TRIGGER、EVENT） |
| 数据库连接失败 `Can't connect` | `MYSQL_HOST` 不对（Docker 里尤其常见，见上一节）或 MySQL 没起来 |

页面顶部的「上次备份失败」红条会一直保留到下次成功为止，失败原因也写在里面。
服务器日志里搜 `自动备份失败` 或 `备份自检` 能看到同样的信息。

## 备份不能解决的问题

自动备份防的是「硬盘坏了」和「数据被改乱了」，恢复粒度最细到上一次备份的时刻。
它不是高可用方案：机器开不了机时，系统就用不了，需要另找一台机器部署并从备份恢复。
