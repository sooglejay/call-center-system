/**
 * 修复数据库密码脚本
 * 用法: node fix-password.js
 * 
 * 注意: 当前系统使用明文密码存储，此脚本用于重置用户密码
 */

const fs = require('fs');
const path = require('path');

async function fixPassword() {
  // 动态加载 sql.js
  const initSqlJs = require('sql.js');
  const SQL = await initSqlJs();
  
  const dbPath = path.join(__dirname, 'data/database.sqlite');
  
  console.log('🔧 重置数据库密码...\n');
  
  // 检查数据库文件是否存在
  if (!fs.existsSync(dbPath)) {
    console.log('❌ 数据库文件不存在:', dbPath);
    console.log('   请先启动服务创建数据库');
    process.exit(1);
  }
  
  // 读取数据库文件
  const fileBuffer = fs.readFileSync(dbPath);
  const db = new SQL.Database(fileBuffer);
  
  try {
    // 更新密码为明文（当前系统使用明文密码）
    // 注意: 生产环境应使用 bcrypt 哈希
    db.run("UPDATE users SET password = 'admin123' WHERE username = 'admin'");
    db.run("UPDATE users SET password = 'agent123' WHERE username LIKE 'agent%'");
    
    // 查询验证
    const result = db.exec("SELECT username, password, role FROM users");
    
    console.log('当前用户列表:');
    if (result.length > 0) {
      const columns = result[0].columns;
      const values = result[0].values;
      console.log('  ' + columns.join('\t'));
      console.log('  ' + '-'.repeat(40));
      values.forEach(row => {
        console.log('  ' + row.join('\t'));
      });
    }
    
    // 保存数据库
    const data = db.export();
    const buffer = Buffer.from(data);
    fs.writeFileSync(dbPath, buffer);
    
    console.log('\n✅ 密码重置完成！');
    console.log('');
    console.log('可用账号:');
    console.log('  管理员: admin / admin123');
    console.log('  客服:   agent / agent123');
    
  } catch (error) {
    console.error('❌ 操作失败:', error.message);
    process.exit(1);
  } finally {
    db.close();
  }
}

fixPassword().catch(err => {
  console.error('❌ 脚本执行失败:', err.message);
  process.exit(1);
});
