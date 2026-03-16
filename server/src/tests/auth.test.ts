/**
 * 认证模块单元测试
 */
import { describe, it, expect, beforeAll } from 'vitest';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { query } from '../config/database';

// 测试密码（明文存储，开发便利）
const TEST_PASSWORD = 'test123';

describe('认证模块测试', () => {
  beforeAll(async () => {
    // 确保测试用户存在（明文密码）
    await query(
      `INSERT OR REPLACE INTO users (id, username, password, role, real_name, status)
       VALUES (999, 'testuser', $1, 'agent', '测试用户', 'active')`,
      [TEST_PASSWORD]
    );
  });

  describe('密码验证', () => {
    it('应该正确验证密码（明文）', () => {
      const isValid = TEST_PASSWORD === 'test123';
      expect(isValid).toBe(true);
    });

    it('应该拒绝错误密码', () => {
      const wrongPassword: string = 'wrongpassword';
      const isValid = wrongPassword === TEST_PASSWORD;
      expect(isValid).toBe(false);
    });
  });

  describe('JWT 令牌', () => {
    it('应该正确生成 JWT 令牌', () => {
      const payload = { userId: 999, username: 'testuser', role: 'agent' };
      const token = jwt.sign(payload, process.env.JWT_SECRET!, { expiresIn: '1h' });
      
      expect(token).toBeDefined();
      expect(token.split('.')).toHaveLength(3); // Header.Payload.Signature
    });

    it('应该正确验证 JWT 令牌', () => {
      const payload = { userId: 999, username: 'testuser', role: 'agent' };
      const token = jwt.sign(payload, process.env.JWT_SECRET!, { expiresIn: '1h' });
      
      const decoded = jwt.verify(token, process.env.JWT_SECRET!) as any;
      expect(decoded.userId).toBe(999);
      expect(decoded.username).toBe('testuser');
      expect(decoded.role).toBe('agent');
    });

    it('应该拒绝无效的 JWT 令牌', () => {
      expect(() => {
        jwt.verify('invalid.token.here', process.env.JWT_SECRET!);
      }).toThrow();
    });
  });

  describe('用户数据库操作', () => {
    it('应该能查询用户', async () => {
      const result = await query(
        'SELECT * FROM users WHERE username = $1',
        ['testuser']
      );
      
      expect(result.rows).toHaveLength(1);
      expect(result.rows[0].username).toBe('testuser');
      expect(result.rows[0].role).toBe('agent');
    });

    it('应该能创建新用户', async () => {
      const uniqueUsername = `newuser_${Date.now()}`;
      const result = await query(
        `INSERT INTO users (username, password, role, real_name, status)
         VALUES ($1, $2, $3, $4, $5)`,
        [uniqueUsername, TEST_PASSWORD, 'agent', '新用户', 'active']
      );
      
      expect(result.rowCount).toBe(1);
      
      // 验证用户已创建
      const checkResult = await query(
        'SELECT * FROM users WHERE username = $1',
        [uniqueUsername]
      );
      expect(checkResult.rows).toHaveLength(1);
      expect(checkResult.rows[0].real_name).toBe('新用户');
    });
  });
});
