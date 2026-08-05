# Docker 部署

## 1. 准备配置

```bash
cp .env.docker.example .env.docker
```

编辑 `.env.docker`，至少填写外部 MySQL 的 `MYSQL_HOST`、数据库账号密码和 `JWT_SECRET_KEY`。生产环境不要提交这个文件。

默认情况下 MySQL 使用外部服务器，Compose 只启动 WMS、Redis 和 Nginx。外部 MySQL 服务器需要允许这台 WMS 服务器访问 3306 端口，并提前创建数据库和账号。

## 2. 初始化数据库

第一次部署到外部的全新数据库时，在 WMS 服务器上执行 `backend/script/sql/wms.full-init.DANGEROUS.sql`。
它包含删除表语句，只能用于空数据库；已有系统必须先备份，再按功能执行增量 SQL。

例如将脚本复制到服务器后，通过 MySQL 客户端执行：

```bash
set -a
. ./.env.docker
set +a

mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USERNAME" \
  -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  < backend/script/sql/wms.full-init.DANGEROUS.sql
```

如果没有安装 MySQL 客户端，也可以临时启用 Compose 自带的本地 MySQL：

```bash
docker compose --profile local-db --env-file .env.docker up -d mysql
```

此时将 `.env.docker` 中的 `MYSQL_HOST` 改为 `mysql`。

## 3. 构建和启动

```bash
docker compose --env-file .env.docker build
docker compose --env-file .env.docker up -d
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs -f wms-backend
docker compose --env-file .env.docker logs -f wms-db-backup
```

访问服务器的 80 端口即可。域名和 HTTPS 建议在云服务器上配置 Nginx 或云厂商证书，再转发到本 Compose 的 80 端口。

## 4. 更新发布

```bash
git pull
docker compose --env-file .env.docker build --no-cache wms-backend nginx
docker compose --env-file .env.docker up -d
docker image prune -f
```

自动备份文件保存在 `wms_backend_backups` Docker volume 中。MySQL 和 Redis 数据也保存在 Docker volumes 中，不要执行 `docker compose down -v`，否则会删除数据库和备份卷。

自动备份默认每天 03:00 执行并保留 7 天，可在 `.env.docker` 中修改 `BACKUP_HOUR`、`BACKUP_MINUTE` 和 `BACKUP_RETENTION_DAYS`。

后端应用另有一套内置的自动备份（`WMS_BACKUP_*` 系列变量），主要给不用 Docker 的原生部署使用。
两套只应启用一套，Docker 下默认关闭内置的那套。详见 [数据库备份](database-backup.md)。

## 5. 数据库合并导入

管理员可访问 `/wms/dbImport`，上传本系统导出的 `.sql` 或 `.sql.gz` 文件。导入账号必须有创建和删除临时数据库的权限，因为系统会先创建临时库进行预览，确认后才合并到正式库。

导入规则：

- 只处理 `wms_*` 业务表；用户、角色、菜单和权限表不会直接覆盖当前系统。
- 相同主键且内容相同的记录自动跳过。
- 新记录可以补充进正式库。
- 冲突可以按表选择跳过，或使用导入库内容覆盖。
- `create_by`、`update_by` 会按用户映射重写；未绑定的用户名会保留原名称并在导入前提示。

导入前必须先做一次当前数据库备份。不要上传包含 `CREATE DATABASE`、`DROP DATABASE` 或 `USE` 语句的任意 SQL 脚本，只使用系统导出的备份文件。
