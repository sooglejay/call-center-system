import { Router } from 'express';
import { getCustomers, getCustomerById, createCustomer, updateCustomer, deleteCustomer, batchImportCustomers, getAgentCustomers, batchAssignAgents, getCustomersByNameLetter, getNameLetterStats } from '../controllers/customer.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getCustomers);
router.post('/', adminMiddleware, createCustomer);
router.get('/by-name-letter', adminMiddleware, getCustomersByNameLetter);
router.get('/name-letter-stats', adminMiddleware, getNameLetterStats);
router.get('/agent/list', getAgentCustomers);
router.get('/:id', getCustomerById);
router.put('/:id', updateCustomer);
router.delete('/:id', adminMiddleware, deleteCustomer);
router.post('/import', adminMiddleware, batchImportCustomers);
router.post('/batch-assign', adminMiddleware, batchAssignAgents);

export default router;
