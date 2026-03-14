# 快速开始指南

## 📋 环境要求

- **Node.js**: 18+ (推荐 20+)
- **pnpm**: 8+ (包管理器)
- **SQLite**: 内置，无需额外安装

## 🚀 5分钟快速启动

### 1. 克隆项目

```bash
git clone <你的仓库地址>
cd call-center-system
```

### 2. 安装依赖

```bash
# 安装后端依赖（会自动编译 better-sqlite3）
cd server
pnpm install

# 安装前端依赖
cd ../client
pnpm install
```

**注意：** macOS/Windows 用户在执行 `pnpm install` 时会**自动编译** better-sqlite3，如遇问题请手动执行：

```bash
cd server/node_modules/.pnpm/better-sqlite3@12.8.0/node_modules/better-sqlite3
node-gyp clean && node-gyp configure && node-gyp build
```

### 3. 配置环境变量

后端已预设默认配置，无需修改即可运行。如需自定义，复制 `.env.example` 到 `.env`：

```bash
cd server
cp .env.example .env
```

### 4. 初始化数据库并生成测试数据

```bash
# 进入后端目录
cd server

# 生成测试数据（约67个客户、30条通话记录、20个任务）
pnpm db:seed
```

### 5. 启动服务

```bash
# 启动后端服务（端口 3001）
pnpm dev

# 另开终端，启动前端（端口 5173）
cd client
pnpm dev
```

### 6. 访问系统

- 前端界面: http://localhost:5173
- 后端 API: http://localhost:3001
- 默认账号:
  - 管理员: `admin` / `admin123`
  - 客服1: `agent01` / `agent123`
  - 客服2: `agent02` / `agent123`

## 🧪 运行测试

```bash
cd server

# 运行所有测试
pnpm test

# 运行测试并生成覆盖率报告
pnpm test:coverage

# 交互式测试模式
pnpm test:watch

# 图形化测试界面
pnpm test:ui
```

## 📁 数据库说明

项目使用 **SQLite** 作为默认数据库：

- **数据库文件**: `server/data/database.sqlite`
- **优势**: 无需配置，单文件存储，适合开发和演示
- **生产环境**: 可切换到 PostgreSQL（见下方配置）

### 数据库类型切换

编辑 `server/.env`：

```bash
# SQLite（默认，开发推荐）
DB_TYPE=sqlite
SQLITE_PATH=./data/database.sqlite

# PostgreSQL（生产环境）
DB_TYPE=postgres
DB_HOST=localhost
DB_PORT=5432
DB_NAME=callcenter
DB_USER=postgres
DB_PASSWORD=your_password

# 内存数据库（快速测试，数据不保留）
DB_TYPE=memory
```

## 📊 测试数据说明

运行 `pnpm db:seed` 会生成：

| 数据类型 | 数量 | 说明 |
|---------|------|------|
| 用户 | 5 | 1管理员 + 4客服 |
| 客户 | 67 | 涵盖 A-Z 各姓氏 |
| 通话记录 | 30 | 各种通话结果 |
| 任务 | 20 | 不同优先级和状态 |

### 最小化测试数据

如果只需要少量数据测试：

```bash
pnpm db:seed:mini  # 只生成基础用户和客户
```

## 🔧 常用命令

```bash
# 后端
pnpm dev           # 开发模式（热重载）
pnpm build         # 构建生产版本
pnpm start         # 运行生产版本
pnpm db:seed       # 生成完整测试数据
pnpm db:seed:mini  # 生成最小测试数据
pnpm test          # 运行单元测试

# 前端
pnpm dev           # 开发服务器
pnpm build         # 构建生产版本
pnpm preview       # 预览生产构建
```

## 🐛 常见问题

### 1. 端口被占用

如果 3001 或 5173 端口被占用，修改 `.env`：

```bash
# server/.env
PORT=3002  # 改为其他端口

# client/.env
VITE_API_URL=http://localhost:3002
```

### 2. SQLite 权限错误

确保对 `server/data` 目录有写权限：

```bash
chmod -R 755 server/data
```

### 3. 测试数据重复

重新生成测试数据时会自动清理旧数据（保留用户表）。如需完全重置：

```bash
rm server/data/database.sqlite
pnpm db:seed
```

### 4. better-sqlite3 编译错误（macOS）

如果在 macOS 上遇到 `Could not locate the bindings file` 错误，需要手动编译 better-sqlite3：

```bash
# 进入 better-sqlite3 目录
cd node_modules/.pnpm/better-sqlite3@12.8.0/node_modules/better-sqlite3

# 手动编译
node-gyp clean
node-gyp configure
node-gyp build

# 或者使用 npm 脚本
npm run rebuild
```

**前提条件：**
- 安装 Xcode 命令行工具: `xcode-select --install`
- 安装 node-gyp: `npm install -g node-gyp`
- Python 3 已安装: `python3 --version`

**备选方案 - 删除重新安装：**

```bash
cd server
rm -rf node_modules pnpm-lock.yaml
pnpm install
```

**临时方案 - 使用内存数据库：**

```bash
# 修改 server/.env
echo "DB_TYPE=memory" >> server/.env
# 然后直接运行 pnpm dev（无需执行 db:seed）
```

## 📖 项目结构

```
call-center-system/
├── server/              # 后端 (Node.js + Express + SQLite)
│   ├── src/
│   │   ├── config/      # 配置文件（数据库等）
│   │   ├── controllers/ # 控制器
│   │   ├── routes/      # 路由
│   │   ├── services/    # 服务层
│   │   └── tests/       # 单元测试
│   ├── data/            # SQLite 数据库文件
│   └── package.json
├── client/              # 前端 (React + Vite + Ant Design)
│   ├── src/
│   └── package.json
└── README.md
```

## 📞 技术支持

如有问题，请查看：
1. 项目 Wiki 文档
2. GitHub Issues
3. 联系项目维护者

---

**享受使用！** 🎉
