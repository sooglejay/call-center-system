import { Request, Response } from 'express';
import { query } from '../config/database';

export const getTasks = async (req: Request, res: Response) => {
  try {
    const { agent_id, status, page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    let sql = `
      SELECT t.*, 
        u.real_name as agent_name,
        creator.real_name as created_by_name,
        array_length(t.customer_ids, 1) as customer_count,
        (SELECT COUNT(DISTINCT customer_id) FROM call_records 
         WHERE task_id = t.id AND is_connected = true) as completed_count
      FROM tasks t
      JOIN users u ON t.agent_id = u.id
      JOIN users creator ON t.created_by = creator.id
      WHERE 1=1
    `;
    const params: any[] = [];
    
    if (agent_id) {
      sql += ` AND t.agent_id = $${params.length + 1}`;
      params.push(agent_id);
    }
    
    if (status) {
      sql += ` AND t.status = $${params.length + 1}`;
      params.push(status);
    }
    
    const countResult = await query(`SELECT COUNT(*) FROM (${sql}) as count_query`, params);
    const total = parseInt(countResult.rows[0].count);
    
    sql += ` ORDER BY t.created_at DESC LIMIT $${params.length + 1} OFFSET $${params.length + 2}`;
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
    console.error('获取任务列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const createTask = async (req: any, res: Response) => {
  try {
    const { name, agent_id, customer_ids, task_type, start_date, end_date } = req.body;
    
    const result = await query(
      `INSERT INTO tasks (name, agent_id, customer_ids, task_type, start_date, end_date, created_by)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
      [name, agent_id, customer_ids, task_type, start_date, end_date, req.user.id]
    );
    
    res.status(201).json(result.rows[0]);
  } catch (error) {
    console.error('创建任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { name, customer_ids, task_type, start_date, end_date, status } = req.body;
    
    const result = await query(
      `UPDATE tasks SET 
        name = COALESCE($1, name),
        customer_ids = COALESCE($2, customer_ids),
        task_type = COALESCE($3, task_type),
        start_date = COALESCE($4, start_date),
        end_date = COALESCE($5, end_date),
        status = COALESCE($6, status),
        updated_at = CURRENT_TIMESTAMP
       WHERE id = $7 RETURNING *`,
      [name, customer_ids, task_type, start_date, end_date, status, id]
    );
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const deleteTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('DELETE FROM tasks WHERE id = $1 RETURNING id', [id]);
    
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
    const result = await query(
      `SELECT t.*, 
        array_length(t.customer_ids, 1) as customer_count,
        (SELECT COUNT(DISTINCT customer_id) FROM call_records 
         WHERE task_id = t.id AND is_connected = true) as completed_count
       FROM tasks t
       WHERE t.agent_id = $1 AND t.status = 'active'
       ORDER BY t.created_at DESC`,
      [req.user.id]
    );
    
    res.json(result.rows);
  } catch (error) {
    console.error('获取我的任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
