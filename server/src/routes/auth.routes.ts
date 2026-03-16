import { Router } from 'express';
import { login, getCurrentUser, updateProfile, changePassword } from '../controllers/auth.controller';
import { authMiddleware } from '../middleware/auth';

const router: Router = Router();

router.post('/login', login);
router.get('/me', authMiddleware, getCurrentUser);
router.put('/profile', authMiddleware, updateProfile);
router.put('/password', authMiddleware, changePassword);

export default router;
