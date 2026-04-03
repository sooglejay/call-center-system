import { Request, Response } from 'express';
import { query } from '../config/database';

export const getCallRecords = async (req: any, res: Response) => {
  try {
    const { customer_id, status, start_date, end_date, page = 1, pageSize = 20 } = req.query;
    const agentId = req.user.role === 'agent' ? req.user.id : null;
    const pageNum = parseInt(page as string);
    const sizeNum = parseInt(pageSize as string);
    const offset = (pageNum - 1) * sizeNum;
    
    // 构建WHERE条件
    const whereConditions: string[] = [];
    const params: any[] = [];
    
    // 客服只能看到自己的通话记录
    if (agentId) {
      whereConditions.push(`c.agent_id = $${params.length + 1}`);
      params.push(agentId);
    }
    
    // 客户过滤
    if (customer_id) {
      whereConditions.push(`c.customer_id = $${params.length + 1}`);
      params.push(parseInt(customer_id as string));
    }
    
    // 状态过滤
    if (status) {
      whereConditions.push(`c.status = $${params.length + 1}`);
      params.push(status);
    }
    
    // 日期范围过滤
    if (start_date) {
      whereConditions.push(`c.created_at >= $${params.length + 1}`);
      params.push(start_date);
    }
    if (end_date) {
      whereConditions.push(`c.created_at <= $${params.length + 1}`);
      params.push(end_date);
    }
    
    const whereClause = whereConditions.length > 0 ? `WHERE ${whereConditions.join(' AND ')}` : '';
    
    // 获取总数
    const countResult = await query(`SELECT COUNT(*) as total FROM calls c ${whereClause}`, params);
    const total = parseInt(countResult.rows[0].total);
    
    // 获取分页数据
    const queryParams = [...params, sizeNum, offset];
    const result = await query(
      `SELECT c.*, 
              cu.name as customer_name, cu.phone as customer_phone,
              u.username as agent_username, u.real_name as agent_real_name, u.role as agent_role
       FROM calls c
       LEFT JOIN customers cu ON c.customer_id = cu.id
       LEFT JOIN users u ON c.agent_id = u.id
       ${whereClause}
       ORDER BY c.created_at DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      queryParams
    );
    
    // 格式化数据
    const data = result.rows.map((c: any) => ({
      id: c.id,
      customer_id: c.customer_id,
      customer: c.customer_name ? {
        id: c.customer_id,
        name: c.customer_name,
        phone: c.customer_phone
      } : null,
      agent_id: c.agent_id,
      agent: c.agent_username ? {
        id: c.agent_id,
        username: c.agent_username,
        real_name: c.agent_real_name,
        role: c.agent_role
      } : null,
      phone: c.customer_phone || '',
      direction: 'outbound',
      status: c.status,
      duration: c.call_duration || 0,
      notes: c.call_notes || '',
      recording: c.recording_url || '',
      call_sid: c.twilio_call_sid || '',
      dialed_at: c.started_at || null,
      connected_at: c.connected_at || null,
      ended_at: c.ended_at || null,
      created_at: c.created_at
    }));
    
    res.json({
      data,
      total,
      page: pageNum,
      page_size: sizeNum
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
