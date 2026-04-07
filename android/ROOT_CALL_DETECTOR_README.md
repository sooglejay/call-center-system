# Root 通话状态检测模块使用说明

## 概述

本模块为 Root 过的 Android 手机提供更精确的通话状态检测能力，通过读取 RIL (Radio Interface Layer) 日志，可以区分以下状态：

- **IDLE**: 空闲
- **DIALING**: 拨号中
- **ALERTING**: 对方响铃中
- **ACTIVE**: 通话已接通
- **HOLDING**: 通话保持中
- **DISCONNECTING**: 正在挂断
- **DISCONNECTED**: 已挂断

## 自动通话状态标记功能（v1.7.6+）

从版本 1.7.6 开始，系统支持自动识别通话状态并自动完成打标，无需客服手动点击按钮。

### 自动标记逻辑

```
通话状态变更时：
├─ DIALING (拨号中) → 记录开始时间
├─ ALERTING (对方响铃) → 更新状态为"对方响铃中"
├─ ACTIVE (通话接通) → 自动标记为"已接听"
├─ 状态变更检测：
│   ├─ BUSY → 自动标记为"对方忙线"
│   ├─ REJECTED → 自动标记为"对方拒接"
│   ├─ VOICEMAIL → 自动标记为"语音信箱"
│   └─ POWER_OFF → 自动标记为"对方关机"
└─ IDLE (通话结束) → 根据最后状态判断：
    ├─ 如果已标记过 → 保持原标记
    ├─ 如果通话时长=0 → 根据最后状态判断（忙线/拒接/无人接听/关机/语音信箱/响铃未接）
    └─ 如果通话时长>0 → 已由 ACTIVE 标记为"已接听"
```

### 支持自动标记的状态

| 检测状态 | 自动标记结果 | 说明 |
|---------|-------------|------|
| ACTIVE | 已接听 | 通话成功接通 |
| BUSY | 对方忙线 | 对方正在通话中 |
| REJECTED | 对方拒接 | 对方主动挂断 |
| VOICEMAIL | 语音信箱 | 转接到语音信箱 |
| POWER_OFF | 对方关机 | 对方手机已关机 |
| NO_ANSWER | 无人接听 | 响铃超时无人接听 |
| 其他未接通 | 响铃未接 | 其他原因未接通 |

### 实现位置

自动标记逻辑位于 `AutoDialService.setupCallStateListener()` 中：

```kotlin
private fun setupCallStateListener() {
    rootCallStateDetector.addListener(object : RootPhoneStateListener {
        override fun onActive(number: String?, setupTime: Long) {
            // 通话接通时自动标记为已接听
            autoMarkCallStatus("connected", "已接听")
        }

        override fun onIdle(duration: Long) {
            // 通话结束时，根据时长和最后状态判断结果
            if (duration <= 0) {
                when (lastState) {
                    RootCallState.BUSY -> autoMarkCallStatus("busy", "对方忙线")
                    RootCallState.REJECTED -> autoMarkCallStatus("rejected", "对方拒接")
                    // ... 其他状态
                }
            }
        }

        override fun onStateChanged(oldState: RootCallState, newState: RootCallState) {
            // 状态变更时实时检测并标记
            when (newState) {
                RootCallState.BUSY -> autoMarkCallStatus("busy", "对方忙线")
                RootCallState.REJECTED -> autoMarkCallStatus("rejected", "对方拒接")
                // ... 其他状态
            }
        }
    })
}
```

### 使用限制

1. **Root 权限必需**：自动识别功能仅在 Root 设备上有效
2. **RIL 日志依赖**：依赖厂商 RIL 日志格式，不同手机可能存在差异
3. **网络状态无法检测**：以下状态无法通过 RIL 日志检测，可能标记为"响铃未接"：
   - 号码无效/空号
   - 号码已停机
   - 网络信号弱导致呼叫失败

## 文件结构

```
com.callcenter.app.util.root/
├── RootCallStateDetector.kt    # 核心检测类
├── RootUtils.kt                 # Root 工具类
└── EnhancedCallHelper.kt        # 增强版通话助手
```

## 使用方法

### 1. 基础使用 - RootCallStateDetector

```kotlin
class MyService : Service() {
    private lateinit var detector: RootCallStateDetector

    override fun onCreate() {
        super.onCreate()

        // 检查 Root 权限
        if (!RootUtils.hasRootPermission()) {
            Log.e("MyService", "没有 Root 权限")
            return
        }

        // 初始化检测器
        detector = RootCallStateDetector()

        // 添加监听器
        detector.addListener(object : RootPhoneStateListener {
            override fun onDialing(number: String?) {
                Log.d("MyService", "拨号中: $number")
            }

            override fun onAlerting(number: String?) {
                Log.d("MyService", "对方响铃: $number")
            }

            override fun onActive(number: String?, setupTime: Long) {
                Log.d("MyService", "通话接通: $number, 接通耗时: ${setupTime}ms")
            }

            override fun onIdle(duration: Long) {
                Log.d("MyService", "通话结束, 时长: ${duration}ms")
            }
        })

        // 开始检测
        detector.startDetection()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.release()
    }
}
```

### 2. 增强版通话助手 - EnhancedCallHelper

```kotlin
// 在 AutoDialService 中使用
class AutoDialService : Service() {
    private lateinit var enhancedHelper: EnhancedCallHelper

    override fun onCreate() {
        super.onCreate()
        enhancedHelper = EnhancedCallHelper(this)
    }

    private suspend fun waitForCallEndOrTimeout(): Boolean {
        val MAX_CALL_DURATION = 600000 // 10 分钟

        while (isRunning) {
            when (enhancedHelper.getCurrentCallState()) {
                RootCallState.ACTIVE -> {
                    // 精确检测到已接通
                    Log.d(TAG, "通话已接通")
                }
                RootCallState.IDLE -> {
                    // 通话结束
                    val duration = enhancedHelper.getCurrentCallDuration()
                    Log.d(TAG, "通话结束, 时长: ${duration}ms")
                    return true
                }
                else -> {
                    // DIALING, ALERTING 等状态
                }
            }

            // 检查超时
            if (enhancedHelper.getCurrentCallDuration() > MAX_CALL_DURATION) {
                return true
            }

            delay(500)
        }

        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        enhancedHelper.release()
    }
}
```

### 3. Root 工具类使用

```kotlin
// 检查设备是否已 Root
val isRooted = RootUtils.isDeviceRooted()

// 检查是否有 Root 权限
val hasPermission = RootUtils.hasRootPermission()

// 执行 Root 命令
val output = RootUtils.executeRootCommand("getprop gsm.operator.alpha")

// 获取 Root 管理器类型
val managerType = RootUtils.getRootManagerType() // Magisk, SuperSU, 等

// 清除 logcat 缓冲区
val cleared = RootUtils.clearLogcatBuffer()
```

## 与普通通话状态检测对比

| 功能 | 普通手机 (TelephonyManager) | Root 手机 (RootCallStateDetector) |
|------|---------------------------|----------------------------------|
| 拨号中 | ❌ 无法检测 | ✅ DIALING |
| 对方响铃 | ❌ 无法检测 | ✅ ALERTING |
| 已接通 | ⚠️ OFFHOOK (包含拨号中) | ✅ ACTIVE (精确) |
| 通话保持 | ❌ 无法检测 | ✅ HOLDING |
| 正在挂断 | ❌ 无法检测 | ✅ DISCONNECTING |
| 接通耗时 | ❌ 无法计算 | ✅ 自动计算 |
| 通话时长 | ⚠️ 自己计算 | ✅ 精确统计 |

## 注意事项

1. **Root 权限**：使用此模块需要手机已 Root，且应用已获得 Root 权限
2. **电池消耗**：持续读取 logcat 会有一定的电池消耗
3. **兼容性**：不同厂商的 RIL 日志格式可能略有差异，可能需要适配
4. **安全性**：Root 后手机安全性降低，建议仅在内部测试设备使用

## 集成到现有 AutoDialService

修改 `AutoDialService.kt` 中的 `waitForCallEndOrTimeout` 方法：

```kotlin
// 添加成员变量
private var enhancedHelper: EnhancedCallHelper? = null
private var useRootDetection: Boolean = false

override fun onCreate() {
    super.onCreate()

    // 初始化增强版通话助手（如果有 Root 权限）
    if (RootUtils.hasRootPermission()) {
        enhancedHelper = EnhancedCallHelper(this)
        useRootDetection = true
    }
}

private suspend fun waitForCallEndOrTimeout(): Boolean {
    // 如果有 Root 检测，使用它
    if (useRootDetection && enhancedHelper != null) {
        return waitForCallEndWithRoot()
    }

    // 否则使用原来的方式
    return waitForCallEndNormal()
}

private suspend fun waitForCallEndWithRoot(): Boolean {
    val helper = enhancedHelper ?: return false
    var elapsedTime = 0
    val MAX_CALL_DURATION = 600000

    while (_isRunning.value) {
        val state = helper.getCurrentCallState()

        when (state) {
            RootCallState.ACTIVE -> {
                // 通话已接通
            }
            RootCallState.IDLE -> {
                // 通话结束
                return true
            }
            else -> {}
        }

        if (helper.getCurrentCallDuration() > MAX_CALL_DURATION) {
            return true
        }

        delay(500)
        elapsedTime += 500
    }

    return false
}

override fun onDestroy() {
    super.onDestroy()
    enhancedHelper?.release()
}
```

## 测试建议

1. 在 Root 手机上安装测试
2. 使用 `adb logcat -b radio | grep RILJ` 查看 RIL 日志
3. 拨打电话测试各个状态是否正确触发
4. 检查不同厂商手机的兼容性（小米、华为、三星等）
