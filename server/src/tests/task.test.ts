/**
 * 任务管理模块单元测试
 */
import { describe, it, expect, beforeEach, beforeAll } from 'vitest';
import { query } from '../config/database';

describe('任务管理模块测试', () => {
  let testCustomerId: number;
  let testAgentId: number = 2;

  beforeAll(async () => {
    // 创建测试用的客户
    const result = await query(
      `INSERT INTO customers (name, phone, status)
       VALUES ($1, $2, $3)`,
      ['测试_任务专用客户', '13800888888', 'pending']
    );
    testCustomerId = result.rows[0]?.id;

    // 确保测试客服用户存在 (id=2,3)
    for (const agentId of [2, 3]) {
      await query(
        `INSERT OR IGNORE INTO users (id, username, password, role, real_name, status)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [agentId, `testagent${agentId}`, '$2a$10$test', 'agent', `测试客服${agentId}`, 'active']
      );
    }
  });

  beforeEach(async () => {
    // 清理测试任务数据
    await query("DELETE FROM tasks WHERE title LIKE '测试_%'", []);
  });

  describe('任务 CRUD 操作', () => {
    it('应该能创建任务', async () => {
      const result = await query(
        `INSERT INTO tasks (title, description, assigned_to, priority, status, due_date, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        ['测试_跟进客户', '测试任务描述', testAgentId, 'high', 'pending', '2024-12-31', 1]
      );
      
      expect(result.rowCount).toBe(1);
      expect(result.rows[0].id).toBeDefined();
    });

    it('应该能查询任务列表', async () => {
      // 创建测试任务
      for (let i = 0; i < 5; i++) {
        await query(
          `INSERT INTO tasks (title, assigned_to, priority, status, due_date, created_by)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [`测试_任务${i}`, testAgentId, 'normal', 'pending', '2024-12-31', 1]
        );
      }
      
      const result = await query(
        "SELECT * FROM tasks WHERE title LIKE '测试_%' ORDER BY created_at DESC",
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(5);
    });

    it('应该能更新任务状态', async () => {
      // 创建任务
      const insertResult = await query(
        `INSERT INTO tasks (title, assigned_to, priority, status, due_date, created_by)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        ['测试_状态任务', testAgentId, 'normal', 'pending', '2024-12-31', 1]
      );
      
      const taskId = insertResult.rows[0]?.id;
      
      // 更新状态为进行中
      await query(
        `UPDATE tasks SET status = $1, updated_at = datetime('now') WHERE id = $2`,
        ['in_progress', taskId]
      );
      
      // 验证
      const result = await query('SELECT * FROM tasks WHERE id = $1', [taskId]);
      expect(result.rows[0].status).toBe('in_progress');
      
      // 更新状态为已完成
      await query(
        `UPDATE tasks SET status = $1, completed_at = datetime('now'), updated_at = datetime('now') WHERE id = $2`,
        ['completed', taskId]
      );
      
      const result2 = await query('SELECT * FROM tasks WHERE id = $1', [taskId]);
      expect(result2.rows[0].status).toBe('completed');
      expect(result2.rows[0].completed_at).toBeDefined();
    });

    it('应该能删除任务', async () => {
      const insertResult = await query(
        `INSERT INTO tasks (title, assigned_to, status, due_date, created_by)
         VALUES ($1, $2, $3, $4, $5)`,
        ['测试_删除任务', testAgentId, 'pending', '2024-12-31', 1]
      );
      
      const taskId = insertResult.rows[0]?.id;
      
      await query('DELETE FROM tasks WHERE id = $1', [taskId]);
      
      const result = await query('SELECT * FROM tasks WHERE id = $1', [taskId]);
      expect(result.rows).toHaveLength(0);
    });
  });

  describe('任务筛选功能', () => {
    it('应该能按状态筛选任务', async () => {
      // 创建不同状态的任务
      const statuses = ['pending', 'in_progress', 'completed'];
      for (const status of statuses) {
        await query(
          `INSERT INTO tasks (title, assigned_to, priority, status, due_date, created_by)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [`测试_状态${status}`, testAgentId, 'normal', status, '2024-12-31', 1]
        );
      }
      
      // 查询待处理任务
      const pendingResult = await query(
        "SELECT * FROM tasks WHERE status = $1 AND title LIKE '测试_%'",
        ['pending']
      );
      expect(pendingResult.rows.length).toBeGreaterThanOrEqual(1);
      
      // 查询已完成任务
      const completedResult = await query(
        "SELECT * FROM tasks WHERE status = $1 AND title LIKE '测试_%'",
        ['completed']
      );
      expect(completedResult.rows.length).toBeGreaterThanOrEqual(1);
    });

    it('应该能按优先级筛选任务', async () => {
      // 创建不同优先级的任务
      const priorities = ['urgent', 'high', 'normal', 'low'];
      for (const priority of priorities) {
        await query(
          `INSERT INTO tasks (title, assigned_to, priority, status, due_date, created_by)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [`测试_优先级${priority}`, testAgentId, priority, 'pending', '2024-12-31', 1]
        );
      }
      
      // 查询紧急任务
      const urgentResult = await query(
        "SELECT * FROM tasks WHERE priority = $1 AND title LIKE '测试_%'",
        ['urgent']
      );
      expect(urgentResult.rows.length).toBeGreaterThanOrEqual(1);
      
      // 查询高优先级任务
      const highResult = await query(
        "SELECT * FROM tasks WHERE priority = $1 AND title LIKE '测试_%'",
        ['high']
      );
      expect(highResult.rows.length).toBeGreaterThanOrEqual(1);
    });

    it('应该能按负责人筛选任务', async () => {
      // 创建分配给不同客服的任务
      for (let agentId = 2; agentId <= 3; agentId++) {
        await query(
          `INSERT INTO tasks (title, assigned_to, status, due_date, created_by)
           VALUES ($1, $2, $3, $4, $5)`,
          [`测试_客服${agentId}任务`, agentId, 'pending', '2024-12-31', 1]
        );
      }
      
      // 查询客服2的任务
      const result = await query(
        "SELECT * FROM tasks WHERE assigned_to = $1 AND title LIKE '测试_%'",
        [2]
      );
      expect(result.rows.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('任务统计功能', () => {
    it('应该能统计任务数量', async () => {
      // 创建一些任务
      for (let i = 0; i < 3; i++) {
        await query(
          `INSERT INTO tasks (title, assigned_to, status, due_date, created_by)
           VALUES ($1, $2, $3, $4, $5)`,
          [`测试_统计任务${i}`, testAgentId, 'pending', '2024-12-31', 1]
        );
      }
      
      const result = await query(
        "SELECT COUNT(*) as count FROM tasks WHERE title LIKE '测试_%'",
        []
      );
      
      expect(parseInt(result.rows[0].count)).toBeGreaterThanOrEqual(3);
    });

    it('应该能统计各状态任务数量', async () => {
      // 确保有测试数据
      await query(
        `INSERT INTO tasks (title, assigned_to, status, due_date, created_by)
         VALUES ($1, $2, $3, $4, $5)`,
        [`测试_状态统计任务`, testAgentId, 'pending', '2024-12-31', 1]
      );

      const result = await query(
        `SELECT status, COUNT(*) as count 
         FROM tasks 
         WHERE title LIKE '测试_%'
         GROUP BY status`,
        []
      );
      
      expect(result.rows.length).toBeGreaterThanOrEqual(1);
    });
  });
});
