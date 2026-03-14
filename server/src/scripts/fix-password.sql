-- 修复默认用户密码
-- admin123 / agent123 的 bcrypt 哈希
UPDATE users SET password = '$2a$10$NgG1WV0QVLtPT1zDzDtE3eI9X1hJTOliq8ySP7tTPLWWV5F1YOd6K' WHERE username = 'admin';

-- 添加客服用户 agent/agent123
INSERT INTO users (username, password, role, real_name, status) 
VALUES ('agent', '$2a$10$NgG1WV0QVLtPT1zDzDtE3eI9X1hJTOliq8ySP7tTPLWWV5F1YOd6K', 'agent', '客服专员', 'active')
ON CONFLICT (username) DO NOTHING;
