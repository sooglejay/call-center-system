import { Router } from 'express';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import { 
  uploadLogsMiddleware, 
  uploadDeviceLogs, 
  downloadDeviceLogs,
  getLogFileInfo,
  getAllLogsList
} from '../controllers/logs.controller';

const router = Router();

/**
 * 上传设备日志（需要登录）
 * POST /api/logs/upload
 */
router.post('/upload', authMiddleware, uploadLogsMiddleware, uploadDeviceLogs);

/**
 * 下载指定用户的日志（管理员）
 * GET /api/logs/user/:userId
 */
router.get('/user/:userId', authMiddleware, adminMiddleware, downloadDeviceLogs);

/**
 * 获取指定用户的日志信息（管理员）
 * GET /api/logs/user/:userId/info
 */
router.get('/user/:userId/info', authMiddleware, adminMiddleware, getLogFileInfo);

/**
 * 获取所有用户的日志列表（管理员）
 * GET /api/logs/list
 */
router.get('/list', authMiddleware, adminMiddleware, getAllLogsList);

export default router;
