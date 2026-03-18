import { Router } from 'express';
import { 
  uploadMiddleware, 
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

// 上传 CSV 导入真实数据
router.post('/upload', uploadMiddleware, importRealCustomers);

// 初始化 mock 数据
router.post('/init-mock', initMockData);

// 清空数据
router.delete('/mock', clearMockData);
router.delete('/real', clearRealData);

export default router;
