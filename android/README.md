# 智能呼叫中心 Android App

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-1.9.20-purple?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-14-green?logo=android" alt="Android">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-1.5-blue?logo=jetpackcompose" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

Android 端自动拨号销售系统，与 Web 后端系统数据同步。

## ✨ 功能特性

### 核心功能
- 📞 **一键拨号** - 点击即可拨打客户电话
- 🔄 **自动拨号** - 按顺序自动拨打待联系客户
- 📋 **客户列表** - 查看和管理客户信息
- 📝 **通话备注** - 记录通话结果和备注
- 📊 **数据同步** - 与 Web 系统实时同步

### 自动拨号
- 可配置拨号间隔（5-60秒）
- 可配置无人接听超时（10-60秒）
- 后台运行，通知栏显示状态
- 一键暂停/继续

### 离线支持
- 本地数据库缓存客户数据
- 离线查看客户信息
- 网络恢复后自动同步

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **语言** | Kotlin 1.9.20 |
| **UI** | Jetpack Compose, Material 3 |
| **架构** | MVVM, Clean Architecture |
| **依赖注入** | Hilt |
| **网络** | Retrofit, OkHttp |
| **数据库** | Room |
| **存储** | DataStore |
| **异步** | Coroutines, Flow |

## 📁 项目结构

```
app/src/main/java/com/callcenter/app/
├── CallCenterApp.kt          # Application 类
├── MainActivity.kt           # 主 Activity
│
├── data/                     # 数据层
│   ├── api/                  # API 接口
│   │   └── ApiService.kt
│   ├── local/                # 本地存储
│   │   ├── dao/              # Room DAO
│   │   ├── entity/           # 数据实体
│   │   └── preferences/      # 偏好设置
│   ├── model/                # 数据模型
│   └── repository/           # 数据仓库
│
├── di/                       # 依赖注入
│   ├── DatabaseModule.kt
│   └── NetworkModule.kt
│
├── service/                  # 后台服务
│   ├── AutoDialService.kt    # 自动拨号服务
│   └── CallMonitorService.kt # 通话监控服务
│
├── receiver/                 # 广播接收器
│   ├── PhoneStateReceiver.kt # 电话状态监听
│   └── BootReceiver.kt       # 开机启动
│
├── ui/                       # UI 层
│   ├── navigation/           # 导航
│   ├── screens/              # 页面
│   │   ├── auth/             # 登录
│   │   └── main/             # 主页
│   ├── theme/                # 主题
│   └── viewmodel/            # ViewModel
│
└── util/                     # 工具类
    └── CallHelper.kt         # 拨号工具
```

## 🚀 快速开始

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

### 配置服务器地址

在登录页面输入服务器地址，例如：
- 模拟器访问本机：`http://10.0.2.2:8081/api/`
- 局域网访问：`http://localhost:8081/api/`

### 编译运行

1. 克隆项目
```bash
git clone https://github.com/sooglejay/call-center-system.git
cd call-center-system/call-center-android
```

2. 用 Android Studio 打开项目

3. 等待 Gradle 同步完成

4. 连接设备或启动模拟器

5. 点击 Run 按钮

### 默认账号

使用 Web 系统的账号登录：
- 用户名：`admin`
- 密码：`admin123`

## 📱 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络请求 |
| `CALL_PHONE` | 直接拨打电话 |
| `READ_PHONE_STATE` | 监听通话状态 |
| `READ_CALL_LOG` | 读取通话记录 |
| `FOREGROUND_SERVICE` | 后台服务 |
| `POST_NOTIFICATIONS` | 通知显示 |
| `RECEIVE_BOOT_COMPLETED` | 开机启动 |

## 🔧 配置说明

### 自动拨号设置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 拨号间隔 | 10秒 | 每次拨打后的等待时间 |
| 超时时间 | 30秒 | 无人接听自动挂断时间 |

### 服务器配置

确保后端服务已启动并正确配置 CORS：

```javascript
// server/src/app.ts
app.use(cors({
  origin: true,
  credentials: true
}));
```

## 📖 API 对接

App 与 Web 系统共享同一套 API：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 登录 |
| `/api/customers` | GET | 获取客户列表 |
| `/api/customers/pending` | GET | 获取待拨打客户 |
| `/api/calls` | GET/POST | 通话记录 |
| `/api/stats/dashboard` | GET | 统计数据 |

## 🔒 安全说明

- Token 存储在 DataStore 中，应用私有空间
- 密码不明文存储，仅用于登录请求
- 支持 HTTPS 加密传输

## 📝 开发笔记

### 调试模式

在 `build.gradle.kts` 中配置：
```kotlin
buildTypes {
    debug {
        isMinifyEnabled = false
    }
}
```

### 日志查看

使用 Android Studio Logcat 过滤：
```
tag:CallCenter
```

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 License

MIT License - 详见 [LICENSE](LICENSE) 文件
