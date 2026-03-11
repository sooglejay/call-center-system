import { Request, Response } from 'express';
import twilio from 'twilio';
import { query } from '../config/database';
import { smsService } from '../services/sms.service';
import { voicemailService } from '../services/voicemail.service';

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
    
    // 检查是否启用语音信箱
    const voicemailEnabled = await voicemailService.isVoicemailEnabled();
    
    // 创建通话记录
    const callRecordResult = await query(
      `INSERT INTO calls (customer_id, agent_id, customer_phone, status, started_at)
       VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP) RETURNING *`,
      [customer_id, agentId, phone, 'calling']
    );
    
    const callRecord = callRecordResult.rows[0];
    
    // 获取回调URL
    const callbackResult = await query('SELECT config_value FROM system_configs WHERE config_key = $1', ['twilio_callback_url']);
    const baseCallbackUrl = callbackResult.rows[0]?.config_value || `${req.protocol}://${req.get('host')}/api/twilio`;
    
    // 发起Twilio呼叫
    const callOptions: any = {
      url: `${baseCallbackUrl}/voice?call_id=${callRecord.id}`,
      to: phone,
      from: fromNumber,
      record: true,
      recordingStatusCallback: `${baseCallbackUrl}/recording?call_id=${callRecord.id}`,
      statusCallback: `${baseCallbackUrl}/status?call_id=${callRecord.id}`,
      statusCallbackEvent: ['initiated', 'ringing', 'answered', 'completed', 'busy', 'no-answer', 'failed'],
      statusCallbackMethod: 'POST',
    };
    
    // 如果启用了语音信箱，添加自动检测
    if (voicemailEnabled) {
      callOptions.machineDetection = 'Enable';
      callOptions.asyncAmd = true;
      callOptions.asyncAmdStatusCallback = `${baseCallbackUrl}/amd?call_id=${callRecord.id}`;
    }
    
    const call = await client.calls.create(callOptions);
    
    // 更新通话记录 with Twilio SID
    await query('UPDATE calls SET twilio_call_sid = $1 WHERE id = $2', [call.sid, callRecord.id]);
    
    res.json({
      success: true,
      data: {
        call_record: callRecord,
        twilio_call: {
          sid: call.sid,
          status: call.status
        }
      }
    });
  } catch (error: any) {
    console.error('拨打电话错误:', error);
    res.status(500).json({ error: error.message || '拨打电话失败' });
  }
};

export const handleVoiceWebhook = async (req: Request, res: Response) => {
  try {
    const { call_id } = req.query;
    
    const twiml = new twilio.twiml.VoiceResponse();
    
    // 播放提示音
    twiml.say({ voice: 'alice', language: 'zh-CN' }, '您好，欢迎接听来电。');
    
    // 更新通话状态
    await query('UPDATE calls SET status = $1, started_at = CURRENT_TIMESTAMP WHERE id = $2',
      ['in-progress', call_id]);
    
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
    const { call_id } = req.query;
    const { CallStatus, CallDuration } = req.body;
    
    let status = 'pending';
    let callResult: string | null = null;
    
    switch (CallStatus) {
      case 'in-progress':
        status = 'in-progress';
        break;
      case 'completed':
        status = 'completed';
        callResult = 'answered';
        break;
      case 'busy':
        status = 'completed';
        callResult = 'busy';
        break;
      case 'no-answer':
        status = 'completed';
        callResult = 'no-answer';
        break;
      case 'failed':
        status = 'completed';
        callResult = 'failed';
        break;
      case 'canceled':
        status = 'completed';
        callResult = 'canceled';
        break;
    }
    
    // 获取通话记录
    const callResult2 = await query('SELECT customer_id, agent_id, customer_phone, customer_name FROM calls WHERE id = $1', [call_id]);
    const callRecord = callResult2.rows[0];
    
    await query(
      `UPDATE calls 
       SET status = $1, call_result = $2, recording_duration = $3, ended_at = CURRENT_TIMESTAMP
       WHERE id = $4`,
      [status, callResult, parseInt(CallDuration) || 0, call_id]
    );
    
    // 处理未接通情况 - 自动发送短信并保存未接通记录
    if (callResult === 'no-answer' || callResult === 'busy') {
      try {
        // 保存未接通记录
        await voicemailService.saveUnansweredRecord({
          callId: parseInt(call_id as string),
          customerId: callRecord?.customer_id,
          agentId: callRecord?.agent_id,
          customerPhone: callRecord?.customer_phone,
          customerName: callRecord?.customer_name,
          reason: callResult,
        });
        
        // 自动发送短信
        await smsService.sendSms({
          callId: parseInt(call_id as string),
          customerId: callRecord?.customer_id,
          agentId: callRecord?.agent_id,
          customerPhone: callRecord?.customer_phone,
          customerName: callRecord?.customer_name,
        });
      } catch (smsError) {
        console.error('自动发送短信失败:', smsError);
      }
    }
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理状态Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const handleAMDWebhook = async (req: Request, res: Response) => {
  try {
    const { call_id } = req.query;
    const { AnsweredBy, CallSid } = req.body;
    
    // 如果是语音信箱，重定向到语音信箱提示
    if (AnsweredBy === 'machine_start' || AnsweredBy === 'machine_end_beep') {
      const client = await getTwilioClient();
      
      // 获取回调URL
      const callbackResult = await query('SELECT config_value FROM system_configs WHERE config_key = $1', ['twilio_callback_url']);
      const baseCallbackUrl = callbackResult.rows[0]?.config_value || `${req.protocol}://${req.get('host')}/api/twilio`;
      
      // 更新通话到语音信箱模式
      await query('UPDATE calls SET status = $1, call_result = $2 WHERE id = $3', ['completed', 'voicemail', call_id]);
      
      // 重定向到语音信箱提示
      await client.calls(CallSid).update({
        url: `${baseCallbackUrl}/voicemail?call_id=${call_id}`,
        method: 'POST'
      });
    }
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理AMD Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const handleVoicemailWebhook = async (req: Request, res: Response) => {
  try {
    const { call_id } = req.query;
    
    const greeting = await voicemailService.getVoicemailGreeting();
    
    const twiml = new twilio.twiml.VoiceResponse();
    twiml.say({ voice: 'alice', language: 'zh-CN' }, greeting);
    twiml.record({
      maxLength: 300,
      playBeep: true,
      transcribe: true,
      recordingStatusCallback: `${req.protocol}://${req.get('host')}/api/twilio/voicemail/recording?call_id=${call_id}`,
    });
    
    res.type('text/xml');
    res.send(twiml.toString());
  } catch (error) {
    console.error('处理语音信箱Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const handleVoicemailRecording = async (req: Request, res: Response) => {
  try {
    const { call_id } = req.query;
    const { RecordingUrl, RecordingDuration } = req.body;
    
    // 获取通话记录
    const callResult = await query('SELECT customer_id, agent_id, customer_phone, customer_name FROM calls WHERE id = $1', [call_id]);
    const callRecord = callResult.rows[0];
    
    if (callRecord) {
      // 保存语音信箱记录
      await voicemailService.saveVoicemail({
        callId: parseInt(call_id as string),
        customerId: callRecord.customer_id,
        agentId: callRecord.agent_id,
        customerPhone: callRecord.customer_phone,
        customerName: callRecord.customer_name,
        voicemailUrl: RecordingUrl,
        recordingDuration: parseInt(RecordingDuration) || 0,
      });
      
      // 更新通话记录
      await query(
        'UPDATE calls SET recording_url = $1, recording_duration = $2 WHERE id = $3',
        [RecordingUrl, parseInt(RecordingDuration) || 0, call_id]
      );
    }
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理语音信箱录音错误:', error);
    res.sendStatus(500);
  }
};

export const handleRecordingWebhook = async (req: Request, res: Response) => {
  try {
    const { call_id } = req.query;
    const { RecordingUrl, RecordingDuration } = req.body;
    
    await query(
      'UPDATE calls SET recording_url = $1, recording_duration = $2 WHERE id = $3',
      [RecordingUrl, parseInt(RecordingDuration) || 0, call_id]
    );
    
    res.sendStatus(200);
  } catch (error) {
    console.error('处理录音Webhook错误:', error);
    res.sendStatus(500);
  }
};

export const getCallStatus = async (req: Request, res: Response) => {
  try {
    const { callSid } = req.params;
    
    const client = await getTwilioClient();
    const call = await client.calls(callSid).fetch();
    
    res.json({
      success: true,
      data: {
        sid: call.sid,
        status: call.status,
        duration: call.duration,
        startTime: call.startTime,
        endTime: call.endTime,
      }
    });
  } catch (error: any) {
    console.error('获取通话状态错误:', error);
    res.status(500).json({ error: error.message || '获取通话状态失败' });
  }
};

export const endCall = async (req: Request, res: Response) => {
  try {
    const { callSid } = req.params;
    
    const client = await getTwilioClient();
    await client.calls(callSid).update({ status: 'completed' });
    
    res.json({
      success: true,
      message: '通话已结束'
    });
  } catch (error: any) {
    console.error('结束通话错误:', error);
    res.status(500).json({ error: error.message || '结束通话失败' });
  }
};
