import { Request, Response } from 'express';
import { query } from '../config/database';
import path from 'path';
import fs from 'fs';

const getApkStorageCandidates = () => {
  const candidates = [
    path.join(__dirname, '../../uploads/apk'),
    path.join(process.cwd(), 'uploads/apk'),
    path.join(process.cwd(), 'server/uploads/apk'),
  ];

  return Array.from(new Set(candidates));
};

const resolveExistingApkPath = (fileName: string) => {
  for (const dir of getApkStorageCandidates()) {
    const fullPath = path.join(dir, fileName);
    if (fs.existsSync(fullPath)) {
      return fullPath;
    }
  }

  return '';
};

const ensureWritableApkDir = () => {
  const preferredDir = getApkStorageCandidates()[0];

  if (!fs.existsSync(preferredDir)) {
    fs.mkdirSync(preferredDir, { recursive: true });
  }

  return preferredDir;
};

/**
 * 检查版本更新
 * GET /api/version/check
 */
export const checkVersion = async (req: Request, res: Response) => {
  try {
    const { version_code, platform = 'android' } = req.query;
    
    if (!version_code) {
      return res.status(400).json({ error: '缺少版本号参数' });
    }

    const currentVersionCode = parseInt(version_code as string, 10);
    
    // 获取当前活跃版本
    const result = query(
      `SELECT * FROM app_versions 
       WHERE platform = ? AND is_active = 1 
       ORDER BY version_code DESC LIMIT 1`,
      [platform]
    );

    if (result.rows.length === 0) {
      // 没有发布版本，返回当前版本信息
      return res.json({
        version_code: currentVersionCode,
        version_name: '1.0.0',
        apk_url: '',
        update_log: '',
        force_update: false,
        min_version_code: currentVersionCode
      });
    }

    const latestVersion = result.rows[0];
    
    // 检查是否需要强制更新
    const minVersionCode = latestVersion.min_version_code || latestVersion.version_code;
    const needForceUpdate = currentVersionCode < minVersionCode;

    res.json({
      version_code: latestVersion.version_code,
      version_name: latestVersion.version_name,
      apk_url: latestVersion.apk_url,
      update_log: latestVersion.update_log,
      force_update: needForceUpdate || latestVersion.force_update === 1,
      min_version_code: minVersionCode
    });
  } catch (error) {
    console.error('检查版本失败:', error);
    res.status(500).json({ error: '检查版本失败' });
  }
};

/**
 * 获取版本列表（管理员）
 * GET /api/version/list
 */
export const getVersionList = async (req: Request, res: Response) => {
  try {
    const { platform = 'android' } = req.query;
    
    const result = query(
      `SELECT v.*, u.real_name as created_by_name 
       FROM app_versions v
       LEFT JOIN users u ON v.created_by = u.id
       WHERE v.platform = ?
       ORDER BY v.version_code DESC`,
      [platform]
    );

    res.json(result.rows);
  } catch (error) {
    console.error('获取版本列表失败:', error);
    res.status(500).json({ error: '获取版本列表失败' });
  }
};

/**
 * 创建新版本（管理员）
 * POST /api/version/create
 */
export const createVersion = async (req: Request, res: Response) => {
  try {
    const { version_code, version_name, platform = 'android', update_log, force_update, min_version_code } = req.body;
    const userId = (req as any).user?.id;

    // 验证必填字段
    if (!version_code || !version_name) {
      return res.status(400).json({ error: '版本号和版本名称不能为空' });
    }

    // 检查APK文件是否存在
    const apkFileName = `app-release-${version_code}.apk`;
    const apkPath = resolveExistingApkPath(apkFileName);

    if (!apkPath) {
      return res.status(400).json({
        error: 'APK文件不存在',
        message: `请先上传APK文件: ${apkFileName}`
      });
    }

    // 构建APK下载URL
    const baseUrl = `${req.protocol}://${req.get('host')}`;
    const apkUrl = `${baseUrl}/uploads/apk/${apkFileName}`;

    // 检查该版本是否已存在
    const existingVersion = query(
      'SELECT id, is_active FROM app_versions WHERE version_code = ? AND platform = ?',
      [version_code, platform]
    );

    if (existingVersion.rows.length > 0) {
      // 版本已存在，先将其他版本设为非活跃，再更新当前版本为活跃
      query(
        `UPDATE app_versions SET is_active = 0, updated_at = datetime('now')
         WHERE platform = ? AND is_active = 1`,
        [platform]
      );

      const versionId = existingVersion.rows[0].id;

      query(
        `UPDATE app_versions SET
          version_name = ?,
          apk_url = ?,
          update_log = ?,
          force_update = ?,
          min_version_code = ?,
          is_active = 1,
          updated_at = datetime('now')
         WHERE id = ?`,
        [version_name, apkUrl, update_log || '', force_update ? 1 : 0, min_version_code || version_code, versionId]
      );

      return res.json({
        id: versionId,
        version_code,
        version_name,
        platform,
        apk_url: apkUrl,
        update_log,
        force_update: force_update ? 1 : 0,
        min_version_code: min_version_code || version_code,
        message: '版本已覆盖更新'
      });
    }

    // 将之前的活跃版本设为不活跃
    query(
      `UPDATE app_versions SET is_active = 0, updated_at = datetime('now')
       WHERE platform = ? AND is_active = 1`,
      [platform]
    );

    // 创建新版本
    const result = query(
      `INSERT INTO app_versions (version_code, version_name, platform, apk_url, update_log, force_update, min_version_code, is_active, created_by, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, datetime('now'), datetime('now'))`,
      [version_code, version_name, platform, apkUrl, update_log || '', force_update ? 1 : 0, min_version_code || version_code, userId]
    );

    res.json({
      id: result.rows[0]?.id,
      version_code,
      version_name,
      platform,
      apk_url: apkUrl,
      update_log,
      force_update: force_update ? 1 : 0,
      min_version_code: min_version_code || version_code,
      message: '版本发布成功'
    });
  } catch (error) {
    console.error('创建版本失败:', error);
    res.status(500).json({ error: '创建版本失败' });
  }
};

/**
 * 上传APK文件（管理员）
 * POST /api/version/upload
 */
export const uploadApk = async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '没有上传文件' });
    }

    const { version_code } = req.body;

    if (!version_code) {
      // 删除上传的文件
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: '缺少版本号' });
    }

    // 重命名文件为规范格式
    const apkDir = ensureWritableApkDir();

    const newFileName = `app-release-${version_code}.apk`;
    const newPath = path.join(apkDir, newFileName);

    // 如果文件已存在，先删除（支持覆盖更新）
    if (fs.existsSync(newPath)) {
      fs.unlinkSync(newPath);
    }

    // 移动文件
    fs.renameSync(req.file.path, newPath);

    // 构建下载URL
    const baseUrl = `${req.protocol}://${req.get('host')}`;
    const apkUrl = `${baseUrl}/uploads/apk/${newFileName}`;

    // 检查该版本号是否已存在
    const existingVersion = query(
      'SELECT id FROM app_versions WHERE version_code = ? AND platform = ?',
      [version_code, 'android']
    );

    if (existingVersion.rows.length > 0) {
      // 版本号已存在，更新 APK 文件和更新时间
      query(
        `UPDATE app_versions SET 
          apk_url = ?, 
          updated_at = datetime('now') 
         WHERE version_code = ? AND platform = ?`,
        [apkUrl, version_code, 'android']
      );

      return res.json({
        message: 'APK已覆盖更新',
        file_name: newFileName,
        file_size: req.file.size,
        download_url: apkUrl,
        updated: true,
        version_code: version_code
      });
    }

    res.json({
      message: 'APK上传成功',
      file_name: newFileName,
      file_size: req.file.size,
      download_url: apkUrl
    });
  } catch (error) {
    console.error('上传APK失败:', error);
    // 清理上传的文件
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }
    res.status(500).json({ error: '上传APK失败' });
  }
};

/**
 * 删除版本（管理员）
 * DELETE /api/version/:id
 */
export const deleteVersion = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;

    // 获取版本信息
    const versionResult = query(
      'SELECT * FROM app_versions WHERE id = ?',
      [id]
    );

    if (versionResult.rows.length === 0) {
      return res.status(404).json({ error: '版本不存在' });
    }

    const version = versionResult.rows[0];

    // 如果是活跃版本，不允许删除
    if (version.is_active === 1) {
      return res.status(400).json({ error: '不能删除当前活跃版本，请先发布新版本' });
    }

    // 删除APK文件
    if (version.apk_url) {
      const apkPath = path.join(__dirname, '../../uploads/apk', path.basename(version.apk_url));
      if (fs.existsSync(apkPath)) {
        fs.unlinkSync(apkPath);
      }
    }

    // 删除数据库记录
    query('DELETE FROM app_versions WHERE id = ?', [id]);

    res.json({ message: '版本删除成功' });
  } catch (error) {
    console.error('删除版本失败:', error);
    res.status(500).json({ error: '删除版本失败' });
  }
};

/**
 * 获取当前活跃版本（管理员）
 * GET /api/version/current
 */
export const getCurrentVersion = async (req: Request, res: Response) => {
  try {
    const { platform = 'android' } = req.query;

    const result = query(
      `SELECT v.*, u.real_name as created_by_name 
       FROM app_versions v
       LEFT JOIN users u ON v.created_by = u.id
       WHERE v.platform = ? AND v.is_active = 1
       LIMIT 1`,
      [platform]
    );

    if (result.rows.length === 0) {
      return res.json(null);
    }

    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取当前版本失败:', error);
    res.status(500).json({ error: '获取当前版本失败' });
  }
};
