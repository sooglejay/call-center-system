import { Request, Response } from 'express';
import { query } from '../config/database';

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

      // 从内存数据库获取记录
      let voicemail: any[] = [];
      let sms: any[] = [];
      let unanswered: any[] = [];

      try {
        const voicemailResult = await query('SELECT * FROM voicemail_records ORDER BY created_at DESC LIMIT 50');
        voicemail = voicemailResult.rows;
        if (agentId) {
          voicemail = voicemail.filter((v: any) => v.agent_id === agentId);
        }
      } catch (e) {
        // 忽略错误
      }

      try {
        const smsResult = await query('SELECT * FROM sms_records ORDER BY created_at DESC LIMIT 50');
        sms = smsResult.rows;
        if (agentId) {
          sms = sms.filter((s: any) => s.agent_id === agentId);
        }
      } catch (e) {
        // 忽略错误
      }

      try {
        const unansweredResult = await query('SELECT * FROM unanswered_records ORDER BY created_at DESC LIMIT 50');
        unanswered = unansweredResult.rows;
        if (agentId) {
          unanswered = unanswered.filter((u: any) => u.agent_id === agentId);
        }
      } catch (e) {
        // 忽略错误
      }

      res.json({
        success: true,
        data: {
          voicemail,
          sms,
          unanswered,
          stats: {
            voicemailCount: voicemail.length,
            smsCount: sms.length,
            unansweredCount: unanswered.length,
          }
        }
      });
    } catch (error) {
      console.error('获取通信记录失败:', error);
      res.status(500).json({
        success: false,
        error: '获取通信记录失败'
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

      // 从内存数据库获取统计
      let voicemailCount = 0;
      let smsCount = 0;
      let unansweredCount = 0;

      try {
        const voicemailResult = await query('SELECT * FROM voicemail_records');
        voicemailCount = agentId 
          ? voicemailResult.rows.filter((v: any) => v.agent_id === agentId).length
          : voicemailResult.rows.length;
      } catch (e) {
        // 忽略错误
      }

      try {
        const smsResult = await query('SELECT * FROM sms_records');
        smsCount = agentId
          ? smsResult.rows.filter((s: any) => s.agent_id === agentId).length
          : smsResult.rows.length;
      } catch (e) {
        // 忽略错误
      }

      try {
        const unansweredResult = await query('SELECT * FROM unanswered_records');
        unansweredCount = agentId
          ? unansweredResult.rows.filter((u: any) => u.agent_id === agentId).length
          : unansweredResult.rows.length;
      } catch (e) {
        // 忽略错误
      }

      res.json({
        success: true,
        data: {
          voicemailCount,
          smsCount,
          unansweredCount,
          totalCount: voicemailCount + smsCount + unansweredCount
        }
      });
    } catch (error) {
      console.error('获取通信统计失败:', error);
      res.status(500).json({
        success: false,
        error: '获取通信统计失败'
      });
    }
  }
}

export const communicationController = new CommunicationController();
