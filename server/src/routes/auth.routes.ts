import { Router } from 'express';
import { login, getCurrentUser, updateProfile, changePassword, checkInit, register, getPublicConfig } from '../controllers/auth.controller';
import { authMiddleware } from '../middleware/auth';

const router: Router = Router();

// 公开路由
router.get('/init-status', checkInit);  // 检查系统是否已初始化
router.get('/public-config', getPublicConfig);  // 获取公开配置（注册开关等）
router.post('/login', login);
router.post('/register', register);  // 用户注册

// 需要认证的路由
router.get('/me', authMiddleware, getCurrentUser);
router.put('/profile', authMiddleware, updateProfile);
router.put('/password', authMiddleware, changePassword);

export default router;
