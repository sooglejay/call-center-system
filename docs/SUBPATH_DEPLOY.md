# 客服外呼系统 - 子路径部署指南

本文档介绍如何在现有网站下通过子路径部署客服外呼系统，**不影响任何现有服务**。

## 🎯 方案优势

- ✅ **零端口冲突**：不占用 80/443 端口
- ✅ **无需新域名**：复用现有域名
- ✅ **完全隔离**：与现有网站完全独立运行
- ✅ **易于管理**：统一管理，统一域名下的不同服务

## 📋 前置要求

1. 已有网站运行在 Nginx 上
2. 能够修改 Nginx 配置文件
3. 有 root 权限或 sudo 权限

## 🚀 快速部署（推荐）

### 第一步：环境检测

```bash
cd /root/jw/call-center-system

# 运行环境检测
./check-env.sh 8080 8081
```

### 第二步：部署服务（内部端口）

```bash
# 使用内部端口部署，不对外暴露
sudo ./quick-deploy.sh -p 8080 -a 8081
```

服务将运行在：
- 前端：`http://localhost:8080`
- API：`http://localhost:8081`

**注意**：外部无法直接访问，需要通过 Nginx 反向代理

### 第三步：配置 Nginx 子路径

使用自动配置脚本：

```bash
# 基本配置（会自动检测 Nginx 配置文件）
sudo ./setup-subpath.sh -d yourdomain.com

# 完整配置示例
sudo ./setup-subpath.sh \
  -d yourdomain.com \
  -s callcenter \
  -f 8080 \
  -a 8081
```

参数说明：
- `-d`: 你的域名（必需）
- `-s`: 子路径名称（默认: callcenter）
- `-f`: 前端内部端口（默认: 8080）
- `-a`: API 内部端口（默认: 8081）

### 第四步：访问系统

部署完成后，访问地址：

```
http://yourdomain.com/callcenter/
```

默认账号：`admin` / `admin123`

## 🔧 手动配置（如果自动脚本不适用）

### 1. 找到你的 Nginx 配置文件

常见位置：

```bash
# CentOS/RHEL
/etc/nginx/conf.d/your-domain.conf

# Ubuntu/Debian
/etc/nginx/sites-enabled/your-domain

# 宝塔面板
/www/server/panel/vhost/nginx/your-domain.conf
```

### 2. 备份原配置

```bash
sudo cp /etc/nginx/conf.d/your-domain.conf \
        /etc/nginx/conf.d/your-domain.conf.backup.$(date +%Y%m%d)
```

### 3. 添加反向代理配置

在你的 `server` 块中添加以下内容：

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    
    # 现有网站配置（保持不变）
    location / {
        root /var/www/html;
        index index.html;
    }
    
    # ================================================
    # 【客服外呼系统配置 - 添加到这里】
    # ================================================
    
    # 前端页面
    location /callcenter/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # API 接口
    location /callcenter/api/ {
        proxy_pass http://localhost:8081/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
    }
}
```

### 4. 检查并重载 Nginx

```bash
# 检查配置语法
sudo nginx -t

# 重载配置
sudo nginx -s reload
```

## 🌐 配置 HTTPS（SSL）

如果你有 SSL 证书，可以这样配置：

```nginx
server {
    listen 443 ssl;
    server_name yourdomain.com;
    
    # SSL 证书配置
    ssl_certificate /path/to/your/cert.pem;
    ssl_certificate_key /path/to/your/key.pem;
    
    # 现有网站配置...
    
    # 客服系统配置（同上）
    location /callcenter/ {
        proxy_pass http://localhost:8080/;
        # ...
    }
}

# HTTP 自动跳转 HTTPS
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$server_name$request_uri;
}
```

## 📝 常见问题

### Q1: 访问时显示 404 错误

**原因**：前端路由问题，需要配置 `try_files`

**解决**：确保 Nginx 配置中 `proxy_pass` 的 URL 以 `/` 结尾

```nginx
proxy_pass http://localhost:8080/;  # 注意末尾的 /
```

### Q2: API 请求失败

**原因**：API 路径映射问题

**解决**：检查 API 路径配置

```nginx
location /callcenter/api/ {
    proxy_pass http://localhost:8081/;  # 注意末尾的 /
    # ...
}
```

### Q3: 静态资源（CSS/JS）加载失败

**原因**：前端打包时使用了绝对路径

**解决**：修改前端配置，使用相对路径，或在 Nginx 添加静态资源映射：

```nginx
location /callcenter/assets/ {
    proxy_pass http://localhost:8080/assets/;
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

### Q4: 如何修改子路径名称？

**答**：可以修改为你想要的任何名称，例如：

```bash
# 使用 /crm/ 作为子路径
sudo ./setup-subpath.sh -d yourdomain.com -s crm

# 访问地址变为: http://yourdomain.com/crm/
```

### Q5: 如何移除部署？

**答**：两步移除：

1. 停止 Docker 服务：
```bash
cd /opt/call-center-system
docker-compose down
```

2. 移除 Nginx 配置（恢复备份）：
```bash
sudo cp /etc/nginx/conf.d/your-domain.conf.backup.xxxx \
        /etc/nginx/conf.d/your-domain.conf
sudo nginx -s reload
```

## 📊 完整部署流程图

```
┌─────────────────────────────────────────────────────────┐
│                     现有服务器                           │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │                 Nginx (80/443)                    │  │
│  │                                                   │  │
│  │  ┌─────────────┐      ┌─────────────────────┐    │  │
│  │  │ /           │      │ /callcenter/        │    │  │
│  │  │             │      │                     │    │  │
│  │  │ 现有网站     │      │ proxy_pass          │────┼──┼──► ┌─────────────┐
│  │  │             │      │ localhost:8080      │    │  │    │  Docker     │
│  │  │             │      │                     │    │  │    │             │
│  │  └─────────────┘      └─────────────────────┘    │  │    │ ┌─────────┐ │
│  │                            │                     │  │    │ │ Frontend│ │
│  │                            │ /api/               │  │    │ │ :8080   │ │
│  │                            ▼                     │  │    │ └─────────┘ │
│  │                     ┌─────────────┐              │  │    │ ┌─────────┐ │
│  │                     │ API 代理     │──────────────┼──┼──► │ │ Backend │ │
│  │                     │ :8081       │              │  │    │ │ :8081   │ │
│  │                     └─────────────┘              │  │    │ └─────────┘ │
│  │                                                   │  │    └─────────────┘
│  └──────────────────────────────────────────────────┘  │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## 🔍 检查部署状态

```bash
# 检查 Docker 容器状态
docker-compose ps

# 检查 Nginx 配置
sudo nginx -t

# 查看访问日志
sudo tail -f /var/log/nginx/access.log

# 查看错误日志
sudo tail -f /var/log/nginx/error.log

# 测试 API 是否通
curl http://localhost:8081/api/health

# 测试前端是否通
curl -I http://localhost:8080
```

## 📞 获取帮助

遇到问题？

1. 查看日志：`docker-compose logs -f`
2. 检查环境：`./check-env.sh`
3. 恢复配置：从备份文件恢复 Nginx 配置

## ✅ 部署检查清单

- [ ] 运行 `./check-env.sh` 检查环境
- [ ] 使用 `sudo ./quick-deploy.sh -p 8080 -a 8081` 部署服务
- [ ] 运行 `sudo ./setup-subpath.sh -d yourdomain.com` 配置 Nginx
- [ ] 访问 `http://yourdomain.com/callcenter/` 测试
- [ ] 使用默认账号 `admin`/`admin123` 登录
- [ ] 修改默认密码

---

**祝你部署顺利！** 🎉
