import { query } from '../config/database';

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
      // 使用内存数据库查询
      const result = await query(
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
      // 使用内存数据库更新
      await query(
        'UPDATE system_configs SET config_value = $1 WHERE config_key = $2',
        [value, key]
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
