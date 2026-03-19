#!/bin/bash

# =====================================================
# 客服外呼系统 - IP 地址直接部署脚本
# 自动检测可用端口，从 8320 开始
# =====================================================

set -e

echo ""
echo "=============================================="
echo "  客服外呼系统 - 自动端口部署"
echo "=============================================="
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

# 获取服务器 IP
SERVER_IP=$(curl -s ifconfig.me 2>/dev/null || curl -s ip.sb 2>/dev/null || echo "YOUR_SERVER_IP")
echo "检测到服务器 IP: $SERVER_IP"
echo ""

# ============================================
# 端口检测函数
# ============================================

# 检查端口是否被占用
check_port() {
    local port=$1
    if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
        return 1  # 端口被占用
    fi
    return 0  # 端口可用
}

# 检查端口范围是否可用
check_port_range() {
    local http_port=$1
    local api_port=$2
    
    if check_port $http_port && check_port $api_port; then
        return 0  # 两个端口都可用
    fi
    return 1  # 至少一个端口被占用
}

# 自动检测可用端口
find_available_ports() {
    print_info "正在检测可用端口..."
    
    # 从 8320 开始，每次递增 20
    local start_port=8320
    local step=20
    
    while true; do
        local http_port=$start_port
        local api_port=$((start_port + 1))
        
        if check_port_range $http_port $api_port; then
            echo ""
            print_success "找到可用端口: HTTP=$http_port, API=$api_port"
            HTTP_PORT=$http_port
            API_PORT=$api_port
            return 0
        else
            print_warning "端口 $http_port/$api_port 已被占用，尝试下一组..."
        fi
        
        # 递增到下一组
        start_port=$((start_port + step))
        
        # 防止无限循环，最大检测到 9000
        if [ $start_port -gt 9000 ]; then
            print_error "未找到可用端口（8320-9000 范围内）"
            echo "请手动指定端口："
            echo "  HTTP_PORT=8080 API_PORT=8081 $0"
            exit 1
        fi
    done
}

# 显示当前端口占用情况
show_port_status() {
    echo ""
    echo "端口检测详情:"
    echo "----------------------------------------"
    for port in 8320 8321 8340 8341 8360 8361; do
        if check_port $port; then
            echo -e "  端口 $port: ${GREEN}可用${NC}"
        else
            echo -e "  端口 $port: ${RED}已占用${NC}"
        fi
    done
    echo ""
}

# ============================================
# 主流程
# ============================================

# 如果环境变量中指定了端口，优先使用
if [ -n "$HTTP_PORT" ] && [ -n "$API_PORT" ]; then
    print_info "使用指定的端口: HTTP=$HTTP_PORT, API=$API_PORT"
else
    # 显示端口状态
    show_port_status
    
    # 自动检测端口
    find_available_ports
fi

# 确认端口
echo ""
echo "将使用的端口配置:"
echo "  前端端口: $HTTP_PORT"
echo "  后端端口: $API_PORT"
echo ""

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

# 创建正确的 .env 文件
cat > .env << EOF
# 前端端口
HTTP_PORT=$HTTP_PORT

# 后端 API 端口
API_PORT=$API_PORT

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
if curl -s http://localhost:$API_PORT/api/system/health > /dev/null 2>&1; then
    print_success "后端 API 正常"
    curl -s http://localhost:$API_PORT/api/system/health
else
    print_error "后端 API 无法访问"
    print_info "查看日志: docker logs callcenter-backend"
fi

# 测试前端
echo ""
print_info "测试前端服务..."
if curl -s -I http://localhost:$HTTP_PORT 2>&1 | grep -q "200 OK"; then
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
echo -e "  前端: ${GREEN}http://${SERVER_IP}:${HTTP_PORT}${NC}"
echo -e "  后端: ${GREEN}http://${SERVER_IP}:${API_PORT}${NC}"
echo ""
echo "测试命令:"
echo "  curl http://localhost:${HTTP_PORT}"
echo "  curl http://localhost:${API_PORT}/api/system/health"
echo ""
echo "防火墙设置 (如需外网访问):"
echo "  firewall-cmd --add-port=${HTTP_PORT}/tcp --permanent"
echo "  firewall-cmd --add-port=${API_PORT}/tcp --permanent"
echo "  firewall-cmd --reload"
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
