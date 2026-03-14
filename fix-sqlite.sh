#!/bin/bash
# 修复 better-sqlite3 在 macOS 上的编译问题

echo "🔧 修复 better-sqlite3 编译问题..."

cd server

# 1. 清理旧的构建文件
rm -rf node_modules/better-sqlite3/build

# 2. 重新编译
pnpm rebuild better-sqlite3

# 如果上面的命令失败，尝试以下备选方案：
# 方案 A: 使用 node-gyp 直接编译
cd node_modules/better-sqlite3
node-gyp rebuild

# 方案 B: 如果使用 Apple Silicon Mac，可能需要指定架构
# cd node_modules/better-sqlite3
# CXXFLAGS="-arch arm64" node-gyp rebuild

echo "✅ 编译完成！"
echo ""
echo "现在可以运行: pnpm db:seed"
