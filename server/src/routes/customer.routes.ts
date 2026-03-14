import { Router } from 'express';
import { getCustomers, getCustomerById, updateCustomer, deleteCustomer, batchImportCustomers, getAgentCustomers, batchAssignAgents } from '../controllers/customer.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getCustomers);
router.get('/agent/list', getAgentCustomers);
router.get('/:id', getCustomerById);
router.put('/:id', updateCustomer);
router.delete('/:id', adminMiddleware, deleteCustomer);
router.post('/import', adminMiddleware, batchImportCustomers);
router.post('/batch-assign', adminMiddleware, batchAssignAgents);

export default router;
