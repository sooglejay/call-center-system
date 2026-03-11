import { Router } from 'express';
import { makeCall, handleVoiceWebhook, handleStatusWebhook, handleRecordingWebhook, getCallStatus, endCall } from '../controllers/twilio.controller';
import { authMiddleware } from '../middleware/auth';

const router = Router();

// Webhook路由不需要认证
router.post('/webhook/voice', handleVoiceWebhook);
router.post('/webhook/status', handleStatusWebhook);
router.post('/webhook/recording', handleRecordingWebhook);

// API路由需要认证
router.use(authMiddleware);
router.post('/call', makeCall);
router.get('/call/:call_sid/status', getCallStatus);
router.post('/call/:call_sid/end', endCall);

export default router;
