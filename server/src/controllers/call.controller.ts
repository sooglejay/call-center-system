import { Request, Response } from 'express';
import { query } from '../config/database';

export const getCallRecords = async (req: any, res: Response) => {
  try {
    const { customer_id, status, start_date, end_date, page = 1, pageSize = 20 } = req.query;
    const agentId = req.user.role === 'agent' ? req.user.id : null;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    let sql = `
      SELECT cr.*, 
        c.name as customer_name,
        u.real_name as agent_name
      FROM call_records cr
      JOIN customers c ON cr.customer_id = c.id
      JOIN users u ON cr.agent_id = u.id
      WHERE 1=1
    `;
    const params: any[] = [];
    
    if (agentId) {
      sql += ` AND cr.agent_id = $${params.length + 1}`;
      params.push(agentId);
    }
    
    if (customer_id) {
      sql += ` AND cr.customer_id = $${params.length + 1}`;
      params.push(customer_id);
    }
    
    if (status) {
      sql += ` AND cr.status = $${params.length + 1}`;
      params.push(status);
    }
    
    if (start_date) {
      sql += ` AND cr.created_at >= $${params.length + 1}`;
      params.push(start_date);
    }
    
    if (end_date) {
      sql += ` AND cr.created_at <= $${params.length + 1}`;
      params.push(end_date);
    }
    
    const countResult = await query(`SELECT COUNT(*) FROM (${sql}) as count_query`, params);
    const total = parseInt(countResult.rows[0].count);
    
    sql += ` ORDER BY cr.created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
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
    console.error('获取通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createCallRecord = async (req: any, res: Response) => {
  try {
    const { customer_id, phone, task_id } = req.body;
    
    const result = await query(
      `INSERT INTO call_records (customer_id, agent_id, phone, task_id, status, started_at)
       VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP) RETURNING *`,
      [customer_id, req.user.id, phone, task_id, 'calling']
    );
    
    res.status(201).json(result.rows[0]);
  } catch (error) {
    console.error('创建通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCallRecord = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { status, is_connected, call_duration, recording_url, recording_duration, call_notes, call_result } = req.body;
    
    const updates: string[] = [];
    const params: any[] = [];
    
    if (status !== undefined) {
      updates.push(`status = $${params.length + 1}`);
      params.push(status);
    }
    if (is_connected !== undefined) {
      updates.push(`is_connected = $${params.length + 1}`);
      params.push(is_connected);
    }
    if (call_duration !== undefined) {
      updates.push(`call_duration = $${params.length + 1}`);
      params.push(call_duration);
    }
    if (recording_url !== undefined) {
      updates.push(`recording_url = $${params.length + 1}`);
      params.push(recording_url);
    }
    if (recording_duration !== undefined) {
      updates.push(`recording_duration = $${params.length + 1}`);
      params.push(recording_duration);
    }
    if (call_notes !== undefined) {
      updates.push(`call_notes = $${params.length + 1}`);
      params.push(call_notes);
    }
    if (call_result !== undefined) {
      updates.push(`call_result = $${params.length + 1}`);
      params.push(call_result);
    }
    
    if (status === 'connected') {
      updates.push(`connected_at = CURRENT_TIMESTAMP`);
    }
    if (status === 'completed' || status === 'failed' || status === 'no_answer' || status === 'busy') {
      updates.push(`ended_at = CURRENT_TIMESTAMP`);
    }
    
    updates.push(`updated_at = CURRENT_TIMESTAMP`);
    params.push(id);
    
    const result = await query(
      `UPDATE call_records SET ${updates.join(', ')} WHERE id = $${params.length} RETURNING *`,
      params
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '通话记录不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新通话记录错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateCallNotes = async (req: any, res: Response) => {
  try {
    const { id } = req.params;
    const { call_notes, call_result } = req.body;
    
    const result = await query(
      `UPDATE call_records SET call_notes = $1, call_result = $2, updated_at = CURRENT_TIMESTAMP
       WHERE id = $3 AND agent_id = $4 RETURNING *`,
      [call_notes, call_result, id, req.user.id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '通话记录不存在或无权限' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新通话备注错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getNextCall = async (req: any, res: Response) => {
  try {
    const agentId = req.user.id;
    
    // 获取客服配置
    const configResult = await query(
      'SELECT * FROM agent_configs WHERE agent_id = $1',
      [agentId]
    );
    const config = configResult.rows[0] || { dial_strategy: 'newest', remove_duplicates: false };
    
    // 查询待拨打客户
    let sql = `
      SELECT c.*, t.id as task_id
      FROM customers c
      INNER JOIN tasks t ON c.id = ANY(t.customer_ids) AND t.agent_id = $1 AND t.status = 'active'
      LEFT JOIN call_records cr ON c.id = cr.customer_id AND cr.agent_id = $1
      WHERE (cr.id IS NULL OR cr.status IN ('pending', 'failed', 'no_answer', 'busy'))
    `;
    
    if (config.remove_duplicates) {
      sql += ` AND NOT EXISTS(
        SELECT 1 FROM call_records cr2 
        WHERE cr2.customer_id = c.id AND cr2.agent_id != $1 AND cr2.is_connected = true
      )`;
    }
    
    sql += ` ORDER BY c.created_at ${config.dial_strategy === 'newest' ? 'DESC' : 'ASC'}`;
    sql += ` LIMIT 1`;
    
    const result = await query(sql, [agentId]);
    
    if (result.rows.length === 0) {
      return res.json({ message: '没有待拨打的客户' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取下一个拨打客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
