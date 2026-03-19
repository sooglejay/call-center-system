# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

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

# 安装 pnpm
RUN npm install -g pnpm

# 复制依赖文件
COPY package.json pnpm-lock.yaml ./

# 仅安装生产依赖（sql.js 是纯 JS，无需编译）
RUN pnpm install --frozen-lockfile --prod

# 从构建阶段复制编译后的代码
COPY --from=builder /app/dist ./dist

# 创建数据目录和上传目录
RUN mkdir -p data uploads && chmod 777 data uploads

# 创建默认环境变量文件
RUN echo "NODE_ENV=production\nPORT=8081\nDB_TYPE=sqlite\nSQLITE_PATH=/app/data/database.sqlite" > .env

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/api/system/health || exit 1

# 启动命令
CMD ["node", "dist/app.js"]
