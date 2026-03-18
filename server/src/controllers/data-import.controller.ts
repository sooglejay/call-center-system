import { Request, Response, RequestHandler } from 'express';
import { query } from '../config/database';
import multer from 'multer';
import csv from 'csv-parser';
import { Readable } from 'stream';

// 配置内存存储
const storage = multer.memoryStorage();
const upload = multer({ storage });

// 导出中间件（添加类型注解）
export const uploadMiddleware: RequestHandler = upload.single('file');

// CSV 解析函数
const parseCSV = (buffer: Buffer): Promise<any[]> => {
  return new Promise((resolve, reject) => {
    const results: any[] = [];
    const stream = Readable.from(buffer.toString('utf-8'));
    
    stream
      .pipe(csv())
      .on('data', (data) => results.push(data))
      .on('end', () => resolve(results))
      .on('error', (error) => reject(error));
  });
};

// 上传并导入真实客户数据
export const importRealCustomers = async (req: any, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '请上传CSV文件' });
    }
    
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以导入真实数据' });
    }
    
    // 解析 CSV
    const records = await parseCSV(req.file.buffer);
    console.log(`[CSV导入] 解析到 ${records.length} 条记录`);
    
    // 验证必要字段
    const requiredFields = ['name', 'phone'];
    const firstRecord = records[0] || {};
    const missingFields = requiredFields.filter(f => !(f in firstRecord));
    
    if (missingFields.length > 0) {
      return res.status(400).json({ 
        error: `CSV缺少必要字段: ${missingFields.join(', ')}`,
        required_fields: requiredFields,
        found_fields: Object.keys(firstRecord)
      });
    }
    
    // 导入数据
    let imported = 0;
    let duplicates = 0;
    let errors = 0;
    const errorDetails: string[] = [];
    
    for (const record of records) {
      try {
        // 检查是否重复
        const existing = await query(
          'SELECT id FROM customers WHERE phone = $1 AND data_source = $2',
          [record.phone, 'real']
        );
        
        if (existing.rows.length > 0) {
          duplicates++;
          continue;
        }
        
        // 插入数据
        await query(
          `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, assigned_to, data_source, imported_by)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'real', $10)`,
          [
            record.name || '',
            record.phone || '',
            record.email || '',
            record.company || '',
            record.address || '',
            record.notes || '',
            record.status || 'pending',
            parseInt(record.priority) || 1,
            record.assigned_to ? parseInt(record.assigned_to) : null,
            adminId
          ]
        );
        imported++;
      } catch (err) {
        errors++;
        errorDetails.push(`行 ${records.indexOf(record) + 1}: ${err instanceof Error ? err.message : '未知错误'}`);
      }
    }
    
    console.log(`[CSV导入] 完成: 成功=${imported}, 重复=${duplicates}, 错误=${errors}`);
    
    res.json({
      message: '导入完成',
      summary: {
        total: records.length,
        imported,
        duplicates,
        errors
      },
      error_details: errorDetails.slice(0, 10) // 只返回前10个错误
    });
  } catch (error) {
    console.error('[CSV导入] 错误:', error);
    res.status(500).json({ error: '服务器错误', detail: error instanceof Error ? error.message : '未知错误' });
  }
};

// 清空 mock 数据
export const clearMockData = async (req: any, res: Response) => {
  try {
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以清空数据' });
    }
    
    const result = await query('DELETE FROM customers WHERE data_source = $1', ['mock']);
    
    res.json({
      message: 'Mock数据已清空',
      deleted_count: result.rowCount || 0
    });
  } catch (error) {
    console.error('[清空Mock数据] 错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 清空真实数据
export const clearRealData = async (req: any, res: Response) => {
  try {
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以清空数据' });
    }
    
    const result = await query('DELETE FROM customers WHERE data_source = $1', ['real']);
    
    res.json({
      message: '真实数据已清空',
      deleted_count: result.rowCount || 0
    });
  } catch (error) {
    console.error('[清空真实数据] 错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 获取数据统计
export const getDataStats = async (req: any, res: Response) => {
  try {
    // 获取各数据源的客户数量
    const mockCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['mock']);
    const realCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['real']);
    
    // 获取各权限类型的用户数量
    const mockUsers = await query('SELECT COUNT(*) as count FROM users WHERE data_access_type = $1', ['mock']);
    const realUsers = await query('SELECT COUNT(*) as count FROM users WHERE data_access_type = $1', ['real']);
    
    // 获取详细用户列表
    const users = await query('SELECT id, username, real_name, role, data_access_type FROM users ORDER BY role, real_name');
    
    res.json({
      customers: {
        mock: mockCount.rows[0]?.count || 0,
        real: realCount.rows[0]?.count || 0
      },
      users: {
        mock: mockUsers.rows[0]?.count || 0,
        real: realUsers.rows[0]?.count || 0
      },
      user_list: users.rows
    });
  } catch (error) {
    console.error('[获取数据统计] 错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

// 初始化 mock 数据
export const initMockData = async (req: any, res: Response) => {
  try {
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以初始化数据' });
    }
    
    // 检查是否已有 mock 数据
    const existingCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['mock']);
    if ((existingCount.rows[0]?.count || 0) > 0) {
      return res.status(400).json({ error: 'Mock数据已存在，请先清空再初始化' });
    }
    
    // 读取并导入 mock_customers.csv
    const fs = await import('fs');
    const path = await import('path');
    const csvPath = path.join(process.cwd(), 'data', 'mock_customers.csv');
    
    if (!fs.existsSync(csvPath)) {
      return res.status(400).json({ error: 'Mock数据文件不存在' });
    }
    
    const fileContent = fs.readFileSync(csvPath, 'utf-8');
    const records: any[] = [];
    
    await new Promise((resolve, reject) => {
      const stream = Readable.from(fileContent);
      stream
        .pipe(csv())
        .on('data', (data) => records.push(data))
        .on('end', resolve)
        .on('error', reject);
    });
    
    let imported = 0;
    for (const record of records) {
      try {
        await query(
          `INSERT INTO customers (name, phone, email, company, status, data_source)
           VALUES ($1, $2, $3, $4, $5, 'mock')`,
          [record.name, record.phone, record.email || '', record.company || '', 'pending']
        );
        imported++;
      } catch (err) {
        console.error('导入失败:', record.name, err);
      }
    }
    
    res.json({
      message: 'Mock数据初始化完成',
      imported
    });
  } catch (error) {
    console.error('[初始化Mock数据] 错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
