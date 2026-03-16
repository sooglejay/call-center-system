import { Request, Response } from 'express';
import { query } from '../config/database';

export const getCallRecords = async (req: any, res: Response) => {
  try {
    const { customer_id, status, start_date, end_date, page = 1, pageSize = 20 } = req.query;
    const agentId = req.user.role === 'agent' ? req.user.id : null;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 获取通话记录
    let result = await query('SELECT * FROM calls ORDER BY created_at DESC');
    let data = result.rows;
    
    // 过滤
    if (agentId) {
      data = data.filter((c: any) => c.agent_id === agentId);
    }
    if (customer_id) {
      data = data.filter((c: any) => c.customer_id === parseInt(customer_id as string));
    }
    if (status) {
      data = data.filter((c: any) => c.status === status);
    }
    if (start_date) {
      data = data.filter((c: any) => new Date(c.created_at) >= new Date(start_date as string));
    }
    if (end_date) {
      data = data.filter((c: any) => new Date(c.created_at) <= new Date(end_date as string));
    }
    
    const total = data.length;
    
    // 获取客户和客服名称
    const customers = await query('SELECT id, name FROM customers');
    const users = await query('SELECT id, real_name FROM users');
    const customerMap = new Map(customers.rows.map((c: any) => [c.id, c.name]));
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    // 分页并添加名称
    data = data.slice(offset, offset + parseInt(pageSize as string)).map((c: any) => ({
      ...c,
      customer_name: customerMap.get(c.customer_id) || '',
      agent_name: userMap.get(c.agent_id) || ''
    }));
    
    res.json({
      data,
      pagination: {
        total,
        page: parseInt(page as string),
        pageSize: parseInt(pageSize as string),
        totalPages: Math.ceil(total / parseInt(pageSize as string))
      }
    });
  } catch (error) {
    console.error('获取通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createCallRecord = async (req: any, res: Response) => {
  try {
    const { customer_id, phone, task_id } = req.body;
    
    const result = await query(
      'INSERT INTO calls (customer_id, agent_id, customer_phone, status, is_connected) VALUES ($1, $2, $3, $4, $5)',
      [customer_id, req.user.id, phone, 'calling', false]
    );
    
    // 获取插入的记录
    const newCall = await query(
      'SELECT * FROM calls WHERE id = (SELECT MAX(id) FROM calls)'
    );
    
    res.status(201).json(newCall.rows[0]);
  } catch (error) {
    console.error('创建通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCallRecord = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { status, is_connected, call_duration, recording_url, recording_duration, call_notes, call_result } = req.body;
    
    const result = await query('SELECT * FROM calls WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '通话记录不存在' });
    }
    
    const updated = { 
      ...result.rows[0], 
      status: status || result.rows[0].status,
      is_connected: is_connected !== undefined ? is_connected : result.rows[0].is_connected,
      call_duration: call_duration !== undefined ? call_duration : result.rows[0].call_duration,
      recording_url: recording_url !== undefined ? recording_url : result.rows[0].recording_url,
      recording_duration: recording_duration !== undefined ? recording_duration : result.rows[0].recording_duration,
      call_notes: call_notes !== undefined ? call_notes : result.rows[0].call_notes,
      call_result: call_result !== undefined ? call_result : result.rows[0].call_result,
      updated_at: new Date().toISOString() 
    };
    
    res.json(updated);
  } catch (error) {
    console.error('更新通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCallNotes = async (req: any, res: Response) => {
  try {
    const { id } = req.params;
    const { call_notes, call_result } = req.body;
    
    const result = await query('SELECT * FROM calls WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '通话记录不存在或无权限' });
    }
    
    const updated = { 
      ...result.rows[0], 
      call_notes: call_notes || result.rows[0].call_notes,
      call_result: call_result || result.rows[0].call_result,
      updated_at: new Date().toISOString()
    };
    
    res.json(updated);
  } catch (error) {
    console.error('更新通话备注错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getNextCall = async (req: any, res: Response) => {
  try {
    const agentId = req.user.id;
    
    // 获取客服的任务
    const tasks = await query('SELECT * FROM tasks WHERE assigned_to = $1 AND status = $2', [agentId, 'pending']);
    
    // 获取这些任务关联的客户
    const customerIds = new Set(tasks.rows.map((t: any) => t.customer_id).filter(Boolean));
    
    if (customerIds.size === 0) {
      return res.json({ message: '没有待拨打客户' });
    }
    
    // 获取客户列表，排除已接通的
    const customers = await query('SELECT * FROM customers ORDER BY created_at DESC');
    const calls = await query('SELECT * FROM calls WHERE agent_id = $1', [agentId]);
    
    const connectedCustomerIds = new Set(
      calls.rows.filter((c: any) => c.is_connected).map((c: any) => c.customer_id)
    );
    
    const pendingCustomers = customers.rows.filter((c: any) => 
      customerIds.has(c.id) && !connectedCustomerIds.has(c.id)
    );
    
    if (pendingCustomers.length === 0) {
      return res.json({ message: '没有待拨打客户' });
    }
    
    res.json(pendingCustomers[0]);
  } catch (error) {
    console.error('获取下一个通话错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getCallStatus = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM calls WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '通话记录不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取通话状态错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createRecord = async (req: any, res: Response) => {
  try {
    const { customer_id, task_id, status } = req.body;
    
    // 获取客户信息
    const customers = await query('SELECT * FROM customers WHERE id = $1', [customer_id]);
    if (customers.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    const customer = customers.rows[0];
    
    await query(
      'INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, is_connected) VALUES ($1, $2, $3, $4, $5, $6)',
      [customer_id, req.user.id, customer.phone, customer.name, status || 'pending', false]
    );
    
    // 获取插入的记录
    const newCall = await query(
      'SELECT * FROM calls WHERE id = (SELECT MAX(id) FROM calls)'
    );
    
    res.status(201).json(newCall.rows[0]);
  } catch (error) {
    console.error('创建记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
