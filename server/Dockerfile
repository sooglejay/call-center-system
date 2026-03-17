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

# 安装运行时依赖和编译工具
RUN apk add --no-cache python3 make g++ sqlite sqlite-dev

# 复制 package.json
COPY package.json ./

# 使用 npm 安装生产依赖（会自动编译原生模块）
RUN npm install --omit=dev

# 从构建阶段复制编译后的代码
COPY --from=builder /app/dist ./dist

# 创建数据目录和上传目录
RUN mkdir -p data uploads && chmod 777 data uploads

# 创建默认环境变量文件
RUN echo "NODE_ENV=production\nPORT=5001\nDB_TYPE=sqlite\nSQLITE_PATH=/app/data/database.sqlite" > .env

# 暴露端口
EXPOSE 5001

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:5001/api/system/health || exit 1

# 启动命令
CMD ["node", "dist/app.js"]
