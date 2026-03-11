import { Request, Response } from 'express';
import { query } from '../config/database';

export const getStats = async (req: Request, res: Response) => {
  try {
    const { start_date, end_date, agent_id } = req.query;
    
    let dateFilter = '';
    const params: any[] = [];
    
    if (start_date && end_date) {
      dateFilter = `WHERE created_at BETWEEN $1 AND $2`;
      params.push(start_date, end_date);
    }
    
    // 总体统计
    const totalStats = await query(`
      SELECT 
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COUNT(CASE WHEN is_connected = false THEN 1 END) as failed_calls,
        COALESCE(SUM(call_duration), 0) as total_duration,
        COALESCE(AVG(call_duration), 0) as avg_duration
      FROM call_records
      ${dateFilter}
    `, params);
    
    // 客服排名统计
    let agentSql = `
      SELECT 
        u.id as agent_id,
        u.real_name as agent_name,
        COUNT(cr.id) as total_calls,
        COUNT(CASE WHEN cr.is_connected = true THEN 1 END) as connected_calls,
        COALESCE(SUM(cr.call_duration), 0) as total_duration,
        CASE 
          WHEN COUNT(cr.id) > 0 
          THEN ROUND(COUNT(CASE WHEN cr.is_connected = true THEN 1 END) * 100.0 / COUNT(cr.id), 2)
          ELSE 0 
        END as connection_rate
      FROM users u
      LEFT JOIN call_records cr ON u.id = cr.agent_id
      WHERE u.role = 'agent' AND u.status = 'active'
    `;
    
    if (start_date && end_date) {
      agentSql += ` AND (cr.created_at BETWEEN $1 AND $2 OR cr.id IS NULL)`;
    }
    
    if (agent_id) {
      agentSql += ` AND u.id = $${params.length + 1}`;
      params.push(agent_id);
    }
    
    agentSql += ` GROUP BY u.id, u.real_name ORDER BY connected_calls DESC`;
    
    const agentStats = await query(agentSql, params);
    
    // 今日统计
    const todayStats = await query(`
      SELECT 
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COALESCE(SUM(call_duration), 0) as total_duration
      FROM call_records
      WHERE DATE(created_at) = CURRENT_DATE
    `);
    
    res.json({
      overview: totalStats.rows[0],
      agent_ranking: agentStats.rows,
      today: todayStats.rows[0]
    });
  } catch (error) {
    console.error('获取统计数据错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getMyStats = async (req: any, res: Response) => {
  try {
    const { start_date, end_date } = req.query;
    const agentId = req.user.id;
    
    let dateFilter = '';
    const params: any[] = [agentId];
    
    if (start_date && end_date) {
      dateFilter = `AND created_at BETWEEN $2 AND $3`;
      params.push(start_date, end_date);
    }
    
    const result = await query(`
      SELECT 
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls,
        COUNT(CASE WHEN is_connected = false THEN 1 END) as failed_calls,
        COALESCE(SUM(call_duration), 0) as total_duration,
        COALESCE(AVG(call_duration), 0) as avg_duration,
        CASE 
          WHEN COUNT(*) > 0 
          THEN ROUND(COUNT(CASE WHEN is_connected = true THEN 1 END) * 100.0 / COUNT(*), 2)
          ELSE 0 
        END as connection_rate
      FROM call_records
      WHERE agent_id = $1 ${dateFilter}
    `, params);
    
    // 获取排名
    const rankingResult = await query(`
      SELECT rank FROM (
        SELECT 
          agent_id,
          ROW_NUMBER() OVER (ORDER BY COUNT(CASE WHEN is_connected = true THEN 1 END) DESC) as rank
        FROM call_records
        WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
        GROUP BY agent_id
      ) rankings
      WHERE agent_id = $1
    `, [agentId]);
    
    res.json({
      ...result.rows[0],
      ranking: rankingResult.rows[0]?.rank || 0
    });
  } catch (error) {
    console.error('获取个人统计错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getDashboardStats = async (req: Request, res: Response) => {
  try {
    // 总客户数
    const customerCount = await query('SELECT COUNT(*) as count FROM customers');
    
    // 总通话数
    const callCount = await query('SELECT COUNT(*) as count FROM call_records');
    
    // 接通率
    const connectionRate = await query(`
      SELECT 
        CASE 
          WHEN COUNT(*) > 0 
          THEN ROUND(COUNT(CASE WHEN is_connected = true THEN 1 END) * 100.0 / COUNT(*), 2)
          ELSE 0 
        END as rate
      FROM call_records
    `);
    
    // 活跃客服数
    const activeAgents = await query(`
      SELECT COUNT(DISTINCT agent_id) as count 
      FROM call_records 
      WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
    `);
    
    // 最近7天通话趋势
    const trend = await query(`
      SELECT 
        DATE(created_at) as date,
        COUNT(*) as total_calls,
        COUNT(CASE WHEN is_connected = true THEN 1 END) as connected_calls
      FROM call_records
      WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
      GROUP BY DATE(created_at)
      ORDER BY date DESC
    `);
    
    res.json({
      total_customers: parseInt(customerCount.rows[0].count),
      total_calls: parseInt(callCount.rows[0].count),
      connection_rate: parseFloat(connectionRate.rows[0].rate),
      active_agents: parseInt(activeAgents.rows[0].count),
      trend: trend.rows
    });
  } catch (error) {
    console.error('获取仪表板统计错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
