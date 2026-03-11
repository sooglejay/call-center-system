import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';

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

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5001;

// 中间件
app.use(cors());
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

// 错误处理
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('服务器错误:', err);
  res.status(500).json({ error: '服务器内部错误' });
});

app.listen(PORT, () => {
  console.log(`服务器运行在端口 ${PORT}`);
  console.log(`API地址: http://localhost:${PORT}/api`);
  console.log(`健康检查: http://localhost:${PORT}/api/system/health`);
});

export default app;
