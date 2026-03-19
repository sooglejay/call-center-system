import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';
import { initDatabase } from './config/database';

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

// 静态文件
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// 路由
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

// 错误处理
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('服务器错误:', err);
  res.status(500).json({ error: '服务器内部错误' });
});

// 启动服务器
app.listen(PORT, async () => {
  console.log(`服务器运行在端口 ${PORT}`);
  console.log(`API地址: http://localhost:${PORT}/api`);
  console.log(`健康检查: http://localhost:${PORT}/api/system/health`);
  
  // 启动时初始化数据库
  await initDatabase();
});

export default app;
