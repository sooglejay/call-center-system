import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import { query, initDatabase } from '../config/database';
import { generateToken } from '../middleware/auth';

// 默认管理员账号配置
const DEFAULT_ADMIN = {
  username: 'admin',
  password: 'admin123',
  role: 'admin',
  real_name: '系统管理员',
  status: 'active'
};

// 检查是否需要初始化（数据库中没有用户）
const checkNeedInit = async (): Promise<boolean> => {
  try {
    const result = await query('SELECT COUNT(*) as count FROM users', []);
    return (result.rows[0]?.count || 0) === 0;
  } catch (error) {
    // 如果表不存在，需要初始化
    return true;
  }
};

// 初始化数据库和管理员账号
const initializeDatabase = async (): Promise<void> => {
  console.log('🔄 检测到首次使用，正在初始化数据库...');
  
  // 确保数据库表结构已创建
  await initDatabase();
  
  // 创建默认管理员账号
  await query(
    `INSERT INTO users (username, password, role, real_name, status, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
    [DEFAULT_ADMIN.username, DEFAULT_ADMIN.password, DEFAULT_ADMIN.role, DEFAULT_ADMIN.real_name, DEFAULT_ADMIN.status]
  );
  
  console.log('✅ 数据库初始化完成');
  console.log(`   默认管理员: ${DEFAULT_ADMIN.username} / ${DEFAULT_ADMIN.password}`);
};

export const login = async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;
    
    // 检查是否需要初始化
    const needInit = await checkNeedInit();
    if (needInit) {
      // 只有使用默认管理员账号登录时才初始化
      if (username === DEFAULT_ADMIN.username && password === DEFAULT_ADMIN.password) {
        await initializeDatabase();
      } else {
        return res.status(401).json({ 
          error: '系统尚未初始化，请使用默认管理员账号登录',
          needInit: true,
          hint: '默认账号: admin / admin123'
        });
      }
    }
    
    const result = await query(
      'SELECT * FROM users WHERE username = $1 AND status = $2',
      [username, 'active']
    );
    
    if (result.rows.length === 0) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }
    
    const user = result.rows[0];
    // 明文密码比较（开发便利）
    const isValidPassword = password === user.password;
    
    if (!isValidPassword) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }
    
    const token = generateToken({
      id: user.id,
      username: user.username,
      role: user.role
    });
    
    res.json({
      token,
      user: {
        id: user.id,
        username: user.username,
        real_name: user.real_name,
        role: user.role,
        phone: user.phone,
        email: user.email,
        avatar_url: user.avatar_url
      }
    });
  } catch (error) {
    console.error('登录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 检查系统是否已初始化
export const checkInit = async (req: Request, res: Response) => {
  try {
    const needInit = await checkNeedInit();
    res.json({ 
      initialized: !needInit,
      hint: needInit ? '请使用 admin / admin123 首次登录' : undefined
    });
  } catch (error) {
    console.error('检查初始化状态错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getCurrentUser = async (req: any, res: Response) => {
  try {
    const result = await query(
      'SELECT id, username, real_name, role, phone, email, avatar_url FROM users WHERE id = $1',
      [req.user.id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取用户信息错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateProfile = async (req: any, res: Response) => {
  try {
    const { real_name, phone, email, avatar_url } = req.body;
    
    await query(
      `UPDATE users SET real_name = $1, phone = $2, email = $3, avatar_url = $4, updated_at = CURRENT_TIMESTAMP
       WHERE id = $5`,
      [real_name, phone, email, avatar_url, req.user.id]
    );
    
    // 查询更新后的用户信息
    const result = await query(
      'SELECT id, username, real_name, role, phone, email, avatar_url FROM users WHERE id = $1',
      [req.user.id]
    );
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新用户信息错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const changePassword = async (req: any, res: Response) => {
  try {
    const { old_password, new_password } = req.body;
    
    const userResult = await query('SELECT password FROM users WHERE id = $1', [req.user.id]);
    if (userResult.rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    // 明文密码比较（开发便利）
    const isValidPassword = old_password === userResult.rows[0].password;
    if (!isValidPassword) {
      return res.status(400).json({ error: '原密码错误' });
    }
    
    // 明文存储密码（开发便利）
    await query('UPDATE users SET password = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2', 
      [new_password, req.user.id]);
    
    res.json({ message: '密码修改成功' });
  } catch (error) {
    console.error('修改密码错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
