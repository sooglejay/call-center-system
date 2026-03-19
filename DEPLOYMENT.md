# 客服外呼系统 - 部署指南

## 目录

- [环境要求](#环境要求)
- [部署方式](#部署方式)
  - [方式一：Docker 部署（推荐）](#方式一docker-部署推荐)
  - [方式二：原生部署](#方式二原生部署)
- [Nginx 配置](#nginx-配置)
- [常见问题](#常见问题)
- [快速修复](#快速修复)

## 环境要求

### Docker 部署
- Docker 20.10+
- docker-compose 1.29+ 或 Docker Compose v2

### 原生部署
- **Node.js 18-22 LTS**（推荐 20.x）
- pnpm 8.x+
- SQLite3

⚠️ **重要提示**：
- Node.js 24 及以上版本可能与某些依赖不兼容
- 推荐使用 Node.js 20 LTS 版本

## 部署方式

### 方式一：Docker 部署（推荐）

#### 优点
- 环境隔离，依赖完整
- 不受宿主机 Node.js 版本影响
- 部署简单，易于维护
- 适合生产环境

#### 步骤

```bash
# 1. 进入项目目录
cd call-center-system

# 2. 赋予部署脚本执行权限
chmod +x deploy.sh

# 3. 执行部署脚本
./deploy.sh
# 选择选项 1 (Docker 部署)

# 或直接运行
docker-compose up -d --build
```

#### 访问地址
- 前端：http://localhost:8080
- 后端：http://localhost:8081

#### 管理命令

```bash
# 查看日志
docker-compose logs -f backend
docker-compose logs -f frontend

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看状态
docker-compose ps
```

### 方式二：原生部署

#### 优点
- 资源占用少
- 开发调试方便

#### 步骤

##### 1. 检查 Node.js 版本

```bash
node -v
# 应显示 v18.x, v20.x 或 v22.x

# 如果版本过高（如 v24.x），需要降级
```

##### 2. 安装 Node.js 20（使用 nvm）

```bash
# 安装 nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.bashrc

# 安装 Node.js 20
nvm install 20
nvm use 20
nvm alias default 20
```

##### 3. 部署

```bash
# 使用部署脚本
./deploy.sh
# 选择选项 2（生产环境）或 3（开发环境）
```

##### 4. 手动启动（可选）

```bash
# 开发模式
cd server && pnpm dev &
cd client && pnpm dev &

# 生产模式
cd server && pnpm build && pnpm start &
cd client && pnpm build && pnpm preview --port 8080 &
```

## Nginx 配置

### 为什么需要独立配置？

为了避免影响服务器上已运行的网站，我们提供了独立的 Nginx 配置文件，具有以下优点：

- ✅ **完全隔离**：不影响现有网站配置
- ✅ **独立管理**：单独的配置文件，易于维护
- ✅ **安全可靠**：配置错误不影响其他服务
- ✅ **灵活切换**：支持多种部署方式

### 配置方式

我们提供三种配置方式：

| 方式 | 访问地址 | 适用场景 |
|------|---------|---------|
| 子路径配置 | `yourdomain.com/callcenter/` | 与现有网站共存 |
| 独立域名配置 | `call.yourdomain.com` | 有独立子域名 |
| 仅代理 API | `yourdomain.com/api/` | 前端使用容器端口 |

### 快速安装

```bash
# 1. 进入项目目录
cd call-center-system

# 2. 运行配置脚本（需要 root 权限）
sudo ./setup-nginx.sh

# 3. 选择配置方式
# 输入 1-5 选择不同的配置方案
```

### 手动安装

如果你想手动配置：

```bash
# 1. 复制配置文件到 Nginx 目录
sudo cp nginx-callcenter.conf /etc/nginx/conf.d/callcenter.conf

# 2. 编辑配置文件（根据需要选择配置方式）
sudo vim /etc/nginx/conf.d/callcenter.conf

# 3. 测试配置
sudo nginx -t

# 4. 重载 Nginx
sudo nginx -s reload
```

### 配置文件说明

配置文件位于项目根目录：`nginx-callcenter.conf`

```nginx
# 上游服务器定义
upstream callcenter_backend {
    server 127.0.0.1:8081;  # 后端 API
}

upstream callcenter_frontend {
    server 127.0.0.1:8080;  # 前端
}

# 子路径配置（默认）
server {
    listen 80;
    server_name _;

    # 访问地址：http://yourdomain.com/callcenter/
    location /callcenter/ {
        proxy_pass http://callcenter_frontend/;
        ...
    }

    location /callcenter/api/ {
        proxy_pass http://callcenter_backend/api/;
        ...
    }
}
```

### 卸载配置

如果需要移除配置：

```bash
# 运行卸载脚本
sudo ./setup-nginx.sh
# 选择选项 5

# 或手动删除
sudo rm /etc/nginx/conf.d/callcenter.conf
sudo nginx -s reload
```

### 端口映射

```
┌─────────────────────────────────────────────────────┐
│                  Nginx 代理架构                      │
├─────────────────────────────────────────────────────┤
│                                                     │
│  用户请求                                           │
│      │                                              │
│      ▼                                              │
│  ┌─────────────┐                                    │
│  │   Nginx     │  监听 80/443 端口                  │
│  │   :80       │                                    │
│  └──────┬──────┘                                    │
│         │                                           │
│         ├─► /callcenter/*  ──► 127.0.0.1:8080       │
│         │                    (前端容器)              │
│         │                                           │
│         └─► /callcenter/api/* ──► 127.0.0.1:8081    │
│                              (后端容器)              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## 常见问题

### 1. SyntaxError: Unexpected token '{' (tsx 模块错误)

**原因**：Node.js 版本过高（v24+），与 tsx 4.x 不兼容

**解决方案**：

```bash
# 方案A：使用 Node.js 20
nvm install 20
nvm use 20

# 方案B：使用 Docker 部署
docker-compose up -d

# 方案C：编译后运行（不使用 tsx）
cd server
pnpm build
pnpm start  # 使用编译后的 JavaScript
```

### 2. 端口被占用

**错误信息**：`Error: listen EADDRINUSE: address already in use :::8081`

**解决方案**：

```bash
# 查找占用端口的进程
lsof -i:8081

# 杀死进程
kill -9 <PID>

# 或使用不同的端口
PORT=8082 pnpm dev
```

### 3. 数据库初始化失败

**解决方案**：

```bash
cd server
pnpm run db:init
pnpm run db:migrate
```

### 4. 前端无法连接后端

**检查清单**：
- [ ] 后端服务是否启动
- [ ] 后端端口是否正确（默认 8081）
- [ ] 前端配置的 API 地址是否正确
- [ ] 防火墙是否开放端口

## 快速修复

### 修复脚本

```bash
# 运行修复脚本
chmod +x fix-node-version.sh
./fix-node-version.sh
```

### 一键部署

```bash
# Docker 部署（推荐）
chmod +x deploy.sh && ./deploy.sh

# 选择选项 1
```

## 生产环境建议

1. **使用 Docker 部署**
   - 环境一致性强
   - 易于扩展和维护

2. **配置反向代理**
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:8080;
       }
       
       location /api {
           proxy_pass http://localhost:8081;
       }
   }
   ```

3. **数据备份**
   ```bash
   # 备份数据库
   cp data/database.sqlite backups/database-$(date +%Y%m%d).sqlite
   ```

4. **日志管理**
   ```bash
   # 查看日志
   tail -f logs/*.log
   ```

5. **安全配置**
   - 修改默认 JWT_SECRET
   - 配置防火墙规则
   - 启用 HTTPS

## 技术支持

如遇问题，请提供以下信息：
- 操作系统版本
- Node.js 版本 (`node -v`)
- Docker 版本 (`docker -v`)
- 错误日志
