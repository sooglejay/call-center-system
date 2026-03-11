import { Router } from 'express';
import { getCallRecords, createCallRecord, updateCallRecord, updateCallNotes, getNextCall } from '../controllers/call.controller';
import { authMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

router.get('/', getCallRecords);
router.post('/', createCallRecord);
router.put('/:id', updateCallRecord);
router.put('/:id/notes', updateCallNotes);
router.get('/next/dial', getNextCall);

export default router;
