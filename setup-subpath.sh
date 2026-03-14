#!/bin/bash
# =====================================================
# 客服外呼系统 - 子路径部署配置脚本
# 自动配置 Nginx 子路径反向代理
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

# 显示帮助
show_help() {
    echo "客服外呼系统 - 子路径部署配置脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -d, --domain <域名>        你的网站域名 (必需)"
    echo "  -s, --subpath <子路径>     子路径名称 (默认: callcenter)"
    echo "  -f, --frontend-port <端口> 前端内部端口 (默认: 8080)"
    echo "  -a, --api-port <端口>      API内部端口 (默认: 8081)"
    echo "  -c, --config-file <路径>   Nginx配置文件路径 (自动检测)"
    echo "  -h, --help                 显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -d example.com"
    echo "  $0 -d example.com -s crm -f 8080 -a 8081"
    echo "  $0 -d example.com -c /etc/nginx/sites-enabled/default"
    echo ""
}

# 解析参数
DOMAIN=""
SUBPATH="callcenter"
FRONTEND_PORT="8080"
API_PORT="8081"
CONFIG_FILE=""

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
        -f|--frontend-port)
            FRONTEND_PORT="$2"
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

# 验证必需参数
if [ -z "$DOMAIN" ]; then
    print_error "请指定域名！使用 -d 参数"
    show_help
    exit 1
fi

# 自动检测 Nginx 配置文件
if [ -z "$CONFIG_FILE" ]; then
    print_info "正在检测 Nginx 配置文件..."
    
    # 常见配置文件路径
    POSSIBLE_PATHS=(
        "/etc/nginx/conf.d/${DOMAIN}.conf"
        "/etc/nginx/sites-enabled/${DOMAIN}"
        "/etc/nginx/sites-enabled/default"
        "/etc/nginx/conf.d/default.conf"
        "/usr/local/nginx/conf/nginx.conf"
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
        print_error "未找到 Nginx 配置文件"
        echo ""
        echo "请使用 -c 参数手动指定配置文件路径："
        echo "  $0 -d $DOMAIN -c /path/to/your/nginx.conf"
        echo ""
        echo "常见路径："
        echo "  /etc/nginx/conf.d/your-domain.conf"
        echo "  /etc/nginx/sites-enabled/your-domain"
        echo "  /www/server/panel/vhost/nginx/your-domain.conf (宝塔面板)"
        exit 1
    fi
fi

# 验证配置文件存在
if [ ! -f "$CONFIG_FILE" ]; then
    print_error "配置文件不存在: $CONFIG_FILE"
    exit 1
fi

print_info "配置信息："
echo "  域名: $DOMAIN"
echo "  子路径: /$SUBPATH/"
echo "  前端端口: $FRONTEND_PORT"
echo "  API端口: $API_PORT"
echo "  Nginx配置: $CONFIG_FILE"
echo ""

# 检查配置文件中是否已存在该子路径
if grep -q "location /$SUBPATH/" "$CONFIG_FILE"; then
    print_warning "配置文件中已存在 /$SUBPATH/ 的配置"
    read -p "是否覆盖现有配置? (y/N): " confirm
    if [[ ! $confirm =~ ^[Yy]$ ]]; then
        print_info "操作已取消"
        exit 0
    fi
    # 删除现有配置
    sed -i "/# =\+\n# \[客服外呼系统配置/,/# =\+\n# \[客服外呼系统配置结束\]/d" "$CONFIG_FILE" 2>/dev/null || true
fi

# 备份原配置
BACKUP_FILE="${CONFIG_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
cp "$CONFIG_FILE" "$BACKUP_FILE"
print_success "已备份原配置: $BACKUP_FILE"

# 生成配置块
CONFIG_BLOCK="
    # ================================================
    # 【客服外呼系统配置 - 自动生成 $(date +%Y-%m-%d)】
    # ================================================
    
    # 前端页面 - 所有 /$SUBPATH/ 路径
    location /$SUBPATH/ {
        proxy_pass http://localhost:$FRONTEND_PORT/;
        proxy_http_version 1.1;
        
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \"upgrade\";
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # API 接口 - 所有 /$SUBPATH/api/ 路径
    location /$SUBPATH/api/ {
        proxy_pass http://localhost:$API_PORT/;
        proxy_http_version 1.1;
        
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 300s;
    }
    
    # 静态资源缓存优化
    location /$SUBPATH/assets/ {
        proxy_pass http://localhost:$FRONTEND_PORT/assets/;
        expires 1y;
        add_header Cache-Control \"public, immutable\";
    }
    
    # 【客服外呼系统配置结束】
"

# 将配置添加到 server 块中
# 使用 awk 在最后一个 } 之前插入配置（在 server 块内）
awk -v config="$CONFIG_BLOCK" '
    /^server \{/ { in_server = 1 }
    in_server && /^\}/ { 
        print config
        in_server = 0
    }
    { print }
' "$CONFIG_FILE" > "${CONFIG_FILE}.tmp" && mv "${CONFIG_FILE}.tmp" "$CONFIG_FILE"

print_success "Nginx 配置已更新"

# 检查配置语法
print_info "检查 Nginx 配置语法..."
if nginx -t; then
    print_success "配置语法正确"
else
    print_error "配置语法错误，正在恢复备份..."
    cp "$BACKUP_FILE" "$CONFIG_FILE"
    print_success "已恢复原配置"
    exit 1
fi

# 重载 Nginx
print_info "重载 Nginx..."
if systemctl reload nginx 2>/dev/null || service nginx reload 2>/dev/null || nginx -s reload 2>/dev/null; then
    print_success "Nginx 重载成功"
else
    print_warning "Nginx 重载命令执行失败，请手动重载："
    echo "  sudo nginx -s reload"
    echo "  或"
    echo "  sudo systemctl reload nginx"
fi

echo ""
echo "=================================================="
print_success "🎉 子路径配置完成！"
echo "=================================================="
echo ""
echo "📋 访问信息："
echo "   访问地址: http://$DOMAIN/$SUBPATH/"
echo "   API地址:  http://$DOMAIN/$SUBPATH/api/"
echo ""
echo "📁 配置详情："
echo "   Nginx配置: $CONFIG_FILE"
echo "   备份文件: $BACKUP_FILE"
echo ""
echo "🔧 后续操作："
echo "   1. 在浏览器访问: http://$DOMAIN/$SUBPATH/"
echo "   2. 默认账号: admin / admin123"
echo ""
echo "   如需配置 HTTPS，请在域名提供商处配置 SSL 证书"
echo "   然后修改 Nginx 配置添加 443 端口和 SSL 证书"
echo ""
echo "   查看日志: docker-compose logs -f"
echo "   停止服务: docker-compose down"
echo "=================================================="
