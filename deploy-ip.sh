#!/bin/bash

# =====================================================
# 客服外呼系统 - IP 地址直接部署脚本
# 适用于没有域名，直接使用 IP 访问的场景
# =====================================================

set -e

echo ""
echo "=============================================="
echo "  客服外呼系统 - IP 地址部署"
echo "=============================================="
echo ""

# 获取服务器 IP
SERVER_IP=$(curl -s ifconfig.me 2>/dev/null || curl -s ip.sb 2>/dev/null || echo "YOUR_SERVER_IP")
echo "检测到服务器 IP: $SERVER_IP"
echo ""

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
cd "$SCRIPT_DIR"

echo "步骤 1: 清理旧环境"
echo "----------------------------------------"

# 停止并删除旧容器
if [ -f "docker-compose.yml" ]; then
    print_info "停止旧容器..."
    docker-compose down --remove-orphans 2>/dev/null || true
fi

# 删除旧镜像
print_info "删除旧镜像..."
docker rmi call-center-system-frontend 2>/dev/null || true
docker rmi call-center-system-backend 2>/dev/null || true

# 清理构建缓存
print_info "清理构建缓存..."
docker builder prune -f 2>/dev/null || true

echo ""
echo "步骤 2: 创建环境变量文件"
echo "----------------------------------------"

# 创建正确的 .env 文件（直接 IP 访问）
cat > .env << 'EOF'
# 前端端口
HTTP_PORT=8080

# 后端 API 端口
API_PORT=8081

# 基础路径（直接 IP 访问设置为 /）
VITE_BASE_PATH=/
EOF

print_success ".env 文件已创建"
cat .env

echo ""
echo "步骤 3: 拉取最新代码"
echo "----------------------------------------"

if [ -d ".git" ]; then
    print_info "从 Git 拉取最新代码..."
    git fetch origin 2>/dev/null || true
    git reset --hard origin/main 2>/dev/null || git pull origin main 2>/dev/null || true
    print_success "代码已更新"
else
    print_warning "非 Git 目录，跳过代码更新"
fi

echo ""
echo "步骤 4: 构建并启动服务"
echo "----------------------------------------"

print_info "构建 Docker 镜像（首次可能需要几分钟）..."

# 构建镜像
docker-compose build --no-cache

print_info "启动服务..."
docker-compose up -d

echo ""
echo "步骤 5: 等待服务启动"
echo "----------------------------------------"

print_info "等待服务启动..."
sleep 10

# 检查服务状态
echo ""
docker-compose ps

echo ""
echo "步骤 6: 验证服务"
echo "----------------------------------------"

# 测试后端
echo ""
print_info "测试后端 API..."
if curl -s http://localhost:8081/api/system/health > /dev/null 2>&1; then
    print_success "后端 API 正常"
    curl -s http://localhost:8081/api/system/health
else
    print_error "后端 API 无法访问"
    print_info "查看日志: docker logs callcenter-backend"
fi

# 测试前端
echo ""
print_info "测试前端服务..."
if curl -s -I http://localhost:8080 2>&1 | grep -q "200 OK"; then
    print_success "前端服务正常"
else
    print_warning "前端服务可能仍在启动中"
fi

echo ""
echo "=============================================="
echo "           部署完成！"
echo "=============================================="
echo ""
echo "访问地址:"
echo ""
echo "  前端: http://${SERVER_IP}:8080"
echo "  后端: http://${SERVER_IP}:8081"
echo ""
echo "测试命令:"
echo "  curl http://localhost:8080"
echo "  curl http://localhost:8081/api/system/health"
echo ""
echo "查看日志:"
echo "  docker-compose logs -f"
echo "  docker logs callcenter-backend"
echo "  docker logs callcenter-frontend"
echo ""
echo "重启服务:"
echo "  docker-compose restart"
echo ""
echo "停止服务:"
echo "  docker-compose down"
echo ""
