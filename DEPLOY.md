# 部署指南

本文档介绍如何将呼叫中心系统部署到生产环境。

## 部署架构

```
┌─────────────────┐     ┌──────────────────┐
│   Vercel        │────▶│   Railway        │
│   (Frontend)    │     │   (Backend + DB) │
│                 │◀────│                  │
└─────────────────┘     └──────────────────┘
         │                       │
         ▼                       ▼
  https://call-center-    https://call-center-api
  demo.vercel.app         .railway.app
```

## 快速部署

### 1. 部署后端到 Railway

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/new)

**步骤：**

1. 点击上方按钮或登录 [Railway](https://railway.app)
2. 选择 "Deploy from GitHub repo"
3. 选择你的 fork 的仓库
4. 添加 PostgreSQL 数据库（New → Database → PostgreSQL）
5. 配置环境变量（见下方）
6. 部署完成后会获得 API URL: `https://call-center-api.up.railway.app`

**环境变量配置：**

```env
NODE_ENV=production
PORT=5001
JWT_SECRET=your-super-secret-jwt-key
DATABASE_URL=${{Postgres.DATABASE_URL}}  # Railway 会自动填充
TWILIO_ACCOUNT_SID=your-twilio-account-sid
TWILIO_AUTH_TOKEN=your-twilio-auth-token
TWILIO_PHONE_NUMBER=your-twilio-phone-number
```

### 2. 部署前端到 Vercel

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new)

**步骤：**

1. 点击上方按钮或登录 [Vercel](https://vercel.com)
2. 导入 GitHub 仓库
3. 配置项目：
   - **Framework Preset**: Vite
   - **Root Directory**: `client`
4. 添加环境变量：
   ```env
   VITE_API_URL=https://call-center-api.up.railway.app/api
   ```
5. 点击 Deploy

## 详细部署步骤

### 后端部署 (Railway)

#### 方法一：通过 Railway CLI

```bash
# 安装 Railway CLI
npm install -g @railway/cli

# 登录
railway login

# 初始化项目
cd server
railway init

# 添加 PostgreSQL
railway add --database postgres

# 配置环境变量
railway variables set NODE_ENV=production
railway variables set JWT_SECRET=your-secret

# 部署
railway up
```

#### 方法二：通过 Dockerfile

项目已包含 `server/Dockerfile`，Railway 会自动检测并使用。

```dockerfile
FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci --only=production

COPY . .
RUN npm run build

EXPOSE 5001

CMD ["npm", "start"]
```

### 前端部署 (Vercel)

#### 方法一：通过 Vercel CLI

```bash
# 安装 Vercel CLI
npm install -g vercel

# 登录
vercel login

# 部署
cd client
vercel --prod
```

#### 方法二：配置文件

项目已包含 `client/vercel.json`：

```json
{
  "rewrites": [
    {
      "source": "/(.*)",
      "destination": "/index.html"
    }
  ],
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "framework": "vite"
}
```

## 配置 Twilio

部署完成后，需要在 Twilio 控制台配置 Webhook：

1. 登录 [Twilio Console](https://console.twilio.com)
2. 进入 Phone Numbers → Manage → Active numbers
3. 点击你的号码
4. 配置 Voice & Fax：
   - **Accept incoming**: Webhook
   - **Webhook URL**: `https://call-center-api.railway.app/api/twilio/voice`
   - **HTTP Method**: POST
5. 配置 Status Callback：
   - **Status Callback URL**: `https://call-center-api.railway.app/api/twilio/status`
   - **Status Callback Events**: 勾选所有选项

## 初始化数据库

部署后需要初始化数据库表：

```bash
# 连接到 Railway PostgreSQL
railway connect postgres

# 执行初始化 SQL
\i server/src/scripts/init-db.sql
```

或使用 Railway 的 SQL 执行功能。

## 验证部署

### 检查后端

```bash
curl https://call-center-api.railway.app/api/system/health
```

预期响应：
```json
{
  "status": "ok",
  "timestamp": "2024-01-01T00:00:00.000Z"
}
```

### 检查前端

访问 `https://call-center-demo.vercel.app`，应能看到登录页面。

## 自定义域名（可选）

### Vercel 自定义域名

1. 进入项目设置 → Domains
2. 添加你的域名
3. 按提示配置 DNS

### Railway 自定义域名

1. 进入项目设置 → Domains
2. 添加你的域名
3. 配置 DNS CNAME 记录

## 监控与日志

### Railway 日志

```bash
# 查看实时日志
railway logs -f

# 查看特定服务日志
railway logs -s server
```

### Vercel 日志

```bash
# 查看部署日志
vercel logs --production
```

## 更新部署

### 自动更新（推荐）

配置 GitHub Actions 后，推送到 main 分支会自动触发部署：

```bash
git add .
git commit -m "feat: update features"
git push origin main
```

### 手动更新

**Railway:**
```bash
cd server
railway up
```

**Vercel:**
```bash
cd client
vercel --prod
```

## 故障排查

### 后端问题

**问题：无法连接数据库**
- 检查 `DATABASE_URL` 环境变量
- 确认 PostgreSQL 服务已启动

**问题：Twilio 通话失败**
- 检查 Twilio 配置是否正确
- 验证 Webhook URL 可访问
- 检查日志中的错误信息

### 前端问题

**问题：API 请求失败**
- 检查 `VITE_API_URL` 是否正确
- 确认后端服务正常运行
- 检查浏览器控制台网络请求

**问题：404 页面**
- 确认 `vercel.json` 中 rewrite 配置正确

## 安全建议

1. **JWT Secret**: 使用强随机字符串，至少 32 位
2. **数据库**: 使用 Railway 提供的内部网络连接
3. **CORS**: 生产环境限制允许的域名
4. **HTTPS**: 确保使用 HTTPS（Vercel 和 Railway 默认提供）

## 成本估算

| 服务 | 免费额度 | 预估月费 |
|------|---------|---------|
| Vercel | 100GB 带宽 | 免费 |
| Railway | $5 信用额度 | $5+ |
| Twilio | 试用金 | 按量付费 |

## 备份策略

定期备份 PostgreSQL 数据库：

```bash
# 导出数据
pg_dump $DATABASE_URL > backup.sql

# 恢复数据
psql $DATABASE_URL < backup.sql
```

或使用 Railway 的自动备份功能。

## 扩展阅读

- [Railway 文档](https://docs.railway.app)
- [Vercel 文档](https://vercel.com/docs)
- [Twilio 文档](https://www.twilio.com/docs)
- [Docker 文档](https://docs.docker.com)

## 获取帮助

如有部署问题，请：

1. 查看项目 [Issues](https://github.com/yourusername/call-center-system/issues)
2. 检查服务日志
3. 联系支持团队
