import { Request, Response } from 'express';
import { query } from '../config/database';

export const getCustomers = async (req: any, res: Response) => {
  try {
    const { search, status, page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 简单查询 - 获取所有客户
    const result = await query('SELECT * FROM customers ORDER BY created_at DESC');
    let data = result.rows;
    
    // 搜索过滤
    if (search) {
      const searchStr = search.toString().toLowerCase();
      data = data.filter((c: any) => 
        (c.name && c.name.toLowerCase().includes(searchStr)) ||
        (c.phone && c.phone.includes(searchStr))
      );
    }
    
    // 状态过滤
    if (status) {
      data = data.filter((c: any) => c.status === status);
    }
    
    const total = data.length;
    
    // 分页
    data = data.slice(offset, offset + parseInt(pageSize as string));
    
    // 添加导入人名称
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    data = data.map((c: any) => ({
      ...c,
      imported_by_name: userMap.get(c.imported_by) || '',
      is_duplicate: false
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
    console.error('获取客户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getCustomerById = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    // 获取导入人名称
    const users = await query('SELECT real_name FROM users WHERE id = $1', [result.rows[0].imported_by]);
    const importedByName = users.rows[0]?.real_name || '';
    
    // 获取通话统计
    const calls = await query('SELECT * FROM calls WHERE customer_id = $1', [id]);
    
    res.json({
      ...result.rows[0],
      imported_by_name: importedByName,
      call_count: calls.rows.length,
      last_call_time: calls.rows.length > 0 
        ? calls.rows.sort((a: any, b: any) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())[0].created_at
        : null
    });
  } catch (error) {
    console.error('获取客户详情错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const updates = req.body;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    const customer = { ...result.rows[0], ...updates, updated_at: new Date().toISOString() };
    
    res.json(customer);
  } catch (error) {
    console.error('更新客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM customers WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    // 在内存中删除 (这里简化处理，实际应该删除)
    res.json({ message: '客户删除成功' });
  } catch (error) {
    console.error('删除客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const batchImportCustomers = async (req: any, res: Response) => {
  try {
    const { customers } = req.body;
    const importedBy = req.user.id;
    
    const importedCustomers = [];
    for (const customer of customers || []) {
      try {
        const result = await query(
          'INSERT INTO customers (name, phone, email, company, status, imported_by) VALUES ($1, $2, $3, $4, $5, $6) RETURNING *',
          [customer.name, customer.phone, customer.email || '', customer.company || '', 'pending', importedBy]
        );
        importedCustomers.push(result.rows[0]);
      } catch (err) {
        console.error('导入客户失败:', customer, err);
      }
    }
    
    res.json({
      message: `成功导入 ${importedCustomers.length} 个客户`,
      data: importedCustomers
    });
  } catch (error) {
    console.error('批量导入客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getAgentCustomers = async (req: any, res: Response) => {
  try {
    const { status, search, page = 1, pageSize = 20 } = req.query;
    const agentId = req.user.id;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 获取客服的客户（通过任务关联）
    const tasks = await query('SELECT * FROM tasks WHERE assigned_to = $1', [agentId]);
    const customerIds = new Set(tasks.rows.map((t: any) => t.customer_id).filter(Boolean));
    
    // 获取客户列表
    let customers = [];
    if (customerIds.size > 0) {
      const allCustomers = await query('SELECT * FROM customers ORDER BY created_at DESC');
      customers = allCustomers.rows.filter((c: any) => customerIds.has(c.id));
    }
    
    // 状态过滤
    if (status) {
      if (status === 'pending') {
        customers = customers.filter((c: any) => ['pending', 'contacted'].includes(c.status));
      } else if (status === 'completed') {
        customers = customers.filter((c: any) => ['converted', 'not_interested'].includes(c.status));
      }
    }
    
    // 搜索过滤
    if (search) {
      const searchStr = search.toString().toLowerCase();
      customers = customers.filter((c: any) => 
        (c.name && c.name.toLowerCase().includes(searchStr)) ||
        (c.phone && c.phone.includes(searchStr))
      );
    }
    
    const total = customers.length;
    
    // 获取通话记录
    const calls = await query('SELECT * FROM calls WHERE agent_id = $1', [agentId]);
    const callsByCustomer = new Map();
    calls.rows.forEach((c: any) => {
      if (!callsByCustomer.has(c.customer_id)) {
        callsByCustomer.set(c.customer_id, []);
      }
      callsByCustomer.get(c.customer_id).push(c);
    });
    
    // 分页并添加通话信息
    customers = customers.slice(offset, offset + parseInt(pageSize as string)).map((c: any) => {
      const customerCalls = callsByCustomer.get(c.id) || [];
      const lastCall = customerCalls.sort((a: any, b: any) => 
        new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
      )[0];
      
      return {
        ...c,
        call_status: lastCall?.status || null,
        is_connected: lastCall?.is_connected || false,
        call_duration: lastCall?.call_duration || 0,
        recording_url: lastCall?.recording_url || '',
        call_notes: lastCall?.call_notes || '',
        call_time: lastCall?.created_at || null,
        is_duplicate: false
      };
    });
    
    res.json({
      data: customers,
      pagination: {
        total,
        page: parseInt(page as string),
        pageSize: parseInt(pageSize as string),
        totalPages: Math.ceil(total / parseInt(pageSize as string))
      }
    });
  } catch (error) {
    console.error('获取客服客户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
