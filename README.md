# 智能呼叫中心系统

<p align="center">
  <img src="https://img.shields.io/badge/React-18-blue?logo=react" alt="React 18">
  <img src="https://img.shields.io/badge/Node.js-20-green?logo=node.js" alt="Node.js 20">
  <img src="https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Twilio-API-red?logo=twilio" alt="Twilio">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

一款基于 Web 的智能自动拨打电话客服销售系统，支持管理员和客服双角色，集成 Twilio 实现电话拨打、录音、语音信箱和短信通知等功能。

## 在线演示

- **前台地址**: https://call-center-demo.vercel.app
- **后台 API**: https://call-center-api.railway.app

> 演示账号：
> - 管理员：`admin` / `admin123`
> - 客服：`agent` / `agent123`

## 功能特性

### 管理员功能
- 仪表盘数据可视化
- 用户管理（创建/编辑/删除客服账号）
- 客户管理（导入/分配/跟进）
- 任务管理（创建外呼任务）
- 通话记录查看与统计
- 系统配置（Twilio 集成设置）

### 客服功能
- 个人工作台
- 客户电话列表
- 一键拨号（Twilio 集成）
- 通话记录与备注
- 语音信箱查看
- 短信发送记录
- 业绩统计

### 核心特性
- 自动拨号支持
- 通话录音存储
- **未接通自动转语音信箱**
- **未接通自动发送短信通知**
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
- **PostgreSQL** - 数据库
- **Twilio SDK** - 电话服务
- **JWT** - 身份认证

### 部署
- **Docker** - 容器化
- **Vercel** - 前端托管
- **Railway** - 后端托管
- **GitHub Actions** - CI/CD

## 快速开始

### 环境要求
- Node.js >= 20
- PostgreSQL >= 15
- Twilio 账号

### 本地开发

1. 克隆项目
```bash
git clone https://github.com/yourusername/call-center-system.git
cd call-center-system
```

2. 安装依赖
```bash
# 安装前端依赖
cd client && npm install

# 安装后端依赖
cd ../server && npm install
```

3. 配置环境变量

**server/.env**
```env
PORT=5001
DATABASE_URL=postgresql://user:password@localhost:5432/callcenter
JWT_SECRET=your-secret-key
```

4. 初始化数据库
```bash
cd server
psql -d callcenter -f src/scripts/init-db.sql
```

5. 启动服务

```bash
# 启动后端
cd server && npm run dev

# 启动前端（新终端）
cd client && npm run dev
```

6. 访问系统
- 前端：http://localhost:5173
- 后端 API：http://localhost:5001

### Docker 部署

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

## 生产部署

详见 [DEPLOY.md](./DEPLOY.md)

### 一键部署

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/yourusername/call-center-system&root-directory=client)

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/template/your-template)

## Twilio 配置

1. 注册 [Twilio](https://www.twilio.com) 账号
2. 购买电话号码
3. 在系统配置页面填写：
   - Account SID
   - Auth Token
   - 发信号码
   - Webhook 回调 URL

### Webhook 配置

在 Twilio Console 中配置以下 Webhook URL：
- **Voice Webhook**: `https://your-api.com/api/twilio/voice`
- **Status Callback**: `https://your-api.com/api/twilio/status`

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
│   │   ├── controllers/   # 控制器
│   │   ├── routes/        # 路由
│   │   ├── services/      # 业务服务
│   │   └── scripts/       # 脚本
│   └── package.json
├── docker-compose.yml      # Docker 配置
└── README.md
```

## API 文档

### 认证
- `POST /api/auth/login` - 登录
- `GET /api/auth/me` - 获取当前用户

### 通话
- `POST /api/twilio/call` - 拨打电话
- `GET /api/twilio/call/:sid/status` - 查询通话状态
- `POST /api/twilio/call/:sid/end` - 结束通话

### 通信记录
- `GET /api/communication/records` - 获取通信记录
- `GET /api/communication/stats` - 获取通信统计

更多 API 详见源码。

## 环境变量

### 前端 (.env)
```env
VITE_API_URL=http://localhost:5001/api
```

### 后端 (.env)
```env
PORT=5001
DATABASE_URL=postgresql://user:pass@host:5432/db
JWT_SECRET=your-secret
NODE_ENV=production
```

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 许可证

[MIT](LICENSE) © 2024 Call Center System

## 支持

如有问题，请提交 [Issue](https://github.com/yourusername/call-center-system/issues) 或联系支持团队。

---

<p align="center">Made with ❤️ by Call Center Team</p>
