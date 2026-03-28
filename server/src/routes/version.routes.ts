import { Router } from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import {
  checkVersion,
  getVersionList,
  createVersion,
  uploadApk,
  deleteVersion,
  getCurrentVersion
} from '../controllers/version.controller';
import { authMiddleware, adminMiddleware } from '../middleware/auth';

const router = Router();

// 确保上传目录存在
const uploadDir = path.join(__dirname, '../../uploads/apk');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

// 配置 multer
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    // 临时文件名，上传后会重命名
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'temp-' + uniqueSuffix + '.apk');
  }
});

const fileFilter = (req: any, file: any, cb: any) => {
  // 只允许APK文件
  if (file.mimetype === 'application/vnd.android.package-archive' || 
      file.originalname.endsWith('.apk')) {
    cb(null, true);
  } else {
    cb(new Error('只允许上传APK文件'), false);
  }
};

const upload = multer({ 
  storage,
  fileFilter,
  limits: {
    fileSize: 100 * 1024 * 1024 // 限制100MB
  }
});

// 公开接口：检查版本更新
router.get('/check', checkVersion);

// 以下接口需要管理员权限
router.use(authMiddleware);
router.use(adminMiddleware);

// 获取版本列表
router.get('/list', getVersionList);

// 获取当前活跃版本
router.get('/current', getCurrentVersion);

// 上传APK文件
router.post('/upload', upload.single('apk'), uploadApk);

// 创建新版本
router.post('/create', createVersion);

// 删除版本
router.delete('/:id', deleteVersion);

export default router;
