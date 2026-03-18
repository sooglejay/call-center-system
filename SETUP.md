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
pnpm dev              # 启动后端服务 http://localhost:8081
```

后端启动成功后，会显示：
```
服务器运行在端口 8081
API地址: http://localhost:8081/api
健康检查: http://localhost:8081/api/system/health
✅ 内存数据库已就绪，包含默认用户：
   - 管理员: admin / admin123
   - 客服: agent / agent123
```

**注意**: 数据库会在首次启动时自动创建，无需手动初始化。

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

---

## 📝 详细说明

### 目录结构

```
call-center-system/
├── server/           # 后端 (Node.js + Express + sql.js)
│   ├── package.json
│   ├── src/
│   └── data/         # SQLite 数据库文件存储
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
pnpm build           # 构建生产版本
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

### 数据库

- **类型**: SQLite（使用 sql.js，纯 JavaScript 实现，无需编译原生模块）
- **文件位置**: `server/data/database.sqlite`
- **特点**: 跨平台兼容，Docker 部署无需额外配置

---

## 🐛 常见问题

### 1. 端口被占用

```bash
# 查看端口占用
lsof -i :8081
lsof -i :8080

# 杀掉进程
kill -9 <PID>
```

### 2. 数据库权限错误

```bash
chmod -R 755 server/data
```

### 3. 完全重置

```bash
# 删除数据库文件
rm server/data/database.sqlite

# 重启服务后会自动创建新数据库
pnpm dev
```

### 4. 登录失败

确保：
1. 后端服务已启动（http://localhost:8081/api/system/health 可访问）
2. 使用正确的默认账号密码：`admin / admin123`

### 5. 前端无法连接后端

检查前端代理配置（`client/vite.config.ts`）：
```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8081',
    changeOrigin: true,
  }
}
```

---

## 🔧 生产部署

详见 [DEPLOY.md](./DEPLOY.md)

## 📖 开发迭代流程

详见 [WORKFLOW.md](./WORKFLOW.md)
