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
    // 使用用户 ID + 时间戳作为文件名，支持多次上传
    const userId = (req as any).user?.id || 'unknown';
    const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\..+/, '').replace('T', '_');
    const randomSuffix = Math.random().toString(36).substring(2, 6);
    cb(null, `user_${userId}_logs_${timestamp}_${randomSuffix}.txt`);
  }
});

const upload = multer({
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024 // 限制 10MB，防止大文件攻击
  }
});

// 错误处理中间件
export const uploadLogsMiddleware: RequestHandler = (req, res, next) => {
  upload.single('file')(req, res, (err: any) => {
    if (err) {
      if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(413).json({
          error: '日志文件过大，请升级App至最新版本',
          code: 'FILE_TOO_LARGE',
          max_size: 10 * 1024 * 1024
        });
      }
      return res.status(400).json({
        error: err.message || '文件上传失败',
        code: 'UPLOAD_ERROR'
      });
    }
    next();
  });
};

// 日志元数据接口
interface LogMetadata {
  user_id: number;
  description: string;
  upload_time: string;
  file_size: number;
}

// 保存日志元数据（每个日志文件对应一个 .json 文件）
function saveLogMetadata(fileName: string, userId: number, description: string, fileSize: number): void {
  const metaPath = path.join(logsDir, `${fileName}.json`);
  const metadata: LogMetadata = {
    user_id: userId,
    description: description || '',
    upload_time: new Date().toISOString(),
    file_size: fileSize
  };
  fs.writeFileSync(metaPath, JSON.stringify(metadata, null, 2));
}

// 读取日志元数据
function getLogMetadata(fileName: string): LogMetadata | null {
  const metaPath = path.join(logsDir, `${fileName}.json`);
  if (!fs.existsSync(metaPath)) {
    return null;
  }
  try {
    const content = fs.readFileSync(metaPath, 'utf-8');
    return JSON.parse(content);
  } catch {
    return null;
  }
}

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
    const description = req.body.description || ''; // 获取描述信息
    const fileName = req.file.filename; // multer 生成的文件名

    // 再次检查文件大小（双重保险）
    const maxSize = 10 * 1024 * 1024; // 10MB
    if (fileSize > maxSize) {
      // 删除超大的临时文件
      const filePath = req.file.path;
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
      return res.status(413).json({
        error: '日志文件过大，请升级App至最新版本',
        code: 'FILE_TOO_LARGE',
        max_size: maxSize,
        actual_size: fileSize
      });
    }

    // 保存元数据
    saveLogMetadata(fileName, userId, description, fileSize);

    console.log(`[Logs] 用户 ${userId} 上传日志，文件: ${fileName}，大小: ${(fileSize / 1024).toFixed(2)} KB，描述: ${description || '无'}`);

    res.json({
      message: '日志上传成功',
      file_name: fileName,
      file_size: fileSize,
      upload_time: uploadTime,
      description: description
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
    const { fileName } = req.params;

    if (!fileName) {
      return res.status(400).json({ error: '缺少文件名' });
    }

    const logFilePath = path.join(logsDir, fileName as string);

    if (!fs.existsSync(logFilePath)) {
      return res.status(404).json({ error: '日志文件不存在' });
    }

    // 设置响应头
    res.setHeader('Content-Type', 'text/plain; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${fileName}"`);

    // 发送文件
    res.sendFile(logFilePath);
  } catch (error) {
    console.error('下载日志错误:', error);
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
      .filter(file => file.startsWith('user_') && file.endsWith('.txt') && file.includes('_logs_'))
      .map(file => {
        const filePath = path.join(logsDir, file);
        const stats = fs.statSync(filePath);
        const userIdMatch = file.match(/user_(\d+)_logs/);
        const userId = userIdMatch ? parseInt(userIdMatch[1]) : null;

        // 读取元数据获取描述信息
        const metadata = getLogMetadata(file);

        return {
          user_id: userId,
          file_name: file,
          file_size: stats.size,
          upload_time: metadata?.upload_time || stats.mtime,
          file_path: `/uploads/logs/${file}`,
          description: metadata?.description || ''
        };
      })
      .sort((a, b) => new Date(b.upload_time).getTime() - new Date(a.upload_time).getTime());

    res.json({
      total: logsList.length,
      logs: logsList
    });
  } catch (error) {
    console.error('获取日志列表错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

/**
 * 删除所有日志文件（管理员用）
 */
export const deleteAllLogs = async (req: Request, res: Response) => {
  try {
    const files = fs.readdirSync(logsDir);
    let deletedCount = 0;
    let errorCount = 0;

    files.forEach(file => {
      // 删除日志文件和对应的元数据文件
      if (file.endsWith('.txt') || file.endsWith('.json')) {
        const filePath = path.join(logsDir, file);
        try {
          fs.unlinkSync(filePath);
          deletedCount++;
        } catch (err) {
          console.error(`删除文件失败: ${file}`, err);
          errorCount++;
        }
      }
    });

    console.log(`[Logs] 清空所有日志，成功删除 ${deletedCount} 个文件，失败 ${errorCount} 个`);

    res.json({
      message: '日志清空成功',
      deleted_count: deletedCount,
      error_count: errorCount
    });
  } catch (error) {
    console.error('删除日志错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
