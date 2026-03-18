# 客服外呼系统 - 开发迭代流程

## 目录
- [环境说明](#环境说明)
- [开发流程](#开发流程)
- [部署流程](#部署流程)
- [回滚流程](#回滚流程)
- [常见问题](#常见问题)

---

## 环境说明

### 端口规划
| 服务 | 本地开发 | Docker 部署 |
|------|----------|-------------|
| 前端 | 5005 (Vite) | 8080 → 80 |
| 后端 | 8081 | 8081 → 5001 |
| 数据库 | SQLite 文件 | SQLite 文件 (挂载卷) |

### 目录结构
```
/workspace/projects/call-center-system/
├── client/                 # 前端代码
│   ├── src/
│   ├── Dockerfile
│   └── nginx.conf
├── server/                 # 后端代码
│   ├── src/
│   ├── Dockerfile
│   └── .env               # 本地开发配置
├── data/                   # 数据库文件 (挂载卷)
├── logs/                   # 日志文件 (挂载卷)
├── docker-compose.yml      # Docker 编排
└── deploy-with-subpath.sh  # 部署脚本
```

---

## 开发流程

### 1. 启动本地开发环境

```bash
# 进入项目目录
cd /workspace/projects/call-center-system

# 启动后端 (终端1)
cd server && pnpm run dev

# 启动前端 (终端2)
cd client && pnpm run dev

# 访问
# 前端: http://localhost:5005
# API:  http://localhost:8081/api
```

### 2. 开发代码

```bash
# 创建功能分支
git checkout -b feature/xxx

# 开发... 代码会热更新

# 本地测试
pnpm run build  # 构建测试
```

### 3. 提交代码

```bash
# 检查修改
git status
git diff

# 提交
git add .
git commit -m "feat: 新功能描述"

# 推送到远程
git push origin feature/xxx
```

---

## 部署流程

### 方式一：快速更新 (推荐小改动)

```bash
# SSH 登录服务器
ssh user@your-server

# 进入项目目录
cd /opt/call-center-system

# 拉取最新代码
git pull origin main

# 重新构建并启动
docker compose build --no-cache
docker compose up -d

# 查看日志确认
docker compose logs -f
```

### 方式二：完整部署 (推荐大版本更新)

```bash
# 使用部署脚本
./deploy-with-subpath.sh -d example.com -s callcenter

# 或指定自定义端口
./deploy-with-subpath.sh -d example.com -s callcenter -p 8080 -a 8081
```

### 方式三：仅更新前端/后端

```bash
# 仅更新前端
docker compose build frontend --no-cache
docker compose up -d frontend

# 仅更新后端
docker compose build backend --no-cache
docker compose up -d backend
```

### 部署后验证

```bash
# 检查服务状态
docker compose ps

# 检查健康状态
curl http://localhost:8081/api/system/health
curl http://localhost:8080

# 检查日志
docker compose logs -f --tail=100
```

---

## 回滚流程

### 1. 代码回滚

```bash
# 查看提交历史
git log --oneline -10

# 回滚到指定版本
git reset --hard <commit-hash>

# 重新部署
docker compose build --no-cache
docker compose up -d
```

### 2. 快速回滚 (保留上一版本镜像)

```bash
# 查看镜像历史
docker images | grep callcenter

# 使用上一版本镜像 (如果有)
docker tag callcenter-backend:previous callcenter-backend:latest
docker compose up -d
```

### 3. 数据库回滚

```bash
# 数据库文件在 ./data/database.sqlite
# 建议每次部署前备份

# 备份
cp ./data/database.sqlite ./data/database.sqlite.bak.$(date +%Y%m%d_%H%M%S)

# 恢复
cp ./data/database.sqlite.bak.xxx ./data/database.sqlite
docker compose restart backend
```

---

## 常见问题

### Q1: 端口被占用

```bash
# 查看端口占用
lsof -i :8080
lsof -i :8081

# 杀掉进程
kill -9 <PID>
```

### Q2: 容器启动失败

```bash
# 查看详细日志
docker compose logs backend
docker compose logs frontend

# 检查容器状态
docker compose ps -a

# 重建容器
docker compose down
docker compose up -d --build
```

### Q3: 数据库问题

```bash
# 进入后端容器
docker exec -it callcenter-backend sh

# 或直接操作数据库文件
sqlite3 ./data/database.sqlite
```

### Q4: 前端页面空白

```bash
# 检查构建产物
ls -la client/dist/

# 检查 nginx 配置
docker exec -it callcenter-frontend cat /etc/nginx/conf.d/default.conf

# 重新构建前端
docker compose build frontend --no-cache
docker compose up -d frontend
```

---

## 推荐的 Git 分支策略

```
main (生产环境)
  │
  ├── develop (开发环境)
  │     │
  │     ├── feature/xxx (功能分支)
  │     ├── feature/yyy
  │     │
  │     └── bugfix/xxx (修复分支)
  │
  └── hotfix/xxx (紧急修复)
```

### 工作流

1. 从 `develop` 创建 `feature` 分支开发
2. 开发完成后 PR 合并到 `develop`
3. 测试通过后 PR 合并到 `main`
4. 服务器拉取 `main` 分支部署

---

## 自动化部署 (可选)

### 使用 GitHub Actions

创建 `.github/workflows/deploy.yml`:

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Deploy to server
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.HOST }}
          username: ${{ secrets.USERNAME }}
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd /opt/call-center-system
            git pull origin main
            docker compose build --no-cache
            docker compose up -d
```

---

## 监控与日志

### 查看实时日志

```bash
# 所有服务
docker compose logs -f

# 仅后端
docker compose logs -f backend

# 最近 100 行
docker compose logs -f --tail=100
```

### 日志文件位置

```bash
# 容器日志
docker compose logs > logs/container-$(date +%Y%m%d).log

# 应用日志 (如果配置了)
tail -f logs/app.log
```
