# 自动外呼“挂断后一直转圈、不再拨下一个”RCA

## TL;DR

本次故障的**最高概率根因**不是 UI 本身卡死，而是**自动外呼状态机在“等待本次通话结束”阶段没有可靠退出**，导致服务一直停留在“当前客户仍在处理中”的状态，因此不会推进到下一个客户。

最关键证据链：

1. 自动外呼主循环 `processQueue()` 是串行的，只有 `waitForCallEndOrTimeout()` 返回后才会进入“标记本次客户 -> 等待间隔 -> 拨打下一个客户”的分支，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1218`、`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1283`、`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1329`。
2. 日志中前两通客户都完整出现了 `OFFHOOK -> IDLE -> 自动打标 -> 下一位` 的闭环；第三通 `voip接口人` 只看到长时间 `OFFHOOK` 轮询，没有看到后续 `IDLE/返回/下一位` 证据，说明卡点就在“等待结束”阶段。
3. `waitForCallEndOrTimeout()` 主要依赖 `telephonyManager.callState` 从 `OFFHOOK` 回到 `IDLE`；若系统拨号器挂断后该状态未及时回落，循环会持续等待，直到 10 分钟上限 `MAX_CALL_DURATION`，这对现场来说等同“永远转圈”，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1535`、`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1643`、`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1937`。
4. 代码虽已加入 `AudioManager.MODE_IN_CALL / MODE_IN_COMMUNICATION` 作为“接通”兜底，但**没有对“已挂断但 Telephony 仍卡在 OFFHOOK”建立对称兜底**。也就是说，接通检测有备用通道，挂断检测没有，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1898`。
5. UI 上看到的“转圈”更像是**服务仍处于运行中**的表现，而不是页面 `isLoading` 没有关闭。`AgentTaskExecutionScreen` 顶部自动拨号卡片无条件显示一个 `CircularProgressIndicator`，只要自动拨号模式卡片存在就会转；真正决定是否仍在自动拨号的是 `autoDialViewModel.isRunning` / `currentCustomer` 等服务态，而不是页面数据加载态，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:73`、`android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:80`、`android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:2005`。

**结论**：

- **P1 根因**：挂断后未可靠识别到“通话已结束”，`waitForCallEndOrTimeout()` 不返回，主队列不推进。
- **P1.5 放大器**：已接通后的最大等待时间是 10 分钟，缺少“挂断后 N 秒内未推进下一拨则强制复位”的 watchdog，导致故障一旦触发，用户体感就是无限卡住。
- **P2 次要风险**：历史版本/并行分支里曾存在 `isDialing` 未放入 `try/finally` 的典型死锁风险；当前这份 `processQueue()` 已改成 `while` 串行实现，不再直接依赖该旧模式，但同类“状态位未兜底复位”的问题仍值得在修复方案中统一治理。

---

## 现象定义

用户操作路径：

1. 自动外呼拨打客户。
2. 系统拨号器前台接管通话。
3. 用户在系统拨号器中手动挂断。
4. 返回 App。
5. 页面显示持续“转圈 / loading”，且**不会自动拨打下一个客户**。

从状态机角度，这意味着系统停在：

- “本次客户尚未完成”
- 或“已挂断但未被状态机确认完成”
- 因而没有进入“间隔等待 + 下一位拨号”

---

## 相关代码结构审计

### 1. 主状态机：`processQueue()`

自动外呼主循环位于 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1218`。

核心流程：

1. 取当前客户 `customerQueue[currentIndex]`
2. 创建通话记录并发起拨号 `callHelper.makeCall(...)`
3. 调用 `waitForCallEndOrTimeout()` 阻塞等待本次通话结束
4. 返回后更新拨打计数、自动打标
5. `delay(intervalSeconds)`
6. `waitUntilCanDial()`
7. 进入下一客户

这说明：

> **只要 `waitForCallEndOrTimeout()` 不返回，下一位客户就绝不可能被拨出。**

这是本次 RCA 的主锚点。

### 2. “下一拨”前置条件：`waitUntilCanDial()` / `canDialNext()`

下一拨前会执行 `waitUntilCanDial()`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:551`。

当前外部改动后，`canDialNext()` 条件是：

- App 在前台，或至少 `isAppActive()` 为真
- `getCallState() == CALL_STATE_IDLE`

见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:517`。

这次改动的价值：

- 放宽了“必须严格前台”的限制，减少“回到 App 但生命周期判断过严”导致的误挂起。
- 增加了详细日志，便于区分“前台条件不满足”还是“通话状态不空闲”。

但它**不能解决主问题**：

- 如果系统一直认为 `callState != IDLE`，`waitUntilCanDial()` 仍会一直等。
- 更重要的是，当前故障更早卡在 `waitForCallEndOrTimeout()`，甚至还没走到下一拨前置检查。

### 3. 通话结束检测：`waitForCallEndOrTimeout()`

该函数位于 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1468`，是本次故障的核心嫌疑点。

其逻辑大致为：

- 初始状态设为 `DIALING`
- 轮询 `telephonyManager.callState`
- `OFFHOOK` 时认为已接通，记录 `offHookStartTime`
- `IDLE` 且已接通过时，经过 `MIN_CONFIRM_IDLE_TIME` 去抖后，认定通话结束并返回
- 若从未接通且超出 `timeoutSeconds`，按未接通返回
- 若已接通但持续超过 `MAX_CALL_DURATION`，强制按已接听返回

关键点：

- 接通依赖 `CALL_STATE_OFFHOOK`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1535`
- 挂断依赖 `CALL_STATE_IDLE`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1643`
- 已接通后的硬超时是 `MAX_CALL_DURATION`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1937`

### 4. Root 检测器并未真正成为主兜底

`setupCallStateListener()` 只在设备有 Root 权限时启用 `RootCallStateDetector`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:220`。

但即使 Root 监听器会异步自动打标，它也**没有驱动 `waitForCallEndOrTimeout()` 直接退出**。主循环仍然在轮询 `telephonyManager.callState`。

也就是说：

- Root 检测器更像“辅助打标器”
- 不是“主状态机的结束信号源”

因此即便 Root 侧看到了结束，若 Telephony 侧没回到 `IDLE`，主循环仍可能卡住。

### 5. UI 层并非根因

`AgentTaskExecutionScreen` 页面顶部的自动拨号卡片中，标题左侧始终渲染一个 `CircularProgressIndicator`，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:2005`。

而页面级 `isLoading` 来自 `AgentTaskViewModel` 的任务详情加载，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:73`。这类 `isLoading` 只影响任务详情加载骨架/刷新，不控制自动拨号状态机。

自动拨号真正绑定的是：

- `autoDialViewModel.isRunning`，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:80`
- `autoDialViewModel.currentCustomer`，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:81`

而 `AutoDialViewModel` 本身只是镜像 `AutoDialService` 的静态 `StateFlow`，见 `android/app/src/main/java/com/callcenter/app/ui/viewmodel/AutoDialViewModel.kt:77`、`android/app/src/main/java/com/callcenter/app/ui/viewmodel/AutoDialViewModel.kt:85`。

因此：

> UI 的“转圈”是服务卡住后的表象，不是根因。

---

## 日志取证

日志文件：`android/user_5_logs_20260429_125604_0w3t.txt`

### 时间线摘要

#### 第一通：涂雷

- 约 `20:47:46` 进入 `OFFHOOK`
- 约 `20:48:15` 回到 `IDLE`
- 随后自动打标并拨打下一位

说明：

- 正常路径可工作
- `OFFHOOK -> IDLE -> processQueue继续推进` 这条链路在该设备/该版本上**不是完全失效**，而是**间歇性失效**

#### 第二通：有活动

- 约 `20:48:29` 进入 `OFFHOOK`
- 约 `20:48:56` 回到 `IDLE`
- 随后自动打标并拨打下一位

说明：

- 第二次也成功闭环
- 进一步排除“代码完全写错”这种静态必现问题
- 更像是**某些通话结束场景下系统状态回传不稳定**

#### 第三通：voip接口人

- 约 `20:49:10` 进入 `OFFHOOK`
- 日志末尾 `20:51:44` 仍在 `OFFHOOK` 轮询，`timeSinceDial=153708ms`
- 没有看到 `IDLE`、没有看到“通话结束处理完成”、没有看到“等待间隔后拨打下一位”

这与用户描述高度一致：

- 用户已经主观上“挂断并回到 App”
- 但服务侧仍认为“本次通话未结束”
- 所以不会进入下一拨

### 取证结论

日志最强证据不是某条异常栈，而是：

> **第三通缺失了前两通都有的“结束闭环日志”。**

这类“缺少应有状态迁移”的证据，通常比单条报错更能说明状态机卡死点。

---

## 根因综合与排序

### 假设 1（最高概率，>70%）
### 挂断后 `TelephonyManager.callState` 未及时回落到 `IDLE`，导致 `waitForCallEndOrTimeout()` 一直不返回

**证据：**

1. 主循环必须等待 `waitForCallEndOrTimeout()` 返回，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1283`。
2. 该函数对“结束”的主判据是 `CALL_STATE_IDLE`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1643`。
3. 第三通日志长时间停留在 `OFFHOOK` 轮询，没有后续结束日志。
4. 代码只对“接通检测”做了 `AudioManager` 兜底，没有对“挂断检测”做对称兜底，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1898`。
5. Xiaomi / Android 14 / 系统拨号器切前后台场景下，Telephony 状态回传延迟或丢失并不罕见，尤其是系统拨号器、InCallService、音频路由切换并发时。

**机制解释：**

- 用户在系统拨号器中挂断
- App 回前台
- 但 `telephonyManager.callState` 仍短暂甚至长时间保持 `OFFHOOK`
- `waitForCallEndOrTimeout()` 继续轮询
- `processQueue()` 无法推进
- UI 仍显示自动拨号进行中

**为什么前两通正常、第三通异常？**

因为这是**设备/系统状态回传的偶发不一致**，不是纯静态逻辑错误。前两通回传正常，第三通回传异常即可复现。

---

### 假设 2（中高概率，40%~60%）
### 已接通后的超时保护过长（10 分钟），把“状态丢失”放大成“无限卡住”

`MAX_CALL_DURATION` 触发点在 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1937`。

这不是第一根因，但它是**故障放大器**：

- 一旦挂断事件漏掉
- 系统要等到 10 分钟后才会强制结束
- 对用户来说就是“永远不动”

因此它不是“为什么会卡住”的第一原因，但它解释了“为什么体感如此严重”。

---

### 假设 3（中等概率，25%~40%）
### Root/辅助检测与主轮询源分裂，导致“辅助层已知道结束，主状态机却没结束”

`setupCallStateListener()` 中 Root 检测器会异步自动打标，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:234`。

但主循环退出条件仍由 `waitForCallEndOrTimeout()` 内部轮询决定。

这会造成一种架构性问题：

- A 通道（Root/辅助）知道通话结束了
- B 通道（主轮询）还不知道
- 结果：服务器状态可能已更新，但队列仍不推进

这类“双信号源未统一”的设计很容易产生“看起来部分成功、整体却卡住”的问题。

---

### 假设 4（中等偏低概率，15%~25%）
### 前后台判定过严导致下一拨被挂起

新改动前，`canDialNext()` 只看 `isAppInForeground()`；现在已放宽为 `isAppInForeground() || isAppActive()`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:517`。

这说明团队已经观察到“回到 App 但仍被判后台”的风险。

但从本次现象看，这更像**次要阻塞点**，不是主根因：

- 因为用户描述是“挂断后一直转圈，不拨下一位”
- 而日志更像卡在“等待本次通话结束”而非“已结束但不允许下一拨”

不过它仍应纳入修复，因为它会让问题更难排查。

---

### 假设 5（低概率，但必须防御）
### 状态位/协程异常退出后未复位，导致服务逻辑自锁

在你之前给出的旧代码片段中，存在典型模式：

```kotlin
isDialing = true
val callCompleted = waitForCallEndOrTimeout(...)
isDialing = false // 不在 finally
```

这类写法一旦 `waitForCallEndOrTimeout()` 抛出取消异常，`isDialing` 会永久卡在 `true`。

当前这份 `AutoDialService.kt` 的 `processQueue()` 已经是 `while` 串行版本，未直接看到同名 `isDialing` 守卫；因此它**不是本次日志对应版本的首要根因**。但从工程治理角度，仍应把“所有状态位必须 finally 复位”列为修复原则，否则未来会以别的状态位重演。

---

## 为什么 UI 看起来像“loading 一直不消失”

### 1. 页面级 `isLoading` 不是主因

`AgentTaskExecutionScreen` 的 `isLoading` 来自 `AgentTaskViewModel`，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:73`。

这主要控制任务详情加载，不控制自动拨号主状态机。

### 2. 自动拨号卡片本身就画了一个 spinner

自动拨号卡片标题左侧固定渲染 `CircularProgressIndicator`，见 `android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:2005`。

因此用户看到“转圈”并不等于页面网络请求没结束，而更可能只是：

- 自动拨号仍被认为在运行
- 当前客户仍未完成
- 卡片一直处于“自动拨号中”视觉态

### 3. `AutoDialViewModel` 只是服务镜像

`AutoDialViewModel` 在初始化时直接订阅 `AutoDialService.isRunning/currentCustomer/dialedCount/totalCount`，见 `android/app/src/main/java/com/callcenter/app/ui/viewmodel/AutoDialViewModel.kt:77`、`android/app/src/main/java/com/callcenter/app/ui/viewmodel/AutoDialViewModel.kt:85`。

所以只要服务没退出等待态，UI 就会持续显示“还在跑”。

---

## 状态机图（现状）

```text
processQueue
  ↓
取当前客户
  ↓
makeCall()
  ↓
waitForCallEndOrTimeout()
  ├─ 检测到 OFFHOOK → 进入 ACTIVE/通话中
  ├─ 检测到 IDLE(已接通过) → 确认挂断 → 返回 true
  ├─ 未接通且超时 → 返回 true
  ├─ 已接通且超过 MAX_CALL_DURATION(10分钟) → 返回 true
  └─ 一直收不到“结束”信号 → 卡死在这里
  ↓
更新拨打计数/自动打标
  ↓
delay(interval)
  ↓
waitUntilCanDial()
  ├─ App可见 + callState=IDLE → 继续
  └─ 否则继续等待
  ↓
拨打下一个客户
```

### 故障态图

```text
用户在系统拨号器挂断
  ↓
系统/设备未把 callState 及时回落到 IDLE
  ↓
waitForCallEndOrTimeout() 持续认为“仍在通话”
  ↓
processQueue() 不返回
  ↓
不会进入下一拨
  ↓
UI 仍显示自动拨号进行中（转圈）
```

---

## 修复方案

以下方案按“必须做 / 强烈建议 / 可选增强”分层。

### A. 必须做：把“通话结束信号”从单一 Telephony 轮询改成多信号融合

#### A1. 为“挂断检测”增加对称兜底

当前只有“接通兜底”：

- `AudioManager.MODE_IN_CALL / MODE_IN_COMMUNICATION` 可辅助判定已接通，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1902`

缺失的是“挂断兜底”。建议在 `waitForCallEndOrTimeout()` 中加入：

- 若此前 `callConnected == true`
- 且连续 N 次轮询满足以下任一条件：
  - `audioMode == MODE_NORMAL`
  - `InCallService` 已收到 `STATE_DISCONNECTED` / `onCallRemoved`
  - `CallAudioState` 不再处于通话路由
  - Root 检测器进入 `IDLE/DISCONNECTED`
- 则即使 `telephonyManager.callState` 仍未回到 `IDLE`，也应判定“通话已结束”并推进状态机

也就是把结束判定从：

```text
Telephony IDLE
```

升级为：

```text
Telephony IDLE OR Audio恢复正常 OR InCallService断开 OR Root检测断开
```

#### A2. 统一“结束事件总线”

不要让：

- `waitForCallEndOrTimeout()` 自己轮询一套
- `RootCallStateDetector` 异步打一套
- `InCallService` 再感知一套

建议统一成一个 `CallLifecycleSnapshot` / `CallTerminationSignal`：

- `telephonyState`
- `audioMode`
- `inCallServiceState`
- `rootState`
- `lastStateChangeAt`

由一个聚合器输出：

- `isCallEstablished`
- `isCallTerminated`
- `terminationReason`

然后 `waitForCallEndOrTimeout()` 只消费这个聚合结果，不再自己散落判断。

这样能避免“辅助层知道结束了，主循环却不知道”。

---

### B. 必须做：增加“下一拨 watchdog”

这是你明确要求加入的防御机制，建议作为**独立兜底层**实现。

#### B1. 定义一个 `DIALING_NEXT_PENDING` 状态

在本次通话完成后、下一位尚未拨出前，显式进入：

- `CALL_FINISHED`
- `DIALING_NEXT_PENDING`
- `DIALING_NEXT_STARTED`

并记录时间戳：

- `enteredNextPendingAt`
- `lastCallFinishedAt`
- `lastNextDialStartedAt`

#### B2. watchdog 规则

如果进入 `DIALING_NEXT_PENDING` 后超过 N 秒（建议 8~15 秒，可配置）仍未进入 `DIALING_NEXT_STARTED`，则：

1. 打印高优先级错误日志
2. 强制复位本轮临时状态：
   - `_currentCallState = IDLE`
   - `hasAutoMarkedCurrentCall = false` 或按已完成态清理
   - 清理本次 call session 的临时标志
3. 重新执行一次 `waitUntilCanDial()` 的快速检查
4. 若条件满足，直接推进 `processQueue()` 到下一位
5. 若条件仍不满足，记录阻塞原因（前台/通话态/暂停态）

#### B3. watchdog 目标

watchdog 不是替代正确状态机，而是保证：

> 即使某个结束信号漏了，也不能让自动外呼无限卡死。

---

### C. 必须做：缩短“已接通后卡死”的最大等待时间

当前 `MAX_CALL_DURATION` 为 10 分钟，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1937`。

建议拆成两个超时：

1. **业务通话上限**：例如 10 分钟，保留
2. **结束确认异常上限**：例如 8~20 秒

更具体地说：

- 一旦检测到“用户已从系统拨号器返回 App”
- 且音频模式已恢复正常 / InCallService 已断开
- 但 Telephony 仍卡在 `OFFHOOK`
- 不应再等 10 分钟
- 应在一个很短的“结束确认异常窗口”后强制收敛

建议新增：

- `POST_HANGUP_STUCK_TIMEOUT_MS = 8000L`

---

### D. 强烈建议：把 `waitForCallEndOrTimeout()` 改成 `try/finally` 资源收敛

当前函数内部有多个 `return true` 分支，且会启动：

- `audioEnergyAnalyzer`
- `keywordDetector`
- 免提开关
- `_currentCallState` 更新

建议统一改成：

- `try { ... } finally { ... }`

在 `finally` 中保证：

- 停止音频分析器
- 释放关键词检测器
- 关闭/恢复音频路由（按业务需要）
- 将 `_currentCallState` 收敛到最终态
- 记录退出原因

这样即使未来出现 `CancellationException`、权限异常、设备异常，也不会留下半开状态。

---

### E. 强烈建议：把“前台判定”与“通话结束判定”解耦

你们刚加的 `isAppActive()` 放宽是对的，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:517`。

但建议进一步明确：

- **通话是否结束**：只由通话生命周期信号决定
- **是否允许下一拨**：由前台/可见性策略决定

不要把“回到前台”混入“本次通话是否结束”的判断，否则会让问题边界模糊。

---

### F. 建议：UI 显式区分“正在通话”与“等待恢复/卡住”

当前用户只看到一个 spinner，不知道卡在哪。

建议把自动拨号卡片状态文案细分为：

- 正在拨号
- 对方响铃中
- 通话中
- 通话已结束，等待下一拨
- 已挂起，等待回到应用
- 检测到状态异常，正在自动恢复

这样即使再出问题，现场也能第一时间判断卡点。

---

## 建议的代码级改造点

### 1. `waitForCallEndOrTimeout()` 内新增“挂断多信号判定”

目标文件：

- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1468`

建议新增局部状态：

- `lastConfirmedInCallAt`
- `hangupSuspectStartAt`
- `postHangupStuckStartAt`
- `terminationSignals`

伪代码：

```kotlin
val telephonyIdle = state == TelephonyManager.CALL_STATE_IDLE
val audioBackToNormal = audioMode == AudioManager.MODE_NORMAL
val inCallDisconnected = !AutoSpeakerInCallService.isServiceActive || inCallState == DISCONNECTED
val rootIdle = _currentCallState.value == RootCallState.IDLE || rootDetectorState == RootCallState.IDLE

val terminatedByFallback = callConnected && (
    telephonyIdle ||
    (audioBackToNormal && inCallDisconnected) ||
    rootIdle
)

if (terminatedByFallback) {
    // 进入结束确认窗口，连续满足 N ms 后返回
}
```

### 2. `processQueue()` 增加 next-dial watchdog 埋点

目标文件：

- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1218`

建议在以下节点打点：

- 本次通话结束时：`lastCallFinishedAt = now`
- 进入下一拨等待前：`nextDialPendingAt = now`
- 真正发起下一拨时：`nextDialStartedAt = now`

并启动一个轻量 watchdog job：

- 若 `now - nextDialPendingAt > N` 且仍未 `nextDialStartedAt`
- 则强制复位并推进

### 3. `AutoSpeakerInCallService` 输出可消费的断开信号

目标文件：

- `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:50`

当前它只在内部日志里知道 `STATE_DISCONNECTED` / `onCallRemoved`，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:63`、`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:79`。

建议把这些状态暴露为：

- `StateFlow<InCallSnapshot>`
- 或静态原子字段 + 时间戳

供 `AutoDialService` 读取。

### 4. Root 检测器从“只打标”升级为“可结束主流程”

目标文件：

- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:220`
- `android/app/src/main/java/com/callcenter/app/util/root/RootCallStateDetector.kt:255`

当前 Root 检测器更多用于自动打标，不足以驱动主循环退出。

建议：

- Root 侧一旦进入 `IDLE/DISCONNECTING/DISCONNECTED`
- 记录一个 `rootTerminationAt`
- 主循环将其纳入结束判定

---

## 验证计划

### 一、单机场景验证

#### Case 1：正常接通后本地挂断

步骤：

1. 自动拨号拨出
2. 对方接听
3. 在系统拨号器中手动挂断
4. 返回 App

期望：

- 3 秒内识别本次通话结束
- 进入 `DIALING_NEXT_PENDING`
- 在配置间隔后拨打下一位
- 不出现无限 spinner

#### Case 2：对方未接听自动结束

期望：

- 走未接通分支
- 自动打标“响铃未接”
- 正常推进下一位

#### Case 3：系统 Telephony 故意延迟/卡住 OFFHOOK

做法：

- mock `telephonyManager.callState` 长时间保持 `OFFHOOK`
- 同时让 `audioMode -> MODE_NORMAL`、`InCallService -> DISCONNECTED`

期望：

- 通过 fallback 在 `POST_HANGUP_STUCK_TIMEOUT_MS` 内结束
- 不再等待 10 分钟

#### Case 4：App 回前台判定边界

步骤：

- 挂断后快速切回 App
- 分别验证 `isAppInForeground=false, isAppActive=true` 场景

期望：

- 不会误判为“必须继续挂起”
- 新增日志能明确打印阻塞原因

### 二、日志验收标准

新增日志后，必须能在一次完整通话中看到以下链路：

```text
CALL_ESTABLISHED
CALL_TERMINATION_SIGNAL_DETECTED
CALL_TERMINATION_CONFIRMED
NEXT_DIAL_PENDING
NEXT_DIAL_STARTED
```

若卡住，日志必须能明确说明卡在：

- Telephony 未回 IDLE
- App 不可见
- pause 状态
- watchdog 触发恢复

### 三、回归验证

重点回归：

1. 语音信箱识别不被破坏
2. 音频能量分析仍能正常启动/停止
3. Root 设备与非 Root 设备都能工作
4. 手动停止自动拨号不会被 watchdog 误恢复
5. 多轮拨打同一客户 `dialsPerCustomer` 不被打乱

---

## 最终结论

本次 bug 的本质是：

> **自动外呼主状态机把“本次通话是否结束”过度绑定在单一 Telephony `IDLE` 信号上，而该信号在系统拨号器挂断返回 App 的场景下并不总是可靠。**

因此一旦 `IDLE` 漏报或延迟：

- `waitForCallEndOrTimeout()` 不返回
- `processQueue()` 不推进
- 下一位客户不拨出
- UI 继续显示自动拨号进行中，形成“无限转圈”的用户感知

### 根因排序

1. **主根因**：挂断结束信号单点依赖 `TelephonyManager.callState == IDLE`
2. **放大器**：已接通后的超时上限过长（10 分钟）
3. **架构缺陷**：Root / InCall / Audio / Telephony 多信号未统一成单一结束判定
4. **次要问题**：前后台判定可能让“已结束但未下一拨”更难恢复

### 最小有效修复集

至少要同时做这 4 件事：

1. 给“挂断检测”增加多信号兜底
2. 增加 `DIALING_NEXT_PENDING` watchdog
3. 缩短“挂断后异常卡住”的收敛时间
4. 用统一状态机/统一事件源替代分散判断

---

## 附录：关键代码定位

- 自动外呼主循环：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1218`
- 发起拨号：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1253`
- 等待通话结束：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1468`
- OFFHOOK 接通判定：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1535`
- IDLE 挂断判定：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1643`
- AudioManager 接通兜底：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1898`
- 已接通最大超时：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1937`
- 下一拨前置检查：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:517`
- 下一拨等待：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:551`
- Root 监听器接入：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:220`
- Root 检测器状态变更：`android/app/src/main/java/com/callcenter/app/util/root/RootCallStateDetector.kt:255`
- InCallService 断开事件：`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:63`
- InCallService onCallRemoved：`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:79`
- 自动拨号 UI 状态镜像：`android/app/src/main/java/com/callcenter/app/ui/viewmodel/AutoDialViewModel.kt:77`
- 页面级 isLoading：`android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:73`
- 自动拨号卡片 spinner：`android/app/src/main/java/com/callcenter/app/ui/screens/agent/AgentTaskExecutionScreen.kt:2005`
