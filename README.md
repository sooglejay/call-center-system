# 智能呼叫中心系统

<p align="center">
  <img src="https://img.shields.io/badge/React-18-blue?logo=react" alt="React 18">
  <img src="https://img.shields.io/badge/Node.js-20-green?logo=node.js" alt="Node.js 20">
  <img src="https://img.shields.io/badge/SQLite-3-blue?logo=sqlite" alt="SQLite">
  <img src="https://img.shields.io/badge/Twilio-API-red?logo=twilio" alt="Twilio">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

一款基于 Web 的智能自动拨打电话客服销售系统，支持管理员和客服双角色，集成 Twilio 实现电话拨打、录音、语音信箱和短信通知等功能。

## ✨ 功能特性

### 管理员功能
- 📊 **仪表盘** - 数据可视化概览
- 👥 **用户管理** - 创建/编辑/删除客服账号
- 📋 **客户管理** - 导入/分配/按姓氏分组
- 📝 **任务管理** - 创建任务，分配给客服
- 📈 **统计分析** - 通话趋势、坐席绩效
- ⚙️ **系统配置** - Twilio 集成设置
- 🧪 **Twilio 测试** - 连接测试、短信测试、电话测试

### 客服功能
- 🎯 **工作台** - 今日任务、待办列表
- 📞 **通话列表** - 一键拨号、标记结果
- 💬 **通话记录** - 历史记录、客户沟通
- 📊 **个人统计** - 绩效数据、通话统计
- ⚙️ **设置** - 个人信息、偏好设置

### 核心特性
- ✅ 首次登录自动初始化数据库
- ✅ 登录态鉴权，未登录自动跳转
- ✅ 角色权限控制
- ✅ **智能数据导入 - 动态列匹配**
  - 自动识别 CSV/Excel 列名，无需严格按模板格式
  - 智能建议匹配（如"客户姓名"→"姓名"，"手机号"→"电话"）
  - **复合字段自动拆分**：识别单元格内的多值字段（如"机构,姓名,英文名"）
  - 支持手动调整列映射
  - 数据预览与验证
- ✅ 按姓氏首字母分组筛选（支持中英文）
- ✅ 批量分配客户给客服
- ✅ Twilio 电话拨打与录音
- ✅ 未接通自动短信通知
- ✅ 语音信箱支持
- ✅ 响应式设计

---

## 🚀 快速开始

### 方式一：Docker 部署（推荐）

```bash
# 克隆项目
git clone https://github.com/sooglejay/call-center-system.git
cd call-center-system

# 一键启动
docker-compose up -d

# 查看日志
docker logs -f callcenter-backend
```

访问 http://localhost:8080

### 方式二：本地开发

**前置要求：**
- Node.js 20+
- pnpm 8+

```bash
# 1. 克隆项目
git clone https://github.com/sooglejay/call-center-system.git
cd call-center-system

# 2. 安装后端依赖并启动
cd server
pnpm install
pnpm dev    # http://localhost:8081

# 3. 新开终端，安装前端依赖并启动
cd client
pnpm install
pnpm dev    # http://localhost:8080
```

---

## 🔐 默认账号

首次登录自动创建管理员账号：

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |

> ⚠️ 生产环境请及时修改默认密码

---

## 📁 项目结构

```
call-center-system/
├── client/                    # 前端项目
│   ├── src/
│   │   ├── components/        # 公共组件
│   │   │   └── PrivateRoute.tsx  # 路由守卫
│   │   ├── pages/             # 页面组件
│   │   │   ├── admin/         # 管理员页面
│   │   │   ├── agent/         # 客服页面
│   │   │   └── login/         # 登录页
│   │   ├── services/          # API 服务
│   │   ├── types/             # TypeScript 类型
│   │   └── App.tsx            # 路由配置
│   ├── Dockerfile
│   └── package.json
│
├── server/                    # 后端项目
│   ├── src/
│   │   ├── config/
│   │   │   └── database.ts    # 数据库配置
│   │   ├── controllers/       # 控制器
│   │   ├── routes/            # 路由
│   │   ├── services/          # 业务服务
│   │   ├── middleware/        # 中间件
│   │   └── app.ts             # 入口文件
│   ├── data/                  # SQLite 数据文件
│   ├── Dockerfile
│   └── package.json
│
├── docker-compose.yml         # Docker 编排
├── deploy-with-subpath.sh     # 部署脚本
└── README.md
```

---

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **前端** | React 18, TypeScript, Ant Design 5, Vite |
| **后端** | Node.js 20, Express, TypeScript |
| **数据库** | SQLite (sql.js), PostgreSQL (可选) |
| **认证** | JWT |
| **电话服务** | Twilio |
| **容器化** | Docker, docker-compose |

---

## 📡 API 接口

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 登录 |
| GET | /api/auth/me | 获取当前用户 |
| PUT | /api/auth/profile | 更新个人信息 |
| PUT | /api/auth/password | 修改密码 |

### 客户管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/customers | 客户列表 |
| GET | /api/customers/:id | 客户详情 |
| POST | /api/customers/import | 批量导入 |
| POST | /api/customers/batch-assign | 批量分配 |
| PUT | /api/customers/:id | 更新客户 |
| DELETE | /api/customers/:id | 删除客户 |

### 通话
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/calls | 通话记录 |
| POST | /api/calls | 创建通话 |
| PUT | /api/calls/:id/notes | 更新备注 |
| GET | /api/calls/next/dial | 获取下一个待拨打客户 |

### Twilio
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/twilio/config | 获取配置状态 |
| GET | /api/twilio/test/connection | 测试连接 |
| POST | /api/twilio/test/sms | 发送测试短信 |
| POST | /api/twilio/test/call | 拨打测试电话 |
| GET | /api/twilio/phone-numbers | 获取可用号码 |

### 任务
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/tasks | 任务列表 |
| POST | /api/tasks | 创建任务 |
| PUT | /api/tasks/:id | 更新任务 |
| DELETE | /api/tasks/:id | 删除任务 |

---

## ⚙️ 环境变量

### 后端 (server/.env)

```env
# 服务配置
NODE_ENV=production
PORT=5001

# 数据库
DB_TYPE=sqlite
SQLITE_PATH=/app/data/database.sqlite

# JWT
JWT_SECRET=your-secret-key

# Twilio（可选）
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
```

### 前端 (client/.env)

```env
VITE_API_BASE_PATH=/api
```

---

## 📞 Twilio 配置

### 1. 注册 Twilio
访问 [Twilio 官网](https://www.twilio.com) 注册账号，新用户可获得免费试用额度。

### 2. 获取凭证
登录 Twilio Console，获取：
- Account SID
- Auth Token
- 电话号码

### 3. 系统配置
在系统「系统配置」页面填写 Twilio 凭证。

### 4. 测试验证
在「Twilio 测试」页面验证功能是否正常。

---

## 🔒 安全特性

- ✅ JWT Token 认证
- ✅ 路由守卫鉴权
- ✅ 角色权限控制
- ✅ Token 过期自动跳转登录
- ✅ 密码加密存储

---

## 🧪 测试

```bash
cd server

# 运行测试
pnpm test

# 测试覆盖率
pnpm test:coverage

# 监听模式
pnpm test:watch
```

---

## 📦 生产部署

### Docker 部署

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看状态
docker-compose ps

# 查看日志
docker logs -f callcenter-backend
```

### 更新部署

```bash
git pull
docker-compose build --no-cache backend
docker-compose down && docker-compose up -d
```

---

## 🤝 贡献

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

---

## 📄 许可证

[MIT](LICENSE)

---

## 🙏 致谢

- [React](https://react.dev/)
- [Ant Design](https://ant.design/)
- [Express](https://expressjs.com/)
- [Twilio](https://www.twilio.com/)
- [sql.js](https://sql.js.org/)
