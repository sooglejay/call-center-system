import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';
import swaggerUi from 'swagger-ui-express';
import { initDatabase } from './config/database';
import { swaggerSpec } from './config/swagger';
import { errorHandler, notFoundHandler } from './middleware/errorHandler';

import authRoutes from './routes/auth.routes';
import userRoutes from './routes/user.routes';
import customerRoutes from './routes/customer.routes';
import callRoutes from './routes/call.routes';
import taskRoutes from './routes/task.routes';
import statsRoutes from './routes/stats.routes';
import twilioRoutes from './routes/twilio.routes';
import configRoutes from './routes/config.routes';
import reportRoutes from './routes/report.routes';
import systemRoutes from './routes/system.routes';
import communicationRoutes from './routes/communication.routes';
import dataImportRoutes from './routes/data-import.routes';

dotenv.config();

const app: express.Application = express();
const PORT = process.env.PORT || 8081;

// 中间件 - CORS 配置
app.use(cors({
  origin: true, // 允许所有来源
  credentials: true, // 允许携带凭证
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With']
}));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 请求日志中间件
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const duration = Date.now() - start;
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.path} ${res.statusCode} ${duration}ms`);
  });
  next();
});

// 静态文件
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// API 文档
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec, {
  customCss: '.swagger-ui .topbar { display: none }',
  customSiteTitle: '呼叫中心系统 API 文档'
}));

// Swagger JSON
app.get('/api-docs/swagger.json', (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.send(swaggerSpec);
});

// API 文档重定向
app.get('/docs', (req, res) => {
  res.redirect('/api-docs');
});

// 健康检查
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// API 路由
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/customers', customerRoutes);
app.use('/api/calls', callRoutes);
app.use('/api/tasks', taskRoutes);
app.use('/api/stats', statsRoutes);
app.use('/api/twilio', twilioRoutes);
app.use('/api/config', configRoutes);
app.use('/api/report', reportRoutes);
app.use('/api/system', systemRoutes);
app.use('/api/communication', communicationRoutes);
app.use('/api/data-import', dataImportRoutes);

// 404 处理
app.use(notFoundHandler);

// 统一错误处理
app.use(errorHandler);

// 启动服务器（仅在非测试环境）
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, async () => {
    console.log(`服务器运行在端口 ${PORT}`);
    console.log(`API地址: http://localhost:${PORT}/api`);
    console.log(`API文档: http://localhost:${PORT}/api-docs`);
    console.log(`健康检查: http://localhost:${PORT}/api/health`);
    
    // 启动时初始化数据库
    await initDatabase();
  });
}

export default app;
