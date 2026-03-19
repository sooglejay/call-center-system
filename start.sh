#!/bin/bash

# 快速启动脚本 - 适用于生产环境
# 使用编译后的 JavaScript 运行，避免 tsx 兼容性问题

set -e

echo "======================================"
echo "  客服外呼系统 - 快速启动"
echo "======================================"
echo ""

# 检查是否已编译
if [ ! -f "server/dist/app.js" ]; then
    echo "首次运行，正在编译..."
    cd server
    pnpm install
    pnpm build
    cd ..
fi

# 创建必要的目录
mkdir -p data logs

# 停止旧进程
echo "停止旧进程..."
pkill -f "node.*server/dist/app.js" 2>/dev/null || true
pkill -f "vite.*client" 2>/dev/null || true

sleep 2

# 启动后端
echo "启动后端服务..."
cd server
nohup node dist/app.js > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
cd ..

# 等待后端启动
sleep 3

# 检查后端是否启动成功
if curl -s http://localhost:8081/api/system/health > /dev/null; then
    echo "✅ 后端服务启动成功 (PID: $BACKEND_PID)"
else
    echo "❌ 后端服务启动失败，请查看日志: logs/backend.log"
    exit 1
fi

# 启动前端（如果已构建）
if [ -d "client/dist" ]; then
    echo "启动前端服务..."
    cd client
    nohup pnpm preview --port 8080 > ../logs/frontend.log 2>&1 &
    FRONTEND_PID=$!
    cd ..
    
    sleep 2
    echo "✅ 前端服务启动成功 (PID: $FRONTEND_PID)"
fi

echo ""
echo "======================================"
echo "  服务已启动"
echo "======================================"
echo ""
echo "访问地址:"
echo "  前端: http://localhost:8080"
echo "  后端: http://localhost:8081"
echo ""
echo "停止服务:"
echo "  pkill -f 'node.*server/dist/app.js'"
echo "  pkill -f 'vite.*client'"
echo ""
echo "查看日志:"
echo "  tail -f logs/backend.log"
echo "  tail -f logs/frontend.log"
