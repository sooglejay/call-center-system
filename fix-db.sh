#!/bin/bash

echo "🔧 重置数据库..."

cd "$(dirname "$0")/server" || exit 1

# 1. 清理数据库文件
echo "🧹 清理数据库文件..."
rm -f data/database.sqlite
rm -f data/database.sqlite-shm
rm -f data/database.sqlite-wal
echo "   已删除旧数据库文件"

# 2. 确保数据目录存在且有权限
echo "📁 检查数据目录权限..."
mkdir -p data
chmod 755 data

echo ""
echo "✅ 数据库已重置！"
echo "   重启服务后会自动创建新数据库"
echo "   启动服务: pnpm dev"
