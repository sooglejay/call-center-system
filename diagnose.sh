#!/bin/bash

# 客服外呼系统 - 问题诊断脚本

echo "=============================================="
echo "  客服外呼系统 - 问题诊断"
echo "=============================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 1. 检查 .env 文件
echo "1. 检查环境变量文件 (.env)"
echo "----------------------------------------"
if [ -f ".env" ]; then
    echo -e "${GREEN}✓ .env 文件存在${NC}"
    cat .env
else
    echo -e "${RED}✗ .env 文件不存在${NC}"
    echo "请创建 .env 文件："
    echo "  cat > .env << 'EOF'
HTTP_PORT=8080
API_PORT=8081
VITE_BASE_PATH=/
EOF"
fi
echo ""

# 2. 检查容器状态
echo "2. 检查 Docker 容器状态"
echo "----------------------------------------"
if command -v docker-compose &> /dev/null; then
    docker-compose ps
elif docker compose version &> /dev/null; then
    docker compose ps
else
    echo -e "${RED}✗ Docker Compose 未安装${NC}"
fi
echo ""

# 3. 检查后端 API
echo "3. 检查后端 API"
echo "----------------------------------------"
if curl -s http://localhost:8081/api/system/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 后端 API 正常${NC}"
    curl -s http://localhost:8081/api/system/health
else
    echo -e "${RED}✗ 后端 API 无法访问${NC}"
fi
echo ""

# 4. 检查前端
echo "4. 检查前端服务"
echo "----------------------------------------"
if curl -s -I http://localhost:8080 2>&1 | grep -q "200 OK"; then
    echo -e "${GREEN}✓ 前端服务正常${NC}"
else
    echo -e "${YELLOW}! 前端服务可能未正常启动${NC}"
fi
echo ""

# 5. 检查前端 Nginx 配置
echo "5. 检查前端 Nginx 配置"
echo "----------------------------------------"
if docker exec callcenter-frontend cat /etc/nginx/conf.d/default.conf 2>/dev/null | grep -q "proxy_pass"; then
    echo -e "${GREEN}✓ Nginx 代理配置正确${NC}"
    echo "API 代理地址："
    docker exec callcenter-frontend cat /etc/nginx/conf.d/default.conf 2>/dev/null | grep "proxy_pass" | head -3
else
    echo -e "${RED}✗ 无法检查 Nginx 配置${NC}"
fi
echo ""

# 6. 检查前端构建参数
echo "6. 检查前端构建时的环境变量"
echo "----------------------------------------"
echo "查看 docker-compose.yml 中的 VITE_BASE_PATH："
grep -A 2 "VITE_BASE_PATH" docker-compose.yml 2>/dev/null || echo "未找到配置"
echo ""

# 7. 测试 CORS
echo "7. 测试 CORS 配置"
echo "----------------------------------------"
CORS_TEST=$(curl -s -I -X OPTIONS http://localhost:8081/api/auth/login \
    -H "Origin: http://localhost:8080" \
    -H "Access-Control-Request-Method: POST" 2>&1)

if echo "$CORS_TEST" | grep -q "Access-Control-Allow-Origin"; then
    echo -e "${GREEN}✓ CORS 配置正常${NC}"
    echo "$CORS_TEST" | grep "Access-Control"
else
    echo -e "${YELLOW}! CORS 可能有问题${NC}"
fi
echo ""

# 8. 常见问题提示
echo "=============================================="
echo "  常见问题解决"
echo "=============================================="
echo ""
echo "问题 1: 前端请求地址错误 (http://api/xxx)"
echo "  原因: VITE_BASE_PATH 配置错误或前端未重新构建"
echo "  解决: "
echo "    1. 确保 .env 中 VITE_BASE_PATH=/"
echo "    2. 重新构建前端: docker-compose build --no-cache frontend"
echo "    3. 重启服务: docker-compose up -d"
echo ""
echo "问题 2: CORS 跨域错误"
echo "  原因: 后端 CORS 配置不完整"
echo "  解决: 已更新 server/src/app.ts 中的 CORS 配置"
echo ""
echo "问题 3: 容器 unhealthy"
echo "  原因: 服务启动失败"
echo "  解决: docker logs callcenter-backend 查看日志"
echo ""
