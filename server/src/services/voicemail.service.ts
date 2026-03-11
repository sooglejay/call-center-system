import { Pool } from 'pg';
import twilio from 'twilio';
import { configService } from './config.service';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

export class VoicemailService {
  private async getTwilioClient() {
    const accountSid = await configService.getConfig('twilio_account_sid');
    const authToken = await configService.getConfig('twilio_auth_token');
    
    if (!accountSid || !authToken) {
      throw new Error('Twilio配置不完整');
    }
    
    return twilio(accountSid, authToken);
  }

  /**
   * 检查是否启用了语音信箱
   */
  async isVoicemailEnabled(): Promise<boolean> {
    const enabled = await configService.getConfig('voicemail_enabled');
    return enabled === 'true';
  }

  /**
   * 获取语音信箱问候语
   */
  async getVoicemailGreeting(): Promise<string> {
    const greeting = await configService.getConfig('voicemail_greeting');
    return greeting || '您好，我现在无法接听您的电话，请在听到提示音后留言。';
  }

  /**
   * 保存语音信箱记录
   */
  async saveVoicemail(params: {
    callId: number;
    customerId?: number;
    agentId?: number;
    customerPhone: string;
    customerName?: string;
    voicemailUrl: string;
    recordingDuration?: number;
  }): Promise<any> {
    const result = await pool.query(
      `INSERT INTO voicemail_records 
       (call_id, customer_id, agent_id, customer_phone, customer_name, voicemail_url, duration)
       VALUES ($1, $2, $3, $4, $5, $6, $7)
       RETURNING *`,
      [
        params.callId,
        params.customerId,
        params.agentId,
        params.customerPhone,
        params.customerName,
        params.voicemailUrl,
        params.recordingDuration || 0,
      ]
    );

    console.log('语音信箱记录已保存:', result.rows[0].id);
    return result.rows[0];
  }

  /**
   * 获取语音信箱记录列表
   */
  async getVoicemailRecords(agentId?: number, limit: number = 50): Promise<any[]> {
    let query = 'SELECT * FROM voicemail_records';
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
   * 保存未接通记录
   */
  async saveUnansweredRecord(params: {
    callId: number;
    customerId?: number;
    agentId?: number;
    customerPhone: string;
    customerName?: string;
    reason?: string;
  }): Promise<any> {
    const result = await pool.query(
      `INSERT INTO unanswered_records 
       (call_id, customer_id, agent_id, customer_phone, customer_name, reason)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [
        params.callId,
        params.customerId,
        params.agentId,
        params.customerPhone,
        params.customerName,
        params.reason || 'no-answer',
      ]
    );

    console.log('未接通记录已保存:', result.rows[0].id);
    return result.rows[0];
  }

  /**
   * 获取未接通记录列表
   */
  async getUnansweredRecords(agentId?: number, limit: number = 50): Promise<any[]> {
    let query = 'SELECT * FROM unanswered_records';
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
}

export const voicemailService = new VoicemailService();
