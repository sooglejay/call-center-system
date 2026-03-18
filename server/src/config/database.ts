import initSqlJs, { Database } from 'sql.js';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';

dotenv.config();

// 数据库实例
let db: Database | null = null;
let dbPath: string;
let saveTimeout: NodeJS.Timeout | null = null;

// 创建数据库表的 SQL
const createTablesSQL = `
-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'agent',
  real_name TEXT,
  phone TEXT,
  email TEXT,
  department TEXT,
  position TEXT,
  avatar_url TEXT,
  status TEXT DEFAULT 'active',
  data_access_type TEXT DEFAULT 'mock',
  last_login_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 客户表
CREATE TABLE IF NOT EXISTS customers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  phone TEXT NOT NULL,
  email TEXT,
  company TEXT,
  address TEXT,
  notes TEXT,
  status TEXT DEFAULT 'pending',
  priority INTEGER DEFAULT 1,
  assigned_to INTEGER REFERENCES users(id),
  data_source TEXT DEFAULT 'mock',
  imported_by INTEGER REFERENCES users(id),
  source TEXT,
  data_source TEXT DEFAULT 'mock',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 通话记录表
CREATE TABLE IF NOT EXISTS calls (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone TEXT NOT NULL,
  customer_name TEXT,
  twilio_call_sid TEXT,
  status TEXT DEFAULT 'pending',
  call_result TEXT,
  call_notes TEXT,
  recording_url TEXT,
  recording_duration INTEGER,
  call_duration INTEGER,
  is_connected INTEGER DEFAULT 0,
  started_at DATETIME,
  ended_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  description TEXT,
  assigned_to INTEGER REFERENCES users(id),
  customer_id INTEGER REFERENCES customers(id),
  priority TEXT DEFAULT 'normal',
  status TEXT DEFAULT 'pending',
  due_date DATE,
  completed_at DATETIME,
  created_by INTEGER REFERENCES users(id),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  config_key TEXT UNIQUE NOT NULL,
  config_value TEXT,
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 客服配置表
CREATE TABLE IF NOT EXISTS agent_configs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  agent_id INTEGER UNIQUE REFERENCES users(id),
  auto_dial_enabled INTEGER DEFAULT 0,
  dial_interval INTEGER DEFAULT 30,
  dial_start_time TEXT DEFAULT '09:00:00',
  dial_end_time TEXT DEFAULT '18:00:00',
  dial_strategy TEXT DEFAULT 'newest',
  dial_delay INTEGER DEFAULT 3,
  remove_duplicates INTEGER DEFAULT 0,
  sip_endpoint TEXT,
  sip_username TEXT,
  sip_password TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 语音信箱记录表
CREATE TABLE IF NOT EXISTS voicemail_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone TEXT NOT NULL,
  customer_name TEXT,
  voicemail_url TEXT NOT NULL,
  duration INTEGER DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 短信记录表
CREATE TABLE IF NOT EXISTS sms_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone TEXT NOT NULL,
  customer_name TEXT,
  sms_content TEXT NOT NULL,
  twilio_message_sid TEXT,
  sms_status TEXT DEFAULT 'pending',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 未接通记录表
CREATE TABLE IF NOT EXISTS unanswered_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone TEXT NOT NULL,
  customer_name TEXT,
  reason TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
`;

// 默认系统配置
const defaultConfigSQL = `
INSERT OR IGNORE INTO system_configs (config_key, config_value, description) VALUES
('twilio_account_sid', '', 'Twilio Account SID'),
('twilio_auth_token', '', 'Twilio Auth Token'),
('twilio_phone_number', '', 'Twilio发信号码'),
('twilio_callback_url', '', 'Twilio Webhook回调URL'),
('sms_enabled', 'false', '是否启用短信功能'),
('sms_template_unanswered', '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}', '未接通短信模板'),
('voicemail_enabled', 'false', '是否启用语音信箱'),
('voicemail_greeting', '您好，我现在无法接听您的电话，请在听到提示音后留言。', '语音信箱问候语'),
('allow_register', 'true', '是否允许用户注册'),
('register_default_role', 'agent', '注册用户默认角色');
`;

// 保存数据库到文件
const saveDatabase = () => {
  if (!db) return;
  
  // 防抖：延迟保存，避免频繁写入
  if (saveTimeout) {
    clearTimeout(saveTimeout);
  }
  
  saveTimeout = setTimeout(() => {
    try {
      const data = db!.export();
      const buffer = Buffer.from(data);
      
      // 确保目录存在
      const dbDir = path.dirname(dbPath);
      if (!fs.existsSync(dbDir)) {
        fs.mkdirSync(dbDir, { recursive: true });
      }
      
      fs.writeFileSync(dbPath, buffer);
    } catch (error) {
      console.error('保存数据库失败:', error);
    }
  }, 100);
};

// 同步保存数据库到文件（用于脚本）
const saveDatabaseSync = () => {
  if (!db) return;
  
  try {
    const data = db!.export();
    const buffer = Buffer.from(data);
    
    // 确保目录存在
    const dbDir = path.dirname(dbPath);
    if (!fs.existsSync(dbDir)) {
      fs.mkdirSync(dbDir, { recursive: true });
    }
    
    fs.writeFileSync(dbPath, buffer);
    console.log('✅ 数据库已保存');
  } catch (error) {
    console.error('保存数据库失败:', error);
  }
};

// 初始化数据库
const initDatabase = async (): Promise<void> => {
  const SQL = await initSqlJs();
  
  dbPath = process.env.SQLITE_PATH || path.join(__dirname, '../../data/database.sqlite');
  
  // 确保数据目录存在
  const dbDir = path.dirname(dbPath);
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
  }
  
  // 尝试从文件加载
  if (fs.existsSync(dbPath)) {
    const buffer = fs.readFileSync(dbPath);
    db = new SQL.Database(buffer);
    console.log('✅ 从文件加载数据库:', dbPath);
  } else {
    db = new SQL.Database();
    console.log('✅ 创建新数据库');
  }
  
  // 创建表结构
  try {
    db.run(createTablesSQL);
    db.run(defaultConfigSQL);
    
    // 创建默认用户（如果不存在）
    const defaultUsers = [
      { username: 'admin', password: 'admin123', role: 'admin', real_name: '系统管理员' },
      { username: 'agent', password: 'agent123', role: 'agent', real_name: '客服专员' }
    ];
    
    for (const user of defaultUsers) {
      const existing = db!.exec(`SELECT id FROM users WHERE username = '${user.username}'`);
      if (existing.length === 0 || existing[0].values.length === 0) {
        db!.run(
          `INSERT INTO users (username, password, role, real_name, status, created_at, updated_at)
           VALUES (?, ?, ?, ?, 'active', datetime('now'), datetime('now'))`,
          [user.username, user.password, user.role, user.real_name]
        );
        console.log(`✅ 创建默认用户: ${user.username}`);
      }
    }
    
    saveDatabase();
    console.log('✅ 数据库表结构初始化完成');
  } catch (error) {
    console.error('❌ 数据库初始化失败:', error);
    throw error;
  }
};

// 查询函数（返回 { rows, rowCount } 格式）
const query = (sql: string, params?: any[]): { rows: any[]; rowCount: number } => {
  if (!db) {
    throw new Error('数据库未初始化');
  }
  
  // 将 PostgreSQL 的 $1, $2 转换为 ? ? 
  const sqliteSql = sql.replace(/\$(\d+)/g, '?');
  
  try {
    const upperSql = sqliteSql.trim().toUpperCase();
    
    // SELECT 查询
    if (upperSql.startsWith('SELECT')) {
      const stmt = db!.prepare(sqliteSql);
      
      if (params && params.length > 0) {
        stmt.bind(params);
      }
      
      const rows: any[] = [];
      while (stmt.step()) {
        const row = stmt.getAsObject();
        rows.push(row);
      }
      stmt.free();
      
      return { rows, rowCount: rows.length };
    }
    
    // INSERT, UPDATE, DELETE
    db!.run(sqliteSql, params);
    
    // 获取最后插入的 ID 和影响行数
    const lastId = db!.exec("SELECT last_insert_rowid() as id")[0]?.values[0]?.[0];
    const changes = db!.getRowsModified();
    
    // 保存更改
    saveDatabase();
    
    // 对于 INSERT，返回新记录
    if (upperSql.startsWith('INSERT')) {
      return { 
        rows: [{ id: lastId }], 
        rowCount: changes 
      };
    }
    
    return { rows: [], rowCount: changes };
  } catch (error) {
    console.error('SQL 查询错误:', error);
    console.error('SQL:', sqliteSql);
    console.error('Params:', params);
    throw error;
  }
};

// 导出
export default { query };
export { query, initDatabase };
