import { Request, Response } from 'express';
import { query } from '../config/database';

// 通话结果展示值收敛：只保留两类（兼容历史/英文码/旧中文值）
function normalizeCallResult(raw: any): string | null {
  const v = (raw ?? '').toString().trim();
  if (!v) return null;

  const normalized = v.toLowerCase();
  const connectedSet = new Set([
    '真人已接通',
    '已接听',
    'connected',
    'answered',
  ]);
  if (connectedSet.has(v) || connectedSet.has(normalized)) {
    return '真人已接通';
  }

  // 其他所有情况（含 语音信箱/无人接听/忙线/拒接 等）统一归为“响铃未接通”
  return '响铃未接通';
}

const DEFAULT_CUSTOMER_TAG = '未打标客户';

// 获取任务列表（管理员视角）
export const getTasks = async (req: Request, res: Response) => {
  try {
    const { agent_id, status, page = 1, pageSize = 20 } = req.query;
    const pageNum = parseInt(page as string);
    const sizeNum = parseInt(pageSize as string);
    const offset = (pageNum - 1) * sizeNum;
    
    // 构建WHERE条件
    const whereConditions: string[] = [];
    const params: any[] = [];
    
    if (agent_id) {
      whereConditions.push(`t.assigned_to = $${params.length + 1}`);
      params.push(parseInt(agent_id as string));
    }
    if (status) {
      whereConditions.push(`t.status = $${params.length + 1}`);
      params.push(status);
    }
    
    const whereClause = whereConditions.length > 0 ? `WHERE ${whereConditions.join(' AND ')}` : '';
    
    // 获取总数
    const countResult = await query(`SELECT COUNT(*) as total FROM tasks t ${whereClause}`, params);
    const total = parseInt(countResult.rows[0].total);
    
    // 获取分页数据
    const queryParams = [...params, sizeNum, offset];
    const result = await query(
      `SELECT t.*, 
              u.username as agent_username, u.real_name as agent_real_name,
              COALESCE(tc_stats.total_customers, 0) as total_customers,
              COALESCE(tc_stats.completed_customers, 0) as completed_customers,
              COALESCE(tc_stats.called_customers, 0) as called_customers
       FROM tasks t
       LEFT JOIN users u ON t.assigned_to = u.id
       LEFT JOIN (
         SELECT task_id, 
                COUNT(*) as total_customers,
                SUM(CASE WHEN status != 'pending' THEN 1 ELSE 0 END) as completed_customers,
                SUM(CASE WHEN status != 'pending' THEN 1 ELSE 0 END) as called_customers
         FROM task_customers
         GROUP BY task_id
       ) tc_stats ON t.id = tc_stats.task_id
       ${whereClause}
       ORDER BY t.created_at DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      queryParams
    );
    
    // 格式化数据
    const data = result.rows.map((t: any) => ({
      id: t.id,
      title: t.title || '',
      description: t.description || '',
      status: t.status || 'pending',
      priority: t.priority || 'normal',
      assigned_to: t.assigned_to,
      assigned_agent: t.agent_username ? {
        id: t.assigned_to,
        username: t.agent_username,
        real_name: t.agent_real_name
      } : null,
      due_date: t.due_date || null,
      created_at: t.created_at,
      updated_at: t.updated_at,
      customer_count: parseInt(t.total_customers) || 0,
      completed_count: parseInt(t.completed_customers) || 0,
      called_count: parseInt(t.called_customers) || 0,
      progress: parseInt(t.total_customers) > 0 
        ? Math.round((parseInt(t.completed_customers) / parseInt(t.total_customers)) * 100) 
        : 0
    }));
    
    res.json({
      data,
      total,
      page: pageNum,
      page_size: sizeNum,
      total_pages: Math.ceil(total / sizeNum)
    });
  } catch (error) {
    console.error('获取任务列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 创建任务（支持关联多个客户）
export const createTask = async (req: any, res: Response) => {
  try {
    const { title, description, assigned_to, customer_ids, priority, due_date } = req.body;
    
    if (!title || !assigned_to) {
      return res.status(400).json({ error: '任务名称和分配客服不能为空' });
    }
    
    if (!customer_ids || !Array.isArray(customer_ids) || customer_ids.length === 0) {
      return res.status(400).json({ error: '请至少选择一个客户' });
    }
    
    // 创建任务
    await query(
      `INSERT INTO tasks (title, description, assigned_to, priority, status, due_date, created_by, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, datetime('now'), datetime('now'))`,
      [title, description || '', assigned_to, priority || 'normal', 'pending', due_date || null, req.user.id]
    );
    
    // 获取新任务ID
    const newTaskResult = await query('SELECT last_insert_rowid() as id');
    const taskId = newTaskResult.rows[0].id;
    
    // 批量插入任务-客户关联
    for (const customerId of customer_ids) {
      await query(
        `INSERT INTO task_customers (task_id, customer_id, status, created_at)
         VALUES ($1, $2, $3, datetime('now'))`,
        [taskId, customerId, 'pending']
      );
    }
    
    // 返回创建的任务
    const taskResult = await query('SELECT * FROM tasks WHERE id = $1', [taskId]);
    const task = taskResult.rows[0];
    
    res.status(201).json({
      ...task,
      customer_count: customer_ids.length
    });
  } catch (error) {
    console.error('创建任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 客服基于客户列表自建任务（自动分配给自己）
export const createTaskForSelf = async (req: any, res: Response) => {
  try {
    const { title, description, customer_ids, priority, due_date } = req.body;
    const assignedTo = req.user.id;

    if (!title?.trim()) {
      return res.status(400).json({ error: '任务名称不能为空' });
    }

    if (!customer_ids || !Array.isArray(customer_ids) || customer_ids.length === 0) {
      return res.status(400).json({ error: '请至少选择一个客户' });
    }

    const uniqueCustomerIds = Array.from(
      new Set(
        customer_ids
          .map((id: any) => Number(id))
          .filter((id: number) => Number.isInteger(id) && id > 0)
      )
    );

    if (uniqueCustomerIds.length === 0) {
      return res.status(400).json({ error: '客户列表无效' });
    }

    const placeholders = uniqueCustomerIds.map((_, index) => `$${index + 1}`).join(', ');
    const customerExistsResult = await query(
      `SELECT id FROM customers WHERE id IN (${placeholders})`,
      uniqueCustomerIds
    );

    if (customerExistsResult.rows.length !== uniqueCustomerIds.length) {
      return res.status(400).json({ error: '部分客户不存在，无法创建任务' });
    }

    await query(
      `INSERT INTO tasks (title, description, assigned_to, priority, status, due_date, created_by, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, datetime('now'), datetime('now'))`,
      [title.trim(), description?.trim() || '', assignedTo, priority || 'normal', 'pending', due_date || null, req.user.id]
    );

    const newTaskResult = await query('SELECT last_insert_rowid() as id');
    const taskId = newTaskResult.rows[0].id;

    for (const customerId of uniqueCustomerIds) {
      await query(
        `INSERT INTO task_customers (task_id, customer_id, status, created_at)
         VALUES ($1, $2, $3, datetime('now'))`,
        [taskId, customerId, 'pending']
      );
    }

    const taskResult = await query('SELECT * FROM tasks WHERE id = $1', [taskId]);
    const task = taskResult.rows[0];

    res.status(201).json({
      ...task,
      customer_count: uniqueCustomerIds.length
    });
  } catch (error) {
    console.error('客服自建任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 获取任务详情（包含客户列表和拨号情况）
export const getTaskById = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    // 获取任务基本信息
    const taskResult = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (taskResult.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    const task = taskResult.rows[0];
    
    // 获取分配客服信息
    const agentResult = await query('SELECT id, username, real_name, role, phone, email, status FROM users WHERE id = $1', [task.assigned_to]);
    const agent = agentResult.rows[0] || null;
    
    // 获取任务关联的客户列表
    const customersResult = await query(`
      SELECT tc.id as task_customer_id, tc.status as call_status, tc.call_result, tc.called_at,
             c.id, c.name, c.phone, c.email, c.company, COALESCE(NULLIF(TRIM(c.tag), ''), '${DEFAULT_CUSTOMER_TAG}') as tag, c.status as customer_status,
             ca.id as call_id, ca.call_duration, ca.is_connected, ca.created_at as call_time, ca.recording_url
      FROM task_customers tc
      LEFT JOIN customers c ON tc.customer_id = c.id
      LEFT JOIN calls ca ON tc.call_id = ca.id
      WHERE tc.task_id = $1
      ORDER BY tc.created_at ASC
    `, [id]);
    
    // 统计信息
    const totalCustomers = customersResult.rows.length;
    const completedCustomers = customersResult.rows.filter((c: any) => 
      c.call_status !== 'pending'
    ).length;
    const calledCustomers = customersResult.rows.filter((c: any) => 
      c.call_status !== 'pending'
    ).length;
    
    res.json({
      id: task.id,
      title: task.title,
      description: task.description,
      status: task.status,
      priority: task.priority,
      due_date: task.due_date,
      created_at: task.created_at,
      updated_at: task.updated_at,
      assigned_to: task.assigned_to,
      assigned_agent: agent ? {
        id: agent.id,
        username: agent.username,
        real_name: agent.real_name,
        phone: agent.phone,
        email: agent.email,
        status: agent.status
      } : null,
      // 统计信息
      customer_count: totalCustomers,
      completed_count: completedCustomers,
      called_count: calledCustomers,
      progress: totalCustomers > 0 ? Math.round((completedCustomers / totalCustomers) * 100) : 0,
      // 客户列表
      customers: customersResult.rows.map((c: any) => ({
        task_customer_id: c.task_customer_id,
        id: c.id,
        name: c.name,
        phone: c.phone,
        email: c.email,
        company: c.company,
        tag: c.tag,
        customer_status: c.customer_status,
        call_status: c.call_status || 'pending',
        call_result: normalizeCallResult(c.call_result),
        called_at: c.called_at,
        call_id: c.call_id,
        call_duration: c.call_duration,
        is_connected: c.is_connected === 1,
        call_time: c.call_time,
        recording_url: c.recording_url
      }))
    });
  } catch (error) {
    console.error('获取任务详情错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 更新任务
export const updateTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { title, description, priority, status, due_date, assigned_to } = req.body;
    
    const result = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    // 构建更新SQL
    const updates: string[] = [];
    const params: any[] = [];
    let paramIndex = 1;
    
    if (title !== undefined) {
      updates.push(`title = $${paramIndex++}`);
      params.push(title);
    }
    if (description !== undefined) {
      updates.push(`description = $${paramIndex++}`);
      params.push(description);
    }
    if (priority !== undefined) {
      updates.push(`priority = $${paramIndex++}`);
      params.push(priority);
    }
    if (status !== undefined) {
      updates.push(`status = $${paramIndex++}`);
      params.push(status);
    }
    if (due_date !== undefined) {
      updates.push(`due_date = $${paramIndex++}`);
      params.push(due_date);
    }
    if (assigned_to !== undefined) {
      updates.push(`assigned_to = $${paramIndex++}`);
      params.push(assigned_to);
    }
    
    if (updates.length > 0) {
      updates.push(`updated_at = datetime('now')`);
      params.push(id);
      await query(
        `UPDATE tasks SET ${updates.join(', ')} WHERE id = $${paramIndex}`,
        params
      );
    }
    
    // 返回更新后的任务
    const updatedResult = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    res.json(updatedResult.rows[0]);
  } catch (error) {
    console.error('更新任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 删除任务
export const deleteTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    
    const result = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    // 删除任务-客户关联
    await query('DELETE FROM task_customers WHERE task_id = $1', [id]);
    
    // 删除任务
    await query('DELETE FROM tasks WHERE id = $1', [id]);
    
    res.json({ message: '任务删除成功' });
  } catch (error) {
    console.error('删除任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 获取我的任务（客服视角）
export const getMyTasks = async (req: any, res: Response) => {
  try {
    const agentId = req.user.id;
    const result = await query('SELECT * FROM tasks WHERE assigned_to = $1 ORDER BY created_at DESC', [agentId]);
    
    // 获取用户信息
    const users = await query('SELECT id, username, real_name, role, phone, email FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u]));
    
    // 获取每个任务的客户统计
    const taskCustomersResult = await query(`
      SELECT task_id, 
             COUNT(*) as total_customers,
             SUM(CASE WHEN status != 'pending' THEN 1 ELSE 0 END) as completed_customers,
             SUM(CASE WHEN status != 'pending' THEN 1 ELSE 0 END) as called_customers
      FROM task_customers
      WHERE task_id IN (SELECT id FROM tasks WHERE assigned_to = $1)
      GROUP BY task_id
    `, [agentId]);
    const taskStatsMap = new Map(taskCustomersResult.rows.map((r: any) => [r.task_id, r]));
    
    const data = result.rows.map((t: any) => {
      const agent = userMap.get(t.assigned_to);
      const stats = taskStatsMap.get(t.id) || { total_customers: 0, completed_customers: 0, called_customers: 0 };
      
      return {
        id: t.id,
        title: t.title || '',
        description: t.description || '',
        status: t.status || 'pending',
        priority: t.priority || 'normal',
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
        customer_count: stats.total_customers || 0,
        completed_count: stats.completed_customers || 0,
        called_count: stats.called_customers || 0,
        progress: stats.total_customers > 0 
          ? Math.round((stats.completed_customers / stats.total_customers) * 100) 
          : 0
      };
    });
    
    res.json(data);
  } catch (error) {
    console.error('获取我的任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 添加客户到任务
export const addCustomersToTask = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { customer_ids } = req.body;
    
    if (!customer_ids || !Array.isArray(customer_ids) || customer_ids.length === 0) {
      return res.status(400).json({ error: '请提供客户ID列表' });
    }
    
    // 检查任务是否存在
    const taskResult = await query('SELECT * FROM tasks WHERE id = $1', [id]);
    if (taskResult.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    // 添加客户（忽略已存在的）
    let addedCount = 0;
    for (const customerId of customer_ids) {
      try {
        await query(
          `INSERT OR IGNORE INTO task_customers (task_id, customer_id, status, created_at)
           VALUES ($1, $2, $3, datetime('now'))`,
          [id, customerId, 'pending']
        );
        addedCount++;
      } catch (e) {
        // 忽略重复
      }
    }
    
    res.json({ message: `成功添加 ${addedCount} 个客户到任务` });
  } catch (error) {
    console.error('添加客户到任务错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 从任务移除客户
export const removeCustomerFromTask = async (req: Request, res: Response) => {
  try {
    const { id, customerId } = req.params;
    
    await query('DELETE FROM task_customers WHERE task_id = $1 AND customer_id = $2', [id, customerId]);
    
    res.json({ message: '客户已从任务中移除' });
  } catch (error) {
    console.error('移除客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 更新任务中客户的拨打状态
export const updateTaskCustomerStatus = async (req: Request, res: Response) => {
  try {
    const { id, customerId } = req.params;
    // 兼容：老客户端/服务端使用 call_result，新客户端可能发送 callResult
    const { status, call_result, callResult, call_id, callId, call_duration, callDuration } = req.body as any;

    const normalizedCallResult = normalizeCallResult(call_result ?? callResult);
    const normalizedCallId = call_id ?? callId ?? null;
    const rawDuration = call_duration ?? callDuration;
    const normalizedCallDuration = (rawDuration === undefined || rawDuration === null || rawDuration === '')
      ? null
      : Number(rawDuration);
    
    // 更新 task_customers 表
    await query(
      `UPDATE task_customers 
       SET status = $1, call_result = $2, call_id = $3, called_at = datetime('now')
       WHERE task_id = $4 AND customer_id = $5`,
      [status, normalizedCallResult, normalizedCallId, id, customerId]
    );

    // 如果提供了通话时长，且关联了 call 记录，则同步更新 calls 表的通话时长
    if (
      normalizedCallId !== null &&
      normalizedCallId !== undefined &&
      normalizedCallDuration !== null &&
      !Number.isNaN(normalizedCallDuration) &&
      Number.isFinite(normalizedCallDuration) &&
      normalizedCallDuration >= 0
    ) {
      await query(
        `UPDATE calls
         SET call_duration = $1,
             updated_at = datetime('now')
         WHERE id = $2`,
        [Math.floor(normalizedCallDuration), normalizedCallId]
      );
    }
    
    // 同时更新 customers 表的状态，保持数据一致性
    // 如果任务客户状态为 connected 或 completed，则更新客户状态为 contacted
    if (status === 'connected' || status === 'completed') {
      await query(
        `UPDATE customers 
         SET status = 'contacted', updated_at = datetime('now')
         WHERE id = $1`,
        [customerId]
      );
    } else if (status === 'called' || status === 'failed') {
      // 如果已拨打但未接通，标记为 contacted（已触达）
      await query(
        `UPDATE customers 
         SET status = 'contacted', updated_at = datetime('now')
         WHERE id = $1 AND status = 'pending'`,
        [customerId]
      );
    }
    
    res.json({ message: '状态更新成功' });
  } catch (error) {
    console.error('更新任务客户状态错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 更新任务中客户的信息（编辑电话号码等）
export const updateTaskCustomerInfo = async (req: Request, res: Response) => {
  try {
    const { id, customerId } = req.params;
    const { name, phone, company } = req.body;
    
    // 验证：如果要更新电话号码，则不能为空
    if (phone !== undefined && !phone) {
      return res.status(400).json({ error: '电话号码不能为空' });
    }
    
    // 更新 customers 表
    const updateFields: string[] = [];
    const params: any[] = [];
    
    if (name !== undefined) {
      updateFields.push(`name = $${params.length + 1}`);
      params.push(name);
    }
    if (phone !== undefined) {
      updateFields.push(`phone = $${params.length + 1}`);
      params.push(phone);
    }
    if (company !== undefined) {
      updateFields.push(`company = $${params.length + 1}`);
      params.push(company);
    }
    
    if (updateFields.length > 0) {
      updateFields.push(`updated_at = datetime('now')`);
      params.push(customerId);
      
      await query(
        `UPDATE customers SET ${updateFields.join(', ')} WHERE id = $${params.length}`,
        params
      );
    }
    
    res.json({ message: '客户信息更新成功' });
  } catch (error) {
    console.error('更新任务客户信息错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 从任务中移除客户
export const removeTaskCustomer = async (req: Request, res: Response) => {
  try {
    const { id, customerId } = req.params;
    
    // 从 task_customers 表中删除关联
    await query(
      'DELETE FROM task_customers WHERE task_id = $1 AND customer_id = $2',
      [id, customerId]
    );
    
    res.json({ message: '客户已从任务中移除' });
  } catch (error) {
    console.error('移除任务客户错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 分页获取任务客户列表
export const getTaskCustomers = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { page = 1, pageSize = 100, status } = req.query;
    const pageNum = parseInt(page as string);
    const sizeNum = parseInt(pageSize as string);
    const offset = (pageNum - 1) * sizeNum;
    
    // 检查任务是否存在
    const taskResult = await query('SELECT id FROM tasks WHERE id = $1', [id]);
    if (taskResult.rows.length === 0) {
      return res.status(404).json({ error: '任务不存在' });
    }
    
    // 构建WHERE条件
    const whereConditions: string[] = ['tc.task_id = $1'];
    const params: any[] = [id];
    
    if (status) {
      whereConditions.push(`tc.status = $${params.length + 1}`);
      params.push(status);
    }
    
    const whereClause = whereConditions.join(' AND ');
    
    // 获取总数
    const countResult = await query(
      `SELECT COUNT(*) as total FROM task_customers tc WHERE ${whereClause}`,
      params
    );
    const total = parseInt(countResult.rows[0].total);
    
    // 获取分页数据
    const customersResult = await query(`
      SELECT tc.id as task_customer_id, tc.status as call_status, tc.call_result, tc.called_at,
             c.id, c.name, c.phone, c.email, c.company, COALESCE(NULLIF(TRIM(c.tag), ''), '${DEFAULT_CUSTOMER_TAG}') as tag, c.status as customer_status,
             ca.id as call_id, ca.call_duration, ca.is_connected, ca.created_at as call_time, ca.recording_url
      FROM task_customers tc
      LEFT JOIN customers c ON tc.customer_id = c.id
      LEFT JOIN calls ca ON tc.call_id = ca.id
      WHERE ${whereClause}
      ORDER BY tc.created_at ASC
      LIMIT $${params.length + 1} OFFSET $${params.length + 2}
    `, [...params, sizeNum, offset]);
    
    res.json({
      data: customersResult.rows.map((c: any) => ({
        task_customer_id: c.task_customer_id,
        id: c.id,
        name: c.name,
        phone: c.phone,
        email: c.email,
        company: c.company,
        tag: c.tag,
        customer_status: c.customer_status,
        call_status: c.call_status || 'pending',
        call_result: normalizeCallResult(c.call_result),
        called_at: c.called_at,
        call_id: c.call_id,
        call_duration: c.call_duration,
        is_connected: c.is_connected === 1,
        call_time: c.call_time,
        recording_url: c.recording_url
      })),
      total,
      page: pageNum,
      page_size: sizeNum,
      total_pages: Math.ceil(total / sizeNum)
    });
  } catch (error) {
    console.error('获取任务客户列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
