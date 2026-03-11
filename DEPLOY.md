# 部署指南

## 免费云服务部署（推荐）

本项目支持多种免费云服务部署方案，选择适合你的方式：

### 方案一：Vercel + Railway（推荐）

#### 1. 部署前端到 Vercel

```bash
# 安装 Vercel CLI
npm i -g vercel

# 登录
vercel login

# 进入前端目录并部署
cd client
vercel

# 生产部署
vercel --prod
```

#### 2. 部署后端到 Railway

```bash
# 安装 Railway CLI
npm i -g @railway/cli

# 登录
railway login

# 进入后端目录
cd server

# 初始化项目
railway init

# 添加 PostgreSQL 数据库
railway add --database postgres

# 设置环境变量
railway variables set JWT_SECRET=your-secret
railway variables set NODE_ENV=production

# 部署
railway up
```

#### 3. 配置前端 API 地址

在 Vercel 控制台设置环境变量：
- `VITE_API_URL`: 你的 Railway 后端地址 (如 `https://call-center-api.up.railway.app`)

### 方案二：Docker Compose 部署

```bash
# 克隆项目
git clone https://github.com/sooglejay/call-center-system.git
cd call-center-system

# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

访问地址：
- 前端: http://localhost:3000
- 后端 API: http://localhost:5001
- 数据库: localhost:5432

### 方案三：Render 部署

1. Fork 本仓库到你的 GitHub
2. 在 Render 创建 New Web Service
3. 选择你的仓库
4. 配置：
   - Build Command: `cd server && pnpm install && pnpm build`
   - Start Command: `cd server && pnpm start`
5. 添加 PostgreSQL 数据库
6. 设置环境变量

## 环境变量配置

### 必需变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| PORT | 服务端口 | 5001 |
| DB_HOST | 数据库主机 | localhost |
| DB_PORT | 数据库端口 | 5432 |
| DB_NAME | 数据库名 | callcenter |
| DB_USER | 数据库用户 | callcenter |
| DB_PASSWORD | 数据库密码 | your_password |
| JWT_SECRET | JWT 密钥 | your-secret-key |

### 可选变量（Twilio 集成）

| 变量名 | 说明 | 示例 |
|--------|------|------|
| TWILIO_ACCOUNT_SID | Twilio Account SID | ACxxxxxxxx |
| TWILIO_AUTH_TOKEN | Twilio Auth Token | your_auth_token |
| TWILIO_PHONE_NUMBER | 发信号码 | +1234567890 |
| TWILIO_CALLBACK_URL | Webhook 回调地址 | https://xxx/api/twilio/webhook |

## GitHub Actions 自动部署

项目已配置 CI/CD，推送到 main 分支会自动：

1. 运行代码检查
2. 构建前端并部署到 Vercel
3. 构建后端（可扩展部署到 Railway）

### 配置 Secrets

在 GitHub 仓库 Settings → Secrets → Actions 添加：

```
VERCEL_TOKEN=your_vercel_token
VERCEL_ORG_ID=your_org_id
VERCEL_PROJECT_ID=your_project_id
RAILWAY_TOKEN=your_railway_token
```

## 数据库迁移

### 自动迁移

后端启动时会自动创建表结构。

### 手动迁移

```bash
cd server
psql -U your_db_user -d callcenter -f src/scripts/init-db.sql
```

## 生产环境优化

### 1. 启用 HTTPS

使用 Cloudflare 或 Let's Encrypt：

```bash
# 使用 certbot
sudo apt install certbot
sudo certbot --nginx
```

### 2. 配置 Nginx 反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location /api {
        proxy_pass http://localhost:5001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
    
    location / {
        root /path/to/client/dist;
        try_files $uri $uri/ /index.html;
    }
}
```

### 3. 监控和日志

使用 PM2 管理服务：

```bash
npm install -g pm2

# 启动
pm2 start server/dist/app.js --name "call-center-api"

# 保存配置
pm2 save
pm2 startup

# 查看日志
pm2 logs call-center-api
```

## 常见问题

### 1. 数据库连接失败

检查数据库配置是否正确，确保数据库服务已启动。

### 2. 前端无法连接后端

检查 `VITE_API_URL` 是否配置正确，确保后端服务可访问。

### 3. Twilio 回调不生效

确保服务器有公网 IP，Webhook URL 可访问。

## 支持

如有问题，请提交 Issue 或联系：sooglejay@gmail.com
