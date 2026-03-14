import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import { query } from '../config/database';

export const getUsers = async (req: Request, res: Response) => {
  try {
    const { role, status, search } = req.query;
    let sql = 'SELECT id, username, real_name, role, phone, email, status, created_at FROM users WHERE 1=1';
    const params: any[] = [];
    
    if (role) {
      sql += ` AND role = $${params.length + 1}`;
      params.push(role);
    }
    
    if (status) {
      sql += ` AND status = $${params.length + 1}`;
      params.push(status);
    }
    
    if (search) {
      sql += ` AND (username ILIKE $${params.length + 1} OR real_name ILIKE $${params.length + 1})`;
      params.push(`%${search}%`);
    }
    
    sql += ' ORDER BY created_at DESC';
    
    const result = await query(sql, params);
    res.json(result.rows);
  } catch (error) {
    console.error('获取用户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createUser = async (req: Request, res: Response) => {
  try {
    const { username, password, real_name, role, phone, email } = req.body;
    
    const existingUser = await query('SELECT id FROM users WHERE username = $1', [username]);
    if (existingUser.rows.length > 0) {
      return res.status(400).json({ error: '用户名已存在' });
    }
    
    // 明文存储密码（开发便利）
    const result = await query(
      `INSERT INTO users (username, password, real_name, role, phone, email)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, username, real_name, role, phone, email, status, created_at`,
      [username, password, real_name, role, phone, email]
    );
    
    // 为客服创建默认配置
    if (role === 'agent') {
      await query(
        'INSERT INTO agent_configs (agent_id, dial_strategy, dial_delay, remove_duplicates) VALUES ($1, $2, $3, $4)',
        [result.rows[0].id, 'newest', 3, false]
      );
    }
    
    res.status(201).json(result.rows[0]);
  } catch (error) {
    console.error('创建用户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateUser = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { real_name, phone, email, status } = req.body;
    
    const result = await query(
      `UPDATE users SET real_name = $1, phone = $2, email = $3, status = $4, updated_at = CURRENT_TIMESTAMP
       WHERE id = $5 RETURNING id, username, real_name, role, phone, email, status, created_at`,
      [real_name, phone, email, status, id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新用户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteUser = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    await query('DELETE FROM agent_configs WHERE agent_id = $1', [id]);
    const result = await query('DELETE FROM users WHERE id = $1 RETURNING id', [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    res.json({ message: '用户删除成功' });
  } catch (error) {
    console.error('删除用户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const resetPassword = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { new_password } = req.body;
    
    // 明文存储密码（开发便利）
    await query('UPDATE users SET password = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2', 
      [new_password, id]);
    
    res.json({ message: '密码重置成功' });
  } catch (error) {
    console.error('重置密码错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getAgents = async (req: Request, res: Response) => {
  try {
    const result = await query(
      'SELECT id, username, real_name, phone, email, status FROM users WHERE role = $1 AND status = $2',
      ['agent', 'active']
    );
    res.json(result.rows);
  } catch (error) {
    console.error('获取客服列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
