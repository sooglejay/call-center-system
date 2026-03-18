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

// 默认客服账号配置
const DEFAULT_AGENT = {
  username: 'agent',
  password: 'agent123',
  role: 'agent',
  real_name: '客服专员',
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
  
  // 检查默认管理员是否已存在
  const existingAdmin = await query('SELECT id FROM users WHERE username = $1', [DEFAULT_ADMIN.username]);
  
  if (existingAdmin.rows.length === 0) {
    // 创建默认管理员账号
    await query(
      `INSERT INTO users (username, password, role, real_name, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
      [DEFAULT_ADMIN.username, DEFAULT_ADMIN.password, DEFAULT_ADMIN.role, DEFAULT_ADMIN.real_name, DEFAULT_ADMIN.status]
    );
    console.log('✅ 默认管理员账号已创建');
  } else {
    console.log('✅ 默认管理员账号已存在');
  }
  
  // 检查默认客服是否已存在
  const existingAgent = await query('SELECT id FROM users WHERE username = $1', [DEFAULT_AGENT.username]);
  
  if (existingAgent.rows.length === 0) {
    // 创建默认客服账号
    await query(
      `INSERT INTO users (username, password, role, real_name, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
      [DEFAULT_AGENT.username, DEFAULT_AGENT.password, DEFAULT_AGENT.role, DEFAULT_AGENT.real_name, DEFAULT_AGENT.status]
    );
    console.log('✅ 默认客服账号已创建');
  } else {
    console.log('✅ 默认客服账号已存在');
  }
  
  console.log('✅ 数据库初始化完成');
  console.log(`   默认管理员: ${DEFAULT_ADMIN.username} / ${DEFAULT_ADMIN.password}`);
  console.log(`   默认客服: ${DEFAULT_AGENT.username} / ${DEFAULT_AGENT.password}`);
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

// 获取系统配置（公开）
export const getPublicConfig = async (req: Request, res: Response) => {
  try {
    const result = await query(
      "SELECT config_key, config_value FROM system_configs WHERE config_key IN ('allow_register', 'register_default_role')"
    );
    
    const config: Record<string, string> = {};
    for (const row of result.rows) {
      config[row.config_key] = row.config_value;
    }
    
    res.json({
      allowRegister: config.allow_register === 'true',
      registerDefaultRole: config.register_default_role || 'agent'
    });
  } catch (error) {
    console.error('获取系统配置错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 用户注册
export const register = async (req: Request, res: Response) => {
  try {
    const { username, password, real_name, phone, email } = req.body;
    
    // 检查是否允许注册
    const configResult = await query(
      "SELECT config_value FROM system_configs WHERE config_key = 'allow_register'"
    );
    
    if (configResult.rows.length === 0 || configResult.rows[0].config_value !== 'true') {
      return res.status(403).json({ error: '系统已关闭注册功能' });
    }
    
    // 获取默认角色
    const roleResult = await query(
      "SELECT config_value FROM system_configs WHERE config_key = 'register_default_role'"
    );
    const defaultRole = roleResult.rows[0]?.config_value || 'agent';
    
    // 验证必填字段
    if (!username || !password) {
      return res.status(400).json({ error: '用户名和密码不能为空' });
    }
    
    // 验证用户名格式
    if (username.length < 3 || username.length > 20) {
      return res.status(400).json({ error: '用户名长度需在3-20个字符之间' });
    }
    
    // 验证密码长度
    if (password.length < 6) {
      return res.status(400).json({ error: '密码长度至少6个字符' });
    }
    
    // 检查用户名是否已存在
    const existingUser = await query('SELECT id FROM users WHERE username = $1', [username]);
    if (existingUser.rows.length > 0) {
      return res.status(400).json({ error: '用户名已存在' });
    }
    
    // 创建用户
    const result = await query(
      `INSERT INTO users (username, password, role, real_name, phone, email, status, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, 'active', datetime('now'), datetime('now'))`,
      [username, password, defaultRole, real_name || username, phone || null, email || null]
    );
    
    // 获取新用户信息
    const newUserResult = await query('SELECT id, username, real_name, role FROM users WHERE id = $1', [result.rows[0].id]);
    const newUser = newUserResult.rows[0];
    
    // 生成 token
    const token = generateToken({
      id: newUser.id,
      username: newUser.username,
      role: newUser.role
    });
    
    res.status(201).json({
      message: '注册成功',
      token,
      user: {
        id: newUser.id,
        username: newUser.username,
        real_name: newUser.real_name,
        role: newUser.role
      }
    });
  } catch (error) {
    console.error('注册错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
