import { Router } from 'express';
import multer from 'multer';
import fs from 'fs';
import path from 'path';
import { getCallRecords, createCallRecord, updateCallRecord, updateCallNotes, getNextCall, getCallStatus, createRecord, uploadCallRecording } from '../controllers/call.controller';
import { authMiddleware } from '../middleware/auth';

const router: Router = Router();

const recordingsDir = path.join(__dirname, '../../uploads/recordings');
if (!fs.existsSync(recordingsDir)) {
  fs.mkdirSync(recordingsDir, { recursive: true });
}

const recordingUpload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, recordingsDir),
    filename: (_req, file, cb) => {
      const ext = path.extname(file.originalname || '.m4a') || '.m4a';
      cb(null, `recording-${Date.now()}-${Math.round(Math.random() * 1e9)}${ext}`);
    }
  })
});

router.use(authMiddleware);

router.get('/', getCallRecords);
router.get('/:id/status', getCallStatus);
router.post('/', createCallRecord);
router.post('/record', createRecord);
router.post('/:id/recording', recordingUpload.single('file'), uploadCallRecording);
router.put('/:id', updateCallRecord);
router.put('/:id/notes', updateCallNotes);
router.get('/next/dial', getNextCall);

export default router;
