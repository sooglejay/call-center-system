/**
 * 安装后自动编译 better-sqlite3
 * 在 macOS/Windows 上会自动执行编译
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const platform = process.platform;

// 只在 macOS 和 Windows 上自动编译
if (platform !== 'darwin' && platform !== 'win32') {
  console.log('🐧 Linux 系统，跳过 better-sqlite3 自动编译');
  process.exit(0);
}

const sqlite3Path = path.join(
  __dirname,
  '../node_modules/.pnpm/better-sqlite3@12.8.0/node_modules/better-sqlite3'
);

const bindingPath = path.join(sqlite3Path, 'build/Release/better_sqlite3.node');

// 检查是否已编译
if (fs.existsSync(bindingPath)) {
  console.log('✅ better-sqlite3 已编译，跳过');
  process.exit(0);
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
} catch (error) {
  console.error('❌ better-sqlite3 编译失败，请手动执行：');
  console.error(`   cd ${sqlite3Path}`);
  console.error('   node-gyp clean && node-gyp configure && node-gyp build');
  // 不退出错误，让安装继续
  process.exit(0);
}
