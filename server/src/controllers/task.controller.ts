import { Request, Response } from 'express';
import { query } from '../config/database';

// 获取任务列表（管理员视角）
export const getTasks = async (req: Request, res: Response) => {
  try {
    const { agent_id, status, page = 1, pageSize = 20 } = req.query;
    const offset = (parseInt(page as string) - 1) * parseInt(pageSize as string);
    
    // 获取所有任务
    let result = await query('SELECT * FROM tasks ORDER BY created_at DESC');
    let tasks = result.rows;
    
    // 过滤
    if (agent_id) {
      tasks = tasks.filter((t: any) => t.assigned_to === parseInt(agent_id as string));
    }
    if (status) {
      tasks = tasks.filter((t: any) => t.status === status);
    }
    
    const total = tasks.length;
    
    // 获取用户信息
    const users = await query('SELECT id, username, real_name, role, phone, email, status, data_access_type FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u]));
    
    // 获取每个任务的客户统计
    const taskCustomersResult = await query(`
      SELECT task_id, 
             COUNT(*) as total_customers,
             SUM(CASE WHEN status = 'completed' OR status = 'connected' THEN 1 ELSE 0 END) as completed_customers,
             SUM(CASE WHEN status = 'called' THEN 1 ELSE 0 END) as called_customers
      FROM task_customers
      GROUP BY task_id
    `);
    const taskStatsMap = new Map(taskCustomersResult.rows.map((r: any) => [r.task_id, r]));
    
    // 分页并添加信息
    const data = tasks.slice(offset, offset + parseInt(pageSize as string)).map((t: any) => {
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
        // 新增统计字段
        customer_count: stats.total_customers || 0,
        completed_count: stats.completed_customers || 0,
        called_count: stats.called_customers || 0,
        progress: stats.total_customers > 0 
          ? Math.round((stats.completed_customers / stats.total_customers) * 100) 
          : 0
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
             c.id, c.name, c.phone, c.email, c.company, c.status as customer_status,
             ca.id as call_id, ca.call_duration, ca.is_connected, ca.created_at as call_time
      FROM task_customers tc
      LEFT JOIN customers c ON tc.customer_id = c.id
      LEFT JOIN calls ca ON tc.call_id = ca.id
      WHERE tc.task_id = $1
      ORDER BY tc.created_at ASC
    `, [id]);
    
    // 统计信息
    const totalCustomers = customersResult.rows.length;
    const completedCustomers = customersResult.rows.filter((c: any) => 
      c.call_status === 'completed' || c.call_status === 'connected'
    ).length;
    const calledCustomers = customersResult.rows.filter((c: any) => 
      c.call_status === 'called' || c.call_status === 'completed' || c.call_status === 'connected'
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
        customer_status: c.customer_status,
        call_status: c.call_status || 'pending',
        call_result: c.call_result,
        called_at: c.called_at,
        call_id: c.call_id,
        call_duration: c.call_duration,
        is_connected: c.is_connected === 1,
        call_time: c.call_time
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
             SUM(CASE WHEN status = 'completed' OR status = 'connected' THEN 1 ELSE 0 END) as completed_customers,
             SUM(CASE WHEN status = 'called' THEN 1 ELSE 0 END) as called_customers
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
    const { status, call_result, call_id } = req.body;
    
    // 更新 task_customers 表
    await query(
      `UPDATE task_customers 
       SET status = $1, call_result = $2, call_id = $3, called_at = datetime('now')
       WHERE task_id = $4 AND customer_id = $5`,
      [status, call_result || null, call_id || null, id, customerId]
    );
    
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
