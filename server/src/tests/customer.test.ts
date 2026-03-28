/**
 * 客户管理模块单元测试
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
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
    // 清理测试客户数据
    await query("DELETE FROM customers WHERE name LIKE '测试_%'", []);
  });

  afterEach(async () => {
    // 清理测试数据
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

  describe('数据导入与分配功能', () => {
    it('导入客户时应该能同时指定分配客服', async () => {
      // 模拟导入数据并分配客服
      const result = await query(
        `INSERT INTO customers (name, phone, email, company, status, assigned_to, data_source, imported_by, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, datetime('now'), datetime('now'))`,
        ['测试_导入客户A', '13900139001', 'importa@test.com', '导入公司A', 'pending', 2, 'real', 1]
      );
      
      expect(result.rowCount).toBe(1);
      
      // 验证数据
      const customer = await query(
        'SELECT * FROM customers WHERE phone = $1',
        ['13900139001']
      );
      
      expect(customer.rows[0].name).toBe('测试_导入客户A');
      expect(customer.rows[0].assigned_to).toBe(2);
      expect(customer.rows[0].data_source).toBe('real');
    });

    it('应该能查询指定客服的客户列表', async () => {
      // 创建分配给客服ID=2的客户
      for (let i = 0; i < 3; i++) {
        await query(
          `INSERT INTO customers (name, phone, status, assigned_to, data_source, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
          [`测试_客服客户${i}`, `1390013910${i}`, 'pending', 2, 'real']
        );
      }
      
      // 创建未分配的客户
      await query(
        `INSERT INTO customers (name, phone, status, assigned_to, data_source, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, datetime('now'), datetime('now'))`,
        ['测试_未分配客户', '13900139999', 'pending', null, 'real']
      );
      
      // 查询客服ID=2的客户
      const result = await query(
        'SELECT * FROM customers WHERE assigned_to = $1 AND name LIKE $2',
        [2, '测试_客服%']
      );
      
      expect(result.rows.length).toBe(3);
      expect(result.rows.every((c: any) => c.assigned_to === 2)).toBe(true);
    });

    it('应该能正确过滤数据源类型', async () => {
      // 创建真实数据
      await query(
        `INSERT INTO customers (name, phone, status, data_source, created_at, updated_at)
         VALUES ($1, $2, $3, $4, datetime('now'), datetime('now'))`,
        ['测试_真实客户', '13900138001', 'pending', 'real']
      );
      
      // 创建测试数据
      await query(
        `INSERT INTO customers (name, phone, status, data_source, created_at, updated_at)
         VALUES ($1, $2, $3, $4, datetime('now'), datetime('now'))`,
        ['测试_测试客户', '13900138002', 'pending', 'mock']
      );
      
      // 查询真实数据
      const realResult = await query(
        "SELECT * FROM customers WHERE data_source = 'real' AND name LIKE '测试_%'"
      );
      
      // 查询测试数据
      const mockResult = await query(
        "SELECT * FROM customers WHERE data_source = 'mock' AND name LIKE '测试_%'"
      );
      
      expect(realResult.rows.some((c: any) => c.name === '测试_真实客户')).toBe(true);
      expect(mockResult.rows.some((c: any) => c.name === '测试_测试客户')).toBe(true);
    });
  });

  describe('客户状态管理', () => {
    it('应该能更新客户状态', async () => {
      // 创建客户
      const insertResult = await query(
        `INSERT INTO customers (name, phone, status)
         VALUES ($1, $2, $3)`,
        ['测试_状态客户', '13900138055', 'pending']
      );
      
      const customerId = insertResult.rows[0]?.id;
      
      // 更新状态为已联系
      await query(
        `UPDATE customers SET status = $1, updated_at = datetime('now') WHERE id = $2`,
        ['contacted', customerId]
      );
      
      // 验证
      const result = await query('SELECT * FROM customers WHERE id = $1', [customerId]);
      expect(result.rows[0].status).toBe('contacted');
    });

    it('应该能按状态筛选客户', async () => {
      // 创建不同状态的客户
      const statuses = ['pending', 'contacted', 'converted', 'not_interested'];
      for (const status of statuses) {
        await query(
          `INSERT INTO customers (name, phone, status)
           VALUES ($1, $2, $3)`,
          [`测试_${status}客户`, `13900138${status.slice(0, 2)}01`, status]
        );
      }
      
      // 查询待跟进客户
      const pendingResult = await query(
        "SELECT * FROM customers WHERE status = 'pending' AND name LIKE '测试_%'"
      );
      
      expect(pendingResult.rows.length).toBeGreaterThanOrEqual(1);
      expect(pendingResult.rows.every((c: any) => c.status === 'pending')).toBe(true);
    });
  });
});
