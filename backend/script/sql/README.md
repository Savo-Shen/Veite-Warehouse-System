# 数据库脚本使用说明

## 日常升级

只能执行与功能对应的增量脚本，例如：

- `order_supplement_images.sql`：增加出入库补充图片字段
- `location_shelf.sql`：增加货架布局相关字段
- `item_tag.sql`：增加商品标签功能
- `db_align_menu.sql`：增加数据库对齐菜单

执行前必须先备份数据库。不要选择整个 `sql` 目录批量运行。

## 禁止事项

- 不要在已有数据库中执行 `wms.full-init.DANGEROUS.sql`。
- `wms.sql` 是保护入口，只会抛出错误，不会修改数据库。
- 不要将完整初始化脚本当作增量迁移脚本。

`wms.full-init.DANGEROUS.sql` 仅用于全新空数据库初始化，它包含大量
`DROP TABLE IF EXISTS`，会删除现有用户、商品、库存、出入库单等数据。
