import { Router, Request, Response, NextFunction } from 'express';
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

const getApkStorageCandidates = () => {
  const candidates = [
    path.join(__dirname, '../../uploads/apk'),
    path.join(process.cwd(), 'uploads/apk'),
    path.join(process.cwd(), 'server/uploads/apk'),
  ];

  return Array.from(new Set(candidates));
};

const ensureWritableApkDir = () => {
  const uploadDir = getApkStorageCandidates()[0];

  if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
  }

  return uploadDir;
};

// 确保上传目录存在
const uploadDir = ensureWritableApkDir();

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

// 公开接口：检查版本更新（App客户端使用）
router.get('/check', checkVersion);

// 需要登录的接口（普通用户可用）
router.use(authMiddleware);

// 获取最新版本（用于下载页面）
router.get('/latest', getCurrentVersion);

// 获取版本列表（普通用户可查看，用于下载历史版本）
router.get('/list', getVersionList);

// 以下接口需要管理员权限
router.use(adminMiddleware);

// 获取当前活跃版本（管理员）
router.get('/current', getCurrentVersion);

// 上传APK文件
router.post('/upload', upload.single('apk'), uploadApk);

// 创建新版本
router.post('/create', createVersion);

// 删除版本
router.delete('/:id', deleteVersion);

export default router;
