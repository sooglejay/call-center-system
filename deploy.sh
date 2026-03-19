#!/bin/bash

# 客服外呼系统部署脚本
# 支持两种部署方式：Docker 和 非Docker

set -e

echo "======================================"
echo "  客服外呼系统部署脚本"
echo "======================================"
echo ""

# 检查操作系统
OS=$(uname -s)
echo "检测到操作系统: $OS"

# 检查 Node.js 版本
check_node_version() {
    if command -v node &> /dev/null; then
        NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
        echo "当前 Node.js 版本: $(node -v)"
        
        if [ "$NODE_VERSION" -gt 22 ]; then
            echo ""
            echo "⚠️  警告: Node.js 版本过高 (v$NODE_VERSION)"
            echo "推荐使用 Node.js 18-22 LTS 版本"
            echo ""
            echo "请使用以下方式之一解决："
            echo "1. 使用 nvm 安装 Node.js 20: nvm install 20 && nvm use 20"
            echo "2. 使用 Docker 部署（推荐）"
            exit 1
        fi
    else
        echo "❌ 未检测到 Node.js，请先安装 Node.js 18-22"
        exit 1
    fi
}

# Docker 部署
deploy_docker() {
    echo ""
    echo "🐳 使用 Docker 部署..."
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        echo "❌ 未安装 Docker，请先安装 Docker"
        echo "安装指南: https://docs.docker.com/engine/install/"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo "❌ 未安装 docker-compose，请先安装"
        exit 1
    fi
    
    # 停止旧容器
    echo "停止旧容器..."
    docker-compose down 2>/dev/null || docker compose down 2>/dev/null || true
    
    # 构建并启动
    echo "构建并启动容器..."
    if docker-compose version &> /dev/null; then
        docker-compose up -d --build
    else
        docker compose up -d --build
    fi
    
    # 等待服务启动
    echo "等待服务启动..."
    sleep 5
    
    # 检查服务状态
    echo ""
    echo "服务状态:"
    docker-compose ps 2>/dev/null || docker compose ps 2>/dev/null
    
    echo ""
    echo "✅ Docker 部署完成！"
    echo ""
    echo "访问地址:"
    echo "  前端: http://localhost:8080"
    echo "  后端: http://localhost:8081"
    echo ""
    echo "日志查看:"
    echo "  docker-compose logs -f backend"
    echo "  docker-compose logs -f frontend"
}

# 非Docker 部署
deploy_native() {
    echo ""
    echo "📦 使用原生部署..."
    
    # 检查 Node.js 版本
    check_node_version
    
    # 检查 pnpm
    if ! command -v pnpm &> /dev/null; then
        echo "安装 pnpm..."
        npm install -g pnpm
    fi
    
    # 安装后端依赖
    echo ""
    echo "安装后端依赖..."
    cd server
    rm -rf node_modules pnpm-lock.yaml
    pnpm install
    cd ..
    
    # 安装前端依赖
    echo ""
    echo "安装前端依赖..."
    cd client
    rm -rf node_modules pnpm-lock.yaml
    pnpm install
    cd ..
    
    # 构建前端
    echo ""
    echo "构建前端..."
    cd client
    pnpm build
    cd ..
    
    # 创建数据目录
    mkdir -p data logs
    
    echo ""
    echo "✅ 依赖安装完成！"
    echo ""
    echo "启动服务:"
    echo "  后端: cd server && pnpm start"
    echo "  前端: cd client && pnpm preview --port 8080"
    echo ""
    echo "开发模式:"
    echo "  后端: cd server && pnpm dev"
    echo "  前端: cd client && pnpm dev"
}

# 开发模式
deploy_dev() {
    echo ""
    echo "🔧 开发模式部署..."
    
    # 检查 Node.js 版本
    check_node_version
    
    # 检查 pnpm
    if ! command -v pnpm &> /dev/null; then
        echo "安装 pnpm..."
        npm install -g pnpm
    fi
    
    # 安装后端依赖
    echo ""
    echo "安装后端依赖..."
    cd server
    pnpm install
    cd ..
    
    # 安装前端依赖
    echo ""
    echo "安装前端依赖..."
    cd client
    pnpm install
    cd ..
    
    # 创建数据目录
    mkdir -p data logs
    
    echo ""
    echo "✅ 开发环境准备完成！"
    echo ""
    echo "启动开发服务器:"
    echo "  后端: cd server && pnpm dev"
    echo "  前端: cd client && pnpm dev"
}

# 主菜单
echo "请选择部署方式:"
echo "  1) Docker 部署（推荐）"
echo "  2) 原生部署（生产环境）"
echo "  3) 开发模式"
echo "  4) 检查 Node.js 版本"
echo ""
read -p "请输入选项 (1-4): " choice

case $choice in
    1)
        deploy_docker
        ;;
    2)
        deploy_native
        ;;
    3)
        deploy_dev
        ;;
    4)
        check_node_version
        echo "✅ Node.js 版本检查通过"
        ;;
    *)
        echo "无效选项"
        exit 1
        ;;
esac
