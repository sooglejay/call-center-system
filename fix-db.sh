#!/bin/bash

echo "🔧 修复数据库 I/O 错误..."

cd "$(dirname "$0")/server" || exit 1

# 1. 检查并删除可能损坏的数据库文件
echo "🧹 清理数据库文件..."
if [ -f "data/database.sqlite" ]; then
    rm -f data/database.sqlite
    echo "   已删除 database.sqlite"
fi
if [ -f "data/database.sqlite-shm" ]; then
    rm -f data/database.sqlite-shm
    echo "   已删除 database.sqlite-shm"
fi
if [ -f "data/database.sqlite-wal" ]; then
    rm -f data/database.sqlite-wal
    echo "   已删除 database.sqlite-wal"
fi

# 2. 确保数据目录存在且有权限
echo "📁 检查数据目录权限..."
mkdir -p data
chmod 755 data

# 3. 重新初始化数据库
echo "🌱 重新初始化数据库..."
if [ -f "node_modules/.bin/tsx" ]; then
    node_modules/.bin/tsx src/scripts/seed.ts --mini
else
    npx tsx src/scripts/seed.ts --mini
fi

echo ""
echo "✅ 数据库修复完成！"
echo "   启动服务: pnpm dev"
