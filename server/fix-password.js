/**
 * 修复数据库密码脚本
 * 用法: node fix-password.js
 */

const Database = require('better-sqlite3');
const bcrypt = require('bcryptjs');
const path = require('path');

const dbPath = path.join(__dirname, 'data/database.sqlite');

console.log('🔧 修复数据库密码...\n');

try {
  const db = new Database(dbPath);

  // 生成正确的密码哈希
  const adminHash = bcrypt.hashSync('admin123', 10);
  const agentHash = bcrypt.hashSync('agent123', 10);

  console.log('生成的密码哈希:');
  console.log('  admin123:', adminHash);
  console.log('  agent123:', agentHash);

  // 更新密码
  const updateAdmin = db.prepare("UPDATE users SET password = ? WHERE username = 'admin'");
  const updateAgents = db.prepare("UPDATE users SET password = ? WHERE username LIKE 'agent%'");

  const adminResult = updateAdmin.run(adminHash);
  const agentResult = updateAgents.run(agentHash);

  console.log('\n更新结果:');
  console.log('  admin 用户更新:', adminResult.changes, '条');
  console.log('  agent 用户更新:', agentResult.changes, '条');

  // 验证
  console.log('\n验证密码:');
  const users = db.prepare('SELECT username, password FROM users').all();
  for (const u of users) {
    const isAdmin = bcrypt.compareSync('admin123', u.password);
    const isAgent = bcrypt.compareSync('agent123', u.password);
    console.log(`  ${u.username}: ${isAdmin ? 'admin123 ✓' : isAgent ? 'agent123 ✓' : '未知 ✗'}`);
  }

  console.log('\n✅ 密码修复完成！');
  console.log('现在可以使用以下账号登录:');
  console.log('  管理员: admin / admin123');
  console.log('  客服: agent / agent123');
  console.log('  客服: agent01 / agent123');
  console.log('  客服: agent02 / agent123');
  console.log('  客服: agent03 / agent123');

} catch (error) {
  console.error('❌ 修复失败:', error.message);
  console.error('请确保在 server 目录下运行此脚本');
  process.exit(1);
}
