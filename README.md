# 威特仓库管理系统
>
> 基于 RuoYi系统 二次开发

## 快速启动

### 启动脚本

- **macOS/Linux**: `./system.sh` 或 `./dev-start.sh`
- **Windows**: `system.bat`

#### 脚本使用说明

`system.sh` 用于启动、停止或查看系统状态：

```bash
./system.sh start   # 启动系统（前后端）
./system.sh stop    # 停止系统
./system.sh status  # 查看系统运行状态
```

#### 日志

- 日志文件存放在 `logs/` 目录下
- 前端日志: `logs/frontend.log`
- 后端日志: `logs/backend.log`

### 访问地址

- 前端: <http://localhost:80>
- 后端: <http://localhost:8080>

## 新增功能

### 1.位置管理

对于仓库中的货位进行管理，支持货位的增删改查
![位置管理](docs/assets/位置管理.png)

### 2.部分数字高亮

提高数字的可读性
![数字高亮](docs/assets//数字高亮.png)

### 3.新增回车、上下左右键支持

在新增和编辑页面，支持回车键保存和搜索，支持上下左右键切换表格

参考 [RuoYi-WMS](https://github.com/zccbbg/wms-ruoyi)
