import { Pool } from 'pg';
import dotenv from 'dotenv';

dotenv.config();

// 检查是否使用 PostgreSQL
const usePostgres = process.env.DATABASE_URL || (process.env.DB_HOST && process.env.DB_HOST !== 'localhost');

// 内存数据库实现
interface User {
  id: number;
  username: string;
  password: string;
  role: string;
  real_name?: string;
  phone?: string;
  email?: string;
  department?: string;
  position?: string;
  avatar_url?: string;
  status: string;
  created_at: string;
}

interface Customer {
  id: number;
  name: string;
  phone: string;
  email?: string;
  company?: string;
  address?: string;
  notes?: string;
  status: string;
  priority: number;
  assigned_to?: number;
  created_at: string;
  updated_at: string;
}

interface Call {
  id: number;
  customer_id?: number;
  agent_id?: number;
  customer_phone: string;
  customer_name?: string;
  twilio_call_sid?: string;
  status: string;
  call_result?: string;
  call_notes?: string;
  recording_url?: string;
  recording_duration?: number;
  started_at?: string;
  ended_at?: string;
  created_at: string;
  updated_at: string;
}

interface Task {
  id: number;
  title: string;
  description?: string;
  assigned_to?: number;
  customer_id?: number;
  priority: string;
  status: string;
  due_date?: string;
  completed_at?: string;
  created_by: number;
  created_at: string;
  updated_at: string;
}

class MemoryDB {
  private data: {
    users: User[];
    customers: Customer[];
    calls: Call[];
    tasks: Task[];
    configs: Record<string, { id: number; config_key: string; config_value: string; description?: string }>;
    voicemail_records: any[];
    sms_records: any[];
    unanswered_records: any[];
  };

  constructor() {
    this.data = {
      users: [
        {
          id: 1,
          username: 'admin',
          password: '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjIXDZJ8JQYVZlHKbXRKJVB0eWlxGqC', // admin123
          role: 'admin',
          real_name: '系统管理员',
          status: 'active',
          created_at: new Date().toISOString()
        },
        {
          id: 2,
          username: 'agent',
          password: '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjIXDZJ8JQYVZlHKbXRKJVB0eWlxGqC', // agent123
          role: 'agent',
          real_name: '客服专员',
          status: 'active',
          created_at: new Date().toISOString()
        }
      ],
      customers: [
        { 
          id: 1, 
          name: '张三', 
          phone: '13800138001', 
          email: 'zhangsan@example.com',
          company: 'ABC科技公司', 
          status: 'pending',
          priority: 1,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        },
        { 
          id: 2, 
          name: '李四', 
          phone: '13800138002', 
          email: 'lisi@example.com',
          company: 'XYZ贸易公司', 
          status: 'pending',
          priority: 2,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        },
        { 
          id: 3, 
          name: '王五', 
          phone: '13800138003',
          email: 'wangwu@example.com',
          status: 'contacted',
          priority: 1,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        }
      ],
      calls: [],
      tasks: [],
      configs: {
        twilio_account_sid: { id: 1, config_key: 'twilio_account_sid', config_value: '', description: 'Twilio Account SID' },
        twilio_auth_token: { id: 2, config_key: 'twilio_auth_token', config_value: '', description: 'Twilio Auth Token' },
        twilio_phone_number: { id: 3, config_key: 'twilio_phone_number', config_value: '', description: 'Twilio发信号码' },
        twilio_callback_url: { id: 4, config_key: 'twilio_callback_url', config_value: '', description: 'Twilio Webhook回调URL' },
        sms_enabled: { id: 5, config_key: 'sms_enabled', config_value: 'false', description: '是否启用短信功能' },
        sms_template_unanswered: { id: 6, config_key: 'sms_template_unanswered', config_value: '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}', description: '未接通短信模板' },
        voicemail_enabled: { id: 7, config_key: 'voicemail_enabled', config_value: 'false', description: '是否启用语音信箱' },
        voicemail_greeting: { id: 8, config_key: 'voicemail_greeting', config_value: '您好，我现在无法接听您的电话，请在听到提示音后留言。', description: '语音信箱问候语' }
      },
      voicemail_records: [],
      sms_records: [],
      unanswered_records: []
    };
    console.log('✅ 内存数据库已初始化');
  }

  getNextId(table: keyof typeof this.data): number {
    const items = this.data[table] as any[];
    if (!Array.isArray(items)) return 1;
    return items.length > 0 ? Math.max(...items.map((i: any) => i.id || 0)) + 1 : 1;
  }

  // 模拟 pg 的 query 方法
  async query(text: string, params?: any[]): Promise<{ rows: any[]; rowCount: number }> {
    const sql = text.toLowerCase().trim();
    
    // SELECT 查询
    if (sql.startsWith('select')) {
      return this.handleSelect(sql, params);
    }
    
    // INSERT 插入
    if (sql.startsWith('insert')) {
      return this.handleInsert(sql, params);
    }
    
    // UPDATE 更新
    if (sql.startsWith('update')) {
      return this.handleUpdate(sql, params);
    }
    
    // DELETE 删除
    if (sql.startsWith('delete')) {
      return this.handleDelete(sql, params);
    }
    
    return { rows: [], rowCount: 0 };
  }

  private handleSelect(sql: string, params?: any[]): { rows: any[]; rowCount: number } {
    // 解析表名
    const tableMatch = sql.match(/from\s+(\w+)/);
    const tableName = tableMatch ? tableMatch[1] : '';
    
    if (!tableName || !(tableName in this.data)) {
      return { rows: [], rowCount: 0 };
    }
    
    let data = (this.data as any)[tableName];
    
    // 处理 WHERE 条件
    if (sql.includes('where')) {
      if (tableName === 'users') {
        if (sql.includes('username')) {
          const username = params?.[0];
          const status = params?.[1];
          data = data.filter((u: User) => u.username === username && (!status || u.status === status));
        } else if (sql.includes('id')) {
          const id = params?.[0];
          data = data.filter((u: User) => u.id === id);
        }
      } else if (tableName === 'system_configs' && sql.includes('config_key')) {
        const key = params?.[0];
        const config = this.data.configs[key];
        return { rows: config ? [config] : [], rowCount: config ? 1 : 0 };
      }
    }
    
    // 处理 ORDER BY
    if (sql.includes('order by') && sql.includes('created_at')) {
      const isDesc = sql.includes('desc');
      data = [...data].sort((a: any, b: any) => {
        const timeA = new Date(a.created_at || 0).getTime();
        const timeB = new Date(b.created_at || 0).getTime();
        return isDesc ? timeB - timeA : timeA - timeB;
      });
    }
    
    // 处理 LIMIT
    if (sql.includes('limit')) {
      const limitMatch = sql.match(/limit\s+\$(\d+)/);
      if (limitMatch) {
        const limitIndex = parseInt(limitMatch[1]) - 1;
        const limit = params?.[limitIndex];
        if (limit) {
          data = data.slice(0, limit);
        }
      }
    }
    
    return { rows: data, rowCount: data.length };
  }

  private handleInsert(sql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = sql.match(/into\s+(\w+)/);
    const tableName = tableMatch ? tableMatch[1] : '';
    
    if (!tableName || !(tableName in this.data)) {
      return { rows: [], rowCount: 0 };
    }
    
    const newItem: any = { id: this.getNextId(tableName as any) };
    
    // 根据表名设置默认值
    if (tableName === 'calls') {
      newItem.customer_id = params?.[0];
      newItem.agent_id = params?.[1];
      newItem.customer_phone = params?.[2];
      newItem.status = params?.[3] || 'pending';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    } else if (tableName === 'customers') {
      newItem.name = params?.[0];
      newItem.phone = params?.[1];
      newItem.email = params?.[2];
      newItem.company = params?.[3];
      newItem.status = params?.[4] || 'pending';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    } else if (tableName === 'users') {
      newItem.username = params?.[0];
      newItem.password = params?.[1];
      newItem.role = params?.[2] || 'agent';
      newItem.real_name = params?.[3];
      newItem.status = 'active';
      newItem.created_at = new Date().toISOString();
      newItem.updated_at = new Date().toISOString();
    }
    
    (this.data as any)[tableName].push(newItem);
    return { rows: [newItem], rowCount: 1 };
  }

  private handleUpdate(sql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = sql.match(/update\s+(\w+)/);
    const tableName = tableMatch ? tableMatch[1] : '';
    
    if (!tableName || !(tableName in this.data)) {
      return { rows: [], rowCount: 0 };
    }
    
    // 简单处理：找到 id 匹配的记录并更新
    const idMatch = sql.match(/where\s+id\s*=\s*\$(\d+)/);
    if (idMatch) {
      const idIndex = parseInt(idMatch[1]) - 1;
      const id = params?.[idIndex];
      
      const items = (this.data as any)[tableName];
      const item = items.find((i: any) => i.id === id);
      
      if (item) {
        item.updated_at = new Date().toISOString();
        return { rows: [item], rowCount: 1 };
      }
    }
    
    return { rows: [], rowCount: 0 };
  }

  private handleDelete(sql: string, params?: any[]): { rows: any[]; rowCount: number } {
    const tableMatch = sql.match(/from\s+(\w+)/);
    const tableName = tableMatch ? tableMatch[1] : '';
    
    if (!tableName || !(tableName in this.data)) {
      return { rows: [], rowCount: 0 };
    }
    
    const idMatch = sql.match(/where\s+id\s*=\s*\$(\d+)/);
    if (idMatch) {
      const idIndex = parseInt(idMatch[1]) - 1;
      const id = params?.[idIndex];
      
      const items = (this.data as any)[tableName];
      const index = items.findIndex((i: any) => i.id === id);
      
      if (index > -1) {
        items.splice(index, 1);
        return { rows: [], rowCount: 1 };
      }
    }
    
    return { rows: [], rowCount: 0 };
  }
}

// 创建内存数据库实例
const memoryDB = new MemoryDB();

// 如果有 PostgreSQL 配置，使用 PostgreSQL，否则使用内存数据库
let pool: any;

if (usePostgres) {
  pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432'),
    database: process.env.DB_NAME || 'postgres',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || '',
  });
  console.log('✅ 使用 PostgreSQL 数据库');
} else {
  pool = memoryDB;
  console.log('✅ 使用内存数据库（演示模式）');
}

export default pool;
export const query = (text: string, params?: any[]) => pool.query(text, params);
