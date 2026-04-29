import { Router } from 'express';
import { authMiddleware, adminMiddleware } from '../middleware/auth';
import {
  uploadLogsMiddleware,
  uploadDeviceLogs,
  downloadDeviceLogs,
  getAllLogsList,
  deleteAllLogs
} from '../controllers/logs.controller';

const router = Router();

/**
 * 上传设备日志（需要登录）
 * POST /api/logs/upload
 */
router.post('/upload', authMiddleware, uploadLogsMiddleware, uploadDeviceLogs);

/**
 * 下载指定日志文件（管理员）
 * GET /api/logs/file/:fileName
 */
router.get('/file/:fileName', authMiddleware, adminMiddleware, downloadDeviceLogs);

/**
 * 获取所有用户的日志列表（管理员）
 * GET /api/logs/list
 */
router.get('/list', authMiddleware, adminMiddleware, getAllLogsList);

/**
 * 删除所有日志文件（管理员）
 * DELETE /api/logs/all
 */
router.delete('/all', authMiddleware, adminMiddleware, deleteAllLogs);

export default router;
