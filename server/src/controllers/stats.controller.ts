import { Request, Response } from 'express';
import { query } from '../config/database';

export const getStats = async (req: Request, res: Response) => {
  try {
    const calls = await query('SELECT * FROM calls');
    const users = await query('SELECT * FROM users');
    const customers = await query('SELECT * FROM customers');
    
    const totalCalls = calls.rows.length;
    const connectedCalls = calls.rows.filter((c: any) => c.is_connected).length;
    const failedCalls = totalCalls - connectedCalls;
    const connectionRate = totalCalls > 0 ? connectedCalls / totalCalls : 0;
    
    // 计算总时长
    const totalDuration = calls.rows.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
    const avgDuration = totalCalls > 0 ? Math.round(totalDuration / totalCalls) : 0;
    
    // 客服排名统计
    const agentRanking = users.rows
      .filter((u: any) => u.role === 'agent')
      .map((u: any) => {
        const agentCalls = calls.rows.filter((c: any) => c.agent_id === u.id);
        const agentConnected = agentCalls.filter((c: any) => c.is_connected).length;
        const agentDuration = agentCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
        const rate = agentCalls.length > 0 ? agentConnected / agentCalls.length : 0;
        
        return {
          agent_id: u.id,
          agent_name: u.real_name,
          total_calls: agentCalls.length,
          successful_calls: agentConnected,
          connected_calls: agentConnected, // 兼容 Web 端
          total_duration: agentDuration,
          connection_rate: rate, // 兼容 Web 端
          success_rate: rate
        };
      });
    
    // 今日统计
    const today = new Date().toISOString().split('T')[0];
    const todayCalls = calls.rows.filter((c: any) => 
      c.created_at && c.created_at.toString().startsWith(today)
    );
    
    res.json({
      overview: {
        total_calls: totalCalls,
        connected_calls: connectedCalls,
        failed_calls: failedCalls,
        total_duration: totalDuration,
        avg_duration: avgDuration,
        connection_rate: connectionRate
      },
      // 兼容 Android 端扁平化格式
      total_calls: totalCalls,
      total_duration: totalDuration,
      successful_calls: connectedCalls,
      failed_calls: failedCalls,
      pending_customers: customers.rows.filter((c: any) => c.status === 'pending').length,
      completed_customers: customers.rows.filter((c: any) => c.status === 'completed').length,
      connection_rate: connectionRate,
      agent_ranking: agentRanking,
      today: {
        total_calls: todayCalls.length,
        connected_calls: todayCalls.filter((c: any) => c.is_connected).length,
        total_duration: todayCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0)
      }
    });
  } catch (error) {
    console.error('获取统计数据错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getMyStats = async (req: any, res: Response) => {
  try {
    const agentId = req.user.id;
    const calls = await query('SELECT * FROM calls WHERE agent_id = $1', [agentId]);
    const customers = await query('SELECT * FROM customers WHERE assigned_to = $1', [agentId]);
    
    const totalCalls = calls.rows.length;
    const connectedCalls = calls.rows.filter((c: any) => c.is_connected).length;
    const failedCalls = totalCalls - connectedCalls;
    const totalDuration = calls.rows.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
    const avgDuration = totalCalls > 0 ? Math.round(totalDuration / totalCalls) : 0;
    const connectionRate = totalCalls > 0 ? connectedCalls / totalCalls : 0;
    
    // 今日统计
    const today = new Date().toISOString().split('T')[0];
    const todayCalls = calls.rows.filter((c: any) => 
      c.created_at && c.created_at.toString().startsWith(today)
    );
    
    // 本周统计
    const weekAgo = new Date();
    weekAgo.setDate(weekAgo.getDate() - 7);
    const weekCalls = calls.rows.filter((c: any) => 
      c.created_at && new Date(c.created_at) >= weekAgo
    );
    
    // 本月统计
    const monthAgo = new Date();
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    const monthCalls = calls.rows.filter((c: any) => 
      c.created_at && new Date(c.created_at) >= monthAgo
    );
    
    res.json({
      // 兼容 Web 端
      total_calls: totalCalls,
      total_duration: totalDuration,
      connected_calls: connectedCalls,
      failed_calls: failedCalls,
      avg_duration: avgDuration,
      connection_rate: connectionRate,
      // 兼容 Android 端
      successful_calls: connectedCalls,
      pending_customers: customers.rows.filter((c: any) => c.status === 'pending').length,
      completed_customers: customers.rows.filter((c: any) => c.status === 'completed').length,
      // 扩展统计
      today_calls: todayCalls.length,
      today_duration: todayCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0),
      today_successful: todayCalls.filter((c: any) => c.is_connected).length,
      week_calls: weekCalls.length,
      week_duration: weekCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0),
      month_calls: monthCalls.length,
      month_duration: monthCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0)
    });
  } catch (error) {
    console.error('获取个人统计错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getDashboardStats = async (req: Request, res: Response) => {
  try {
    const customers = await query('SELECT * FROM customers');
    const calls = await query('SELECT * FROM calls');
    const users = await query('SELECT * FROM users WHERE role = $1', ['agent']);
    
    // 今日数据
    const today = new Date().toISOString().split('T')[0];
    const todayCalls = calls.rows.filter((c: any) => 
      c.created_at && c.created_at.toString().startsWith(today)
    );
    const todayConnected = todayCalls.filter((c: any) => c.is_connected).length;
    const todayDuration = todayCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
    const todaySuccessRate = todayCalls.length > 0 ? todayConnected / todayCalls.length : 0;
    
    // 活跃客服
    const agentIds = [...new Set(calls.rows.map((c: any) => c.agent_id))];
    
    // 客服排行榜
    const agentRanking = users.rows
      .filter((u: any) => u.role === 'agent')
      .map((u: any) => {
        const agentCalls = calls.rows.filter((c: any) => c.agent_id === u.id);
        const agentConnected = agentCalls.filter((c: any) => c.is_connected).length;
        const agentDuration = agentCalls.reduce((sum: number, c: any) => sum + (c.call_duration || 0), 0);
        const rate = agentCalls.length > 0 ? agentConnected / agentCalls.length : 0;
        
        return {
          agent_id: u.id,
          agent_name: u.real_name,
          total_calls: agentCalls.length,
          successful_calls: agentConnected,
          connected_calls: agentConnected,
          total_duration: agentDuration,
          success_rate: rate,
          connection_rate: rate
        };
      })
      .sort((a: any, b: any) => b.total_calls - a.total_calls)
      .slice(0, 10);
    
    // 最近通话
    const recentCalls = calls.rows
      .sort((a: any, b: any) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())
      .slice(0, 10)
      .map((c: any) => ({
        id: c.id,
        customer_id: c.customer_id,
        agent_id: c.agent_id,
        phone: c.customer_phone || c.phone || '',
        status: c.status,
        duration: c.call_duration || 0,
        created_at: c.created_at
      }));
    
    // 生成7天趋势数据（Web 端需要）
    const trend = [];
    for (let i = 6; i >= 0; i--) {
      const date = new Date();
      date.setDate(date.getDate() - i);
      const dateStr = date.toISOString().split('T')[0];
      const dayCalls = calls.rows.filter((c: any) => 
        c.created_at && c.created_at.toString().startsWith(dateStr)
      );
      trend.push({
        date: dateStr,
        total_calls: dayCalls.length,
        connected_calls: dayCalls.filter((c: any) => c.is_connected).length
      });
    }
    
    res.json({
      // Web 端需要的字段
      total_customers: customers.rows.length,
      total_calls: calls.rows.length,
      connection_rate: calls.rows.length > 0 
        ? calls.rows.filter((c: any) => c.is_connected).length / calls.rows.length 
        : 0,
      active_agents: agentIds.length,
      trend: trend,
      // Android 端需要的字段
      today_calls: todayCalls.length,
      today_duration: todayDuration,
      today_success_rate: todaySuccessRate,
      pending_customers: customers.rows.filter((c: any) => c.status === 'pending').length,
      total_agents: users.rows.length,
      agent_ranking: agentRanking,
      recent_calls: recentCalls
    });
  } catch (error) {
    console.error('获取仪表板统计错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
