-- 用户表
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'agent',
  real_name VARCHAR(50),
  phone VARCHAR(20),
  email VARCHAR(100),
  department VARCHAR(50),
  position VARCHAR(50),
  avatar_url TEXT,
  status VARCHAR(20) DEFAULT 'active',
  last_login_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 客户表
CREATE TABLE IF NOT EXISTS customers (
  id SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(100),
  company VARCHAR(100),
  address TEXT,
  notes TEXT,
  status VARCHAR(20) DEFAULT 'pending',
  priority INTEGER DEFAULT 1,
  assigned_to INTEGER REFERENCES users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 通话记录表
CREATE TABLE IF NOT EXISTS calls (
  id SERIAL PRIMARY KEY,
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone VARCHAR(20) NOT NULL,
  customer_name VARCHAR(100),
  twilio_call_sid VARCHAR(100),
  status VARCHAR(20) DEFAULT 'pending',
  call_result VARCHAR(50),
  call_notes TEXT,
  recording_url TEXT,
  recording_duration INTEGER,
  started_at TIMESTAMP,
  ended_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
  id SERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  assigned_to INTEGER REFERENCES users(id),
  customer_id INTEGER REFERENCES customers(id),
  priority VARCHAR(20) DEFAULT 'normal',
  status VARCHAR(20) DEFAULT 'pending',
  due_date DATE,
  completed_at TIMESTAMP,
  created_by INTEGER REFERENCES users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
  id SERIAL PRIMARY KEY,
  config_key VARCHAR(100) UNIQUE NOT NULL,
  config_value TEXT,
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 客服配置表
CREATE TABLE IF NOT EXISTS agent_configs (
  id SERIAL PRIMARY KEY,
  agent_id INTEGER UNIQUE REFERENCES users(id),
  auto_dial_enabled BOOLEAN DEFAULT false,
  dial_interval INTEGER DEFAULT 30,
  dial_start_time TIME DEFAULT '09:00:00',
  dial_end_time TIME DEFAULT '18:00:00',
  sip_endpoint TEXT,
  sip_username VARCHAR(100),
  sip_password VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 语音信箱记录表
CREATE TABLE IF NOT EXISTS voicemail_records (
  id SERIAL PRIMARY KEY,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone VARCHAR(20) NOT NULL,
  customer_name VARCHAR(100),
  voicemail_url TEXT NOT NULL,
  duration INTEGER DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 短信记录表
CREATE TABLE IF NOT EXISTS sms_records (
  id SERIAL PRIMARY KEY,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone VARCHAR(20) NOT NULL,
  customer_name VARCHAR(100),
  sms_content TEXT NOT NULL,
  twilio_message_sid VARCHAR(100),
  sms_status VARCHAR(20) DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 未接通记录表
CREATE TABLE IF NOT EXISTS unanswered_records (
  id SERIAL PRIMARY KEY,
  call_id INTEGER REFERENCES calls(id),
  customer_id INTEGER REFERENCES customers(id),
  agent_id INTEGER REFERENCES users(id),
  customer_phone VARCHAR(20) NOT NULL,
  customer_name VARCHAR(100),
  reason VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO users (username, password, role, real_name, status) 
VALUES ('admin', 'admin123', 'admin', '系统管理员', 'active')
ON CONFLICT (username) DO NOTHING;

-- 插入默认客服用户 (密码: agent123)
INSERT INTO users (username, password, role, real_name, status) 
VALUES ('agent', 'agent123', 'agent', '客服专员', 'active')
ON CONFLICT (username) DO NOTHING;

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, description) VALUES
('twilio_account_sid', '', 'Twilio Account SID'),
('twilio_auth_token', '', 'Twilio Auth Token'),
('twilio_phone_number', '', 'Twilio发信号码'),
('twilio_callback_url', '', 'Twilio Webhook回调URL'),
('sms_enabled', 'false', '是否启用短信功能'),
('sms_template_unanswered', '您好，我是{agentName}，刚刚给您打了电话。如有需要请联系我：{agentPhone}', '未接通短信模板'),
('voicemail_enabled', 'false', '是否启用语音信箱'),
('voicemail_greeting', '您好，我现在无法接听您的电话，请在听到提示音后留言。', '语音信箱问候语')
ON CONFLICT (config_key) DO NOTHING;
