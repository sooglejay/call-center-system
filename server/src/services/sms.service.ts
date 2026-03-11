import { Pool } from 'pg';
import twilio from 'twilio';
import { configService } from './config.service';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

export class SmsService {
  private async getTwilioClient() {
    const accountSid = await configService.getConfig('twilio_account_sid');
    const authToken = await configService.getConfig('twilio_auth_token');
    
    if (!accountSid || !authToken) {
      throw new Error('Twilio配置不完整');
    }
    
    return twilio(accountSid, authToken);
  }

  /**
   * 发送短信给客户
   */
  async sendSms(params: {
    callId: number;
    customerId: number;
    agentId: number;
    customerPhone: string;
    customerName?: string;
    agentName?: string;
    agentPhone?: string;
  }): Promise<any> {
    try {
      // 检查是否启用了短信功能
      const smsEnabled = await configService.getConfig('sms_enabled');
      if (smsEnabled !== 'true') {
        console.log('短信功能未启用，跳过发送');
        return null;
      }

      const client = await this.getTwilioClient();
      const fromNumber = await configService.getConfig('twilio_phone_number');
      
      // 获取短信模板
      let template = await configService.getConfig('sms_template_unanswered');
      if (!template) {
        template = '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}';
      }

      // 替换模板变量
      const messageBody = template
        .replace('{agentName}', params.agentName || '客服')
        .replace('{agentPhone}', params.agentPhone || '');

      // 发送短信
      const message = await client.messages.create({
        body: messageBody,
        from: fromNumber || undefined,
        to: params.customerPhone,
      });

      // 保存短信记录
      const result = await pool.query(
        `INSERT INTO sms_records 
         (call_id, customer_id, agent_id, customer_phone, customer_name, sms_content, twilio_message_sid, sms_status)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
         RETURNING *`,
        [
          params.callId,
          params.customerId,
          params.agentId,
          params.customerPhone,
          params.customerName,
          messageBody,
          message.sid,
          message.status,
        ]
      );

      console.log('短信发送成功:', message.sid);
      return result.rows[0];
    } catch (error) {
      console.error('发送短信失败:', error);
      
      // 即使发送失败也保存记录
      const result = await pool.query(
        `INSERT INTO sms_records 
         (call_id, customer_id, agent_id, customer_phone, customer_name, sms_content, sms_status)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING *`,
        [
          params.callId,
          params.customerId,
          params.agentId,
          params.customerPhone,
          params.customerName,
          '短信发送失败',
          'failed',
        ]
      );
      
      throw error;
    }
  }

  /**
   * 获取短信记录列表
   */
  async getSmsRecords(agentId?: number, limit: number = 50): Promise<any[]> {
    let query = 'SELECT * FROM sms_records';
    const params: any[] = [];
    
    if (agentId) {
      query += ' WHERE agent_id = $1';
      params.push(agentId);
    }
    
    query += ' ORDER BY created_at DESC LIMIT $' + (params.length + 1);
    params.push(limit);
    
    const result = await pool.query(query, params);
    return result.rows;
  }

  /**
   * 更新短信状态
   */
  async updateSmsStatus(messageSid: string, status: string): Promise<void> {
    await pool.query(
      'UPDATE sms_records SET sms_status = $1 WHERE twilio_message_sid = $2',
      [status, messageSid]
    );
  }
}

export const smsService = new SmsService();
