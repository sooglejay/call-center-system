/**
 * 安装后初始化脚本
 * 创建必要目录，检查 sql.js 是否可用
 */

const fs = require('fs');
const path = require('path');

const rootDir = path.join(__dirname, '..');
const dataDir = path.join(rootDir, 'data');
const logsDir = path.join(rootDir, 'logs');

console.log('🚀 初始化客服外呼系统...\n');

// 确保数据目录存在
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
  console.log('✅ 创建数据目录: data/');
}

// 确保日志目录存在
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
  console.log('✅ 创建日志目录: logs/');
}

// 检查 sql.js 是否可用
function checkSqlJs() {
  try {
    const sqlJsPath = path.join(rootDir, 'node_modules/sql.js');
    if (fs.existsSync(sqlJsPath)) {
      console.log('✅ sql.js 已安装');
      return true;
    }
    
    // 检查 pnpm 结构
    const pnpmPath = path.join(rootDir, 'node_modules/.pnpm');
    if (fs.existsSync(pnpmPath)) {
      const entries = fs.readdirSync(pnpmPath);
      const sqlJsEntry = entries.find(e => e.startsWith('sql.js'));
      if (sqlJsEntry) {
        console.log('✅ sql.js 已安装 (pnpm)');
        return true;
      }
    }
    
    console.log('⚠️  sql.js 未找到，请运行: pnpm install');
    return false;
  } catch (error) {
    console.log('⚠️  检查 sql.js 时出错:', error.message);
    return false;
  }
}

// 检查数据库文件
function checkDatabase() {
  const dbPath = path.join(dataDir, 'database.sqlite');
  if (fs.existsSync(dbPath)) {
    const stats = fs.statSync(dbPath);
    if (stats.size > 0) {
      console.log('✅ 数据库文件已存在');
      return true;
    }
  }
  console.log('ℹ️  数据库将在首次启动时自动创建');
  return false;
}

checkSqlJs();
checkDatabase();

console.log('\n✨ 准备就绪！');
console.log('');
console.log('   启动开发服务: pnpm dev');
console.log('   构建生产版本: pnpm build');
console.log('   默认账号: admin / admin123');
console.log('');
