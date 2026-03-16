# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

# 安装编译工具（better-sqlite3 需要）
RUN apk add --no-cache python3 make g++ sqlite-dev

# 安装 pnpm
RUN npm install -g pnpm

# 复制依赖文件
COPY package.json pnpm-lock.yaml ./

# 安装所有依赖
RUN pnpm install --frozen-lockfile

# 复制源代码
COPY . .

# 编译 TypeScript
RUN pnpm run build

# 生产阶段
FROM node:20-alpine AS production

WORKDIR /app

# 安装运行时依赖和编译工具（better-sqlite3 需要重新编译）
RUN apk add --no-cache python3 make g++ sqlite sqlite-dev

# 安装 pnpm
RUN npm install -g pnpm

# 复制依赖文件
COPY package.json pnpm-lock.yaml tsconfig.json ./

# 安装所有依赖（包括 devDependencies，tsx 用于 db:seed）
RUN pnpm install --frozen-lockfile

# 复制源代码（seed 脚本需要）
COPY src ./src

# 从构建阶段复制编译后的代码
COPY --from=builder /app/dist ./dist

# 创建数据目录和上传目录（确保权限正确）
RUN mkdir -p data uploads && chmod 777 data uploads

# 创建默认环境变量文件
RUN echo "NODE_ENV=production\nPORT=5001\nDB_TYPE=sqlite\nSQLITE_PATH=/app/data/database.sqlite" > .env

# 重新编译原生模块
RUN pnpm rebuild better-sqlite3

# 创建启动脚本
RUN echo '#!/bin/sh' > /app/docker-entrypoint.sh && \
    echo 'set -e' >> /app/docker-entrypoint.sh && \
    echo '' >> /app/docker-entrypoint.sh && \
    echo '# 检查数据库是否存在，不存在则初始化' >> /app/docker-entrypoint.sh && \
    echo 'if [ ! -f /app/data/database.sqlite ]; then' >> /app/docker-entrypoint.sh && \
    echo '    echo "📦 首次启动，初始化数据库..."' >> /app/docker-entrypoint.sh && \
    echo '    pnpm db:seed' >> /app/docker-entrypoint.sh && \
    echo 'else' >> /app/docker-entrypoint.sh && \
    echo '    echo "✅ 数据库已存在，跳过初始化"' >> /app/docker-entrypoint.sh && \
    echo 'fi' >> /app/docker-entrypoint.sh && \
    echo '' >> /app/docker-entrypoint.sh && \
    echo 'echo "🚀 启动服务..."' >> /app/docker-entrypoint.sh && \
    echo 'exec pnpm start' >> /app/docker-entrypoint.sh && \
    chmod +x /app/docker-entrypoint.sh

# 暴露端口
EXPOSE 5001

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:5001/api/system/health || exit 1

# 使用入口脚本启动
CMD ["/app/docker-entrypoint.sh"]
