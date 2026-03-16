import { Router } from 'express';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import {
  makeCall,
  handleVoiceWebhook,
  handleStatusWebhook,
  handleRecordingWebhook,
  handleAMDWebhook,
  handleVoicemailWebhook,
  handleVoicemailRecording,
  getCallStatus,
  endCall,
  // 测试功能
  getTwilioConfig,
  testConnection,
  testSms,
  testCall,
  getAvailablePhoneNumbers,
  getSmsRecords,
  checkSmsStatus,
} from '../controllers/twilio.controller';

const router: Router = Router();

// Webhook路由 - 不需要认证 (Twilio调用)
router.post('/voice', handleVoiceWebhook);
router.post('/status', handleStatusWebhook);
router.post('/recording', handleRecordingWebhook);
router.post('/amd', handleAMDWebhook);
router.post('/voicemail', handleVoicemailWebhook);
router.post('/voicemail/recording', handleVoicemailRecording);

// API路由 - 需要认证
router.use(authMiddleware);

// 基本通话功能
router.post('/call', makeCall);
router.get('/call/:callSid/status', getCallStatus);
router.post('/call/:callSid/end', endCall);

// Twilio 测试功能 - 仅管理员
router.get('/config', adminMiddleware, getTwilioConfig);
router.get('/test/connection', adminMiddleware, testConnection);
router.post('/test/sms', adminMiddleware, testSms);
router.post('/test/call', adminMiddleware, testCall);
router.get('/phone-numbers', adminMiddleware, getAvailablePhoneNumbers);
router.get('/sms/records', adminMiddleware, getSmsRecords);
router.get('/sms/:messageSid/status', adminMiddleware, checkSmsStatus);

export default router;
