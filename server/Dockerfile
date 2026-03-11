# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

# 安装 pnpm
RUN npm install -g pnpm

# 复制 package 文件
COPY package.json pnpm-lock.yaml* ./
COPY .env* ./

# 安装依赖
RUN pnpm install

# 复制源代码
COPY . .

# 构建（如果有构建脚本）
# RUN pnpm build

# 生产阶段
FROM node:20-alpine AS production

WORKDIR /app

# 安装 pnpm
RUN npm install -g pnpm

# 复制 package 文件
COPY package.json pnpm-lock.yaml* ./

# 安装生产依赖
RUN pnpm install --prod

# 从构建阶段复制代码
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/src ./src

# 暴露端口
EXPOSE 5001

# 启动命令
CMD ["node", "dist/app.js"]
