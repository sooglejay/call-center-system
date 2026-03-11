import { Request, Response } from 'express';
import twilio from 'twilio';
import { query } from '../config/database';

const getTwilioClient = async () => {
  const result = await query('SELECT config_key, config_value FROM system_configs WHERE config_key LIKE $1', ['twilio_%']);
  const configs: any = {};
  result.rows.forEach(row => {
    configs[row.config_key] = row.config_value;
  });
  
  if (!configs.twilio_account_sid || !configs.twilio_auth_token) {
    throw new Error('Twilio 配置不完整');
  }
  
  return twilio(configs.twilio_account_sid, configs.twilio_auth_token);
};

export const makeCall = async (req: any, res: Response) => {
  try {
    const { customer_id, phone, task_id } = req.body;
    const agentId = req.user.id;
    
    const client = await getTwilioClient();
    
    const configResult = await query('SELECT config_value FROM system_configs WHERE config_key = $1', ['twilio_phone_number']);
    const fromNumber = configResult.rows[0]?.config_value;
    
    if (!fromNumber) {
      return res.status(400).json({ error: 'Twilio 发信号码未配置' });
    }
    
    // 创建通话记录
    const callRecordResult = await query(
      `INSERT INTO call_records (customer_id, agent_id, phone, task_id, status, started_at)
       VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP) RETURNING *`,
      [customer_id, agentId, phone, task_id, 'calling']
    );
    
    const callRecord = callRecordResult.rows[0];
    
    // 获取回调URL
    const callbackResult = await query('SELECT config_value FROM system_configs WHERE config_key = $1', ['twilio_callback_url']);
    const callbackUrl = callbackResult.rows[0]?.config_value || `${req.protocol}://${req.get('host')}/api/twilio/webhook`;
    
    // 发起Twilio呼叫
    const call = await client.calls.create({
      url: `${callbackUrl}/voice?record_id=${callRecord.id}`,
      to: phone,
      from: fromNumber,
      record: true,
      recordingStatusCallback: `${callbackUrl}/recording?record_id=${callRecord.id}`,
      statusCallback: `${callbackUrl}/status?record_id=${callRecord.id}`,
      statusCallbackEvent: ['initiated', 'ringing', 'answered', 'completed'],
      statusCallbackMethod: 'POST'
    });
    
    // 更新通话记录 with Twilio SID
    await query('UPDATE call_records SET twilio_call_sid = $1 WHERE id = $2', [call.sid, callRecord.id]);
    
    res.json({
      call_record: callRecord,
      twilio_call: {
        sid: call.sid,
        status: call.status
      }
    });
  } catch (error: any) {
    console.error('拨打电话错误:', error);
    res.status(500).json({ error: error.message || '拨打电话失败' });
  }
};

export const handleVoiceWebhook = async (req: Request, res: Response) => {
  try {
    const { record_id } = req.query;
    
    const twiml = new twilio.twiml.VoiceResponse();
    
    // 播放提示音或连接客服
    twiml.say({ voice: 'alice', language: 'zh-CN' }, '您好，欢迎接听来电。');
    
    // 更新通话状态
    await query('UPDATE call_records SET status = $1, connected_at = CURRENT_TIMESTAMP WHERE id = $2',
      ['connected', record_id]);
    
    res.type('text/xml');
    res.send(twiml.toString());
  } catch (error) {
    console.error('处理语音Webhook错误:', error);
    const twiml = new twilio.twiml.VoiceResponse();
    twiml.say({ voice: 'alice', language: 'zh-CN' }, '系统错误，请稍后再试。');
    res.type('text/xml');
    res.send(twiml.toString());
  }
};

export const handleStatusWebhook = async (req: Request, res: Response) => {
  try {
    const { record_id } = req.query;
    const { CallStatus, CallDuration } = req.body;
    
    let status = 'pending';
    let isConnected = false;
    
    switch (CallStatus) {
      case 'in-progress':
        status = 'connected';
        isConnected = true;
        break;
      case 'completed':
        status = 'completed';
        isConnected = true;
        break;
      case 'busy':
        status = 'busy';
        break;
      case 'no-answer':
        status = 'no_answer';
        break;
      case 'failed':
        status = 'failed';
        break;
      case 'canceled':
        status = 'failed';
        break;
    }
    
    await query(
      `UPDATE call_records 
       SET status = $1, is_connected = $2, call_duration = $3, ended_at = CURRENT_TIMESTAMP
       WHERE id = $4`,
      [status, isConnected, parseInt(CallDuration) || 0, record_id]
    );
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理状态Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const handleRecordingWebhook = async (req: Request, res: Response) => {
  try {
    const { record_id } = req.query;
    const { RecordingUrl, RecordingDuration } = req.body;
    
    await query(
      'UPDATE call_records SET recording_url = $1, recording_duration = $2 WHERE id = $3',
      [RecordingUrl, parseInt(RecordingDuration) || 0, record_id]
    );
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理录音Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const getCallStatus = async (req: Request, res: Response) => {
  try {
    const { call_sid } = req.params;
    
    const client = await getTwilioClient();
    const call = await client.calls(call_sid).fetch();
    
    res.json({
      sid: call.sid,
      status: call.status,
      duration: call.duration,
      from: call.from,
      to: call.to
    });
  } catch (error: any) {
    console.error('获取通话状态错误:', error);
    res.status(500).json({ error: error.message || '获取通话状态失败' });
  }
};

export const endCall = async (req: Request, res: Response) => {
  try {
    const { call_sid } = req.params;
    
    const client = await getTwilioClient();
    await client.calls(call_sid).update({ status: 'completed' });
    
    res.json({ message: '通话已结束' });
  } catch (error: any) {
    console.error('结束通话错误:', error);
    res.status(500).json({ error: error.message || '结束通话失败' });
  }
};
