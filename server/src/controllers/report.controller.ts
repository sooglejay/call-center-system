import { Request, Response } from 'express';
import { query } from '../config/database';

// 导出客户数据
export const exportCustomers = async (req: any, res: Response) => {
  try {
    const { format = 'csv' } = req.query;
    
    const customers = await query('SELECT * FROM customers ORDER BY created_at DESC');
    const users = await query('SELECT id, real_name FROM users');
    const userMap = new Map(users.rows.map((u: any) => [u.id, u.real_name]));
    
    const data = customers.rows.map((c: any) => ({
      ...c,
      imported_by_name: userMap.get(c.imported_by) || ''
    }));
    
    if (format === 'csv') {
      const csvHeader = '电话号码,客户姓名,备注,来源,导入人,导入时间\n';
      const csvContent = data.map(c => 
        `"${c.phone}","${c.name || ''}","${c.remark || c.notes || ''}","${c.source || ''}","${c.imported_by_name || ''}","${c.created_at}"`
      ).join('\n');
      
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', 'attachment; filename=customers.csv');
      res.send(csvHeader + csvContent);
    } else {
      res.json(data);
    }
  } catch (error) {
    console.error('导出客户错误:', error);
    res.status(500).json({ error: '导出失败' });
  }
};

// 获取数据看板
export const getDashboard = async (req: Request, res: Response) => {
  try {
    const calls = await query('SELECT * FROM calls');
    const customers = await query('SELECT * FROM customers');
    const tasks = await query('SELECT * FROM tasks');
    const users = await query('SELECT * FROM users');
    
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    
    // 今日数据
    const todayCalls = calls.rows.filter((c: any) => new Date(c.created_at) >= todayStart);
    const todayTotalCalls = todayCalls.length;
    const todayConnectedCalls = todayCalls.filter((c: any) => c.is_connected).length;
    const todayActiveAgents = new Set(todayCalls.map((c: any) => c.agent_id)).size;
    
    // 本周数据
    const weekCalls = calls.rows.filter((c: any) => new Date(c.created_at) >= weekAgo);
    const weekTotalCalls = weekCalls.length;
    const weekConnectedCalls = weekCalls.filter((c: any) => c.is_connected).length;
    const weekTotalDuration = weekCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
    
    // 客户统计
    const totalCustomers = customers.rows.length;
    const newCustomers = customers.rows.filter((c: any) => new Date(c.created_at) >= weekAgo).length;
    
    // 任务完成率
    const activeTasks = tasks.rows.filter((t: any) => t.status !== 'cancelled');
    const completedTasks = activeTasks.filter((t: any) => t.status === 'completed').length;
    
    res.json({
      today: {
        total_calls: todayTotalCalls,
        connected_calls: todayConnectedCalls,
        active_agents: todayActiveAgents
      },
      thisWeek: {
        total_calls: weekTotalCalls,
        connected_calls: weekConnectedCalls,
        total_duration: weekTotalDuration
      },
      customers: {
        total_customers: totalCustomers,
        new_customers: newCustomers
      },
      tasks: {
        total_tasks: activeTasks.length,
        completed_tasks: completedTasks
      }
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
    const daysNum = parseInt(days as string);
    
    const calls = await query('SELECT * FROM calls');
    const now = new Date();
    
    const trend = [];
    for (let i = daysNum - 1; i >= 0; i--) {
      const date = new Date(now.getTime() - i * 24 * 60 * 60 * 1000);
      const dateStr = date.toISOString().split('T')[0];
      const dayStart = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      const dayEnd = new Date(dayStart.getTime() + 24 * 60 * 60 * 1000);
      
      const dayCalls = calls.rows.filter((c: any) => {
        const callDate = new Date(c.created_at);
        return callDate >= dayStart && callDate < dayEnd;
      });
      
      trend.push({
        date: dateStr,
        total_calls: dayCalls.length,
        connected_calls: dayCalls.filter((c: any) => c.is_connected).length,
        total_duration: dayCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0)
      });
    }
    
    res.json(trend);
  } catch (error) {
    console.error('获取趋势错误:', error);
    res.status(500).json({ error: '获取趋势失败' });
  }
};
