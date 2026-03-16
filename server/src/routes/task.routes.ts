import { Router } from 'express';
import { getTasks, createTask, updateTask, deleteTask, getMyTasks } from '../controllers/task.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getTasks);
router.post('/', adminMiddleware, createTask);
router.put('/:id', adminMiddleware, updateTask);
router.delete('/:id', adminMiddleware, deleteTask);
router.get('/my/list', getMyTasks);

export default router;
