import { Router } from 'express';
import { 
  uploadMiddleware, 
  previewCSV,
  importWithMapping,
  importRealCustomers, 
  clearMockData, 
  clearRealData, 
  getDataStats,
  initMockData
} from '../controllers/data-import.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);
router.use(adminMiddleware);

// 获取数据统计
router.get('/stats', getDataStats);

// 预览 CSV 文件（返回列名和预览数据）
router.post('/preview', uploadMiddleware, previewCSV);

// 使用列映射导入数据
router.post('/import', uploadMiddleware, importWithMapping);

// 快速导入（自动匹配，兼容旧版本）
router.post('/upload', uploadMiddleware, importRealCustomers);

// 初始化 mock 数据
router.post('/init-mock', initMockData);

// 清空数据
router.delete('/mock', clearMockData);
router.delete('/real', clearRealData);

export default router;
