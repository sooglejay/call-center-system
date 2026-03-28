# App 新版本发布流程

## 概述

本文档描述了智能呼叫中心系统 Android App 的版本管理和发布流程。

## 发布流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                        新版本发布流程                            │
└─────────────────────────────────────────────────────────────────┘

  ┌──────────┐
  │ 开发完成  │
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │ 版本打包  │  ./gradlew assembleRelease
  └────┬─────┘
       │
       ▼
  ┌──────────┐     ┌──────────┐
  │ 签名APK  │────▶│ 测试验证  │
  └────┬─────┘     └────┬─────┘
       │                │
       │           ┌────┴────┐
       │           │ 测试通过?│
       │           └────┬────┘
       │                │ 否
       │                ▼
       │           ┌──────────┐
       │           │ 修复问题  │
       │           └──────────┘
       │
       ▼ 是
  ┌──────────┐
  │ 进入后台  │  http://server/admin/version
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │ 填写信息  │  版本号、版本名称、更新日志
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │ 上传APK  │  选择打包好的APK文件
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │ 点击发布  │
  └────┬─────┘
       │
       ▼
  ┌──────────┐
  │ 发布成功  │  客户端自动检测更新
  └──────────┘
```

## 详细步骤

### 1. 开发完成

- 功能开发完成
- 代码审查通过
- 本地测试通过

### 2. 版本打包

在 Android Studio 中执行：

```bash
# 清理并构建 Release 版本
./gradlew clean assembleRelease
```

构建完成后，APK 文件位于：
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

### 3. 签名 APK

使用发布密钥对 APK 进行签名：

```bash
# 使用 jarsigner 签名
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore my-release-key.jks \
  app-release-unsigned.apk \
  alias_name

# 或使用 apksigner（推荐）
apksigner sign --ks my-release-key.jks \
  --out app-release.apk \
  app-release-unsigned.apk
```

### 4. 测试验证

在测试环境安装签名后的 APK：

```bash
adb install app-release.apk
```

验证内容：
- [ ] 应用能正常启动
- [ ] 核心功能正常
- [ ] 新版本号显示正确
- [ ] 自动更新功能正常

### 5. 进入后台发布

1. 登录 Web 管理后台
2. 进入菜单：**App版本**
3. 点击 **发布新版本** 按钮

### 6. 填写版本信息

| 字段 | 说明 | 示例 |
|------|------|------|
| 版本号 | Android versionCode，必须递增 | 100 |
| 版本名称 | 用户可见的版本号 | 1.0.0 |
| 更新日志 | 本次更新的内容 | 1. 修复已知问题<br>2. 优化用户体验 |
| 强制更新 | 是否强制用户更新 | 否 |
| 最低版本号 | 低于此版本强制更新 | 100 |

### 7. 上传 APK

1. 点击 **上传APK** 按钮
2. 选择签名后的 APK 文件
3. 等待上传完成

**注意**：APK 文件名格式为 `app-release-{versionCode}.apk`

### 8. 发布版本

1. 确认所有信息填写正确
2. 点击 **确定** 按钮
3. 发布成功后，客户端会自动检测更新

## 客户端更新机制

### 检测时机

- App 启动时自动检测
- 用户手动检查（可在设置中添加）

### 更新流程

```
┌─────────────┐
│   App启动   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 检查版本更新 │  GET /api/version/check?version_code=xxx
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 有新版本?   │
└──────┬──────┘
       │
   是  │      否
   ┌───┘      └───┐
   ▼              ▼
┌──────────┐  ┌──────────┐
│显示更新弹窗│  │正常使用  │
└────┬─────┘  └──────────┘
     │
     ▼
┌──────────┐
│用户确认  │
└────┬─────┘
     │
     ▼
┌──────────┐
│下载APK   │  使用系统 DownloadManager
└────┬─────┘
     │
     ▼
┌──────────┐
│自动安装  │  调用系统安装器
└────┬─────┘
     │
     ▼
┌──────────┐
│更新完成  │
└──────────┘
```

## 版本号规范

### Android versionCode

- 使用整数，必须递增
- 建议格式：`major * 10000 + minor * 100 + patch`
- 例如：1.2.3 = 10203

### 版本名称 (versionName)

- 使用语义化版本号
- 格式：`主版本.次版本.修订号`
- 例如：`1.0.0`、`1.2.3`

## 强制更新策略

### 何时使用强制更新

- 修复了严重的安全漏洞
- 修复了导致崩溃的 Bug
- API 接口不兼容的变更

### 配置方法

在发布版本时：
1. 勾选 **强制更新** 选项
2. 或设置 **最低要求版本号** 高于当前客户端版本

## 回滚策略

如果发现新版本有问题：

1. 立即在后台发布一个修复版本
2. 或联系用户手动卸载后安装旧版本

**注意**：不支持直接回滚到旧版本，必须发布新版本。

## 相关文件

### 服务端

- `server/src/config/database.ts` - 数据库表定义
- `server/src/controllers/version.controller.ts` - 版本管理 API
- `server/src/routes/version.routes.ts` - 版本管理路由

### 客户端 (Web)

- `client/src/pages/admin/VersionManagement.tsx` - 版本管理页面

### 客户端 (Android)

- `android/app/src/main/java/com/callcenter/app/util/AppUpdateManager.kt` - 更新管理器
- `android/app/src/main/java/com/callcenter/app/ui/components/UpdateDialog.kt` - 更新弹窗
- `android/app/src/main/java/com/callcenter/app/ui/viewmodel/UpdateViewModel.kt` - 更新 ViewModel

## API 接口

### 检查版本更新

```
GET /api/version/check?version_code={currentVersion}&platform=android

Response:
{
  "version_code": 100,
  "version_name": "1.0.0",
  "apk_url": "https://server.com/uploads/apk/app-release-100.apk",
  "update_log": "1. 修复已知问题\n2. 优化用户体验",
  "force_update": false,
  "min_version_code": 100
}
```

### 获取版本列表（管理员）

```
GET /api/version/list
Authorization: Bearer {token}
```

### 创建新版本（管理员）

```
POST /api/version/create
Authorization: Bearer {token}
Content-Type: application/json

{
  "version_code": 101,
  "version_name": "1.0.1",
  "update_log": "修复了一些问题",
  "force_update": false,
  "min_version_code": 100
}
```

### 上传 APK（管理员）

```
POST /api/version/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: {apk_file}
version_code: 101
```

## 注意事项

1. **版本号必须递增**：Android 系统要求 versionCode 必须递增
2. **APK 必须签名**：未签名的 APK 无法安装
3. **测试后再发布**：务必在测试环境验证通过后再发布
4. **保留旧版本 APK**：建议保留历史版本的 APK 文件备份
5. **更新日志要清晰**：让用户了解更新的内容
