/**
 * 安装后自动编译 better-sqlite3 并初始化数据库
 * 支持 macOS / Windows / Linux
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const platform = process.platform;
const rootDir = path.join(__dirname, '..');
const dataDir = path.join(rootDir, 'data');

// 确保数据目录存在
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

// 检查数据库是否已存在且已初始化
function checkDatabaseExists() {
  const dbPath = path.join(dataDir, 'database.sqlite');
  if (!fs.existsSync(dbPath)) {
    return false;
  }
  const stats = fs.statSync(dbPath);
  return stats.size > 10000;
}

// 查找 better-sqlite3 路径
function findBetterSqlite3Path() {
  const possiblePaths = [
    path.join(rootDir, 'node_modules/better-sqlite3'),
    path.join(rootDir, '../node_modules/better-sqlite3'),
    path.join(rootDir, '../../node_modules/better-sqlite3'),
  ];
  
  // 尝试在 pnpm 的目录结构中查找
  const nodeModulesPath = path.join(rootDir, 'node_modules');
  if (fs.existsSync(nodeModulesPath)) {
    const entries = fs.readdirSync(nodeModulesPath);
    for (const entry of entries) {
      if (entry.startsWith('.pnpm')) {
        const pnpmPath = path.join(nodeModulesPath, entry, 'better-sqlite3@');
        if (fs.existsSync(pnpmPath)) {
          const versions = fs.readdirSync(pnpmPath);
          if (versions.length > 0) {
            possiblePaths.push(path.join(pnpmPath, versions[0], 'node_modules/better-sqlite3'));
          }
        }
      }
    }
  }

  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      return p;
    }
  }
  return null;
}

// 编译 better-sqlite3
function buildBetterSqlite3() {
  const sqlite3Path = findBetterSqlite3Path();
  
  if (!sqlite3Path) {
    console.log('⚠️  未找到 better-sqlite3，可能未安装');
    return true;
  }

  const bindingPath = path.join(sqlite3Path, 'build/Release/better_sqlite3.node');

  // 检查是否已编译
  if (fs.existsSync(bindingPath)) {
    console.log('✅ better-sqlite3 已编译');
    return true;
  }

  console.log('🔧 正在编译 better-sqlite3...');

  try {
    // 检查 Python 是否可用
    try {
      execSync('python3 --version || python --version', { stdio: 'ignore' });
    } catch {
      console.log('⚠️  未检测到 Python，跳过编译（Linux 通常不需要）');
      return true;
    }

    // 检查 node-gyp 是否可用
    try {
      execSync('node-gyp --version', { stdio: 'ignore' });
    } catch {
      console.log('📦 安装 node-gyp...');
      execSync('npm install -g node-gyp', { stdio: 'inherit' });
    }

    process.chdir(sqlite3Path);

    console.log('🧹 清理...');
    try { execSync('node-gyp clean', { stdio: 'ignore' }); } catch {}

    console.log('⚙️  配置...');
    execSync('node-gyp configure', { stdio: 'ignore' });

    console.log('🔨 编译（可能需要几分钟）...');
    execSync('node-gyp build', { stdio: 'inherit' });

    console.log('✅ better-sqlite3 编译完成');
    return true;
  } catch (error) {
    console.error('❌ 编译失败，请手动执行：');
    console.error(`   cd "${sqlite3Path}"`);
    console.error('   node-gyp rebuild');
    return false;
  }
}

// 初始化数据库
function seedDatabase() {
  if (checkDatabaseExists()) {
    console.log('✅ 数据库已存在');
    return true;
  }

  console.log('🌱 初始化数据库...');

  try {
    process.chdir(rootDir);
    
    // 执行种子数据（会自动创建表并插入数据）
    execSync('npx tsx src/scripts/seed.ts --mini', { stdio: 'inherit' });
    console.log('✅ 数据库初始化完成');
    return true;
  } catch (error) {
    console.error('❌ 数据库初始化失败');
    return false;
  }
}

// 主流程
console.log('🚀 初始化中...\n');

buildBetterSqlite3();
seedDatabase();

console.log('\n✨ 准备就绪！');
console.log('   启动: pnpm dev');
console.log('   账号: admin/admin123 或 agent/agent123');
