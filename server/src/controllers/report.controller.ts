import { Request, Response } from 'express';
import { query } from '../config/database';

// 导出客户数据
export const exportCustomers = async (req: any, res: Response) => {
  try {
    const { format = 'csv' } = req.query;
    
    const result = await query(`
      SELECT c.phone, c.name, c.remark, c.source, c.created_at,
        u.real_name as imported_by_name
      FROM customers c
      LEFT JOIN users u ON c.imported_by = u.id
      ORDER BY c.created_at DESC
    `);
    
    const customers = result.rows;
    
    if (format === 'csv') {
      const csvHeader = '电话号码,客户姓名,备注,来源,导入人,导入时间\n';
      const csvContent = customers.map(c => 
        `"${c.phone}","${c.name || ''}","${c.remark || ''}","${c.source || ''}","${c.imported_by_name || ''}","${c.created_at}"`
      ).join('\n');
      
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', 'attachment; filename=customers.csv');
      res.send(csvHeader + csvContent);
    } else {
      res.json(customers);
    }
  } catch (error) {
    console.error('导出客户错误:', error);
    res.status(500).json({ error: '导出失败' });
  }
};

// 获取数据看板
export const getDashboard = async (req: Request, res: Response) => {
  try {
    // 今日数据
    const todayResult = await query(`
      SELECT 
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COUNT(DISTINCT agent_id) as active_agents
      FROM call_records
      WHERE DATE(created_at) = CURRENT_DATE
    `);
    
    // 本周数据
    const weekResult = await query(`
      SELECT 
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COALESCE(SUM(call_duration), 0) as total_duration
      FROM call_records
      WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
    `);
    
    // 客户统计
    const customerResult = await query(`
      SELECT 
        COUNT(*) as total_customers,
        COUNT(CASE WHEN created_at >= CURRENT_DATE - INTERVAL '7 days' THEN 1 END) as new_customers
      FROM customers
    `);
    
    // 任务完成率
    const taskResult = await query(`
      SELECT 
        COUNT(*) as total_tasks,
        COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_tasks
      FROM tasks
      WHERE status != 'cancelled'
    `);
    
    res.json({
      today: todayResult.rows[0],
      thisWeek: weekResult.rows[0],
      customers: customerResult.rows[0],
      tasks: taskResult.rows[0]
    });
  } catch (error) {
    console.error('获取看板数据错误:', error);
    res.status(500).json({ error: '获取数据失败' });
  }
};

// 获取通话趋势
export const getCallTrend = async (req: Request, res: Response) => {
  try {
    const { days = 7 } = req.query;
    
    const result = await query(`
      SELECT 
        DATE(created_at) as date,
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COALESCE(SUM(call_duration), 0) as total_duration
      FROM call_records
      WHERE created_at >= CURRENT_DATE - INTERVAL '${days} days'
      GROUP BY DATE(created_at)
      ORDER BY date ASC
    `);
    
    res.json(result.rows);
  } catch (error) {
    console.error('获取趋势错误:', error);
    res.status(500).json({ error: '获取趋势失败' });
  }
};
