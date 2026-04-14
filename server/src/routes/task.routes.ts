import { Router } from 'express';
import { 
  getTasks, 
  createTask, 
  createTaskForSelf,
  updateTask, 
  deleteTask, 
  getMyTasks,
  getTaskById,
  addCustomersToTask,
  removeCustomerFromTask,
  updateTaskCustomerStatus,
  updateTaskCustomerInfo,
  removeTaskCustomer
} from '../controllers/task.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

// 管理员路由
router.get('/', adminMiddleware, getTasks);
router.post('/', adminMiddleware, createTask);
router.put('/:id', adminMiddleware, updateTask);
router.delete('/:id', adminMiddleware, deleteTask);
router.post('/:id/customers', adminMiddleware, addCustomersToTask);
router.delete('/:id/customers/:customerId', adminMiddleware, removeCustomerFromTask);

// 客服路由
router.get('/my/list', getMyTasks);
router.post('/my/create', createTaskForSelf);

// 通用路由（需要登录）
router.get('/:id', authMiddleware, getTaskById);
router.put('/:id/customers/:customerId/status', authMiddleware, updateTaskCustomerStatus);
router.put('/:id/customers/:customerId/info', authMiddleware, updateTaskCustomerInfo);
router.delete('/:id/task-customers/:customerId', authMiddleware, removeTaskCustomer);

export default router;
