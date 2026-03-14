#!/bin/bash
# =====================================================
# 客服外呼系统 - 更新脚本
# 用于更新代码后重新部署
# =====================================================

set -e

PROJECT_DIR="/opt/call-center-system"

cd ${PROJECT_DIR}

echo "🔄 正在更新客服外呼系统..."

# 备份数据
echo "📦 备份数据..."
cp -r data data.backup.$(date +%Y%m%d_%H%M%S) 2>/dev/null || true

# 拉取最新代码（如果是 git 仓库）
if [ -d ".git" ]; then
    echo "📥 拉取最新代码..."
    git pull origin main 2>/dev/null || git pull origin master 2>/dev/null || true
fi

# 重新构建并启动
echo "🔨 重新构建镜像..."
docker-compose down
docker-compose build --no-cache
docker-compose up -d

# 等待启动
sleep 10

# 检查状态
if docker-compose ps | grep -q "Up"; then
    echo "✅ 更新完成！"
    docker-compose ps
else
    echo "❌ 更新失败，请检查日志："
    docker-compose logs -f
fi
