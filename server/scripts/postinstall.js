/**
 * 安装后自动编译 better-sqlite3 并初始化数据库
 * 在 macOS/Windows 上会自动执行编译和初始化
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const platform = process.platform;
const rootDir = path.join(__dirname, '..');

// 检查数据库是否已存在且已初始化
function checkDatabaseExists() {
  const dbPath = path.join(rootDir, 'data/database.sqlite');
  if (!fs.existsSync(dbPath)) {
    return false;
  }
  // 检查文件大小，如果太小可能还没初始化
  const stats = fs.statSync(dbPath);
  return stats.size > 10000; // 大于 10KB 认为已初始化
}

// 编译 better-sqlite3
function buildBetterSqlite3() {
  // 只在 macOS 和 Windows 上自动编译
  if (platform !== 'darwin' && platform !== 'win32') {
    console.log('🐧 Linux 系统，跳过 better-sqlite3 自动编译');
    return true;
  }

  const sqlite3Path = path.join(
    rootDir,
    'node_modules/.pnpm/better-sqlite3@12.8.0/node_modules/better-sqlite3'
  );

  const bindingPath = path.join(sqlite3Path, 'build/Release/better_sqlite3.node');

  // 检查是否已编译
  if (fs.existsSync(bindingPath)) {
    console.log('✅ better-sqlite3 已编译，跳过');
    return true;
  }

  console.log('🔧 检测到需要编译 better-sqlite3...');

  try {
    // 检查 node-gyp 是否可用
    try {
      execSync('node-gyp --version', { stdio: 'ignore' });
    } catch {
      console.log('📦 安装 node-gyp...');
      execSync('npm install -g node-gyp', { stdio: 'inherit' });
    }

    // 进入 better-sqlite3 目录
    process.chdir(sqlite3Path);

    console.log('🧹 清理旧编译文件...');
    execSync('node-gyp clean', { stdio: 'ignore' });

    console.log('⚙️  配置编译环境...');
    execSync('node-gyp configure', { stdio: 'ignore' });

    console.log('🔨 编译 better-sqlite3（这可能需要几分钟）...');
    execSync('node-gyp build', { stdio: 'inherit' });

    console.log('✅ better-sqlite3 编译完成！');
    return true;
  } catch (error) {
    console.error('❌ better-sqlite3 编译失败，请手动执行：');
    console.error(`   cd ${sqlite3Path}`);
    console.error('   node-gyp clean && node-gyp configure && node-gyp build');
    return false;
  }
}

// 初始化数据库
function seedDatabase() {
  if (checkDatabaseExists()) {
    console.log('✅ 数据库已存在，跳过初始化');
    return;
  }

  console.log('🌱 初始化数据库并生成测试数据...');

  try {
    process.chdir(rootDir);
    execSync('pnpm db:seed', { stdio: 'inherit' });
    console.log('✅ 数据库初始化完成！');
  } catch (error) {
    console.error('❌ 数据库初始化失败，请手动执行：');
    console.error('   pnpm db:seed');
  }
}

// 主流程
console.log('🚀 执行 postinstall 脚本...\n');

const buildSuccess = buildBetterSqlite3();
if (buildSuccess) {
  seedDatabase();
}

console.log('\n✨ postinstall 完成！');
console.log('   启动服务: pnpm dev');
