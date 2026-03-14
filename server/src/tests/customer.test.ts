/**
 * 客户管理模块单元测试
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { query } from '../config/database';

// 中文姓氏拼音首字母映射（复制自种子脚本）
const getFirstLetter = (name: string): string => {
  if (!name || name.length === 0) return '#';
  
  const firstChar = name.charAt(0);
  const code = firstChar.charCodeAt(0);
  
  if (code >= 65 && code <= 90) return firstChar;
  if (code >= 97 && code <= 122) return firstChar.toUpperCase();
  
  const surnameMap: Record<string, string> = {
    '艾': 'A', '安': 'A', '白': 'B', '包': 'B', '毕': 'B',
    '蔡': 'C', '曹': 'C', '陈': 'C', '邓': 'D', '丁': 'D',
    '方': 'F', '冯': 'F', '范': 'F', '高': 'G', '郭': 'G',
    '黄': 'H', '何': 'H', '韩': 'H', '贾': 'J', '姜': 'J',
    '李': 'L', '刘': 'L', '林': 'L', '马': 'M', '莫': 'M',
    '潘': 'P', '彭': 'P', '钱': 'Q', '秦': 'Q', '任': 'R',
    '孙': 'S', '沈': 'S', '唐': 'T', '田': 'T', '王': 'W',
    '吴': 'W', '徐': 'X', '谢': 'X', '杨': 'Y', '姚': 'Y',
    '张': 'Z', '赵': 'Z', '周': 'Z'
  };
  
  return surnameMap[firstChar] || '#';
};

describe('客户管理模块测试', () => {
  beforeEach(async () => {
    // 清理测试客户数据（保留id<100的客户）
    await query("DELETE FROM customers WHERE name LIKE '测试_%'", []);
  });

  describe('客户 CRUD 操作', () => {
    it('应该能创建客户', async () => {
      const result = await query(
        `INSERT INTO customers (name, phone, email, company, status, priority)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        ['测试_张三', '13800138001', 'zhangsan@test.com', '测试公司', 'pending', 1]
      );
      
      expect(result.rowCount).toBe(1);
    });

    it('应该能查询客户列表', async () => {
      // 先创建一些客户
      for (let i = 0; i < 3; i++) {
        await query(
          `INSERT INTO customers (name, phone, email, status, priority)
           VALUES ($1, $2, $3, $4, $5)`,
          [`测试_客户${i}`, `1380013800${i}`, `customer${i}@test.com`, 'pending', 1]
        );
      }
      
      const result = await query(
        "SELECT * FROM customers WHERE name LIKE '测试_%' ORDER BY id DESC LIMIT 10",
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(3);
    });

    it('应该能更新客户信息', async () => {
      // 创建客户
      const insertResult = await query(
        `INSERT INTO customers (name, phone, email, status)
         VALUES ($1, $2, $3, $4)`,
        ['测试_更新客户', '13800138099', 'update@test.com', 'pending']
      );
      
      const customerId = insertResult.rows[0]?.id;
      
      // 更新客户
      await query(
        `UPDATE customers SET name = $1, status = $2, updated_at = datetime('now') WHERE id = $3`,
        ['测试_已更新', 'contacted', customerId]
      );
      
      // 验证更新
      const result = await query(
        'SELECT * FROM customers WHERE id = $1',
        [customerId]
      );
      
      expect(result.rows[0].name).toBe('测试_已更新');
      expect(result.rows[0].status).toBe('contacted');
    });

    it('应该能删除客户', async () => {
      // 创建客户
      const insertResult = await query(
        `INSERT INTO customers (name, phone, status)
         VALUES ($1, $2, $3)`,
        ['测试_删除客户', '13800138088', 'pending']
      );
      
      const customerId = insertResult.rows[0]?.id;
      
      // 删除客户
      await query('DELETE FROM customers WHERE id = $1', [customerId]);
      
      // 验证删除
      const result = await query('SELECT * FROM customers WHERE id = $1', [customerId]);
      expect(result.rows).toHaveLength(0);
    });
  });

  describe('姓氏首字母功能', () => {
    it('应该正确识别中文姓氏首字母', () => {
      expect(getFirstLetter('张三')).toBe('Z');
      expect(getFirstLetter('李四')).toBe('L');
      expect(getFirstLetter('王五')).toBe('W');
      expect(getFirstLetter('陈六')).toBe('C');
      expect(getFirstLetter('刘明')).toBe('L');
    });

    it('应该正确识别英文姓氏首字母', () => {
      expect(getFirstLetter('Tom')).toBe('T');
      expect(getFirstLetter('john')).toBe('J');
      expect(getFirstLetter('Kardashian')).toBe('K');
    });

    it('应该能按姓氏首字母查询客户', async () => {
      // 创建不同姓氏的客户
      const testNames = [
        { name: '张测试', letter: 'Z' },
        { name: '李测试', letter: 'L' },
        { name: '王测试', letter: 'W' },
        { name: '陈测试', letter: 'C' }
      ];
      
      for (const item of testNames) {
        await query(
          `INSERT INTO customers (name, phone, status)
           VALUES ($1, $2, $3)`,
          [item.name, `13800${item.letter}00001`, 'pending']
        );
      }
      
      // 查询姓氏为 Z 的客户
      const result = await query(
        "SELECT * FROM customers WHERE name LIKE '张%' OR name LIKE 'Z%' ORDER BY name",
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('客户分配功能', () => {
    it('应该能分配客户给客服', async () => {
      // 创建客户
      const insertResult = await query(
        `INSERT INTO customers (name, phone, status, assigned_to)
         VALUES ($1, $2, $3, $4)`,
        ['测试_分配客户', '13800138077', 'pending', null]
      );
      
      const customerId = insertResult.rows[0]?.id;
      
      // 分配给客服 (id=2)
      await query(
        `UPDATE customers SET assigned_to = $1, updated_at = datetime('now') WHERE id = $2`,
        [2, customerId]
      );
      
      // 验证分配
      const result = await query(
        'SELECT * FROM customers WHERE id = $1',
        [customerId]
      );
      
      expect(result.rows[0].assigned_to).toBe(2);
    });

    it('应该能批量分配客户', async () => {
      // 创建多个未分配客户
      const customerIds = [];
      for (let i = 0; i < 3; i++) {
        const result = await query(
          `INSERT INTO customers (name, phone, status, assigned_to)
           VALUES ($1, $2, $3, $4)`,
          [`测试_批量客户${i}`, `1380013806${i}`, 'pending', null]
        );
        customerIds.push(result.rows[0]?.id);
      }
      
      // 批量分配
      for (const id of customerIds) {
        await query(
          'UPDATE customers SET assigned_to = $1 WHERE id = $2',
          [2, id]
        );
      }
      
      // 验证
      const result = await query(
        `SELECT * FROM customers WHERE id IN (${customerIds.join(',')})`,
        []
      );
      
      expect(result.rows.every((c: any) => c.assigned_to === 2)).toBe(true);
    });
  });
});
