import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import pool from './config/database';

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
app.use('/api/communication', communicationRoutes);

// 错误处理
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('服务器错误:', err);
  res.status(500).json({ error: '服务器内部错误' });
});

// 初始化数据库
const initDatabase = async () => {
  try {
    console.log('正在检查数据库连接...');
    await pool.query('SELECT NOW()');
    console.log('✅ 数据库连接成功！');

    // 读取并执行 SQL 初始化脚本
    const sqlPath = path.join(__dirname, 'scripts', 'init-db.sql');
    
    if (fs.existsSync(sqlPath)) {
      console.log('正在执行数据库初始化...');
      const sql = fs.readFileSync(sqlPath, 'utf-8');
      await pool.query(sql);
      console.log('✅ 数据库初始化完成！');
    } else {
      console.log('⚠️ SQL 初始化文件不存在，跳过数据库初始化');
    }
  } catch (error) {
    console.error('❌ 数据库连接失败:', error);
    console.log('\n💡 提示：请确保 PostgreSQL 已启动');
    console.log('   本地开发：docker-compose -f docker-compose.db.yml up -d');
    console.log('   或使用 Railway/Vercel 部署后的云数据库\n');
    // 不阻止应用启动，只是记录错误
  }
};

// 启动服务器
app.listen(PORT, async () => {
  console.log(`服务器运行在端口 ${PORT}`);
  console.log(`API地址: http://localhost:${PORT}/api`);
  console.log(`健康检查: http://localhost:${PORT}/api/system/health`);
  
  // 启动时初始化数据库
  await initDatabase();
});

export default app;
