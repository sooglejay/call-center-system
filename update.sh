#!/bin/bash
# =====================================================
# 客服外呼系统 - 快速更新脚本
# 用于日常开发迭代，快速拉取代码并重新部署
# =====================================================

set -e

# 颜色
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "=============================================="
echo "    客服外呼系统 - 快速更新"
echo "=============================================="
echo ""

# 选择更新类型
echo "请选择更新类型:"
echo "  1) 完整更新 (拉取代码 + 重新构建所有服务)"
echo "  2) 仅前端   (重新构建前端)"
echo "  3) 仅后端   (重新构建后端)"
echo "  4) 仅重启   (不重新构建，仅重启容器)"
echo "  5) 查看日志 (实时查看服务日志)"
echo "  6) 回滚     (回滚到上一版本)"
echo ""
read -p "请输入选项 [1-6]: " choice

case $choice in
    1)
        print_info "完整更新..."
        
        # 备份数据库
        if [ -f "./data/database.sqlite" ]; then
            cp ./data/database.sqlite ./data/database.sqlite.bak.$(date +%Y%m%d_%H%M%S)
            print_success "数据库已备份"
        fi
        
        # 拉取代码
        print_info "拉取最新代码..."
        git pull origin main || git pull origin master
        
        # 重新构建
        print_info "重新构建服务..."
        docker compose build --no-cache
        docker compose up -d
        
        print_success "更新完成!"
        ;;
        
    2)
        print_info "更新前端..."
        docker compose build frontend --no-cache
        docker compose up -d frontend
        print_success "前端更新完成!"
        ;;
        
    3)
        print_info "更新后端..."
        
        # 备份数据库
        if [ -f "./data/database.sqlite" ]; then
            cp ./data/database.sqlite ./data/database.sqlite.bak.$(date +%Y%m%d_%H%M%S)
            print_success "数据库已备份"
        fi
        
        docker compose build backend --no-cache
        docker compose up -d backend
        print_success "后端更新完成!"
        ;;
        
    4)
        print_info "重启服务..."
        docker compose restart
        print_success "服务已重启!"
        ;;
        
    5)
        print_info "查看日志 (Ctrl+C 退出)..."
        docker compose logs -f --tail=100
        ;;
        
    6)
        print_info "回滚操作..."
        
        # 查看最近提交
        echo ""
        echo "最近的提交:"
        git log --oneline -5
        echo ""
        
        read -p "请输入要回滚的 commit hash (或输入 'HEAD~1' 回滚到上一版本): " commit
        
        if [ -n "$commit" ]; then
            # 备份数据库
            if [ -f "./data/database.sqlite" ]; then
                cp ./data/database.sqlite ./data/database.sqlite.bak.$(date +%Y%m%d_%H%M%S)
                print_success "数据库已备份"
            fi
            
            git reset --hard "$commit"
            docker compose build --no-cache
            docker compose up -d
            print_success "已回滚到 $commit"
        fi
        ;;
        
    *)
        echo "无效选项"
        exit 1
        ;;
esac

# 显示服务状态
echo ""
print_info "服务状态:"
docker compose ps

echo ""
echo "=============================================="
echo "访问地址:"
echo "  前端: http://localhost:8080"
echo "  API:  http://localhost:8081/api/system/health"
echo "=============================================="
