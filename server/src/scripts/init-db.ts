import { Pool } from 'pg';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';

dotenv.config();

const initDatabase = async () => {
  // 使用环境变量或默认值创建连接
  const pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432'),
    database: process.env.DB_NAME || 'postgres',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || '',
  });

  try {
    console.log('正在连接数据库...');
    await pool.query('SELECT NOW()');
    console.log('数据库连接成功！');

    // 读取 SQL 文件
    const sqlPath = path.join(__dirname, 'init-db.sql');
    
    if (!fs.existsSync(sqlPath)) {
      console.log('SQL 初始化文件不存在，跳过数据库初始化');
      await pool.end();
      return;
    }

    const sql = fs.readFileSync(sqlPath, 'utf-8');
    console.log('正在执行数据库初始化...');

    // 执行 SQL 脚本
    await pool.query(sql);
    console.log('数据库初始化完成！');

    await pool.end();
    process.exit(0);
  } catch (error) {
    console.error('数据库初始化失败:', error);
    await pool.end();
    // 不退出进程，让安装继续（避免阻塞 CI/CD）
    process.exit(0);
  }
};

initDatabase();
