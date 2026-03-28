import { Router } from 'express';
import { getUsers, createUser, updateUser, deleteUser, resetPassword, getAgents, updateDataAccess } from '../controllers/user.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getUsers);
router.post('/', adminMiddleware, createUser);
router.put('/:id', adminMiddleware, updateUser);
router.put('/:id/data-access', adminMiddleware, updateDataAccess);
router.delete('/:id', adminMiddleware, deleteUser);
router.post('/:id/reset-password', adminMiddleware, resetPassword);

// 兼容两种路由格式：/users/agents 和 /users/agents/list
router.get('/agents', getAgents);
router.get('/agents/list', getAgents);

// 获取单个用户（需要放在 /agents 之后，避免被 /agents 路由匹配）
router.get('/:id', adminMiddleware, async (req, res) => {
  try {
    const { id } = req.params;
    const { query } = await import('../config/database');
    const result = await query('SELECT id, username, real_name, role, phone, email, status, data_access_type, created_at FROM users WHERE id = $1', [id]);
    
    if (result.rows.length === 0) {
      return res.status(404).json({ error: '用户不存在' });
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    res.status(500).json({ error: '服务器错误' });
  }
});

export default router;
