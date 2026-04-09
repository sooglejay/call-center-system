import { Router } from 'express';
import { getCustomers, getCustomerById, createCustomer, updateCustomer, deleteCustomer, batchImportCustomers, getAgentCustomers, batchAssignAgents, getCustomersByNameLetter, getNameLetterStats, getCustomerTags, updateCallStatus, batchUpdateCallStatus } from '../controllers/customer.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router: Router = Router();

router.use(authMiddleware);

router.get('/', adminMiddleware, getCustomers);
router.post('/', adminMiddleware, createCustomer);
router.get('/tags', getCustomerTags);
router.get('/by-name-letter', adminMiddleware, getCustomersByNameLetter);
router.get('/name-letter-stats', adminMiddleware, getNameLetterStats);
router.get('/agent/list', getAgentCustomers);
router.get('/:id', getCustomerById);
router.put('/:id', updateCustomer);
router.delete('/:id', adminMiddleware, deleteCustomer);
router.post('/import', adminMiddleware, batchImportCustomers);
router.post('/batch-assign', adminMiddleware, batchAssignAgents);
router.post('/batch-update-call-status', adminMiddleware, batchUpdateCallStatus);
router.post('/:id/call-status', authMiddleware, updateCallStatus);

export default router;
