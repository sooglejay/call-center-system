#!/bin/bash
# =====================================================
# 客服外呼系统 - Docker 一键部署脚本
# 适用于 CentOS 7 / Ubuntu / Debian 等 Linux 发行版
# 
# 用法:
#   ./deploy-call-center.sh [选项]
#
# 选项:
#   -p, --http-port <端口>    HTTP 端口 (默认: 8080)
#   -a, --api-port <端口>     API 端口 (默认: 8081)
#   -d, --domain <域名>       访问域名 (默认: localhost)
#   -h, --help                显示帮助信息
#
# 示例:
#   ./deploy-call-center.sh                           # 使用默认端口
#   ./deploy-call-center.sh -p 8080 -a 8081          # 自定义端口
#   ./deploy-call-center.sh --http-port 8080         # 仅修改 HTTP 端口
# =====================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的信息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    echo "客服外呼系统 - Docker 部署脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -p, --http-port <端口>    HTTP 端口 (默认: 8080)"
    echo "  -a, --api-port <端口>     API 端口 (默认: 8081)"
    echo "  -d, --domain <域名>       访问域名 (默认: localhost)"
    echo "  -h, --help                显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                           # 使用默认端口"
    echo "  $0 -p 8080 -a 8081          # 自定义端口"
    echo "  $0 --http-port 8080         # 仅修改 HTTP 端口"
    echo ""
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -p|--http-port)
                HTTP_PORT="$2"
                shift 2
                ;;
            -a|--api-port)
                API_PORT="$2"
                shift 2
                ;;
            -d|--domain)
                DOMAIN="$2"
                shift 2
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                print_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 检查端口是否被占用
check_port() {
    local port=$1
    local name=$2
    if command -v ss &> /dev/null; then
        if ss -tuln | grep -q ":$port "; then
            print_error "端口 $port 已被占用，无法启动 $name"
            print_info "请使用 -p 或 -a 参数指定其他端口"
            print_info "示例: $0 -p 8080 -a 8081"
            exit 1
        fi
    elif command -v netstat &> /dev/null; then
        if netstat -tuln 2>/dev/null | grep -q ":$port "; then
            print_error "端口 $port 已被占用，无法启动 $name"
            print_info "请使用 -p 或 -a 参数指定其他端口"
            print_info "示例: $0 -p 8080 -a 8081"
            exit 1
        fi
    fi
}

# 检查是否为 root 用户
if [[ $EUID -ne 0 ]]; then
   print_error "请使用 root 用户运行此脚本"
   exit 1
fi

# 默认配置
PROJECT_NAME="call-center-system"
PROJECT_DIR="/opt/${PROJECT_NAME}"
DOMAIN="localhost"
HTTP_PORT="8080"
API_PORT="8081"

# 解析命令行参数
parse_args "$@"

# 验证端口号是否为数字
if ! [[ "$HTTP_PORT" =~ ^[0-9]+$ ]] || [ "$HTTP_PORT" -lt 1 ] || [ "$HTTP_PORT" -gt 65535 ]; then
    print_error "HTTP 端口必须是 1-65535 之间的数字"
    exit 1
fi

if ! [[ "$API_PORT" =~ ^[0-9]+$ ]] || [ "$API_PORT" -lt 1 ] || [ "$API_PORT" -gt 65535 ]; then
    print_error "API 端口必须是 1-65535 之间的数字"
    exit 1
fi

if [ "$HTTP_PORT" -eq "$API_PORT" ]; then
    print_error "HTTP 端口和 API 端口不能相同"
    exit 1
fi

# 检查端口占用
check_port $HTTP_PORT "前端服务"
check_port $API_PORT "后端 API 服务"

print_info "开始部署客服外呼系统..."
print_info "项目目录: ${PROJECT_DIR}"
print_info "访问域名: ${DOMAIN}"
print_info "HTTP端口: ${HTTP_PORT}"
print_info "API端口: ${API_PORT}"
echo ""

# =====================================================
# 步骤 1: 安装 Docker 和 Docker Compose
# =====================================================
print_info "步骤 1/6: 检查并安装 Docker..."

if ! command -v docker &> /dev/null; then
    print_warning "Docker 未安装，开始安装..."
    
    # 检测 Linux 发行版
    if [ -f /etc/redhat-release ]; then
        # CentOS/RHEL
        print_info "检测到 CentOS/RHEL 系统"
        
        # 更换阿里云镜像源
        curl -o /etc/yum.repos.d/CentOS-Base.repo https://mirrors.aliyun.com/repo/Centos-7.repo 2>/dev/null || true
        yum clean all 2>/dev/null || true
        yum makecache 2>/dev/null || true
        
        # 安装必要工具
        yum install -y yum-utils device-mapper-persistent-data lvm2 2>/dev/null || true
        
        # 添加 Docker 源
        yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null || \
            curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo -o /etc/yum.repos.d/docker-ce.repo
        
        # 安装 Docker
        yum install -y docker-ce docker-ce-cli containerd.io
        
    elif [ -f /etc/debian_version ]; then
        # Ubuntu/Debian
        print_info "检测到 Ubuntu/Debian 系统"
        apt-get update
        apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
        
        # 添加 Docker 官方 GPG key
        curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        
        # 添加 Docker 源
        echo \
            "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu \
            $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
        
        apt-get update
        apt-get install -y docker-ce docker-ce-cli containerd.io
    else
        print_error "不支持的 Linux 发行版"
        exit 1
    fi
    
    # 启动 Docker
    systemctl start docker
    systemctl enable docker
    
    print_success "Docker 安装完成"
else
    print_success "Docker 已安装"
fi

# 检查 Docker Compose
if ! command -v docker-compose &> /dev/null; then
    print_warning "Docker Compose 未安装，开始安装..."
    
    # 安装 Docker Compose
    DOCKER_COMPOSE_VERSION="v2.24.0"
    curl -L "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    ln -sf /usr/local/bin/docker-compose /usr/bin/docker-compose
    
    print_success "Docker Compose 安装完成"
else
    print_success "Docker Compose 已安装"
fi

# 配置 Docker 镜像加速
print_info "配置 Docker 镜像加速..."
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
EOF
systemctl restart docker
print_success "Docker 镜像加速配置完成"

# =====================================================
# 步骤 2: 创建项目目录结构
# =====================================================
print_info "步骤 2/6: 创建项目目录..."

mkdir -p ${PROJECT_DIR}/{client,server,data,logs,nginx}
cd ${PROJECT_DIR}

# 创建必要的子目录
mkdir -p server/uploads server/data client/dist

print_success "项目目录创建完成: ${PROJECT_DIR}"

# =====================================================
# 步骤 3: 创建 Dockerfile 和配置文件
# =====================================================
print_info "步骤 3/6: 创建 Docker 配置文件..."

# 后端 Dockerfile
cat > server/Dockerfile << 'EOF'
FROM node:20-alpine

# 安装必要的系统依赖
RUN apk add --no-cache \
    python3 \
    make \
    g++ \
    sqlite \
    sqlite-dev

WORKDIR /app

# 复制 package 文件
COPY package.json pnpm-lock.yaml* ./

# 安装 pnpm
RUN npm install -g pnpm

# 安装依赖
RUN pnpm install

# 复制源代码
COPY . .

# 构建
RUN pnpm run build

# 暴露端口
EXPOSE 5001

# 启动命令
CMD ["sh", "-c", "pnpm db:seed && pnpm start"]
EOF

# 前端 Dockerfile (用于生产构建)
cat > client/Dockerfile << 'EOF'
FROM node:20-alpine AS builder

WORKDIR /app

# 安装 pnpm
RUN npm install -g pnpm

# 复制 package 文件
COPY package.json pnpm-lock.yaml* ./

# 安装依赖
RUN pnpm install

# 复制源代码
COPY . .

# 构建
RUN pnpm run build

# 生产环境使用 Nginx
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
EOF

# Nginx 配置 - 使用变量替换端口
cat > client/nginx.conf << EOF
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # 前端路由支持
    location / {
        try_files \$uri \$uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API 代理
    location /api/ {
        proxy_pass http://server:5001/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        proxy_read_timeout 86400;
    }

    # WebSocket 支持
    location /socket.io/ {
        proxy_pass http://server:5001/socket.io/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 86400;
    }
}
EOF

# Docker Compose 配置 - 使用传入的端口变量
cat > docker-compose.yml << EOF
version: '3.8'

services:
  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: call-center-server
    restart: always
    ports:
      - "${API_PORT}:5001"
    volumes:
      - ./data:/app/data
      - ./server/uploads:/app/uploads
      - ./logs:/app/logs
    environment:
      - NODE_ENV=production
      - PORT=5001
      - DB_TYPE=sqlite
      - SQLITE_PATH=/app/data/database.sqlite
      - JWT_SECRET=${JWT_SECRET:-$(openssl rand -base64 32)}
      - JWT_EXPIRES_IN=24h
      - UPLOAD_PATH=/app/uploads
      - MAX_FILE_SIZE=10485760
    networks:
      - call-center-network
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:5001/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  client:
    build:
      context: ./client
      dockerfile: Dockerfile
    container_name: call-center-client
    restart: always
    ports:
      - "${HTTP_PORT}:80"
    depends_on:
      server:
        condition: service_healthy
    networks:
      - call-center-network

networks:
  call-center-network:
    driver: bridge
EOF

# 环境变量模板
cat > .env << EOF
# 客服外呼系统环境变量

# 基础配置
NODE_ENV=production
DOMAIN=${DOMAIN}
HTTP_PORT=${HTTP_PORT}
API_PORT=${API_PORT}

# 数据库配置 (SQLite)
DB_TYPE=sqlite
SQLITE_PATH=./data/database.sqlite

# JWT 密钥 (请修改为强密码)
JWT_SECRET=$(openssl rand -base64 32)
JWT_EXPIRES_IN=24h

# 文件上传
UPLOAD_PATH=./uploads
MAX_FILE_SIZE=10485760

# 前端 API 地址
VITE_API_URL=http://${DOMAIN}:${API_PORT}
EOF

print_success "Docker 配置文件创建完成"

# =====================================================
# 步骤 4: 克隆或复制项目代码
# =====================================================
print_info "步骤 4/6: 准备项目代码..."

# 检查是否在当前项目目录中
if [ -f "openclaw.json" ] || [ -d "server/src" ]; then
    print_info "检测到当前目录已有项目代码，将复制到部署目录..."
    
    # 复制后端代码
    if [ -d "server" ]; then
        cp -r server/* ${PROJECT_DIR}/server/
    fi
    
    # 复制前端代码
    if [ -d "client" ]; then
        cp -r client/* ${PROJECT_DIR}/client/
    fi
    
    print_success "项目代码复制完成"
else
    print_warning "未检测到项目代码，请手动将代码复制到以下目录："
    print_info "后端代码: ${PROJECT_DIR}/server/"
    print_info "前端代码: ${PROJECT_DIR}/client/"
    print_info ""
    print_info "您可以通过以下方式获取代码："
    print_info "1. git clone 到 ${PROJECT_DIR}"
    print_info "2. 上传代码到服务器并复制到对应目录"
    print_info ""
    read -p "按回车键继续 (代码准备完成后)..."
fi

# =====================================================
# 步骤 5: 构建并启动服务
# =====================================================
print_info "步骤 5/6: 构建 Docker 镜像..."

cd ${PROJECT_DIR}

# 检查代码是否存在
if [ ! -f "server/package.json" ] || [ ! -f "client/package.json" ]; then
    print_error "项目代码不完整！"
    print_info "请确保以下文件存在："
    print_info "  - ${PROJECT_DIR}/server/package.json"
    print_info "  - ${PROJECT_DIR}/client/package.json"
    exit 1
fi

# 构建并启动
docker-compose build --no-cache

print_success "Docker 镜像构建完成"

print_info "启动服务..."
docker-compose up -d

# 等待服务启动
print_info "等待服务启动..."
sleep 10

# 检查服务状态
if docker-compose ps | grep -q "Up"; then
    print_success "服务启动成功！"
else
    print_error "服务启动失败，请检查日志："
    print_info "docker-compose logs -f"
    exit 1
fi

# =====================================================
# 步骤 6: 配置防火墙和完成部署
# =====================================================
print_info "步骤 6/6: 配置防火墙..."

# 开放端口
if command -v firewall-cmd &> /dev/null; then
    # CentOS/RHEL
    firewall-cmd --permanent --add-port=${HTTP_PORT}/tcp
    firewall-cmd --permanent --add-port=${API_PORT}/tcp
    firewall-cmd --reload
    print_success "防火墙配置完成 (firewalld)"
elif command -v ufw &> /dev/null; then
    # Ubuntu/Debian
    ufw allow ${HTTP_PORT}/tcp
    ufw allow ${API_PORT}/tcp
    print_success "防火墙配置完成 (ufw)"
else
    print_warning "未检测到防火墙工具，请手动开放端口 ${HTTP_PORT} 和 ${API_PORT}"
fi

# =====================================================
# 部署完成
# =====================================================
echo ""
echo "====================================================="
echo -e "${GREEN}🎉 客服外呼系统部署完成！${NC}"
echo "====================================================="
echo ""
echo "📋 访问信息："
echo "   前端地址: http://$(curl -s icanhazip.com 2>/dev/null || echo 'your-server-ip'):${HTTP_PORT}"
echo "   API地址:  http://$(curl -s icanhazip.com 2>/dev/null || echo 'your-server-ip'):${API_PORT}"
echo ""
echo "📁 项目目录: ${PROJECT_DIR}"
echo "📊 数据目录: ${PROJECT_DIR}/data"
echo "📝 日志目录: ${PROJECT_DIR}/logs"
echo ""
echo "🔧 常用命令："
echo "   查看日志:   cd ${PROJECT_DIR} && docker-compose logs -f"
echo "   重启服务:   cd ${PROJECT_DIR} && docker-compose restart"
echo "   停止服务:   cd ${PROJECT_DIR} && docker-compose down"
echo "   更新代码:   cd ${PROJECT_DIR} && docker-compose up -d --build"
echo ""
echo "👤 默认管理员账号："
echo "   用户名: admin"
echo "   密码:   admin123"
echo ""
echo "⚠️  安全提示："
echo "   1. 请及时修改默认管理员密码"
echo "   2. 修改 .env 文件中的 JWT_SECRET"
echo "   3. 生产环境建议配置 HTTPS"
echo ""
if [ "${HTTP_PORT}" != "80" ]; then
    echo "💡 提示：您使用了非标准 HTTP 端口 ${HTTP_PORT}"
    echo "   访问时需要加上端口号，如: http://your-ip:${HTTP_PORT}"
    echo ""
fi
echo "====================================================="
