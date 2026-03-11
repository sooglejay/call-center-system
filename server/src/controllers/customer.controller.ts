import { Request, Response } from 'express';
import { query } from '../config/database';

export const getCustomers = async (req: any, res: Response) => {
  try {
    const { search, status, assigned_to, page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    let sql = `
      SELECT c.*, u.real_name as imported_by_name,
        EXISTS(
          SELECT 1 FROM call_records cr 
          WHERE cr.customer_id = c.id AND cr.agent_id != $1
        ) as is_duplicate
      FROM customers c
      LEFT JOIN users u ON c.imported_by = u.id
      WHERE 1=1
    `;
    const params: any[] = [req.user.id];
    
    if (search) {
      sql += ` AND (c.phone ILIKE $${params.length + 1} OR c.name ILIKE $${params.length + 1})`;
      params.push(`%${search}%`);
    }
    
    if (assigned_to) {
      sql += ` AND EXISTS(
        SELECT 1 FROM tasks t 
        WHERE t.agent_id = $${params.length + 1} 
        AND c.id = ANY(t.customer_ids)
        AND t.status = 'active'
      )`;
      params.push(assigned_to);
    }
    
    const countResult = await query(`SELECT COUNT(*) FROM (${sql}) as count_query`, params);
    const total = parseInt(countResult.rows[0].count);
    
    sql += ` ORDER BY c.created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
    params.push(pageSize, offset);
    
    const result = await query(sql, params);
    
    res.json({
      data: result.rows,
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
    
    const result = await query(
      `SELECT c.*, u.real_name as imported_by_name,
        (SELECT COUNT(*) FROM call_records WHERE customer_id = c.id) as call_count,
        (SELECT MAX(created_at) FROM call_records WHERE customer_id = c.id) as last_call_time
       FROM customers c
       LEFT JOIN users u ON c.imported_by = u.id
       WHERE c.id = $1`,
      [id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取客户详情错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { name, remark } = req.body;
    
    const result = await query(
      `UPDATE customers SET name = $1, remark = $2, updated_at = CURRENT_TIMESTAMP
       WHERE id = $3 RETURNING *`,
      [name, remark, id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteCustomer = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('DELETE FROM customers WHERE id = $1 RETURNING id', [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '客户不存在' });
    }
    
    res.json({ message: '客户删除成功' });
  } catch (error) {
    console.error('删除客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const batchImportCustomers = async (req: any, res: Response) => {
  try {
    const { customers, assigned_to } = req.body;
    const importedBy = req.user.id;
    
    const importedCustomers = [];
    for (const customer of customers) {
      try {
        const result = await query(
          `INSERT INTO customers (phone, name, remark, source, imported_by)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (phone) DO UPDATE SET
           name = EXCLUDED.name,
           remark = COALESCE(EXCLUDED.remark, customers.remark),
           updated_at = CURRENT_TIMESTAMP
           RETURNING *`,
          [customer.phone, customer.name, customer.remark, 'import', importedBy]
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
    
    let sql = `
      SELECT DISTINCT c.*, 
        cr.status as call_status,
        cr.is_connected,
        cr.call_duration,
        cr.recording_url,
        cr.call_notes,
        cr.created_at as call_time,
        EXISTS(
          SELECT 1 FROM call_records cr2 
          WHERE cr2.customer_id = c.id AND cr2.agent_id != $1
        ) as is_duplicate
      FROM customers c
      INNER JOIN tasks t ON c.id = ANY(t.customer_ids) AND t.agent_id = $1 AND t.status = 'active'
      LEFT JOIN LATERAL (
        SELECT * FROM call_records 
        WHERE customer_id = c.id AND agent_id = $1
        ORDER BY created_at DESC LIMIT 1
      ) cr ON true
      WHERE 1=1
    `;
    const params: any[] = [agentId];
    
    if (status === 'pending') {
      sql += ` AND (cr.status IS NULL OR cr.status IN ('pending', 'failed', 'no_answer', 'busy'))`;
    } else if (status === 'completed') {
      sql += ` AND cr.status = 'completed'`;
    }
    
    if (search) {
      sql += ` AND (c.phone ILIKE $${params.length + 1} OR c.name ILIKE $${params.length + 1})`;
      params.push(`%${search}%`);
    }
    
    const countResult = await query(`SELECT COUNT(*) FROM (${sql}) as count_query`, params);
    const total = parseInt(countResult.rows[0].count);
    
    sql += ` ORDER BY c.created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
    params.push(pageSize, offset);
    
    const result = await query(sql, params);
    
    res.json({
      data: result.rows,
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
