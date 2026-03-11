import { Request, Response } from 'express';
import { voicemailService } from '../services/voicemail.service';
import { smsService } from '../services/sms.service';
import { Pool } from 'pg';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

export class CommunicationController {
  /**
   * 获取通信记录
   */
  async getRecords(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.id;
      const userRole = (req as any).user?.role;
      
      // 管理员可以查看所有记录，客服只能查看自己的
      const agentId = userRole === 'admin' ? undefined : userId;

      // 并行获取各种记录
      const [voicemail, sms, unanswered] = await Promise.all([
        voicemailService.getVoicemailRecords(agentId, 50),
        smsService.getSmsRecords(agentId, 50),
        voicemailService.getUnansweredRecords(agentId, 50),
      ]);

      // 获取统计
      const statsResult = await pool.query(
        `SELECT 
          (SELECT COUNT(*) FROM voicemail_records ${agentId ? 'WHERE agent_id = $1' : ''}) as voicemail_count,
          (SELECT COUNT(*) FROM sms_records ${agentId ? 'WHERE agent_id = $1' : ''}) as sms_count,
          (SELECT COUNT(*) FROM unanswered_records ${agentId ? 'WHERE agent_id = $1' : ''}) as unanswered_count`,
        agentId ? [agentId] : []
      );

      res.json({
        success: true,
        data: {
          voicemail,
          sms,
          unanswered,
          stats: {
            voicemailCount: parseInt(statsResult.rows[0].voicemail_count),
            smsCount: parseInt(statsResult.rows[0].sms_count),
            unansweredCount: parseInt(statsResult.rows[0].unanswered_count),
          }
        }
      });
    } catch (error) {
      console.error('获取通信记录失败:', error);
      res.status(500).json({
        success: false,
        message: '获取通信记录失败',
        error: (error as Error).message
      });
    }
  }

  /**
   * 获取通信统计
   */
  async getStats(req: Request, res: Response) {
    try {
      const userId = (req as any).user?.id;
      const userRole = (req as any).user?.role;
      
      const agentId = userRole === 'admin' ? undefined : userId;

      // 获取今日统计
      const todayResult = await pool.query(
        `SELECT 
          (SELECT COUNT(*) FROM voicemail_records 
           WHERE created_at >= CURRENT_DATE ${agentId ? 'AND agent_id = $1' : ''}) as today_voicemail,
          (SELECT COUNT(*) FROM sms_records 
           WHERE created_at >= CURRENT_DATE ${agentId ? 'AND agent_id = $1' : ''}) as today_sms,
          (SELECT COUNT(*) FROM unanswered_records 
           WHERE created_at >= CURRENT_DATE ${agentId ? 'AND agent_id = $1' : ''}) as today_unanswered`,
        agentId ? [agentId] : []
      );

      // 获取本周统计
      const weekResult = await pool.query(
        `SELECT 
          (SELECT COUNT(*) FROM voicemail_records 
           WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as week_voicemail,
          (SELECT COUNT(*) FROM sms_records 
           WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as week_sms,
          (SELECT COUNT(*) FROM unanswered_records 
           WHERE created_at >= DATE_TRUNC('week', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as week_unanswered`,
        agentId ? [agentId] : []
      );

      // 获取本月统计
      const monthResult = await pool.query(
        `SELECT 
          (SELECT COUNT(*) FROM voicemail_records 
           WHERE created_at >= DATE_TRUNC('month', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as month_voicemail,
          (SELECT COUNT(*) FROM sms_records 
           WHERE created_at >= DATE_TRUNC('month', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as month_sms,
          (SELECT COUNT(*) FROM unanswered_records 
           WHERE created_at >= DATE_TRUNC('month', CURRENT_DATE) ${agentId ? 'AND agent_id = $1' : ''}) as month_unanswered`,
        agentId ? [agentId] : []
      );

      res.json({
        success: true,
        data: {
          today: {
            voicemail: parseInt(todayResult.rows[0].today_voicemail),
            sms: parseInt(todayResult.rows[0].today_sms),
            unanswered: parseInt(todayResult.rows[0].today_unanswered),
          },
          thisWeek: {
            voicemail: parseInt(weekResult.rows[0].week_voicemail),
            sms: parseInt(weekResult.rows[0].week_sms),
            unanswered: parseInt(weekResult.rows[0].week_unanswered),
          },
          thisMonth: {
            voicemail: parseInt(monthResult.rows[0].month_voicemail),
            sms: parseInt(monthResult.rows[0].month_sms),
            unanswered: parseInt(monthResult.rows[0].month_unanswered),
          }
        }
      });
    } catch (error) {
      console.error('获取通信统计失败:', error);
      res.status(500).json({
        success: false,
        message: '获取通信统计失败',
        error: (error as Error).message
      });
    }
  }
}

export const communicationController = new CommunicationController();
