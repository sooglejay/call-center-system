# 快速开始指南

## 📋 环境要求

- **Node.js**: 18+ (推荐 20+)
- **pnpm**: 8+ (包管理器)

```bash
# 安装 pnpm
npm install -g pnpm
```

## 🚀 启动服务（前后端分开运行）

本项目采用前后端分离架构，需要**分别启动**后端和前端服务。

### 1. 启动后端服务

```bash
cd server
pnpm install          # 安装后端依赖
pnpm db:seed          # 初始化数据库和测试数据
pnpm dev              # 启动后端服务 http://localhost:8081
```

后端启动成功后，会显示：
```
服务器运行在端口 8081
API地址: http://localhost:8081/api
健康检查: http://localhost:8081/api/system/health
```

**保持此终端窗口运行**，后端服务需要持续运行。

### 2. 启动前端服务

**另开一个新的终端窗口**，执行：

```bash
cd client
pnpm install          # 安装前端依赖
pnpm dev              # 启动前端服务 http://localhost:8080
```

前端启动成功后，会显示：
```
  VITE v5.x.x  ready in xxx ms

  ➜  Local:   http://localhost:8080/
  ➜  Network: http://192.168.x.x:8080/
```

### 3. 登录系统

打开浏览器访问 http://localhost:8080，使用以下账号登录：

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 客服 | `agent` | `agent123` |
| 客服1 | `agent01` | `agent123` |
| 客服2 | `agent02` | `agent123` |

---

## 📝 详细说明

### 目录结构

```
call-center-system/
├── server/           # 后端 (Node.js + Express)
│   ├── package.json
│   ├── src/
│   └── ...
└── client/           # 前端 (React + Vite)
    ├── package.json
    ├── src/
    └── ...
```

### 后端命令（在 server/ 目录下执行）

```bash
cd server

pnpm install         # 安装后端依赖
pnpm dev             # 启动后端开发服务
pnpm db:seed         # 重新生成完整测试数据
pnpm db:seed:mini    # 生成最小测试数据
pnpm test            # 运行测试
```

### 前端命令（在 client/ 目录下执行）

```bash
cd client

pnpm install         # 安装前端依赖
pnpm dev             # 启动前端开发服务
pnpm build           # 构建生产版本
pnpm preview         # 预览生产构建
```

### 测试数据

运行 `pnpm db:seed` 会生成：
- 6 个用户（1 管理员 + 1 客服 + 4 客服专员）
- 67 个客户数据（按姓氏 A-Z 分布）
- 30 条通话记录
- 20 个任务

### 数据库

- **类型**: SQLite（内置，无需配置）
- **文件位置**: `server/data/database.sqlite`
- **重置数据库**: 删除该文件后重新运行 `pnpm db:seed`

---

## 🐛 常见问题

### 1. better-sqlite3 编译失败（macOS）

如果遇到编译错误，手动执行：

```bash
cd server
node scripts/postinstall.js
```

或完全手动：

```bash
cd server/node_modules/better-sqlite3
node-gyp rebuild
cd ../..
pnpm db:seed
```

### 2. 端口被占用

修改端口配置：

```bash
# server/.env
PORT=8081

# client/.env.development
VITE_API_URL=http://localhost:8081/api
```

### 3. 数据库权限错误

```bash
chmod -R 755 server/data
```

### 4. 完全重置

```bash
# 删除数据库文件
rm server/data/database.sqlite

# 重新初始化
cd server
pnpm db:seed
```

### 5. 登录失败

确保：
1. 后端服务已启动（http://localhost:8081/api/system/health 可访问）
2. 数据库已初始化（运行过 `pnpm db:seed`）
3. 使用的是正确的默认账号密码

---

## 🔧 生产部署

详见 [DEPLOY.md](./DEPLOY.md)
