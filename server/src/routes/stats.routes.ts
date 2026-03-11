import { Router } from 'express';
import { getStats, getMyStats, getDashboardStats } from '../controllers/stats.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getStats);
router.get('/dashboard', adminMiddleware, getDashboardStats);
router.get('/my', getMyStats);

export default router;
