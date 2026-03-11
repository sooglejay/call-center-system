import { Router } from 'express';
import { exportCustomers, getDashboard, getCallTrend } from '../controllers/report.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

// 导出客户
router.get('/export/customers', adminMiddleware, exportCustomers);

// 数据看板
router.get('/dashboard', getDashboard);

// 通话趋势
router.get('/trend/calls', getCallTrend);

export default router;
