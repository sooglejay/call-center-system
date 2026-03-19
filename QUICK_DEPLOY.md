# 快速部署指南 - IP 地址直接访问

## 一键部署

在服务器上执行以下命令：

```bash
# 1. 进入项目目录
cd /root/jw/call-center-system

# 2. 拉取最新代码
git pull

# 3. 执行一键部署脚本
chmod +x deploy-ip.sh
./deploy-ip.sh
```

部署完成后访问：`http://你的服务器IP:8080`

---

## 手动部署步骤

如果自动脚本有问题，可以手动执行：

### 步骤 1: 清理旧环境

```bash
cd /root/jw/call-center-system

# 停止并删除容器
docker-compose down

# 删除旧镜像
docker rmi call-center-system-frontend call-center-system-backend 2>/dev/null || true

# 清理缓存
docker builder prune -f
```

### 步骤 2: 创建环境变量

```bash
cat > .env << 'EOF'
HTTP_PORT=8080
API_PORT=8081
VITE_BASE_PATH=/
EOF
```

### 步骤 3: 拉取最新代码

```bash
git pull
```

### 步骤 4: 构建并启动

```bash
# 构建镜像
docker-compose build --no-cache

# 启动服务
docker-compose up -d

# 查看状态
docker-compose ps
```

### 步骤 5: 验证

```bash
# 测试后端
curl http://localhost:8081/api/system/health

# 测试前端
curl -I http://localhost:8080
```

---

## 访问地址

- **前端**: `http://你的服务器IP:8080`
- **后端**: `http://你的服务器IP:8081`

---

## 常见问题

### 1. 页面空白

**原因**: 前端构建问题或缓存

**解决**:
```bash
# 强制重新构建
docker-compose down
docker rmi call-center-system-frontend
docker-compose build --no-cache frontend
docker-compose up -d

# 清除浏览器缓存后重新访问
```

### 2. API 请求失败

**原因**: 前端 API 地址配置错误

**解决**:
```bash
# 检查 .env 文件
cat .env

# 确保包含以下内容
HTTP_PORT=8080
API_PORT=8081
VITE_BASE_PATH=/
```

### 3. 端口无法访问

**原因**: 防火墙未开放端口

**解决**:
```bash
# 开放端口 (CentOS)
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --add-port=8081/tcp --permanent
firewall-cmd --reload

# 或使用 iptables
iptables -I INPUT -p tcp --dport 8080 -j ACCEPT
iptables -I INPUT -p tcp --dport 8081 -j ACCEPT
```

### 4. 容器启动失败

**解决**:
```bash
# 查看日志
docker logs callcenter-backend
docker logs callcenter-frontend

# 重启服务
docker-compose restart
```

---

## 管理命令

```bash
# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 重新构建
docker-compose build --no-cache
docker-compose up -d
```

---

## 防火墙设置

### CentOS 7+

```bash
# 查看防火墙状态
firewall-cmd --state

# 开放端口
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --add-port=8081/tcp --permanent

# 重载配置
firewall-cmd --reload

# 查看已开放端口
firewall-cmd --list-ports
```

### Ubuntu

```bash
# 开放端口
ufw allow 8080/tcp
ufw allow 8081/tcp

# 查看状态
ufw status
```

---

## 验证部署成功

1. 后端 API 测试
```bash
curl http://localhost:8081/api/system/health
# 应返回: {"status":"ok",...}
```

2. 前端页面测试
```bash
curl -I http://localhost:8080
# 应返回: HTTP/1.1 200 OK
```

3. 浏览器访问
```
http://你的服务器IP:8080
```

看到登录页面即部署成功！
