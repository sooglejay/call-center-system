import { Pool } from 'pg';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

export class ConfigService {
  private cache: Map<string, string> = new Map();

  /**
   * 获取配置值
   */
  async getConfig(key: string): Promise<string | null> {
    // 先检查缓存
    if (this.cache.has(key)) {
      return this.cache.get(key)!;
    }

    try {
      const result = await pool.query(
        'SELECT config_value FROM system_configs WHERE config_key = $1',
        [key]
      );

      if (result.rows.length > 0) {
        const value = result.rows[0].config_value;
        this.cache.set(key, value);
        return value;
      }

      return null;
    } catch (error) {
      console.error('获取配置失败:', error);
      return null;
    }
  }

  /**
   * 设置配置值
   */
  async setConfig(key: string, value: string): Promise<void> {
    try {
      await pool.query(
        `INSERT INTO system_configs (config_key, config_value)
         VALUES ($1, $2)
         ON CONFLICT (config_key) 
         DO UPDATE SET config_value = $2, updated_at = CURRENT_TIMESTAMP`,
        [key, value]
      );

      // 更新缓存
      this.cache.set(key, value);
    } catch (error) {
      console.error('设置配置失败:', error);
      throw error;
    }
  }

  /**
   * 清除缓存
   */
  clearCache(): void {
    this.cache.clear();
  }
}

export const configService = new ConfigService();
