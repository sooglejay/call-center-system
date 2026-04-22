import { Request, Response, RequestHandler } from 'express';
import multer from 'multer';
import fs from 'fs';
import path from 'path';

// 配置日志存储
const logsDir = path.join(__dirname, '../../uploads/logs');

// 确保日志目录存在
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

// 配置 multer 存储
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, logsDir);
  },
  filename: (req, file, cb) => {
    // 使用用户 ID 作为文件名
    const userId = (req as any).user?.id || 'unknown';
    cb(null, `user_${userId}_logs.txt`);
  }
});

const upload = multer({ 
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024 // 限制 10MB
  }
});

export const uploadLogsMiddleware: RequestHandler = upload.single('file');

/**
 * 上传设备日志
 */
export const uploadDeviceLogs = async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '未上传日志文件' });
    }

    const userId = (req as any).user?.id;
    const fileSize = req.file.size;
    const uploadTime = new Date().toISOString();

    console.log(`[Logs] 用户 ${userId} 上传日志，大小: ${(fileSize / 1024).toFixed(2)} KB`);

    res.json({
      message: '日志上传成功',
      file_size: fileSize,
      upload_time: uploadTime
    });
  } catch (error) {
    console.error('上传日志错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

/**
 * 下载设备日志
 */
export const downloadDeviceLogs = async (req: Request, res: Response) => {
  try {
    const { userId } = req.params;
    
    if (!userId) {
      return res.status(400).json({ error: '缺少用户 ID' });
    }

    const logFilePath = path.join(logsDir, `user_${userId}_logs.txt`);

    if (!fs.existsSync(logFilePath)) {
      return res.status(404).json({ error: '该用户暂无日志文件' });
    }

    // 获取文件信息
    const stats = fs.statSync(logFilePath);

    // 设置响应头
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="user_${userId}_logs.txt"`);

    // 发送文件
    res.sendFile(logFilePath);
  } catch (error) {
    console.error('下载日志错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

/**
 * 获取日志文件信息
 */
export const getLogFileInfo = async (req: Request, res: Response) => {
  try {
    const { userId } = req.params;
    
    if (!userId) {
      return res.status(400).json({ error: '缺少用户 ID' });
    }

    const logFilePath = path.join(logsDir, `user_${userId}_logs.txt`);

    if (!fs.existsSync(logFilePath)) {
      return res.json({
        exists: false,
        message: '该用户暂无日志文件'
      });
    }

    const stats = fs.statSync(logFilePath);

    res.json({
      exists: true,
      file_size: stats.size,
      upload_time: stats.mtime,
      file_path: `/uploads/logs/user_${userId}_logs.txt`
    });
  } catch (error) {
    console.error('获取日志信息错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

/**
 * 获取所有用户的日志列表（管理员用）
 */
export const getAllLogsList = async (req: Request, res: Response) => {
  try {
    const files = fs.readdirSync(logsDir);
    
    const logsList = files
      .filter(file => file.endsWith('_logs.txt'))
      .map(file => {
        const filePath = path.join(logsDir, file);
        const stats = fs.statSync(filePath);
        const userIdMatch = file.match(/user_(\d+)_logs\.txt/);
        const userId = userIdMatch ? parseInt(userIdMatch[1]) : null;

        return {
          user_id: userId,
          file_name: file,
          file_size: stats.size,
          upload_time: stats.mtime,
          file_path: `/uploads/logs/${file}`
        };
      })
      .sort((a, b) => b.upload_time.getTime() - a.upload_time.getTime());

    res.json({
      total: logsList.length,
      logs: logsList
    });
  } catch (error) {
    console.error('获取日志列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
