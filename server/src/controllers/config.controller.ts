import { Request, Response } from 'express';
import { query } from '../config/database';

export const getConfigs = async (req: Request, res: Response) => {
  try {
    const result = await query('SELECT * FROM system_configs ORDER BY config_key');
    res.json(result.rows);
  } catch (error) {
    console.error('获取配置错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateConfig = async (req: any, res: Response) => {
  try {
    const { config_key, config_value } = req.body;
    
    if (!config_key) {
      return res.status(400).json({ error: 'config_key 是必填项' });
    }
    
    // 先检查是否存在
    const existing = await query(
      'SELECT * FROM system_configs WHERE config_key = $1',
      [config_key]
    );
    
    if (existing.rows.length > 0) {
      // 更新已存在的配置
      await query(
        'UPDATE system_configs SET config_value = $1, updated_at = CURRENT_TIMESTAMP WHERE config_key = $2',
        [config_value || '', config_key]
      );
    } else {
      // 插入新配置
      await query(
        'INSERT INTO system_configs (config_key, config_value) VALUES ($1, $2)',
        [config_key, config_value || '']
      );
    }
    
    // 返回更新后的配置
    const result = await query(
      'SELECT * FROM system_configs WHERE config_key = $1',
      [config_key]
    );
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新配置错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const getAgentConfig = async (req: any, res: Response) => {
  try {
    const result = await query(
      'SELECT * FROM agent_configs WHERE agent_id = $1',
      [req.user.id]
    );
    
    if (result.rows.length === 0) {
      // 创建默认配置
      const defaultConfig = await query(
        `INSERT INTO agent_configs (agent_id, dial_strategy, dial_delay, remove_duplicates)
         VALUES ($1, $2, $3, $4) RETURNING *`,
        [req.user.id, 'newest', 3, false]
      );
      return res.json(defaultConfig.rows[0]);
    }
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('获取客服配置错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};

export const updateAgentConfig = async (req: any, res: Response) => {
  try {
    const { dial_strategy, dial_delay, remove_duplicates } = req.body;
    
    const result = await query(
      `INSERT INTO agent_configs (agent_id, dial_strategy, dial_delay, remove_duplicates)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (agent_id) DO UPDATE SET
       dial_strategy = EXCLUDED.dial_strategy,
       dial_delay = EXCLUDED.dial_delay,
       remove_duplicates = EXCLUDED.remove_duplicates,
       updated_at = CURRENT_TIMESTAMP
       RETURNING *`,
      [req.user.id, dial_strategy, dial_delay, remove_duplicates]
    );
    
    res.json(result.rows[0]);
  } catch (error) {
    console.error('更新客服配置错误:', error);
    res.status(500).json({ error: '服务器错误' });
  }
};
