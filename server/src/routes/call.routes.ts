import { Router } from 'express';
import { getCallRecords, createCallRecord, updateCallRecord, updateCallNotes, getNextCall, getCallStatus, createRecord } from '../controllers/call.controller';
import { authMiddleware } from '../middleware/auth';

const router = Router();

router.use(authMiddleware);

router.get('/', getCallRecords);
router.get('/:id/status', getCallStatus);
router.post('/', createCallRecord);
router.post('/record', createRecord);
router.put('/:id', updateCallRecord);
router.put('/:id/notes', updateCallNotes);
router.get('/next/dial', getNextCall);

export default router;
