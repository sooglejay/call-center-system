import fs from 'fs';
import path from 'path';
import { query } from '../config/database';

// 数据文件路径
const MOCK_DATA_PATH = path.join(__dirname, '../../data/mock_customers.csv');
const REAL_DATA_PATH = path.join(__dirname, '../../data/real_customers.csv');

// CSV 解析函数
const parseCSV = (content: string): Array<{
  first_name: string;
  last_name: string;
  phone: string;
  email: string;
  address: string;
}> => {
  const lines = content.trim().split('\n');
  if (lines.length < 2) return [];
  
  // 跳过标题行
  const dataLines = lines.slice(1);
  const results: Array<{
    first_name: string;
    last_name: string;
    phone: string;
    email: string;
    address: string;
  }> = [];
  
  for (const line of dataLines) {
    // 简单 CSV 解析（处理引号内的逗号）
    const values: string[] = [];
    let current = '';
    let inQuotes = false;
    
    for (const char of line) {
      if (char === '"') {
        inQuotes = !inQuotes;
      } else if (char === ',' && !inQuotes) {
        values.push(current.trim());
        current = '';
      } else {
        current += char;
      }
    }
    values.push(current.trim());
    
    if (values.length >= 5) {
      results.push({
        first_name: values[0] || '',
        last_name: values[1] || '',
        phone: values[2] || '',
        email: values[3] || '',
        address: values[4] || ''
      });
    }
  }
  
  return results;
};

// 检查真实数据是否存在
export const hasRealData = (): boolean => {
  return fs.existsSync(REAL_DATA_PATH);
};

// 获取数据状态
export const getDataStatus = (): { hasMockData: boolean; hasRealData: boolean; mockCount: number; realCount: number } => {
  let mockCount = 0;
  let realCount = 0;
  
  if (fs.existsSync(MOCK_DATA_PATH)) {
    const content = fs.readFileSync(MOCK_DATA_PATH, 'utf-8');
    mockCount = parseCSV(content).length;
  }
  
  if (fs.existsSync(REAL_DATA_PATH)) {
    const content = fs.readFileSync(REAL_DATA_PATH, 'utf-8');
    realCount = parseCSV(content).length;
  }
  
  return {
    hasMockData: mockCount > 0,
    hasRealData: realCount > 0,
    mockCount,
    realCount
  };
};

// 导入客户数据（从 CSV）
export const importCustomersFromCSV = (
  dataAccessType: 'mock' | 'real',
  assignedTo?: number
): { imported: number; errors: string[] } => {
  const filePath = dataAccessType === 'mock' ? MOCK_DATA_PATH : REAL_DATA_PATH;
  const errors: string[] = [];
  let imported = 0;
  
  if (!fs.existsSync(filePath)) {
    return { imported: 0, errors: ['数据文件不存在'] };
  }
  
  const content = fs.readFileSync(filePath, 'utf-8');
  const customers = parseCSV(content);
  
  for (const customer of customers) {
    try {
      // 组合姓名
      const name = `${customer.first_name}${customer.last_name}`.trim() || customer.first_name || '未知';
      
      // 检查是否已存在
      const existing = query(
        'SELECT id FROM customers WHERE phone = $1',
        [customer.phone]
      );
      
      if (existing.rows.length > 0) {
        continue; // 跳过已存在的
      }
      
      // 插入客户
      query(
        `INSERT INTO customers (name, phone, email, address, status, assigned_to, data_source, created_at, updated_at)
         VALUES ($1, $2, $3, $4, 'pending', $5, $6, datetime('now'), datetime('now'))`,
        [name, customer.phone, customer.email, customer.address, assignedTo || null, dataAccessType]
      );
      
      imported++;
    } catch (error: any) {
      errors.push(`导入失败: ${customer.first_name} ${customer.last_name} - ${error.message}`);
    }
  }
  
  return { imported, errors };
};

// 保存真实客户数据（管理员上传）
export const saveRealCustomerData = (content: string): { saved: boolean; count: number; error?: string } => {
  try {
    // 验证 CSV 格式
    const customers = parseCSV(content);
    if (customers.length === 0) {
      return { saved: false, count: 0, error: 'CSV 文件格式错误或没有数据' };
    }
    
    // 确保目录存在
    const dir = path.dirname(REAL_DATA_PATH);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    
    // 保存文件
    fs.writeFileSync(REAL_DATA_PATH, content, 'utf-8');
    
    return { saved: true, count: customers.length };
  } catch (error: any) {
    return { saved: false, count: 0, error: error.message };
  }
};

// 清除客户数据
export const clearCustomersBySource = (dataSource: 'mock' | 'real'): number => {
  const result = query('DELETE FROM customers WHERE data_source = $1', [dataSource]);
  return result.rowCount;
};
