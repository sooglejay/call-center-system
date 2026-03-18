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

// 系统支持的字段定义
export const SYSTEM_FIELDS = [
  { key: 'name', label: '姓名', required: true },
  { key: 'phone', label: '电话', required: true },
  { key: 'email', label: '邮箱', required: false },
  { key: 'company', label: '公司', required: false },
  { key: 'address', label: '地址', required: false },
  { key: 'notes', label: '备注', required: false },
  { key: 'status', label: '状态', required: false },
  { key: 'priority', label: '优先级', required: false },
] as const;

// CSV 解析函数 - 返回列名和数据
const parseCSVWithColumns = (buffer: Buffer): Promise<{ columns: string[], data: any[] }> => {
  return new Promise((resolve, reject) => {
    const data: any[] = [];
    let columns: string[] = [];
    
    const stream = Readable.from(buffer.toString('utf-8'));
    
    stream
      .pipe(csv())
      .on('headers', (headers: string[]) => {
        columns = headers;
      })
      .on('data', (row) => data.push(row))
      .on('end', () => resolve({ columns, data }))
      .on('error', (error) => reject(error));
  });
};

// 预览 CSV 文件 - 返回列名和前几行数据
export const previewCSV = async (req: any, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '请上传CSV文件' });
    }
    
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以导入数据' });
    }
    
    // 解析 CSV
    const { columns, data } = await parseCSVWithColumns(req.file.buffer);
    
    console.log(`[CSV预览] 解析到 ${columns.length} 列, ${data.length} 行`);
    
    // 智能匹配建议
    const suggestions = suggestColumnMapping(columns);
    
    res.json({
      columns,                          // CSV 文件的列名
      preview: data.slice(0, 10),       // 前10行预览数据
      total_rows: data.length,          // 总行数
      system_fields: SYSTEM_FIELDS,     // 系统支持的字段
      suggestions,                      // 智能匹配建议
      has_required_fields: columns.some(c => 
        suggestions.name || 
        c.toLowerCase().includes('name') || 
        c.toLowerCase().includes('姓名') ||
        c.toLowerCase().includes('客户')
      ) && columns.some(c => 
        suggestions.phone || 
        c.toLowerCase().includes('phone') || 
        c.toLowerCase().includes('电话') ||
        c.toLowerCase().includes('手机')
      )
    });
  } catch (error) {
    console.error('[CSV预览] 错误:', error);
    res.status(500).json({ error: '服务器错误', detail: error instanceof Error ? error.message : '未知错误' });
  }
};

// 智能列匹配建议
const suggestColumnMapping = (csvColumns: string[]): Record<string, string> => {
  const mapping: Record<string, string> = {};
  
  const patterns: Record<string, RegExp[]> = {
    name: [
      /^name$/i, 
      /姓名/i, 
      /客户名/i, 
      /名字/i, 
      /^名$/i,
      /联系人/i,
      /顾客名/i,
      /用户名/i,
      /客户姓名/i,
      /^姓名$/i
    ],
    phone: [
      /phone/i, 
      /电话/i, 
      /手机/i, 
      /mobile/i, 
      /联系方式/i, 
      /^tel/i,
      /联系电话/i,
      /手机号/i,
      /电话号码/i,
      /手机号码/i
    ],
    email: [
      /email/i, 
      /邮件/i, 
      /邮箱/i, 
      /e-mail/i,
      /电子邮箱/i,
      /电子邮箱地址/i
    ],
    company: [
      /company/i, 
      /公司/i, 
      /企业/i, 
      /单位/i, 
      /firm/i,
      /企业名称/i,
      /公司名称/i,
      /所属公司/i
    ],
    address: [
      /address/i, 
      /地址/i, 
      /住址/i, 
      /位置/i,
      /详细地址/i,
      /联系地址/i
    ],
    notes: [
      /notes/i, 
      /备注/i, 
      /说明/i, 
      /note/i, 
      /描述/i, 
      /comment/i,
      /其他说明/i,
      /备注信息/i,
      /附注/i
    ],
    status: [/status/i, /状态/i, /情况/i],
    priority: [/priority/i, /优先级/i, /级别/i, /重要/i],
  };
  
  for (const csvCol of csvColumns) {
    for (const [systemField, regexList] of Object.entries(patterns)) {
      if (regexList.some(regex => regex.test(csvCol))) {
        if (!mapping[systemField]) {
          mapping[systemField] = csvCol;
        }
        break;
      }
    }
  }
  
  return mapping;
};

// 执行导入（带列映射）
export const importWithMapping = async (req: any, res: Response) => {
  try {
    let { column_mapping, data_source = 'real' } = req.body;
    
    // 如果 column_mapping 是字符串（来自 FormData），尝试解析 JSON
    if (typeof column_mapping === 'string') {
      try {
        column_mapping = JSON.parse(column_mapping);
      } catch {
        return res.status(400).json({ error: '列映射格式错误，请提供有效的 JSON' });
      }
    }
    
    // 验证列映射
    if (!column_mapping || typeof column_mapping !== 'object') {
      return res.status(400).json({ error: '请提供列映射关系' });
    }
    
    // 检查必填字段
    if (!column_mapping.name || !column_mapping.phone) {
      return res.status(400).json({ error: '姓名和电话是必填字段，请确保已映射' });
    }
    
    const adminId = req.user.id;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以导入数据' });
    }
    
    // 从临时存储获取 CSV 数据（这里需要前端重新上传或使用 session 存储）
    // 为了简化，我们要求前端在导入时再次上传文件
    if (!req.file) {
      return res.status(400).json({ error: '请上传CSV文件' });
    }
    
    const { data } = await parseCSVWithColumns(req.file.buffer);
    
    console.log(`[CSV导入] 解析到 ${data.length} 条记录，使用映射:`, column_mapping);
    
    // 导入数据
    let imported = 0;
    let duplicates = 0;
    let errors = 0;
    const errorDetails: string[] = [];
    
    for (let i = 0; i < data.length; i++) {
      const record = data[i];
      try {
        // 根据映射提取数据
        const name = record[column_mapping.name] || '';
        const phone = record[column_mapping.phone] || '';
        
        // 跳过空行
        if (!name && !phone) {
          continue;
        }
        
        // 检查必填字段
        if (!name || !phone) {
          errors++;
          errorDetails.push(`行 ${i + 1}: 姓名或电话为空`);
          continue;
        }
        
        // 检查是否重复
        const existing = await query(
          'SELECT id FROM customers WHERE phone = $1 AND data_source = $2',
          [phone, data_source]
        );
        
        if (existing.rows.length > 0) {
          duplicates++;
          continue;
        }
        
        // 提取其他字段
        const email = column_mapping.email ? record[column_mapping.email] || '' : '';
        const company = column_mapping.company ? record[column_mapping.company] || '' : '';
        const address = column_mapping.address ? record[column_mapping.address] || '' : '';
        const notes = column_mapping.notes ? record[column_mapping.notes] || '' : '';
        const status = column_mapping.status ? record[column_mapping.status] || 'pending' : 'pending';
        const priority = column_mapping.priority ? parseInt(record[column_mapping.priority]) || 1 : 1;
        
        // 插入数据
        await query(
          `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, data_source, imported_by, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, datetime('now'), datetime('now'))`,
          [name, phone, email, company, address, notes, status, priority, data_source, adminId]
        );
        imported++;
      } catch (err) {
        errors++;
        errorDetails.push(`行 ${i + 1}: ${err instanceof Error ? err.message : '未知错误'}`);
        if (errorDetails.length >= 20) {
          errorDetails.push('... 更多错误已省略');
          break;
        }
      }
    }
    
    console.log(`[CSV导入] 完成: 成功=${imported}, 重复=${duplicates}, 错误=${errors}`);
    
    res.json({
      message: '导入完成',
      summary: {
        total: data.length,
        imported,
        duplicates,
        errors,
        skipped: data.length - imported - duplicates - errors
      },
      error_details: errorDetails.slice(0, 10)
    });
  } catch (error) {
    console.error('[CSV导入] 错误:', error);
    res.status(500).json({ error: '服务器错误', detail: error instanceof Error ? error.message : '未知错误' });
  }
};

// 保留原有的导入函数（兼容旧版本）
export const importRealCustomers = async (req: any, res: Response) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '请上传CSV文件' });
    }
    
    const adminId = req.user.id;
    const { data_source = 'real' } = req.body;
    
    // 验证管理员权限
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以导入数据' });
    }
    
    // 解析 CSV
    const { columns, data } = await parseCSVWithColumns(req.file.buffer);
    console.log(`[CSV导入] 解析到 ${data.length} 条记录，列: ${columns.join(', ')}`);
    
    // 智能匹配
    const mapping = suggestColumnMapping(columns);
    
    // 检查必填字段
    if (!mapping.name || !mapping.phone) {
      return res.status(400).json({ 
        error: '无法自动识别姓名和电话列，请使用预览功能手动映射',
        columns,
        suggestions: mapping
      });
    }
    
    // 导入数据
    let imported = 0;
    let duplicates = 0;
    let errors = 0;
    const errorDetails: string[] = [];
    
    for (let i = 0; i < data.length; i++) {
      const record = data[i];
      try {
        const name = record[mapping.name] || '';
        const phone = record[mapping.phone] || '';
        
        if (!name && !phone) continue;
        if (!name || !phone) {
          errors++;
          continue;
        }
        
        // 检查重复
        const existing = await query(
          'SELECT id FROM customers WHERE phone = $1 AND data_source = $2',
          [phone, data_source]
        );
        
        if (existing.rows.length > 0) {
          duplicates++;
          continue;
        }
        
        const email = mapping.email ? record[mapping.email] || '' : '';
        const company = mapping.company ? record[mapping.company] || '' : '';
        const address = mapping.address ? record[mapping.address] || '' : '';
        const notes = mapping.notes ? record[mapping.notes] || '' : '';
        
        await query(
          `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, data_source, imported_by, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, 'pending', 1, $7, $8, datetime('now'), datetime('now'))`,
          [name, phone, email, company, address, notes, data_source, adminId]
        );
        imported++;
      } catch (err) {
        errors++;
        if (errorDetails.length < 10) {
          errorDetails.push(`行 ${i + 1}: ${err instanceof Error ? err.message : '未知错误'}`);
        }
      }
    }
    
    res.json({
      message: '导入完成',
      summary: { total: data.length, imported, duplicates, errors },
      used_mapping: mapping,
      error_details: errorDetails
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
    const mockCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['mock']);
    const realCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['real']);
    const mockUsers = await query('SELECT COUNT(*) as count FROM users WHERE data_access_type = $1', ['mock']);
    const realUsers = await query('SELECT COUNT(*) as count FROM users WHERE data_access_type = $1', ['real']);
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
      user_list: users.rows,
      system_fields: SYSTEM_FIELDS
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
    
    const userResult = await query('SELECT role FROM users WHERE id = $1', [adminId]);
    if (userResult.rows[0]?.role !== 'admin') {
      return res.status(403).json({ error: '只有管理员可以初始化数据' });
    }
    
    const existingCount = await query('SELECT COUNT(*) as count FROM customers WHERE data_source = $1', ['mock']);
    if ((existingCount.rows[0]?.count || 0) > 0) {
      return res.status(400).json({ error: 'Mock数据已存在，请先清空再初始化' });
    }
    
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
          `INSERT INTO customers (name, phone, email, company, status, data_source, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, 'mock', datetime('now'), datetime('now'))`,
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
