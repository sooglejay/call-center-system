import { query } from '../config/database';
import twilio from 'twilio';
import { configService } from './config.service';

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
      const result = await query(
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
      const result = await query(
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
    const result = await query('SELECT * FROM sms_records ORDER BY created_at DESC');
    let data = result.rows;
    
    if (agentId) {
      data = data.filter((s: any) => s.agent_id === agentId);
    }
    
    return data.slice(0, limit);
  }

  /**
   * 更新短信状态
   */
  async updateSmsStatus(messageSid: string, status: string): Promise<void> {
    // 内存数据库简化处理
    console.log('更新短信状态:', messageSid, status);
  }
}

export const smsService = new SmsService();
