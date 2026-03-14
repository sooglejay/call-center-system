-- 修复默认用户密码（明文存储，开发便利）
UPDATE users SET password = 'admin123' WHERE username = 'admin';

-- 添加客服用户 agent/agent123
INSERT INTO users (username, password, role, real_name, status) 
VALUES ('agent', 'agent123', 'agent', '客服专员', 'active')
ON CONFLICT (username) DO NOTHING;
