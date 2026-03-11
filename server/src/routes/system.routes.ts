import { Router } from 'express';
import { getHealth, getVersion, getSystemInfo } from '../controllers/system.controller';
import { authMiddleware } from '../middleware/auth';

const router = Router();

// 公开接口
router.get('/health', getHealth);
router.get('/version', getVersion);

// 需要认证的接口
router.use(authMiddleware);
router.get('/info', getSystemInfo);

export default router;
