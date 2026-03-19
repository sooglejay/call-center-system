#!/bin/bash

# Node.js 版本兼容性修复脚本
# 用于解决 Node.js 24 与 tsx 不兼容的问题

set -e

echo "======================================"
echo "  Node.js 版本兼容性修复"
echo "======================================"
echo ""

CURRENT_NODE=$(node -v 2>/dev/null || echo "未安装")
CURRENT_NODE_MAJOR=$(node -v 2>/dev/null | cut -d'v' -f2 | cut -d'.' -f1 || echo "0")

echo "当前 Node.js 版本: $CURRENT_NODE"
echo ""

if [ "$CURRENT_NODE_MAJOR" -gt 22 ]; then
    echo "⚠️  检测到 Node.js 版本过高 (v$CURRENT_NODE_MAJOR)"
    echo ""
    echo "问题原因:"
    echo "  Node.js $CURRENT_NODE_MAJOR 与 tsx 4.x 不完全兼容"
    echo ""
    echo "解决方案:"
    echo ""
    echo "方案1: 使用 nvm 切换到 Node.js 20（推荐）"
    echo "  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash"
    echo "  source ~/.bashrc"
    echo "  nvm install 20"
    echo "  nvm use 20"
    echo ""
    echo "方案2: 使用 Docker 部署（推荐生产环境）"
    echo "  docker-compose up -d"
    echo ""
    echo "方案3: 编译后运行（生产环境）"
    echo "  cd server && pnpm build && pnpm start"
    echo ""
    
    read -p "是否尝试方案3（编译后运行）？(y/n): " choice
    
    if [ "$choice" = "y" ] || [ "$choice" = "Y" ]; then
        echo ""
        echo "正在编译..."
        cd server
        pnpm install
        pnpm build
        
        echo ""
        echo "编译完成！使用以下命令启动服务:"
        echo "  cd server && pnpm start"
    fi
else
    echo "✅ Node.js 版本兼容"
    echo ""
    echo "可以正常使用:"
    echo "  pnpm dev  - 开发模式"
    echo "  pnpm start - 生产模式"
fi
