# 自动拨打电话客服销售系统 📞

[![CI/CD](https://github.com/sooglejay/call-center-system/actions/workflows/ci.yml/badge.svg)](https://github.com/sooglejay/call-center-system/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Node.js](https://img.shields.io/badge/node-%3E%3D20-brightgreen.svg)](https://nodejs.org/)
[![React](https://img.shields.io/badge/react-18-blue.svg)](https://react.dev/)

> 基于 Web 的全功能自动拨打电话客服销售系统，支持管理员和客服双角色，集成 Twilio 实现电话拨打、录音等功能。

## ✨ 功能特性

### 管理员功能
- 👥 **人员管理** - 对电话销售人员进行增删改查、密码重置
- 📊 **批量导入客户** - 支持 Excel/CSV/TXT 文件上传，OCR 识别预留
- 📝 **任务分配** - 圈选客户分配给指定客服作为周/日工作任务
- 📈 **监控统计** - 查看所有客服的电话拨打情况、业绩完成度排名

### 客服功能
- 📱 **电话列表** - 待拨打/已拨打列表，支持多种过滤条件
- 🤖 **自动拨号** - 一键开始自动拨号，支持选号策略和拨号延迟配置
- 🎵 **通话录音** - 自动保存录音，支持在线回放
- 🏆 **业绩查看** - 查看个人业绩统计、排名、完成度

### 技术亮点
- 🎯 **前后端分离** - React 18 + Node.js + TypeScript
- 📦 **自动拨号队列** - 智能选号策略，避免重复拨打
- 🔐 **JWT 认证** - 安全的身份验证机制
- 🎨 **企业级 UI** - Ant Design 5 美观界面

## 🚀 在线体验

### 演示环境
- **前台地址**: https://call-center-demo.vercel.app (即将部署)
- **后台 API**: https://call-center-api.railway.app (即将部署)

### 演示账号
```
管理员: admin / admin123
客服: agent / agent123
```

## 📦 快速开始

### 环境要求
- Node.js >= 20
- PostgreSQL >= 14
- pnpm >= 8

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/sooglejay/call-center-system.git
cd call-center-system

# 2. 安装依赖并启动后端
cd server
pnpm install
cp .env.example .env
# 编辑 .env 配置数据库
pnpm dev

# 3. 新终端 - 安装依赖并启动前端
cd client
pnpm install
pnpm dev

# 4. 访问 http://localhost:3000
```

### 数据库初始化

```sql
-- 创建数据库用户和数据库
CREATE USER callcenter WITH PASSWORD 'your_password';
CREATE DATABASE callcenter OWNER callcenter;

-- 初始化表结构（首次运行自动创建）
-- 或执行 server/src/scripts/init-db.sql
```

## 🏗️ 项目架构

```
call-center-system/
├── client/                    # 前端 (React 18 + TypeScript + Ant Design)
│   ├── src/
│   │   ├── pages/            # 页面组件
│   │   │   ├── admin/        # 管理员后台
│   │   │   ├── agent/        # 客服工作台
│   │   │   └── login/        # 登录页
│   │   ├── services/         # API 服务
│   │   └── stores/           # 状态管理 (Zustand)
│   └── package.json
│
├── server/                    # 后端 (Node.js + Express + TypeScript)
│   ├── src/
│   │   ├── controllers/      # 控制器
│   │   ├── routes/           # 路由
│   │   ├── middleware/       # 中间件
│   │   └── config/           # 配置
│   └── package.json
│
├── .github/workflows/         # CI/CD 配置
├── docker-compose.yml         # Docker 部署
└── README.md
```

## 🔧 配置说明

### 后端环境变量 (.env)

```env
PORT=5001
DB_HOST=localhost
DB_PORT=5432
DB_NAME=callcenter
DB_USER=callcenter
DB_PASSWORD=your_password
JWT_SECRET=your-secret-key

# Twilio 配置（可选）
TWILIO_ACCOUNT_SID=your_account_sid
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_PHONE_NUMBER=+1234567890
TWILIO_CALLBACK_URL=https://your-domain.com/api/twilio/webhook
```

### Twilio 集成

1. 注册 [Twilio](https://www.twilio.com) 账号
2. 获取 Account SID 和 Auth Token
3. 购买电话号码
4. 在系统配置页填入信息
5. 配置 Webhook URL 用于接收通话状态

## 🚀 部署

### Docker 部署（推荐）

```bash
# 使用 Docker Compose 一键部署
docker-compose up -d

# 服务将运行在:
# - 前端: http://localhost:3000
# - 后端: http://localhost:5001
# - 数据库: localhost:5432
```

### 免费云服务部署

#### 前端部署到 Vercel

```bash
# 安装 Vercel CLI
npm i -g vercel

# 部署
cd client
vercel --prod
```

#### 后端部署到 Railway

```bash
# 安装 Railway CLI
npm i -g @railway/cli

# 部署
cd server
railway login
railway init
railway up
```

### GitHub Actions 自动部署

项目已配置 CI/CD，每次推送到 main 分支会自动：
- ✅ 运行代码检查
- ✅ 构建前端
- ✅ 构建后端
- ✅ 部署到预览环境

## 📝 API 文档

### 认证相关
```
POST /api/auth/login          # 登录
GET  /api/auth/me             # 获取当前用户
```

### 用户管理
```
GET    /api/users             # 获取用户列表
POST   /api/users             # 创建用户
PUT    /api/users/:id         # 更新用户
DELETE /api/users/:id         # 删除用户
```

### 客户管理
```
GET    /api/customers         # 获取客户列表
GET    /api/customers/agent/list  # 获取客服的客户
POST   /api/customers/import  # 批量导入
```

### 通话管理
```
GET    /api/calls             # 获取通话记录
POST   /api/calls             # 创建通话
GET    /api/calls/next/dial   # 获取下一个拨打客户
```

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18, TypeScript, Ant Design 5, Zustand, Axios |
| 后端 | Node.js, Express, TypeScript, JWT |
| 数据库 | PostgreSQL |
| 电话服务 | Twilio API |
| 部署 | Docker, GitHub Actions, Vercel, Railway |

## 📝 更新日志

### v1.0.0 (2024-03)
- ✨ 初始版本发布
- ✨ 管理员后台完整功能
- ✨ 客服工作台完整功能
- ✨ 自动拨号系统
- ✨ Twilio 集成

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建你的分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

[MIT](LICENSE) © sooglejay

## 💬 联系方式

- Email: sooglejay@gmail.com
- GitHub: [@sooglejay](https://github.com/sooglejay)

---

⭐ 如果这个项目对你有帮助，请给个 Star！
