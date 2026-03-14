/**
 * 测试环境初始化
 */
import { beforeAll, afterAll, beforeEach } from 'vitest';
import path from 'path';
import fs from 'fs';

// 设置测试环境变量
process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-secret-key-for-unit-tests';
process.env.SQLITE_PATH = path.join(__dirname, '../../data/test.sqlite');

// 清理测试数据库
const testDbPath = process.env.SQLITE_PATH;

beforeAll(() => {
  // 确保数据目录存在
  const dbDir = path.dirname(testDbPath!);
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
  }
  
  // 如果存在旧的测试数据库，删除它
  if (fs.existsSync(testDbPath!)) {
    fs.unlinkSync(testDbPath!);
    console.log('🧹 清理旧测试数据库');
  }
  
  console.log('✅ 测试环境初始化完成');
});

afterAll(() => {
  // 测试结束后可以选择清理数据库
  // if (fs.existsSync(testDbPath!)) {
  //   fs.unlinkSync(testDbPath!);
  // }
  console.log('✅ 测试环境清理完成');
});
