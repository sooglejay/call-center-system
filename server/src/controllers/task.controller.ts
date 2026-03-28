import { Request, Response } from 'express';
import { query } from '../config/database';

export const getTasks = async (req: Request, res: Response) => {
  try {
    const { agent_id, status, page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 获取所有任务
    let result = await query('SELECT * FROM tasks ORDER BY created_at DESC');
    let data = result.rows;
    
    // 过滤
    if (agent_id) {
      data = data.filter((t: any) => t.assigned_to === parseInt(agent_id as string));
    }
    if (status) {
      data = data.filter((t: any) => t.status === status);
    }
    
    const total = data.length;
    
    // 获取用户信息
    const users = await query('SELECT id, username, real_name, role, phone, email, status, data_access_type FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u]));
    
    // 获取通话统计
    const calls = await query('SELECT * FROM calls');
    const callsByCustomer = new Map();
    calls.rows.forEach((c: any) => {
      if (!callsByCustomer.has(c.customer_id)) {
        callsByCustomer.set(c.customer_id, 0);
      }
      if (c.is_connected) {
        callsByCustomer.set(c.customer_id, callsByCustomer.get(c.customer_id) + 1);
      }
    });
    
    // 分页并添加信息
    data = data.slice(offset, offset + parseInt(pageSize as string)).map((t: any) => {
      const agent = userMap.get(t.assigned_to);
      return {
        id: t.id,
        title: t.title || t.name || '',
        description: t.description || '',
        status: t.status || 'pending',
        priority: t.priority || 1,
        assigned_to: t.assigned_to,
        assigned_agent: agent ? {
          id: agent.id,
          username: agent.username,
          real_name: agent.real_name,
          role: agent.role,
          phone: agent.phone,
          email: agent.email
        } : null,
        due_date: t.due_date || null,
        created_at: t.created_at,
        updated_at: t.updated_at,
        agent_name: agent?.real_name || '',
        created_by_name: userMap.get(t.created_by)?.real_name || '',
        customer_count: t.customer_id ? 1 : 0,
        completed_count: callsByCustomer.get(t.customer_id) || 0
      };
    });
    
    res.json({
      data,
      total,
      page: parseInt(page as string),
      page_size: parseInt(pageSize as string),
      total_pages: Math.ceil(total / parseInt(pageSize as string))
    });
  } catch (error) {
    console.error('获取任务列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createTask = async (req: any, res: Response) => {
  try {
    const { title, description, assigned_to, customer_id, priority } = req.body;
    
    // 将 undefined 转为 null
    const customerIdValue = customer_id ?? null;
    const priorityValue = priority || 1;
    
    await query(
      'INSERT INTO tasks (title, description, assigned_to, customer_id, priority, status, created_by) VALUES ($1, $2, $3, $4, $5, $6, $7)',
      [title, description, assigned_to, customerIdValue, priorityValue, 'pending', req.user.id]
    );
    
    // 获取插入的记录
    const newTask = await query(
      'SELECT * FROM tasks WHERE id = (SELECT MAX(id) FROM tasks)'
    );
    
    res.status(201).json(newTask.rows[0]);
  } catch (error) {
    console.error('创建任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { title, description, priority, status, due_date, completed_at } = req.body;
    
    const result = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    const updated = { ...result.rows[0], ...req.body, updated_at: new Date().toISOString() };
    res.json(updated);
  } catch (error) {
    console.error('更新任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    res.json({ message: '任务删除成功' });
  } catch (error) {
    console.error('删除任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getMyTasks = async (req: any, res: Response) => {
  try {
    const agentId = req.user.id;
    const result = await query('SELECT * FROM tasks WHERE assigned_to = $1 ORDER BY created_at DESC', [agentId]);
    
    // 获取用户信息
    const users = await query('SELECT id, username, real_name, role, phone, email FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u]));
    
    // 获取通话统计
    const calls = await query('SELECT * FROM calls');
    const callsByCustomer = new Map();
    calls.rows.forEach((c: any) => {
      if (!callsByCustomer.has(c.customer_id)) {
        callsByCustomer.set(c.customer_id, 0);
      }
      if (c.is_connected) {
        callsByCustomer.set(c.customer_id, callsByCustomer.get(c.customer_id) + 1);
      }
    });
    
    const data = result.rows.map((t: any) => {
      const agent = userMap.get(t.assigned_to);
      return {
        id: t.id,
        title: t.title || t.name || '',
        description: t.description || '',
        status: t.status || 'pending',
        priority: t.priority || 1,
        assigned_to: t.assigned_to,
        assigned_agent: agent ? {
          id: agent.id,
          username: agent.username,
          real_name: agent.real_name,
          role: agent.role,
          phone: agent.phone,
          email: agent.email
        } : null,
        due_date: t.due_date || null,
        created_at: t.created_at,
        updated_at: t.updated_at,
        customer_count: t.customer_id ? 1 : 0,
        completed_count: callsByCustomer.get(t.customer_id) || 0
      };
    });
    
    res.json(data);
  } catch (error) {
    console.error('获取我的任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
