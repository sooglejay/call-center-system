import { Router } from 'express';
import { getConfigs, updateConfig, getAgentConfig, updateAgentConfig } from '../controllers/config.controller';
import { uploadFile, uploadImage, uploadMiddleware } from '../controllers/upload.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

// 系统配置（仅管理员）
router.get('/system', adminMiddleware, getConfigs);
router.put('/system', adminMiddleware, updateConfig);

// 客服配置
router.get('/agent', getAgentConfig);
router.put('/agent', updateAgentConfig);

// 文件上传
router.post('/upload/file', adminMiddleware, uploadMiddleware, uploadFile);
router.post('/upload/image', adminMiddleware, uploadMiddleware, uploadImage);

export default router;
