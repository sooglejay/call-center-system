#!/bin/bash

# =====================================================
# Nginx 配置安装脚本
# 安全地将客服外呼系统配置添加到 Nginx
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

echo ""
echo "=============================================="
echo "  客服外呼系统 - Nginx 配置安装"
echo "=============================================="
echo ""

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then
    print_error "请使用 root 权限运行此脚本"
    print_info "sudo $0"
    exit 1
fi

# 检测 Nginx 配置目录
NGINX_CONF_DIR=""
POSSIBLE_DIRS=(
    "/etc/nginx/conf.d"
    "/etc/nginx/sites-available"
    "/usr/local/nginx/conf/conf.d"
)

for dir in "${POSSIBLE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        NGINX_CONF_DIR="$dir"
        break
    fi
done

if [ -z "$NGINX_CONF_DIR" ]; then
    print_error "未找到 Nginx 配置目录"
    print_info "请手动复制配置文件"
    exit 1
fi

print_info "检测到 Nginx 配置目录: $NGINX_CONF_DIR"

# 配置选项
echo ""
echo "请选择配置方式:"
echo "  1) 子路径配置 (yourdomain.com/callcenter/)"
echo "  2) 独立域名配置 (call.yourdomain.com)"
echo "  3) 仅代理后端 API"
echo "  4) 查看配置文件内容"
echo "  5) 卸载配置"
echo ""
read -p "请输入选项 (1-5): " choice

case $choice in
    1|2|3)
        # 复制配置文件
        CONFIG_FILE="${NGINX_CONF_DIR}/callcenter.conf"
        
        if [ -f "$CONFIG_FILE" ]; then
            # 备份现有配置
            BACKUP_FILE="${CONFIG_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
            cp "$CONFIG_FILE" "$BACKUP_FILE"
            print_success "已备份现有配置: $BACKUP_FILE"
        fi
        
        # 复制配置文件
        cp "${SCRIPT_DIR}/nginx-callcenter.conf" "$CONFIG_FILE"
        print_success "配置文件已复制: $CONFIG_FILE"
        
        # 根据选择修改配置
        if [ "$choice" = "1" ]; then
            print_info "已启用子路径配置"
            print_info "访问地址: http://your-domain.com/callcenter/"
            # 子路径配置已是默认，无需修改
        elif [ "$choice" = "2" ]; then
            print_info "请编辑配置文件，取消独立域名配置的注释"
            print_info "vim $CONFIG_FILE"
            echo ""
            read -p "请输入独立域名 (如 call.example.com): " domain
            if [ -n "$domain" ]; then
                # 取消独立域名配置的注释
                sed -i "s/# server {/server {/" "$CONFIG_FILE"
                sed -i "s/#     listen 80;/    listen 80;/" "$CONFIG_FILE"
                sed -i "s/#     server_name call.yourdomain.com;/    server_name ${domain};/" "$CONFIG_FILE"
                # 注释掉子路径配置
                sed -i "s/^    location \/callcenter\//    # location \/callcenter\/ {/" "$CONFIG_FILE"
                print_success "已配置独立域名: $domain"
            fi
        elif [ "$choice" = "3" ]; then
            print_info "已启用仅 API 代理配置"
            print_info "API 地址: http://your-domain.com/api/"
        fi
        
        # 测试 Nginx 配置
        echo ""
        print_info "测试 Nginx 配置..."
        if nginx -t 2>/dev/null; then
            print_success "Nginx 配置测试通过"
            
            # 重载 Nginx
            echo ""
            read -p "是否立即重载 Nginx? (y/n): " reload
            if [ "$reload" = "y" ] || [ "$reload" = "Y" ]; then
                if command -v systemctl &> /dev/null; then
                    systemctl reload nginx
                elif command -v service &> /dev/null; then
                    service nginx reload
                else
                    nginx -s reload
                fi
                print_success "Nginx 已重载"
            fi
        else
            print_error "Nginx 配置测试失败，请检查配置文件"
            print_info "vim $CONFIG_FILE"
        fi
        ;;
        
    4)
        # 查看配置文件
        echo ""
        cat "${SCRIPT_DIR}/nginx-callcenter.conf"
        ;;
        
    5)
        # 卸载配置
        CONFIG_FILE="${NGINX_CONF_DIR}/callcenter.conf"
        
        if [ -f "$CONFIG_FILE" ]; then
            # 备份
            BACKUP_FILE="${CONFIG_FILE}.removed.$(date +%Y%m%d_%H%M%S)"
            mv "$CONFIG_FILE" "$BACKUP_FILE"
            print_success "配置已移除并备份: $BACKUP_FILE"
            
            # 重载 Nginx
            if nginx -t 2>/dev/null; then
                if command -v systemctl &> /dev/null; then
                    systemctl reload nginx
                elif command -v service &> /dev/null; then
                    service nginx reload
                else
                    nginx -s reload
                fi
                print_success "Nginx 已重载"
            fi
        else
            print_warning "未找到配置文件"
        fi
        ;;
        
    *)
        print_error "无效选项"
        exit 1
        ;;
esac

echo ""
echo "=============================================="
echo "           配置完成"
echo "=============================================="
echo ""
echo "配置文件位置: ${NGINX_CONF_DIR}/callcenter.conf"
echo ""
echo "常用命令:"
echo "  查看配置: cat ${NGINX_CONF_DIR}/callcenter.conf"
echo "  编辑配置: vim ${NGINX_CONF_DIR}/callcenter.conf"
echo "  测试配置: nginx -t"
echo "  重载配置: nginx -s reload"
echo ""
