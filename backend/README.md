# 威特仓库管理系统后端

## 手动运行

`cd backend/ruoyi-admin-wms`

`mvn clean spring-boot:run`

## 运行问题

报错找不到核心，就是没有依赖导入，需要手动导入依赖

`cd backend/ruoyi-admin-wms`
`mvn dependency:copy-dependencies -DoutputDirectory=target/dependency`