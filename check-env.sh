#!/bin/bash
# =====================================================
# 客服外呼系统 - 环境检测脚本
# 在部署前检查环境，避免影响现有服务
# =====================================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[✓]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[!]${NC} $1"; }
print_error() { echo -e "${RED}[✗]${NC} $1"; }

HTTP_PORT="${1:-80}"
API_PORT="${2:-5001}"

echo "=================================================="
echo "       客服外呼系统 - 部署环境检测"
echo "=================================================="
echo ""

# 检测 1: 端口占用情况
echo "📡 检测端口占用情况..."
echo "--------------------------------------------------"

check_port() {
    local port=$1
    local name=$2
    local status="✓ 可用"
    local color="$GREEN"
    
    if command -v ss &> /dev/null; then
        if ss -tuln | grep -q ":$port "; then
            # 获取占用端口的进程信息
            local pid_info=$(ss -tulnp 2>/dev/null | grep ":$port " | head -1)
            status="✗ 已被占用"
            color="$RED"
            echo -e "${color}端口 $port ($name): $status${NC}"
            echo "    占用信息: $pid_info"
            return 1
        fi
    elif command -v netstat &> /dev/null; then
        if netstat -tuln 2>/dev/null | grep -q ":$port "; then
            status="✗ 已被占用"
            color="$RED"
            echo -e "${color}端口 $port ($name): $status${NC}"
            return 1
        fi
    fi
    
    echo -e "${color}端口 $port ($name): $status${NC}"
    return 0
}

PORT_OK=true
check_port $HTTP_PORT "HTTP 前端" || PORT_OK=false
check_port $API_PORT "API 后端" || PORT_OK=false

if [ "$PORT_OK" = false ]; then
    echo ""
    print_error "端口冲突检测失败！"
    echo ""
    echo "建议解决方案："
    echo "  1. 使用其他端口部署:"
    echo "     ./deploy-call-center.sh -p 8080 -a 8081"
    echo ""
    echo "  2. 停止占用端口的服务后再部署"
    echo ""
    exit 1
else
    print_success "端口检测通过"
fi

echo ""

# 检测 2: Docker 环境
echo "🐳 检测 Docker 环境..."
echo "--------------------------------------------------"

if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    print_success "Docker 已安装: $DOCKER_VERSION"
    
    # 检查 Docker 运行状态
    if docker info &> /dev/null; then
        print_success "Docker 服务运行正常"
    else
        print_error "Docker 服务未运行"
        echo "    请执行: sudo systemctl start docker"
    fi
else
    print_warning "Docker 未安装"
    echo "    部署脚本会自动安装 Docker"
fi

if command -v docker-compose &> /dev/null; then
    COMPOSE_VERSION=$(docker-compose --version)
    print_success "Docker Compose 已安装: $COMPOSE_VERSION"
else
    print_warning "Docker Compose 未安装"
    echo "    部署脚本会自动安装 Docker Compose"
fi

echo ""

# 检测 3: 系统资源
echo "💾 检测系统资源..."
echo "--------------------------------------------------"

# CPU
CPU_CORES=$(nproc)
print_success "CPU 核心数: $CPU_CORES"

# 内存
if command -v free &> /dev/null; then
    MEM_TOTAL=$(free -h | awk '/^Mem:/ {print $2}')
    MEM_AVAILABLE=$(free -h | awk '/^Mem:/ {print $7}')
    print_success "内存总量: $MEM_TOTAL"
    print_info "可用内存: $MEM_AVAILABLE"
    
    # 检查内存是否足够（建议至少 1GB）
    MEM_MB=$(free -m | awk '/^Mem:/ {print $2}')
    if [ "$MEM_MB" -lt 1024 ]; then
        print_warning "内存较少（${MEM_MB}MB），建议至少 1GB"
    fi
fi

# 磁盘空间
DISK_USAGE=$(df -h . | awk 'NR==2 {print $5}' | tr -d '%')
DISK_AVAIL=$(df -h . | awk 'NR==2 {print $4}')
print_info "当前目录磁盘使用率: ${DISK_USAGE}%"
print_info "可用磁盘空间: $DISK_AVAIL"

if [ "$DISK_USAGE" -gt 80 ]; then
    print_warning "磁盘使用率较高，请注意空间"
fi

echo ""

# 检测 4: 现有 Web 服务
echo "🌐 检测现有 Web 服务..."
echo "--------------------------------------------------"

# 检测常见 Web 服务
SERVICES="nginx apache2 httpd tomcat"
FOUND_SERVICE=false

for service in $SERVICES; do
    if command -v $service &> /dev/null || systemctl is-active --quiet $service 2>/dev/null; then
        print_info "检测到已安装的 Web 服务: $service"
        FOUND_SERVICE=true
        
        # 检查是否占用 80/443 端口
        if [ "$service" = "nginx" ] || [ "$service" = "apache2" ] || [ "$service" = "httpd" ]; then
            if ss -tuln | grep -q ":80 "; then
                print_warning "$service 正在监听 80 端口"
                if [ "$HTTP_PORT" = "80" ]; then
                    echo ""
                    print_error "⚠️  警告: 您选择的 HTTP 端口 80 已被 $service 占用！"
                    echo ""
                    echo "建议:"
                    echo "  1. 使用其他端口部署:"
                    echo "     ./deploy-call-center.sh -p 8080 -a 8081"
                    echo ""
                    echo "  2. 配置 $service 反向代理到 Docker 容器"
                    echo "     （需要手动配置）"
                    echo ""
                fi
            fi
        fi
    fi
done

if [ "$FOUND_SERVICE" = false ]; then
    print_info "未检测到常见的 Web 服务"
fi

echo ""

# 检测 5: 防火墙状态
echo "🔥 检测防火墙状态..."
echo "--------------------------------------------------"

if command -v firewall-cmd &> /dev/null; then
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        print_info "Firewalld 正在运行"
        echo "    部署脚本将自动开放端口 $HTTP_PORT 和 $API_PORT"
    else
        print_warning "Firewalld 已安装但未运行"
    fi
elif command -v ufw &> /dev/null; then
    if ufw status | grep -q "Status: active"; then
        print_info "UFW 防火墙正在运行"
        echo "    部署脚本将自动开放端口 $HTTP_PORT 和 $API_PORT"
    else
        print_warning "UFW 已安装但未启用"
    fi
else
    print_info "未检测到防火墙"
fi

echo ""

# 最终报告
echo "=================================================="
echo "              环境检测报告"
echo "=================================================="
echo ""

if [ "$PORT_OK" = true ]; then
    print_success "✅ 环境检测通过，可以安全部署！"
    echo ""
    echo "建议的部署命令："
    echo "  sudo ./deploy-call-center.sh -p $HTTP_PORT -a $API_PORT"
    echo ""
    echo "或快速部署："
    echo "  sudo ./quick-deploy.sh -p $HTTP_PORT -a $API_PORT"
    echo ""
else
    print_error "❌ 环境检测未通过，请解决上述问题后再部署"
    echo ""
    echo "建议："
    echo "  使用其他端口部署:"
    echo "    sudo ./deploy-call-center.sh -p 8080 -a 8081"
    echo ""
fi

echo "=================================================="
