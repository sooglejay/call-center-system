import { Router } from 'express';
import { authMiddleware } from '../middleware/auth';
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
} from '../controllers/twilio.controller';

const router = Router();

// Webhook路由 - 不需要认证 (Twilio调用)
router.post('/voice', handleVoiceWebhook);
router.post('/status', handleStatusWebhook);
router.post('/recording', handleRecordingWebhook);
router.post('/amd', handleAMDWebhook);
router.post('/voicemail', handleVoicemailWebhook);
router.post('/voicemail/recording', handleVoicemailRecording);

// API路由 - 需要认证
router.use(authMiddleware);
router.post('/call', makeCall);
router.get('/call/:callSid/status', getCallStatus);
router.post('/call/:callSid/end', endCall);

export default router;
