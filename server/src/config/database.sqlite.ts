import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';

const dbPath = process.env.SQLITE_PATH || path.join(__dirname, '../../data/database.sqlite');

// 确保数据目录存在
const dbDir = path.dirname(dbPath);
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
}

const db = new Database(dbPath);

// 启用外键约束
// 启用外键约束
db.pragma('foreign_keys = ON');

// 在 macOS 上禁用 WAL 模式以避免 I/O 错误
if (process.platform !== 'darwin') {
  db.pragma('journal_mode = WAL');
}

// 初始化数据库表
const initSql = `
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

-- 插入默认管理员用户 (密码: admin123)
INSERT OR IGNORE INTO users (username, password, role, real_name, status) 
VALUES ('admin', 'admin123', 'admin', '系统管理员', 'active');

-- 插入默认客服用户 (密码: agent123)
INSERT OR IGNORE INTO users (username, password, role, real_name, status) 
VALUES ('agent', 'agent123', 'agent', '客服专员', 'active');

-- 插入默认系统配置
INSERT OR IGNORE INTO system_configs (config_key, config_value, description) VALUES
('twilio_account_sid', '', 'Twilio Account SID'),
('twilio_auth_token', '', 'Twilio Auth Token'),
('twilio_phone_number', '', 'Twilio发信号码'),
('twilio_callback_url', '', 'Twilio Webhook回调URL'),
('sms_enabled', 'false', '是否启用短信功能'),
('sms_template_unanswered', '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}', '未接通短信模板'),
('voicemail_enabled', 'false', '是否启用语音信箱'),
('voicemail_greeting', '您好，我现在无法接听您的电话，请在听到提示音后留言。', '语音信箱问候语');
`;

try {
  db.exec(initSql);
  console.log('✅ SQLite 数据库初始化完成');
} catch (error) {
  console.error('❌ 数据库初始化失败:', error);
}

export default db;

// 兼容 pg 的 query 接口
export const query = (text: string, params?: any[]): any => {
  // 将 PostgreSQL 的 $1, $2 转换为 SQLite 的 ?
  const sqliteText = text.replace(/\$(\d+)/g, '?');
  
  // 判断是查询还是修改
  const isSelect = sqliteText.trim().toLowerCase().startsWith('select');
  
  try {
    if (isSelect) {
      const stmt = db.prepare(sqliteText);
      const rows = params ? stmt.all(...params) : stmt.all();
      return { rows, rowCount: rows.length };
    } else {
      const stmt = db.prepare(sqliteText);
      const result = params ? stmt.run(...params) : stmt.run();
      return { 
        rows: result.changes > 0 ? [{ id: result.lastInsertRowid }] : [],
        rowCount: result.changes 
      };
    }
  } catch (error) {
    console.error('SQL Error:', error);
    throw error;
  }
};
