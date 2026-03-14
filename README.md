# 智能呼叫中心系统

<p align="center">
  <img src="https://img.shields.io/badge/React-18-blue?logo=react" alt="React 18">
  <img src="https://img.shields.io/badge/Node.js-20-green?logo=node.js" alt="Node.js 20">
  <img src="https://img.shields.io/badge/SQLite-3-blue?logo=sqlite" alt="SQLite">
  <img src="https://img.shields.io/badge/Twilio-API-red?logo=twilio" alt="Twilio">
  <img src="https://img.shields.io/badge/Vitest-Test-green?logo=vitest" alt="Vitest">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

一款基于 Web 的智能自动拨打电话客服销售系统，支持管理员和客服双角色，集成 Twilio 实现电话拨打、录音、语音信箱和短信通知等功能。

## 快速开始 🚀

```bash
# 1. 克隆项目
git clone <仓库地址>
cd call-center-system

# 2. 安装依赖
cd server && pnpm install
cd ../client && pnpm install

# 3. 初始化测试数据
cd ../server
pnpm db:seed

# 4. 启动服务
pnpm dev              # 后端 http://localhost:3001
# 另开终端
cd client && pnpm dev # 前端 http://localhost:5173
```

**详细配置指南**: [SETUP.md](./SETUP.md)

## 默认登录账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 客服1 | agent01 | agent123 |
| 客服2 | agent02 | agent123 |
| 客服3 | agent03 | agent123 |

## 功能特性

### 管理员功能
- 仪表盘数据可视化
- 用户管理（创建/编辑/删除客服账号）
- 客户管理（导入/分配/按姓氏分组）
- 任务管理（按姓氏首字母筛选分配）
- 通话记录查看与统计
- 系统配置（Twilio 集成设置）

### 客服功能
- 个人工作台
- 客户电话列表（按姓氏分组）
- 一键拨号（Twilio 集成）
- 通话记录与备注
- 语音信箱查看
- 短信发送记录
- 业绩统计

### 核心特性
- 自动拨号支持
- 通话录音存储
- 未接通自动转语音信箱
- 未接通自动发送短信通知
- **按姓氏首字母分组筛选客户（支持中英文）**
- **批量分配客户给客服**
- 实时通话状态监控
- 响应式设计

## 技术栈

### 前端
- **React 18** - UI 框架
- **TypeScript** - 类型安全
- **Ant Design 5** - 组件库
- **Zustand** - 状态管理
- **Vite** - 构建工具

### 后端
- **Node.js 20** - 运行时
- **Express** - Web 框架
- **TypeScript** - 类型安全
- **SQLite** - 数据库（默认，零配置）
- **PostgreSQL** - 数据库（生产可选）
- **Twilio SDK** - 电话服务
- **JWT** - 身份认证

### 测试
- **Vitest** - 单元测试框架
- **Supertest** - HTTP 断言库

### 部署
- **Docker** - 容器化
- **Vercel** - 前端托管
- **Railway** - 后端托管
- **GitHub Actions** - CI/CD

## 数据库

本项目使用 **SQLite** 作为默认数据库：

- 无需安装配置，单文件存储
- 适合开发、测试和小规模生产
- 数据文件：`server/data/database.sqlite`
- 支持无缝切换到 PostgreSQL

### 切换数据库类型

```bash
# .env 文件
DB_TYPE=sqlite    # SQLite（默认）
DB_TYPE=postgres  # PostgreSQL
DB_TYPE=memory    # 内存数据库
```

## 测试

```bash
cd server

# 运行所有测试
pnpm test

# 生成覆盖率报告
pnpm test:coverage

# 交互式测试模式
pnpm test:watch
```

### 测试覆盖范围

- 认证模块（密码验证、JWT）
- 客户管理（CRUD、姓氏分组、批量分配）
- 任务管理（CRUD、状态流转、筛选统计）
- 通话记录（CRUD、统计分析）
- 数据库操作（事务、约束）

## 项目结构

```
call-center-system/
├── client/                 # 前端项目
│   ├── src/
│   │   ├── pages/         # 页面组件
│   │   ├── components/    # 公共组件
│   │   ├── services/      # API 服务
│   │   └── stores/        # 状态管理
│   └── package.json
├── server/                 # 后端项目
│   ├── src/
│   │   ├── config/        # 配置文件
│   │   │   └── database.ts # 数据库配置
│   │   ├── controllers/   # 控制器
│   │   ├── routes/        # 路由
│   │   ├── services/      # 业务服务
│   │   ├── tests/         # 单元测试
│   │   └── scripts/       # 脚本
│   ├── data/              # SQLite 数据库文件
│   └── package.json
├── SETUP.md               # 快速开始指南
├── docker-compose.yml     # Docker 配置
└── README.md
```

## API 文档

### 认证
- `POST /api/auth/login` - 登录
- `GET /api/auth/me` - 获取当前用户

### 客户管理
- `GET /api/customers` - 客户列表
- `GET /api/customers/name-letter-stats` - 姓氏统计
- `GET /api/customers/by-name-letter` - 按姓氏查询
- `POST /api/customers/batch-assign` - 批量分配

### 通话
- `POST /api/twilio/call` - 拨打电话
- `GET /api/twilio/call/:sid/status` - 查询通话状态
- `POST /api/twilio/call/:sid/end` - 结束通话

### 任务
- `GET /api/tasks` - 任务列表
- `POST /api/tasks` - 创建任务
- `PUT /api/tasks/:id` - 更新任务

更多 API 详见源码。

## 环境变量

### 后端 (.env)

```env
# 数据库配置
DB_TYPE=sqlite                      # sqlite | postgres | memory
SQLITE_PATH=./data/database.sqlite  # SQLite 文件路径

# PostgreSQL 配置（可选）
# DB_HOST=localhost
# DB_PORT=5432
# DB_NAME=callcenter
# DB_USER=postgres
# DB_PASSWORD=

# JWT 密钥
JWT_SECRET=your-secret-key

# 服务端口
PORT=3001

# Twilio 配置（可选）
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
TWILO_CALLBACK_URL=
```

### 前端 (.env)

```env
VITE_API_URL=http://localhost:3001
```

## 生产部署

详见 [DEPLOY.md](./DEPLOY.md)

## Twilio 配置

1. 注册 [Twilio](https://www.twilio.com) 账号
2. 购买电话号码
3. 在系统配置页面填写：
   - Account SID
   - Auth Token
   - 发信号码
   - Webhook 回调 URL

### Webhook 配置

在 Twilio Console 中配置：
- **Voice Webhook**: `https://your-api.com/api/twilio/voice`
- **Status Callback**: `https://your-api.com/api/twilio/status`

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 许可证

[MIT](LICENSE)

---

**支持中文姓氏拼音首字母智能识别** 🇨🇳

支持按 A-Z 字母对客户进行分组筛选，自动识别中文姓氏的拼音首字母。
