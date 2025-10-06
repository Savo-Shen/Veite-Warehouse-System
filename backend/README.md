# 威特仓库管理系统后端

## 手动运行

`cd backend/ruoyi-admin-wms`

`mvn clean spring-boot:run`

## 运行问题

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

