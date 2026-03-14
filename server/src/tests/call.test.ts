/**
 * 通话记录模块单元测试
 */
import { describe, it, expect, beforeEach, beforeAll } from 'vitest';
import { query } from '../config/database';

describe('通话记录模块测试', () => {
  let testCustomerId: number;
  let testAgentId: number = 2;

  beforeAll(async () => {
    // 创建测试用的客户
    const result = await query(
      `INSERT INTO customers (name, phone, status)
       VALUES ($1, $2, $3)`,
      ['测试_通话专用客户', '13800999999', 'pending']
    );
    testCustomerId = result.rows[0]?.id;
  });

  beforeEach(async () => {
    // 清理测试通话记录
    await query("DELETE FROM calls WHERE customer_name LIKE '测试_%'", []);
  });

  describe('通话记录 CRUD 操作', () => {
    it('应该能创建通话记录', async () => {
      const result = await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [testCustomerId, testAgentId, '13800138001', '测试_客户', 'completed', 'answered']
      );
      
      expect(result.rowCount).toBe(1);
    });

    it('应该能查询通话记录', async () => {
      // 创建通话记录
      for (let i = 0; i < 5; i++) {
        await query(
          `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [testCustomerId, testAgentId, `1380013800${i}`, `测试_客户${i}`, 'completed', 'answered']
        );
      }
      
      const result = await query(
        "SELECT * FROM calls WHERE customer_name LIKE '测试_%' ORDER BY created_at DESC",
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(5);
    });

    it('应该能更新通话记录', async () => {
      const insertResult = await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [testCustomerId, testAgentId, '13800138099', '测试_更新客户', 'pending', null]
      );
      
      const callId = insertResult.rows[0]?.id;
      
      // 更新通话结果
      await query(
        `UPDATE calls SET status = $1, call_result = $2, call_notes = $3, updated_at = datetime('now') WHERE id = $4`,
        ['completed', 'answered', '通话成功', callId]
      );
      
      const result = await query('SELECT * FROM calls WHERE id = $1', [callId]);
      expect(result.rows[0].status).toBe('completed');
      expect(result.rows[0].call_result).toBe('answered');
      expect(result.rows[0].call_notes).toBe('通话成功');
    });
  });

  describe('通话结果统计', () => {
    it('应该能统计通话结果分布', async () => {
      const results = ['answered', 'no-answer', 'busy', 'voicemail', 'failed'];
      
      for (let i = 0; i < results.length; i++) {
        await query(
          `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [testCustomerId, testAgentId, `138001380${i}0`, `测试_统计${i}`, 'completed', results[i]]
        );
      }
      
      const result = await query(
        `SELECT call_result, COUNT(*) as count 
         FROM calls 
         WHERE customer_name LIKE '测试_%'
         GROUP BY call_result`,
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(1);
    });

    it('应该能统计接通率', async () => {
      // 创建已接通和未接通的通话记录
      await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result, recording_duration)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [testCustomerId, testAgentId, '13800138010', '测试_接通1', 'completed', 'answered', 120]
      );
      
      await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result, recording_duration)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [testCustomerId, testAgentId, '13800138011', '测试_接通2', 'completed', 'answered', 180]
      );
      
      await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result, recording_duration)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [testCustomerId, testAgentId, '13800138012', '测试_未接通', 'completed', 'no-answer', 0]
      );
      
      const result = await query(
        `SELECT 
          COUNT(*) as total,
          SUM(CASE WHEN recording_duration > 0 THEN 1 ELSE 0 END) as connected
         FROM calls 
         WHERE customer_name LIKE '测试_%'`,
        []
      );
      
      expect(parseInt(result.rows[0].total)).toBeGreaterThanOrEqual(3);
    });
  });

  describe('通话时间统计', () => {
    it('应该能统计通话时长', async () => {
      await query(
        `INSERT INTO calls (customer_id, agent_id, customer_phone, customer_name, status, call_result, recording_duration)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [testCustomerId, testAgentId, '13800138020', '测试_时长', 'completed', 'answered', 180]
      );
      
      const result = await query(
        "SELECT AVG(recording_duration) as avg_duration FROM calls WHERE customer_name LIKE '测试_%'",
        []
      );
      
      expect(result.rows[0].avg_duration).toBeDefined();
    });
  });
});
