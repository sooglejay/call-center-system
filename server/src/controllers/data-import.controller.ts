import { Request, Response, RequestHandler } from 'express';
import { query } from '../config/database';
import multer from 'multer';
import csv from 'csv-parser';
import { Readable } from 'stream';

const DEFAULT_CUSTOMER_TAG = '未打标客户';

const normalizeCustomerTag = (tag?: string | null) => {
  const normalized = tag?.trim();
  return normalized ? normalized : DEFAULT_CUSTOMER_TAG;
};

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

// 检测复合字段（包含分隔符的字段）
const detectCompositeField = (columnData: any[]): { 
  isComposite: boolean; 
  separator?: string; 
  partCount?: number;
  sampleParts?: string[][];
} => {
  if (columnData.length === 0) return { isComposite: false };
  
  const separators = [',', '，', ';', '；', '|', '/', '\\', '\t'];
  
  // 检查前10行
  const sampleData = columnData.slice(0, Math.min(10, columnData.length));
  
  for (const sep of separators) {
    const hasSeparator = sampleData.every(row => {
      const val = String(row || '');
      return val.includes(sep) && val.split(sep).length > 1;
    });
    
    if (hasSeparator) {
      const sampleParts = sampleData.map(row => {
        return String(row || '').split(sep).map(p => p.trim());
      });
      
      // 检查所有行的拆分部分数量是否一致
      const partCount = sampleParts[0].length;
      const isConsistent = sampleParts.every(parts => parts.length === partCount);
      
      if (isConsistent && partCount > 1) {
        return {
          isComposite: true,
          separator: sep,
          partCount,
          sampleParts
        };
      }
    }
  }
  
  return { isComposite: false };
};

// 智能识别子字段类型
const identifySubFieldType = (samples: string[]): string => {
  const nonEmptySamples = samples.filter(s => s && s.trim());
  if (nonEmptySamples.length === 0) return 'field';
  
  // 检查是否为统一的值（如机构名）
  const uniqueValues = new Set(nonEmptySamples);
  if (uniqueValues.size === 1 && nonEmptySamples.length > 1) {
    return 'company'; // 可能是机构名
  }
  
  // 检查是否为中文姓名（2-4个汉字）
  const chineseNameRegex = /^[\u4e00-\u9fa5·•]{2,10}$/;
  if (nonEmptySamples.every(s => chineseNameRegex.test(s))) {
    return 'name';
  }
  
  // 检查是否为英文姓名
  const englishNameRegex = /^[a-zA-Z\s\-]+$/;
  if (nonEmptySamples.every(s => englishNameRegex.test(s))) {
    return 'name_en';
  }
  
  // 检查是否为邮箱
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (nonEmptySamples.every(s => emailRegex.test(s))) {
    return 'email';
  }
  
  // 检查是否为手机号
  const phoneRegex = /^[\d\s\-\+\(\)]{7,15}$/;
  if (nonEmptySamples.every(s => phoneRegex.test(s))) {
    return 'phone';
  }
  
  return 'field';
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
    
    // 检测复合字段
    const compositeFields: Record<string, any> = {};
    
    for (const col of columns) {
      const columnData = data.map(row => row[col]);
      const detected = detectCompositeField(columnData);
      
      if (detected.isComposite && detected.sampleParts) {
        // 识别每个子字段的类型
        const subFields = [];
        for (let i = 0; i < detected.partCount!; i++) {
          const samples = detected.sampleParts.map(parts => parts[i]);
          const fieldType = identifySubFieldType(samples);
          
          subFields.push({
            key: `${col}_part_${i + 1}`,
            label: getSubFieldLabel(fieldType, i),
            type: fieldType,
            samples: samples.slice(0, 5)
          });
        }
        
        compositeFields[col] = {
          separator: detected.separator,
          partCount: detected.partCount,
          subFields
        };
      }
    }
    
    console.log(`[CSV预览] 检测到 ${Object.keys(compositeFields).length} 个复合字段`);
    
    // 智能匹配建议（包含复合字段的子字段）
    const suggestions = suggestColumnMapping(columns, compositeFields);
    
    res.json({
      columns,                          // CSV 文件的列名
      preview: data.slice(0, 10),       // 前10行预览数据
      total_rows: data.length,          // 总行数
      system_fields: SYSTEM_FIELDS,     // 系统支持的字段
      suggestions,                      // 智能匹配建议
      composite_fields: compositeFields, // 复合字段信息
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

// 获取子字段标签
const getSubFieldLabel = (fieldType: string, index: number): string => {
  const labels: Record<string, string> = {
    'company': '公司/机构',
    'name': '姓名',
    'name_en': '英文名',
    'phone': '电话',
    'email': '邮箱',
    'address': '地址',
    'field': `字段${index + 1}`
  };
  
  return labels[fieldType] || `字段${index + 1}`;
};

// 智能列匹配建议（支持复合字段）
const suggestColumnMapping = (csvColumns: string[], compositeFields: Record<string, any> = {}): Record<string, string> => {
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
      /^姓名$/
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
      /所属公司/i,
      /机构/i,
      /商城/i
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
  
  // 先处理普通列
  for (const csvCol of csvColumns) {
    // 跳过复合字段
    if (compositeFields[csvCol]) continue;
    
    for (const [systemField, regexList] of Object.entries(patterns)) {
      if (regexList.some(regex => regex.test(csvCol))) {
        if (!mapping[systemField]) {
          mapping[systemField] = csvCol;
        }
        break;
      }
    }
  }
  
  // 再处理复合字段的子字段
  for (const [parentCol, composite] of Object.entries(compositeFields)) {
    if (!composite.subFields) continue;
    
    for (const subField of composite.subFields) {
      const type = subField.type;
      
      // 根据识别的类型自动映射
      if (type === 'name' && !mapping.name) {
        mapping.name = subField.key;
      } else if (type === 'company' && !mapping.company) {
        mapping.company = subField.key;
      } else if (type === 'name_en' && !mapping['name_en']) {
        // name_en 不在 SYSTEM_FIELDS 中，但我们可以在 notes 中存储
        // 或者忽略
      } else if (type === 'phone' && !mapping.phone) {
        mapping.phone = subField.key;
      } else if (type === 'email' && !mapping.email) {
        mapping.email = subField.key;
      }
    }
  }
  
  return mapping;
}; 

// 执行导入（带列映射）
export const importWithMapping = async (req: any, res: Response) => {
  try {
    let { column_mapping, data_source = 'real', composite_fields, assigned_to, tag } = req.body;
    const customerTag = normalizeCustomerTag(tag);
    
    // 如果参数是字符串（来自 FormData），尝试解析 JSON
    if (typeof column_mapping === 'string') {
      try {
        column_mapping = JSON.parse(column_mapping);
      } catch {
        return res.status(400).json({ error: '列映射格式错误，请提供有效的 JSON' });
      }
    }
    
    if (typeof composite_fields === 'string') {
      try {
        composite_fields = JSON.parse(composite_fields);
      } catch {
        composite_fields = {};
      }
    }
    
    // 解析 assigned_to 参数
    let assignedToId: number | null = null;
    if (assigned_to) {
      assignedToId = parseInt(assigned_to as string);
      if (isNaN(assignedToId)) {
        return res.status(400).json({ error: 'assigned_to 参数必须是有效的用户ID' });
      }
      
      // 验证客服是否存在
      const agentResult = await query('SELECT id, real_name, role FROM users WHERE id = $1', [assignedToId]);
      if (agentResult.rows.length === 0) {
        return res.status(400).json({ error: '指定的客服不存在' });
      }
      if (agentResult.rows[0].role !== 'agent') {
        return res.status(400).json({ error: '指定的用户不是客服角色' });
      }
      console.log(`[CSV导入] 将分配给客服: ${agentResult.rows[0].real_name} (ID=${assignedToId})`);
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
    
    if (!req.file) {
      return res.status(400).json({ error: '请上传CSV文件' });
    }
    
    const { data } = await parseCSVWithColumns(req.file.buffer);
    
    console.log(`[CSV导入] 解析到 ${data.length} 条记录`);
    console.log(`[CSV导入] 列映射:`, column_mapping);
    console.log(`[CSV导入] 复合字段:`, composite_fields);
    console.log(`[CSV导入] 分配客服ID:`, assignedToId);
    
    // 导入数据
    let imported = 0;
    let duplicates = 0;
    let errors = 0;
    const errorDetails: string[] = [];
    
    for (let i = 0; i < data.length; i++) {
      const record = data[i];
      try {
        // 根据映射提取数据
        const customerData: any = {};
        
        for (const [systemField, sourceField] of Object.entries(column_mapping)) {
          if (!sourceField) continue;
          
          // 检查是否是复合字段的子字段
          if (typeof sourceField === 'string' && sourceField.includes('_part_')) {
            const match = sourceField.match(/^(.+)_part_(\d+)$/);
            if (match && composite_fields[match[1]]) {
              const parentCol = match[1];
              const partIndex = parseInt(match[2]) - 1;
              const separator = composite_fields[parentCol].separator || ',';
              
              const parentValue = record[parentCol] || '';
              const parts = parentValue.split(separator).map((p: string) => p.trim());
              
              if (parts[partIndex]) {
                customerData[systemField] = parts[partIndex];
              }
            }
          } else {
            // 普通字段
            customerData[systemField] = record[sourceField as string] || '';
          }
        }
        
        const name = customerData.name || '';
        const phone = customerData.phone || '';
        
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
        
        // 插入数据（包含 assigned_to 字段）
        await query(
          `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, data_source, imported_by, assigned_to, tag, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, datetime('now'), datetime('now'))`,
          [
            name, 
            phone, 
            customerData.email || '', 
            customerData.company || '', 
            customerData.address || '', 
            customerData.notes || '', 
            customerData.status || 'pending', 
            customerData.priority || 1, 
            data_source, 
            adminId,
            assignedToId,
            customerTag
          ]
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
        skipped: data.length - imported - duplicates - errors,
        assigned_to: assignedToId,
        tag: customerTag
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
    const { data_source = 'real', tag } = req.body;
    const customerTag = normalizeCustomerTag(tag);
    
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
          `INSERT INTO customers (name, phone, email, company, address, notes, status, priority, data_source, imported_by, tag, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, 'pending', 1, $7, $8, $9, datetime('now'), datetime('now'))`,
          [name, phone, email, company, address, notes, data_source, adminId, customerTag]
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
      summary: { total: data.length, imported, duplicates, errors, tag: customerTag },
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
          `INSERT INTO customers (name, phone, email, company, status, data_source, tag, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, 'mock', $6, datetime('now'), datetime('now'))`,
          [record.name, record.phone, record.email || '', record.company || '', 'pending', DEFAULT_CUSTOMER_TAG]
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
