#!/bin/bash
# =====================================================
# 客服外呼系统 - 一站式部署脚本（含子路径配置）
# 一键完成：Docker 部署 + Nginx 子路径配置
#
# 用法:
#   ./deploy-with-subpath.sh -d <域名> [选项]
#
# 选项:
#   -d, --domain <域名>        网站域名 (必需)
#   -s, --subpath <子路径>     子路径名称 (默认: callcenter)
#   -p, --http-port <端口>     前端内部端口 (默认: 8080)
#   -a, --api-port <端口>      API内部端口 (默认: 8081)
#   -c, --config-file <路径>   Nginx配置文件路径 (自动检测)
#   --skip-nginx               跳过 Nginx 配置
#   -h, --help                 显示帮助信息
#
# 示例:
#   ./deploy-with-subpath.sh -d example.com
#   ./deploy-with-subpath.sh -d example.com -s crm
#   ./deploy-with-subpath.sh -d example.com -p 8080 -a 8081
# =====================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_step() { echo -e "${PURPLE}[STEP]${NC} $1"; }

# 显示帮助
show_help() {
    echo "客服外呼系统 - 一站式部署脚本"
    echo ""
    echo "用法: $0 -d <域名> [选项]"
    echo ""
    echo "选项:"
    echo "  -d, --domain <域名>        网站域名 (必需)"
    echo "  -s, --subpath <子路径>     子路径名称 (默认: callcenter)"
    echo "  -p, --http-port <端口>     前端内部端口 (默认: 8080)"
    echo "  -a, --api-port <端口>      API内部端口 (默认: 8081)"
    echo "  -c, --config-file <路径>   Nginx配置文件路径 (自动检测)"
    echo "  --skip-nginx               跳过 Nginx 配置"
    echo "  -h, --help                 显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -d example.com                           # 完整部署"
    echo "  $0 -d example.com -s crm                    # 自定义子路径"
    echo "  $0 -d example.com --skip-nginx              # 仅 Docker 部署"
    echo ""
}

# 参数解析
DOMAIN=""
SUBPATH="callcenter"
HTTP_PORT="8080"
API_PORT="8081"
CONFIG_FILE=""
SKIP_NGINX=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--domain)
            DOMAIN="$2"
            shift 2
            ;;
        -s|--subpath)
            SUBPATH="$2"
            shift 2
            ;;
        -p|--http-port)
            HTTP_PORT="$2"
            shift 2
            ;;
        -a|--api-port)
            API_PORT="$2"
            shift 2
            ;;
        -c|--config-file)
            CONFIG_FILE="$2"
            shift 2
            ;;
        --skip-nginx)
            SKIP_NGINX=true
            shift
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

# 验证域名（如果需要配置 Nginx）
if [ "$SKIP_NGINX" = false ] && [ -z "$DOMAIN" ]; then
    print_error "请指定域名！使用 -d 参数"
    show_help
    exit 1
fi

# 验证端口号
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

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-/opt/call-center-system}"

echo ""
echo "=============================================="
echo "    客服外呼系统 - 一站式部署"
echo "=============================================="
echo ""
print_info "配置信息："
echo "  项目目录: ${PROJECT_DIR}"
echo "  前端端口: ${HTTP_PORT}"
echo "  API端口: ${API_PORT}"
if [ "$SKIP_NGINX" = false ]; then
    echo "  域名: ${DOMAIN}"
    echo "  子路径: /${SUBPATH}/"
fi
echo ""

# ============================================
# 第一步：Docker 部署
# ============================================
print_step "步骤 1/3: Docker 容器部署"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    print_error "Docker 未安装！请先安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    print_error "Docker Compose 未安装！"
    exit 1
fi

# 判断使用 docker-compose 还是 docker compose
if command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
else
    COMPOSE_CMD="docker compose"
fi

# 停止并清理旧容器
print_info "停止旧容器..."
if [ -f "${PROJECT_DIR}/docker-compose.yml" ]; then
    cd "${PROJECT_DIR}"
    $COMPOSE_CMD down --remove-orphans 2>/dev/null || true
else
    # 尝试停止可能存在的容器
    docker stop callcenter-backend callcenter-frontend 2>/dev/null || true
    docker rm callcenter-backend callcenter-frontend 2>/dev/null || true
fi

# 清理 Docker 构建缓存
print_info "清理构建缓存..."
docker builder prune -f 2>/dev/null || true

# 拉取最新代码（如果在 git 仓库中）
if [ -d "${SCRIPT_DIR}/.git" ]; then
    print_info "拉取最新代码..."
    cd "${SCRIPT_DIR}"
    git fetch origin 2>/dev/null || true
    git reset --hard origin/main 2>/dev/null || git pull origin main 2>/dev/null || true
    print_success "代码已更新到最新版本"
fi

# 创建项目目录
mkdir -p "${PROJECT_DIR}"/{client,server,data,logs}

# 检查并复制项目代码
if [ -f "${SCRIPT_DIR}/server/package.json" ] && [ -f "${SCRIPT_DIR}/client/package.json" ]; then
    print_info "清理旧代码..."
    rm -rf "${PROJECT_DIR}/server/"* "${PROJECT_DIR}/client/"* 2>/dev/null || true
    rm -rf "${PROJECT_DIR}/server/".[!.]* "${PROJECT_DIR}/client/".[!.]* 2>/dev/null || true
    
    print_info "复制项目代码..."
    cp -r "${SCRIPT_DIR}"/server/* "${PROJECT_DIR}"/server/ 2>/dev/null || true
    cp -r "${SCRIPT_DIR}"/server/.[!.]* "${PROJECT_DIR}"/server/ 2>/dev/null || true
    cp -r "${SCRIPT_DIR}"/client/* "${PROJECT_DIR}"/client/ 2>/dev/null || true
    cp -r "${SCRIPT_DIR}"/client/.[!.]* "${PROJECT_DIR}"/client/ 2>/dev/null || true
    print_success "代码复制完成"
else
    print_error "未检测到项目代码！"
    print_info "请确保在包含 server/ 和 client/ 目录的项目根目录中运行此脚本"
    exit 1
fi

cd "${PROJECT_DIR}"

# 创建 Dockerfile - 后端（使用 sql.js，无需编译原生模块）
cat > server/Dockerfile << 'DOCKERFILE'
# 构建阶段
FROM node:20-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

# 生产阶段
FROM node:20-alpine AS production
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile --prod
COPY --from=builder /app/dist ./dist
RUN mkdir -p data uploads && chmod 777 data uploads
RUN echo "NODE_ENV=production\nPORT=8081\nDB_TYPE=sqlite\nSQLITE_PATH=/app/data/database.sqlite" > .env
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/api/system/health || exit 1
CMD ["node", "dist/app.js"]
DOCKERFILE

# 创建 Dockerfile - 前端（支持子路径）
cat > client/Dockerfile << 'DOCKERFILE'
FROM node:20-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml* ./
RUN pnpm install
COPY . .
ARG VITE_BASE_PATH=/
ENV VITE_BASE_PATH=$VITE_BASE_PATH
RUN pnpm run build
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
DOCKERFILE

# 创建前端 Nginx 配置（容器内部使用）
cat > client/nginx.conf << 'NGINX'
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8081/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
    }

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
NGINX

# 创建 docker-compose.yml（支持子路径构建）
cat > docker-compose.yml << COMPOSE
services:
  backend:
    build: ./server
    container_name: callcenter-backend
    restart: always
    ports:
      - "${API_PORT}:8081"
    volumes:
      - ./data:/app/data
      - ./logs:/app/logs
    environment:
      - NODE_ENV=production
      - PORT=8081
      - DB_TYPE=sqlite
      - SQLITE_PATH=/app/data/database.sqlite
      - JWT_SECRET=call-center-secret-key-2024
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8081/api/system/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 15s

  frontend:
    build:
      context: ./client
      args:
        VITE_BASE_PATH: /${SUBPATH}
    container_name: callcenter-frontend
    restart: always
    ports:
      - "${HTTP_PORT}:80"
    depends_on:
      - backend
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:80"]
      interval: 30s
      timeout: 10s
      retries: 3
COMPOSE

# 创建环境配置
cat > .env << ENV
HTTP_PORT=${HTTP_PORT}
API_PORT=${API_PORT}
SUBPATH=${SUBPATH}
ENV

print_info "构建 Docker 镜像（首次可能需要几分钟）..."

# 强制重新构建（不使用缓存）
$COMPOSE_CMD build --no-cache
$COMPOSE_CMD up -d

print_success "Docker 容器已启动"

# 等待服务就绪
print_info "等待服务启动..."
sleep 5

# 检查服务状态
if curl -s "http://localhost:${HTTP_PORT}" > /dev/null; then
    print_success "前端服务运行正常 (端口 ${HTTP_PORT})"
else
    print_warning "前端服务可能仍在启动中..."
fi

if curl -s "http://localhost:${API_PORT}/health" > /dev/null 2>&1; then
    print_success "API 服务运行正常 (端口 ${API_PORT})"
else
    print_warning "API 服务可能仍在启动中..."
fi

# ============================================
# 第二步：Nginx 子路径配置（可选）
# ============================================
if [ "$SKIP_NGINX" = false ]; then
    print_step "步骤 2/3: Nginx 子路径配置"
    
    # 自动检测 Nginx 配置文件
    if [ -z "$CONFIG_FILE" ]; then
        print_info "正在检测 Nginx 配置文件..."
        
        POSSIBLE_PATHS=(
            "/etc/nginx/conf.d/${DOMAIN}.conf"
            "/etc/nginx/sites-enabled/${DOMAIN}"
            "/etc/nginx/sites-enabled/default"
            "/etc/nginx/conf.d/default.conf"
            "/www/server/panel/vhost/nginx/${DOMAIN}.conf"
        )
        
        for path in "${POSSIBLE_PATHS[@]}"; do
            if [ -f "$path" ]; then
                CONFIG_FILE="$path"
                print_success "找到配置文件: $path"
                break
            fi
        done
        
        if [ -z "$CONFIG_FILE" ]; then
            print_warning "未找到 Nginx 配置文件，跳过子路径配置"
            print_info "你可以稍后手动运行: ./setup-subpath.sh -d $DOMAIN"
            SKIP_NGINX=true
        fi
    fi
fi

if [ "$SKIP_NGINX" = false ] && [ -n "$CONFIG_FILE" ]; then
    # 检查是否已存在配置
    if grep -q "location /$SUBPATH/" "$CONFIG_FILE"; then
        print_warning "配置文件中已存在 /$SUBPATH/ 的配置，跳过..."
    else
        # 备份原配置
        BACKUP_FILE="${CONFIG_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
        cp "$CONFIG_FILE" "$BACKUP_FILE"
        print_success "已备份原配置: $BACKUP_FILE"
        
        # 生成配置块并写入临时文件
        CONFIG_TMP_FILE=$(mktemp)
        cat > "$CONFIG_TMP_FILE" << CONFIG_BLOCK

    # ================================================
    # 【客服外呼系统配置 - 自动生成 $(date +%Y-%m-%d)】
    # ================================================
    
    # 前端页面
    location /$SUBPATH/ {
        proxy_pass http://localhost:$HTTP_PORT/;
        proxy_http_version 1.1;
        
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # API 接口
    location /$SUBPATH/api/ {
        proxy_pass http://localhost:$HTTP_PORT/api/;
        proxy_http_version 1.1;
        
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
    }
    
    # 上传文件代理 - APK 下载等资源由后端提供
    location /$SUBPATH/uploads/ {
        proxy_pass http://localhost:$API_PORT/uploads/;
        proxy_http_version 1.1;
        
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
    }

    # 静态资源缓存
    location /$SUBPATH/assets/ {
        proxy_pass http://localhost:$HTTP_PORT/assets/;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # 【客服外呼系统配置结束】
CONFIG_BLOCK
        
        # 将配置插入到 server 块内（在最后一个 } 之前）
        # 使用 awk 插入配置（更可靠）
        awk -v config_file="$CONFIG_TMP_FILE" '
            BEGIN { 
                config = ""
                while ((getline line < config_file) > 0) {
                    config = config line "\n"
                }
                close(config_file)
            }
            /^}/ && !inserted { 
                print config
                print "}"
                inserted = 1
                next 
            }
            { print }
        ' "$CONFIG_FILE" > "${CONFIG_FILE}.new"
        
        mv "${CONFIG_FILE}.new" "$CONFIG_FILE"
        rm -f "$CONFIG_TMP_FILE"
        
        print_success "Nginx 配置已更新"
        
        # 测试并重载 Nginx
        if nginx -t 2>/dev/null; then
            if command -v systemctl &> /dev/null; then
                systemctl reload nginx 2>/dev/null || systemctl restart nginx 2>/dev/null || true
            elif command -v service &> /dev/null; then
                service nginx reload 2>/dev/null || service nginx restart 2>/dev/null || true
            else
                nginx -s reload 2>/dev/null || true
            fi
            print_success "Nginx 已重载配置"
        else
            print_warning "Nginx 配置测试失败，请手动检查"
            print_info "运行: nginx -t && nginx -s reload"
        fi
    fi
fi

# ============================================
# 第三步：验证部署
# ============================================
print_step "步骤 3/3: 验证部署"

echo ""
print_info "部署完成！"
echo ""
echo "=============================================="
echo "           访问地址"
echo "=============================================="
echo ""

if [ "$SKIP_NGINX" = false ]; then
    echo "  子路径访问: http://${DOMAIN}/${SUBPATH}/"
    echo "  API 接口:   http://${DOMAIN}/${SUBPATH}/api/"
fi
echo ""
echo "  直接访问（服务器本地）:"
echo "    前端: http://localhost:${HTTP_PORT}"
echo "    API:  http://localhost:${API_PORT}"
echo ""
echo "=============================================="
echo "           管理命令"
echo "=============================================="
echo ""
echo "  查看日志:"
echo "    cd ${PROJECT_DIR}"
echo "    $COMPOSE_CMD logs -f"
echo ""
echo "  重启服务:"
echo "    $COMPOSE_CMD restart"
echo ""
echo "  停止服务:"
echo "    $COMPOSE_CMD down"
echo ""
echo "=============================================="
