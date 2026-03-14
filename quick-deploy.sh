#!/bin/bash
# =====================================================
# 客服外呼系统 - 快速部署脚本（适用于已有项目代码）
# 如果你的项目代码已在服务器上，使用此脚本
# =====================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-/opt/call-center-system}"

print_info "开始快速部署..."
print_info "项目目录: ${PROJECT_DIR}"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    print_error "Docker 未安装！请先运行完整部署脚本，或手动安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose 未安装！请先运行完整部署脚本"
    exit 1
fi

# 创建项目目录
mkdir -p ${PROJECT_DIR}/{client,server,data,logs}

# 检查当前目录是否有项目代码
if [ -f "server/package.json" ] && [ -f "client/package.json" ]; then
    print_info "检测到当前目录有项目代码，开始复制..."
    
    # 复制后端代码
    cp -r server/* ${PROJECT_DIR}/server/ 2>/dev/null || true
    
    # 复制前端代码  
    cp -r client/* ${PROJECT_DIR}/client/ 2>/dev/null || true
    
    print_success "代码复制完成"
else
    print_error "未检测到项目代码！"
    print_info "请确保在包含 server/ 和 client/ 目录的项目根目录中运行此脚本"
    exit 1
fi

# 进入项目目录
cd ${PROJECT_DIR}

# 创建 Dockerfile
cat > server/Dockerfile << 'DOCKERFILE'
FROM node:20-alpine
RUN apk add --no-cache python3 make g++ sqlite sqlite-dev
WORKDIR /app
COPY package.json pnpm-lock.yaml* ./
RUN npm install -g pnpm && pnpm install
COPY . .
RUN pnpm run build
EXPOSE 5001
CMD ["sh", "-c", "pnpm db:seed && pnpm start"]
DOCKERFILE

cat > client/Dockerfile << 'DOCKERFILE'
FROM node:20-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install
COPY . .
RUN pnpm run build
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
DOCKERFILE

# 创建 Nginx 配置
cat > client/nginx.conf << 'NGINX'
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    location /api/ {
        proxy_pass http://server:5001/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 86400;
    }
}
NGINX

# 创建 Docker Compose 配置
cat > docker-compose.yml << 'COMPOSE'
version: '3.8'

services:
  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: call-center-server
    restart: always
    ports:
      - "5001:5001"
    volumes:
      - ./data:/app/data
      - ./server/uploads:/app/uploads
      - ./logs:/app/logs
    environment:
      - NODE_ENV=production
      - PORT=5001
      - DB_TYPE=sqlite
      - SQLITE_PATH=/app/data/database.sqlite
      - JWT_SECRET=your-secret-key-change-this-in-production
      - JWT_EXPIRES_IN=24h
      - UPLOAD_PATH=/app/uploads
      - MAX_FILE_SIZE=10485760
    networks:
      - call-center-network

  client:
    build:
      context: ./client
      dockerfile: Dockerfile
    container_name: call-center-client
    restart: always
    ports:
      - "80:80"
    depends_on:
      - server
    networks:
      - call-center-network

networks:
  call-center-network:
    driver: bridge
COMPOSE

# 构建并启动
print_info "开始构建 Docker 镜像..."
docker-compose build --no-cache

print_info "启动服务..."
docker-compose up -d

sleep 10

# 检查状态
if docker-compose ps | grep -q "Up"; then
    echo ""
    echo "================================================"
    print_success "🎉 部署成功！"
    echo "================================================"
    echo ""
    echo "访问地址:"
    echo "  前端: http://$(curl -s icanhazip.com 2>/dev/null || echo 'your-server-ip')"
    echo "  API:  http://$(curl -s icanhazip.com 2>/dev/null || echo 'your-server-ip'):5001"
    echo ""
    echo "默认账号: admin / admin123"
    echo ""
    echo "常用命令:"
    echo "  查看日志: docker-compose logs -f"
    echo "  停止服务: docker-compose down"
    echo "  重启服务: docker-compose restart"
    echo "================================================"
else
    print_error "服务启动失败，查看日志:"
    docker-compose logs -f
fi
