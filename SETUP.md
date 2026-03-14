# 快速开始指南

## 📋 环境要求

- **Node.js**: 18+ (推荐 20+)
- **pnpm**: 8+ (包管理器)

```bash
# 安装 pnpm
npm install -g pnpm
```

## 🚀 一键启动（2个命令）

### 1. 安装依赖（自动初始化数据库）

```bash
cd call-center-system
pnpm install
```

**自动完成的工作：**
- ✅ 安装前后端所有依赖
- ✅ 自动编译 better-sqlite3（macOS/Windows）
- ✅ 自动初始化 SQLite 数据库
- ✅ 自动生成测试数据

### 2. 启动服务

```bash
pnpm dev
```

这会同时启动：
- 后端服务: http://localhost:3001
- 前端界面: http://localhost:5173

### 3. 登录系统

打开 http://localhost:5173，使用以下账号：

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 客服 | `agent` | `agent123` |

---

## 📝 详细说明

### 目录结构

```
call-center-system/
├── package.json      # 根目录配置
├── server/           # 后端 (Node.js + Express)
│   ├── package.json
│   └── ...
└── client/           # 前端 (React + Vite)
    ├── package.json
    └── ...
```

### 可用命令

```bash
# 根目录命令
pnpm install         # 安装所有依赖（前后端）
pnpm dev             # 同时启动前后端
pnpm dev:server      # 只启动后端
pnpm dev:client      # 只启动前端

# 后端命令（在 server/ 目录下）
pnpm db:seed         # 重新生成完整测试数据
pnpm db:seed:mini    # 生成最小测试数据
pnpm test            # 运行测试

# 前端命令（在 client/ 目录下）
pnpm build           # 构建生产版本
pnpm preview         # 预览生产构建
```

### 测试数据

运行 `pnpm db:seed` 会生成：
- 5 个用户（1 管理员 + 4 客服）
- 67 个客户数据
- 30 条通话记录
- 20 个任务

### 数据库

- **类型**: SQLite（内置，无需配置）
- **文件位置**: `server/data/database.sqlite`
- **重置数据库**: 删除该文件后重新运行 `pnpm dev`

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
PORT=3002

# client/.env.development
VITE_API_URL=http://localhost:3002/api
```

### 3. 数据库权限错误

```bash
chmod -R 755 server/data
```

### 4. 完全重置

```bash
rm server/data/database.sqlite
pnpm install
pnpm dev
```

---

## 🔧 生产部署

详见 [DEPLOY.md](./DEPLOY.md)
