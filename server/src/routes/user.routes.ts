import { Router } from 'express';
import { getUsers, createUser, updateUser, deleteUser, resetPassword, getAgents } from '../controllers/user.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getUsers);
router.post('/', adminMiddleware, createUser);
router.put('/:id', adminMiddleware, updateUser);
router.delete('/:id', adminMiddleware, deleteUser);
router.post('/:id/reset-password', adminMiddleware, resetPassword);
router.get('/agents/list', getAgents);

export default router;
