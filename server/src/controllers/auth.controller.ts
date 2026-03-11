import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import { query } from '../config/database';
import { generateToken } from '../middleware/auth';

export const login = async (req: Request, res: Response) => {
  try {
    const { username, password } = req.body;
    
    const result = await query(
      'SELECT * FROM users WHERE username = $1 AND status = $2',
      [username, 'active']
    );
    
    if (result.rows.length === 0) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }
    
    const user = result.rows[0];
    const isValidPassword = await bcrypt.compare(password, user.password);
    
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
    
    const result = await query(
      `UPDATE users SET real_name = $1, phone = $2, email = $3, avatar_url = $4, updated_at = CURRENT_TIMESTAMP
       WHERE id = $5 RETURNING id, username, real_name, role, phone, email, avatar_url`,
      [real_name, phone, email, avatar_url, req.user.id]
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
    
    const isValidPassword = await bcrypt.compare(old_password, userResult.rows[0].password);
    if (!isValidPassword) {
      return res.status(400).json({ error: '原密码错误' });
    }
    
    const hashedPassword = await bcrypt.hash(new_password, 10);
    await query('UPDATE users SET password = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2', 
      [hashedPassword, req.user.id]);
    
    res.json({ message: '密码修改成功' });
  } catch (error) {
    console.error('修改密码错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
