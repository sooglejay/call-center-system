-- 初始化数据库脚本
-- 执行: psql -U postgres -f init-db.sql

-- 创建数据库（如果不存在）
SELECT 'CREATE DATABASE callcenter'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'callcenter')\gexec

\c callcenter;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'agent')),
    phone VARCHAR(20),
    email VARCHAR(100),
    avatar_url VARCHAR(255),
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 客户表
CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    name VARCHAR(50),
    remark TEXT,
    source VARCHAR(50),
    imported_by INTEGER REFERENCES users(id),
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(phone)
);

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    agent_id INTEGER NOT NULL REFERENCES users(id),
    customer_ids INTEGER[] NOT NULL,
    task_type VARCHAR(20) DEFAULT 'weekly' CHECK (task_type IN ('daily', 'weekly')),
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'completed', 'cancelled')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_by INTEGER NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 通话记录表
CREATE TABLE IF NOT EXISTS call_records (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customers(id),
    agent_id INTEGER NOT NULL REFERENCES users(id),
    task_id INTEGER REFERENCES tasks(id),
    twilio_call_sid VARCHAR(100),
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    is_connected BOOLEAN DEFAULT FALSE,
    call_duration INTEGER DEFAULT 0,
    recording_url VARCHAR(500),
    recording_duration INTEGER DEFAULT 0,
    call_notes TEXT,
    call_result VARCHAR(50),
    started_at TIMESTAMP,
    connected_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id SERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description VARCHAR(255),
    updated_by INTEGER REFERENCES users(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 客服配置表
CREATE TABLE IF NOT EXISTS agent_configs (
    id SERIAL PRIMARY KEY,
    agent_id INTEGER UNIQUE NOT NULL REFERENCES users(id),
    dial_strategy VARCHAR(20) DEFAULT 'newest',
    dial_delay INTEGER DEFAULT 3,
    remove_duplicates BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认管理员（密码: admin123）
INSERT INTO users (username, password, real_name, role, status) 
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqrqQzBZN0UfGNEsKYGsFqPBRvQ9I1G', '系统管理员', 'admin', 'active')
ON CONFLICT (username) DO NOTHING;

-- 插入默认配置
INSERT INTO system_configs (config_key, config_value, description) VALUES
('twilio_account_sid', '', 'Twilio Account SID'),
('twilio_auth_token', '', 'Twilio Auth Token'),
('twilio_phone_number', '', 'Twilio 发信号码'),
('twilio_callback_url', '', 'Twilio Webhook回调URL')
ON CONFLICT (config_key) DO NOTHING;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_call_records_agent ON call_records(agent_id);
CREATE INDEX IF NOT EXISTS idx_call_records_customer ON call_records(customer_id);
CREATE INDEX IF NOT EXISTS idx_call_records_created ON call_records(created_at);
CREATE INDEX IF NOT EXISTS idx_tasks_agent ON tasks(agent_id);
