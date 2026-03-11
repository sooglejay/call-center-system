import { Router } from 'express';
import { communicationController } from '../controllers/communication.controller';
import { authMiddleware } from '../middleware/auth';

const router = Router();

// 所有路由都需要认证
router.use(authMiddleware);

// 获取通信记录
router.get('/records', communicationController.getRecords);

// 获取通信统计
router.get('/stats', communicationController.getStats);

export default router;
