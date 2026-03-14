// 内存数据库 - 用于快速演示
interface User {
  id: number;
  username: string;
  password: string;
  role: string;
  real_name?: string;
  phone?: string;
  email?: string;
  status: string;
}

interface Customer {
  id: number;
  name: string;
  phone: string;
  email?: string;
  company?: string;
  status: string;
  assigned_to?: number;
}

interface Call {
  id: number;
  customer_id?: number;
  agent_id?: number;
  customer_phone: string;
  customer_name?: string;
  status: string;
  call_result?: string;
  created_at: string;
}

class MemoryDB {
  users: User[] = [
    {
      id: 1,
      username: 'admin',
      password: '$2a$10$NgG1WV0QVLtPT1zDzDtE3eI9X1hJTOliq8ySP7tTPLWWV5F1YOd6K', // admin123
      role: 'admin',
      real_name: '系统管理员',
      status: 'active'
    },
    {
      id: 2,
      username: 'agent',
      password: '$2a$10$NgG1WV0QVLtPT1zDzDtE3eI9X1hJTOliq8ySP7tTPLWWV5F1YOd6K', // agent123
      role: 'agent',
      real_name: '客服专员',
      status: 'active'
    }
  ];
  
  customers: Customer[] = [
    { id: 1, name: '张三', phone: '13800138001', company: 'ABC公司', status: 'pending' },
    { id: 2, name: '李四', phone: '13800138002', company: 'XYZ公司', status: 'pending' },
    { id: 3, name: '王五', phone: '13800138003', status: 'contacted' }
  ];
  
  calls: Call[] = [];
  configs: Record<string, string> = {
    twilio_account_sid: '',
    twilio_auth_token: '',
    twilio_phone_number: '',
    sms_enabled: 'false',
    voicemail_enabled: 'false'
  };

  private nextId(table: string): number {
    const items = (this as any)[table];
    return items.length > 0 ? Math.max(...items.map((i: any) => i.id)) + 1 : 1;
  }

  query(text: string, params?: any[]): any {
    // 简单模拟 SQL 查询
    const sql = text.toLowerCase();
    
    // SELECT * FROM users WHERE username = $1
    if (sql.includes('select') && sql.includes('from users')) {
      if (sql.includes('where username')) {
        const username = params?.[0];
        const user = this.users.find(u => u.username === username && u.status === 'active');
        return { rows: user ? [user] : [], rowCount: user ? 1 : 0 };
      }
      if (sql.includes('where id')) {
        const id = params?.[0];
        const user = this.users.find(u => u.id === id);
        return { rows: user ? [user] : [], rowCount: user ? 1 : 0 };
      }
      return { rows: this.users, rowCount: this.users.length };
    }
    
    // SELECT * FROM customers
    if (sql.includes('select') && sql.includes('from customers')) {
      return { rows: this.customers, rowCount: this.customers.length };
    }
    
    // SELECT * FROM calls
    if (sql.includes('select') && sql.includes('from calls')) {
      return { rows: this.calls, rowCount: this.calls.length };
    }
    
    // SELECT config_value FROM system_configs WHERE config_key = $1
    if (sql.includes('select') && sql.includes('from system_configs')) {
      const key = params?.[0];
      const value = this.configs[key] || null;
      return { rows: value ? [{ config_value: value }] : [], rowCount: value ? 1 : 0 };
    }
    
    // INSERT INTO calls
    if (sql.includes('insert into calls')) {
      const newCall: Call = {
        id: this.nextId('calls'),
        customer_phone: params?.[2] || '',
        status: params?.[3] || 'pending',
        created_at: new Date().toISOString()
      };
      this.calls.push(newCall);
      return { rows: [newCall], rowCount: 1 };
    }
    
    // UPDATE calls
    if (sql.includes('update calls')) {
      return { rows: [], rowCount: 1 };
    }
    
    // 默认返回空
    return { rows: [], rowCount: 0 };
  }
}

const db = new MemoryDB();
export default db;
export const query = (text: string, params?: any[]) => db.query(text, params);
