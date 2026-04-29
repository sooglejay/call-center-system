# Android 自动拨号通话状态识别与自动免提整改提案

## 1. 目标与结论

本提案聚焦两个问题：

1. **自动拨号后可靠区分**：
   - 真人接听
   - 响铃未接
   - 语音信箱自动应答
2. **自动拨号后自动开启免提不稳定**：尤其在 MIUI / HyperOS 上，当前 AccessibilityService 方案存在明显不确定性。

结论先行：

- **问题 1 现状**：当前项目已经具备“时长 + 音频能量 + 关键词 + Root 精确状态”的多信号雏形，但**主链路仍然以 `TelephonyManager.callState` / `CALL_STATE_OFFHOOK` 为核心**，这无法从系统层面区分“真人接听”和“语音信箱自动应答”，因此现有识别本质上仍是**启发式推断**，不是强确定性识别。关键实现位于 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1476`、`android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:143`。
- **问题 2 现状**：项目已经同时声明了 `InCallService`、`ConnectionService`、`AccessibilityService` 三条路径，但运行时优先级和能力边界没有彻底收敛。`InCallService` 已有较强实现，但是否真正生效取决于“是否成为默认拨号器”；而 MIUI 上当前仍大量依赖 Accessibility 兜底，导致稳定性受系统权限、界面渲染、后台限制影响。关键实现位于 `android/app/src/main/AndroidManifest.xml:95`、`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:481`、`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:38`、`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:68`。
- **推荐总方案**：
  - **通话状态识别**：建立“**分层信号融合状态机**”，把 `RINGING/OFFHOOK/IDLE` 仅作为粗粒度传输层信号；把“真人/语音信箱/未接”判定下沉为独立的 `CallOutcomeEngine`，统一融合：Telephony / InCall / CallLog / Audio Energy / Keyword / Root / 用户手动确认。
  - **自动免提**：建立“**默认拨号器 + InCallService 为主，Accessibility 为兜底，AudioManager 为末级兜底**”的分层策略；同时补齐 MIUI 健康检查、权限引导、服务存活检测、失败回退与可观测性。

---

## 2. 现状审计

### 2.1 Manifest 与系统能力声明

项目已声明与两类问题相关的关键权限与服务：

- 电话与通话日志权限：`CALL_PHONE`、`READ_PHONE_STATE`、`READ_CALL_LOG`、`WRITE_CALL_LOG`，见 `android/app/src/main/AndroidManifest.xml:13`-`16`
- 录音与音频控制权限：`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`，见 `android/app/src/main/AndroidManifest.xml:19`、`25`
- 前台服务权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`，见 `android/app/src/main/AndroidManifest.xml:28`-`29`
- `AutoDialService` 前台服务声明，见 `android/app/src/main/AndroidManifest.xml:75`-`80`
- `AutoSpeakerInCallService` 声明，带 `BIND_INCALL_SERVICE`，见 `android/app/src/main/AndroidManifest.xml:95`-`108`
- `AutoSpeakerAccessibilityService` 声明，带 `BIND_ACCESSIBILITY_SERVICE`，见 `android/app/src/main/AndroidManifest.xml:110`-`123`

审计结论：**声明层面并不缺主干能力**，问题主要在于**运行时链路选择、状态融合、ROM 兼容与健康检查不足**。

### 2.2 当前拨号与默认拨号器路径

`CallHelper.makeCall()` 会先判断当前应用是否为默认拨号器；若是，则走 `TelecomManager.placeCall()`，否则走 `Intent.ACTION_CALL`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:49`-`101`。

这意味着：

- **成为默认拨号器时**，理论上可以让 `InCallService` 参与通话生命周期与音频路由控制。
- **不是默认拨号器时**，项目只能退回到系统电话 App + Accessibility / AudioManager 旁路控制。

`DefaultDialerHelper` 已提供默认拨号器检查与申请逻辑，Android 10+ 走 `RoleManager.ROLE_DIALER`，Android 6-9 走 `ACTION_CHANGE_DEFAULT_DIALER`，见 `android/app/src/main/java/com/callcenter/app/util/DefaultDialerHelper.kt:26`-`80`。

审计结论：**默认拨号器能力已具备，但尚未被提升为“主路径前置条件”**。这直接影响自动免提稳定性上限。

### 2.3 ConnectionService 现状

`PhoneConnectionService` 已声明为 `ConnectionService`，并在 `onCreateOutgoingConnection()` 中创建 `DialerConnection`，再通过 `TelecomManager.placeCall()` 指定 SIM `PhoneAccount` 发起实际拨号，见 `android/app/src/main/java/com/callcenter/app/service/PhoneConnectionService.kt:30`-`95`。

但当前实现存在几个关键事实：

1. 它更像“连接管理器包装层”，不是完整的通话状态源。
2. `DialerConnection` 只实现了基础 `onStateChanged()` / `onDisconnect()` / `onAbort()`，见 `android/app/src/main/java/com/callcenter/app/service/PhoneConnectionService.kt:100`-`121`。
3. 它**没有把 Connection 级别事件回流给 `AutoDialService` 的结果判定引擎**。

审计结论：`ConnectionService` **已声明但未成为识别链路核心**，目前更多是“默认拨号器生态接入准备”，不是结果识别主信号源。

### 2.4 当前通话状态识别主链路

`AutoDialService.waitForCallEndOrTimeout()` 是当前自动拨号后的核心状态机，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1476`。

其主流程如下：

1. 轮询 `telephonyManager.callState`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1518`
2. 识别三态：
   - `CALL_STATE_OFFHOOK` 视为“进入通话”，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1543`-`1555`
   - `CALL_STATE_RINGING` 视为“对方响铃中”，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1889`-`1902`
   - `CALL_STATE_IDLE` 结合确认时间判断“通话结束/未接通结束”，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1651`-`1680`、`1848`-`1885`
3. 在 `OFFHOOK` 后启动免提与音频分析，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1557`-`1642`
4. 在挂断后停止音频分析、做关键词识别、再调用 `determineCallResult()`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1679`-`1845`

同时，服务还提供了一个 `getCallState()` 包装，底层仍直接读取 `telephonyManager.callState`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:2057`-`2063`。

审计结论：

- 当前**主状态源仍是 Telephony 三态**。
- `OFFHOOK` 被当作“已进入通话”，但代码注释也明确承认：**`OFFHOOK` 可能是语音信箱，不能直接判断为已接听**，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:967`、`1544`-`1555`。
- 因此，当前系统对“真人接听 vs 语音信箱”的区分，本质依赖后验推断，而不是系统 API 的直接真值。

### 2.5 Root 精确状态链路

`AutoDialService.setupCallStateListener()` 中接入了 `rootCallStateDetector`，并监听 `RootPhoneStateListener`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:220`-`339`。

Root 路径可直接给出：

- `BUSY`
- `REJECTED`
- `NO_ANSWER`
- `POWER_OFF`
- `VOICEMAIL`
- `ACTIVE`

并在回调中直接自动标记客户状态，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:250`-`295`、`302`-`330`。

审计结论：

- **Root 设备上，项目已经拥有比普通 Android API 更强的精确状态能力。**
- 但这条链路是**条件性能力**，不能作为通用方案主干。
- 当前非 Root 设备仍需依赖 Telephony + 音频启发式。

### 2.6 当前通话结果分类器

`CallResultClassifier.classify()` 采用三层判断：

1. **时长判断**，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:165`-`173`
2. **音频能量分析**，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:174`-`188`
3. **AI 关键词识别**，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:190`-`204`

第一层阈值：

- `<3s` 倾向语音信箱，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:219`-`226`
- `>20s` 倾向真人接听，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:229`-`236`
- “响铃很短 + OFFHOOK 较短” 倾向语音信箱，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:238`-`248`
- “正常响铃 + OFFHOOK>=8s” 倾向真人接听，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:250`-`259`

审计结论：

- 分类器结构是对的，已经具备“多层降级”的工程雏形。
- 但它当前只输出三类：`CONNECTED` / `VOICEMAIL` / `UNKNOWN`，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:97`-`101`。
- **“响铃未接”并不是分类器的一等公民**，而是由外围状态机在 `IDLE` 且未接通时直接写死为“响铃未接”，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1871`-`1883`。
- 这会导致“结果判定逻辑分散在服务状态机和分类器两处”，后续难以校准和回归测试。

### 2.7 当前音频分析链路

`AudioEnergyAnalyzer` 明确说明：Android 10+ 第三方应用无法访问 `VOICE_CALL` / `VOICE_DOWNLINK`，因此当前方案只能走 **MIC + 免提**，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:49`-`60`。

其关键实现：

- 录音前检查免提状态，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:141`-`149`
- 尝试 `VOICE_RECOGNITION` / `MIC` / `CAMCORDER` 音源，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:167`-`197`
- 采样 RMS 能量并分析平稳/波动模式，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:283`-`337`、`422`-`548`

`AutoDialService` 最近还新增了“等待免提开启后再开始录音”的逻辑，最多等待 2 秒，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1597`-`1612`。

审计结论：

- 当前音频分析链路**技术上可行，但天然不稳定**，因为它依赖：
  1. 免提真的打开
  2. 音量足够大
  3. 环境噪声可控
  4. ROM 没有压制录音/音频路由
- 因此它适合作为**增强信号**，不适合作为唯一真值来源。

### 2.8 当前关键词识别链路

`KeywordDetector` 使用 Vosk 本地模型，对保存下来的 PCM 音频做离线识别，见 `android/app/src/main/java/com/callcenter/app/util/call/KeywordDetector.kt:74`-`77`、`227`-`295`。

关键词库已覆盖：

- 语音信箱提示词，如“请留言”“语音信箱”“after the beep”等，见 `android/app/src/main/java/com/callcenter/app/util/call/KeywordDetector.kt:98`-`133`
- IVR 提示词，如“请按”“欢迎致电”“press”等，见 `android/app/src/main/java/com/callcenter/app/util/call/KeywordDetector.kt:135`-`147`

审计结论：

- 关键词识别对“语音信箱”与“IVR”是有价值的。
- 但它仍受上游录音质量制约；如果免提没开、音量太低、环境噪声大，识别质量会显著下降。
- 因此它应被视为**高价值但非必达的第三层证据**。

### 2.9 当前自动免提链路

`AutoDialService.enableSpeakerphoneWithRetry()` 的优先级是：

1. `InCallService` 已激活则交给它，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:489`-`499`
2. 否则若 AccessibilityService 已激活则交给它，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:501`-`511`
3. 否则退回 `CallHelper.enableSpeakerphoneAsync()`，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:513`-`535`

`CallHelper.enableSpeakerphoneAsync()` 内部会做最多 20 次重试、总超时 5 秒，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:127`-`159`；其多阶段策略包括：

- `MODE_IN_CALL`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:177`-`183`
- `isSpeakerphoneOn = true`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:185`-`192`
- 调大通话音量，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:194`-`208`
- 反射 `AudioSystem.setForceUse`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:210`-`226`
- Android 12+ `setCommunicationDevice()`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:228`-`244`
- Root 命令兜底，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:246`-`258`

审计结论：

- 当前项目已经做了很多“强推免提”的努力。
- 但**只要不在 InCallService 受控上下文里，AudioManager 路由就可能被系统电话 App 或 ROM 重新夺回**。
- 所以“多阶段强推”并不能替代“正确的系统角色接入”。

### 2.10 AccessibilityService 现状

`AutoSpeakerAccessibilityService` 监听电话相关包名的窗口事件，在 `TYPE_WINDOW_STATE_CHANGED` 或 `TYPE_WINDOW_CONTENT_CHANGED` 时，如果判断当前在通话中，就尝试开免提，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:38`-`62`。

其策略是：

1. 先用 `AudioManager` 直接开免提，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:91`-`123`
2. 若失败，再递归查找“speaker / 扬声器 / 免提 / 扩音”等节点并点击，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:128`-`187`

审计结论：

- 这条链路比最初预期更完整，已经监听了 `TYPE_WINDOW_CONTENT_CHANGED`。
- 但它仍有几个根本问题：
  1. **没有“节点稳定后再点击”的时序控制**，事件一来就点，容易点早。
  2. **没有针对不同 ROM / 电话 App 的按钮资源 ID、层级模板库**，只靠文本/描述关键词，命中率有限。
  3. **没有点击后验证闭环**，只做一次递归点击，没有“点击后再次校验路由是否变成 speaker”。
  4. **没有健康检查与权限引导闭环**，仅靠 `isServiceEnabled` 静态标志不足以覆盖 MIUI 的“服务被系统冻结/失活/无事件”场景，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:23`-`35`。

### 2.11 InCallService 现状

`AutoSpeakerInCallService` 已经不是空壳，而是具备较强实现：

- `onCallAdded()` 注册 `Call.Callback` 并立即 `forceSpeakerOn()`，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:68`-`77`
- 在 `STATE_DIALING` / `STATE_CONNECTING` / `STATE_ACTIVE` 时持续强推 speaker，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:50`-`66`
- 在 `onCallAudioStateChanged()` 中发现路由被重置时再次修复，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:89`-`104`
- `forceSpeakerOn()` 同时调用 `setAudioRoute(ROUTE_SPEAKER)`、`AudioManager`、`setCommunicationDevice()`、`AudioSystem.setForceUse()`、Root 命令，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:106`-`145`
- 还带有 500ms 轮询监控，最长 10 分钟，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:197`-`232`

审计结论：

- **真正最有希望稳定解决自动免提的，其实已经是 InCallService 路径。**
- 当前问题不在于它“没实现”，而在于：
  1. 它是否在目标设备上真正被系统启用；
  2. 应用是否真的成为默认拨号器；
  3. 业务流程是否把它当成主路径，而不是“可有可无的增强项”。

---

## 3. 问题 1：通话状态识别的信号源分析

### 3.1 为什么系统 API 不能直接区分真人与语音信箱

当前主链路依赖 `TelephonyManager.callState`，而 `CALL_STATE_OFFHOOK` 只表示“已摘机/已进入通话态”，并不区分：

- 真人接听
- 语音信箱自动应答
- 某些运营商播报
- 某些 IVR / 总机接入

项目代码本身也承认这一点，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:967`、`1544`-`1555`。

因此，**问题 1 不存在单一 API 一步到位的非 Root 通用解**。必须做多信号融合。

### 3.2 可用信号源分级

#### A. 一级信号：系统通话状态 / InCall / Root

- `TelephonyManager.callState`：可判断拨号、响铃、进入通话、挂断，但不能区分真人/语音信箱。
- `InCallService` / `Call.Callback`：可更稳定感知通话生命周期与音频路由，但对“真人/语音信箱”仍无直接语义。
- Root 精确状态：若底层检测能识别 `VOICEMAIL` / `NO_ANSWER` / `BUSY`，则可作为高置信真值。

#### B. 二级信号：CallLog

Manifest 已声明 `READ_CALL_LOG` / `WRITE_CALL_LOG`，见 `android/app/src/main/AndroidManifest.xml:15`-`16`。

CallLog 可提供：

- 最终通话时长
- 通话类型（OUTGOING）
- 结束后落库时间点

但 CallLog 仍然**不能直接告诉你“真人还是语音信箱”**。它更适合：

- 校正最终时长
- 兜底确认“是否真的建立过通话”
- 解决服务中途被杀后恢复结果

#### C. 三级信号：音频能量

`AudioEnergyAnalyzer` 的价值在于区分：

- **平稳单向播放**：更像语音信箱/播报
- **明显波动**：更像真人对话

但它依赖 MIC + 免提，稳定性受设备和环境影响，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:49`-`60`。

#### D. 四级信号：关键词识别

`KeywordDetector` 对“请留言”“哔声后留言”“voicemail”“after the beep”等提示词有较高业务价值，见 `android/app/src/main/java/com/callcenter/app/util/call/KeywordDetector.kt:100`-`133`。

它是识别语音信箱最有业务解释力的证据之一，但前提仍是录音质量可用。

#### E. 五级信号：用户确认

当自动判定置信度不足时，必须允许用户在悬浮窗/通话后面板中一键确认：

- 已接听
- 语音信箱
- 响铃未接
- IVR / 总机
- 其他

这不是“失败兜底”，而是**闭环标注数据来源**，可用于后续阈值校准。

### 3.3 当前信号链的主要缺口

1. **结果模型不统一**：
   - `CallResultClassifier` 只输出 `CONNECTED/VOICEMAIL/UNKNOWN`，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:97`-`101`
   - “响铃未接”在外围状态机里单独处理，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1871`-`1883`
2. **CallLog 没有成为正式融合层**：Manifest 有权限，但当前主链路没有把 CallLog 作为统一后验校正器。
3. **InCall/Connection 事件没有纳入统一结果引擎**。
4. **没有置信度驱动的人工确认 UI**，只有自动标记。
5. **没有样本回流机制**：无法系统性分析“哪些 ROM / 运营商 / 时长区间最容易误判”。

---

## 4. 问题 1 整改方案：建立分层信号融合状态机

## 4.1 目标状态模型

建议把当前结果模型升级为：

```kotlin
enum class CallOutcomeType {
    HUMAN_ANSWERED,
    RANG_NO_ANSWER,
    VOICEMAIL,
    IVR,
    BUSY,
    REJECTED,
    POWER_OFF,
    FAILED,
    UNKNOWN
}
```

并引入统一结果对象：

```kotlin
data class CallOutcome(
    val type: CallOutcomeType,
    val confidence: Float,
    val evidence: List<String>,
    val sourcePriority: Int,
    val needsUserConfirmation: Boolean
)
```

这样可以把当前散落在 `AutoDialService`、`CallResultClassifier`、Root 回调中的判定统一收口。

### 4.2 建议状态机

建议把通话过程拆成两个层次：

#### 传输层状态（系统态）

- `DIALING`
- `ALERTING`
- `CONNECTED_UNVERIFIED`
- `DISCONNECTING`
- `ENDED`

#### 业务层结果（判定态）

- `RANG_NO_ANSWER`
- `VOICEMAIL`
- `HUMAN_ANSWERED`
- `IVR`
- `UNKNOWN`

核心原则：

- **`OFFHOOK` 只进入 `CONNECTED_UNVERIFIED`，绝不直接等于“已接听”。**
- 只有在融合证据后，才从 `CONNECTED_UNVERIFIED` 收敛到 `VOICEMAIL` / `HUMAN_ANSWERED` / `IVR`。

### 4.3 建议判定优先级

建议统一优先级如下：

1. **Root 精确状态**
2. **明确未接通信号**：长时间 `ALERTING` 后 `IDLE`，且从未进入 `CONNECTED_UNVERIFIED`
3. **关键词强命中**：语音信箱 / IVR
4. **音频模式强特征**
5. **时长启发式**
6. **CallLog 后验校正**
7. **人工确认**

### 4.4 具体规则建议

#### 规则 A：响铃未接

判定条件：

- 出现 `ALERTING`
- 从未进入 `CONNECTED_UNVERIFIED`
- 最终回到 `IDLE`

这类结果应直接输出 `RANG_NO_ANSWER`，而不是走 `CONNECTED/VOICEMAIL/UNKNOWN` 分类器。

#### 规则 B：语音信箱

高置信条件任一满足即可：

- Root 明确给出 `VOICEMAIL`
- 关键词命中语音信箱词库
- `ALERTING` 很短 + `CONNECTED_UNVERIFIED` 很短 + 音频平稳
- `CONNECTED_UNVERIFIED` 期间几乎全程单向平稳播报

#### 规则 C：真人接听

高置信条件任一满足即可：

- Root 明确给出 ACTIVE / NORMAL_CLEARING 且时长合理
- 关键词命中真人问候词
- 音频波动明显，且持续时长超过阈值
- `CONNECTED_UNVERIFIED` 时长较长，且非语音信箱关键词

#### 规则 D：IVR

- 关键词命中“请按 1 / 欢迎致电 / press”类词库
- 音频模式偏平稳但不符合语音信箱

IVR 不应再被简单归入“已接听”。当前分类器把 `IVR` 映射为 `CONNECTED`，见 `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:345`-`352`，这会污染业务统计。建议单列。

### 4.5 架构改造建议

新增以下组件：

1. `CallSessionTracker`
   - 负责记录一次呼叫的时间线：dialStart、alertingStart、connectedStart、endTime
2. `CallEvidenceCollector`
   - 收集 Telephony / InCall / Root / Audio / Keyword / CallLog 证据
3. `CallOutcomeEngine`
   - 统一融合证据并输出 `CallOutcome`
4. `CallOutcomeMapper`
   - 把内部结果映射为后端 `callResult` 文案与状态码

### 4.6 对现有代码的具体改造点

#### 改造点 1：收敛 `AutoDialService` 中的结果分支

当前 `waitForCallEndOrTimeout()`、`performFinalCallClassification()`、`performFinalCallClassificationSync()` 都在做结果判定，见：

- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1476`
- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:908`
- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1056`

建议：

- 这些方法只负责“收集证据 + 调用统一引擎”
- 不再各自写 if/else 判定结果

#### 改造点 2：升级 `CallResultClassifier`

把 `CallResultClassifier` 从“二分类 + unknown”升级为“多结果类型 + 证据列表 + 置信度”。

重点修改位置：

- `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:84`
- `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:97`
- `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt:143`

#### 改造点 3：引入 CallLog 后验校正器

新增 `CallLogVerifier`，在通话结束后延迟 300~800ms 查询最近一条 outgoing call：

- 校正最终 duration
- 校验是否真的建立过通话
- 在服务中断恢复时补偿结果

#### 改造点 4：把 InCall 事件纳入证据

`AutoSpeakerInCallService` 已经能拿到 `Call.Callback`，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:50`-`66`。

建议增加一个轻量事件总线或 repository：

- `onCallAdded`
- `onStateChanged`
- `onCallAudioStateChanged`

把这些事件写入当前 `CallSessionTracker`，供结果引擎使用。

### 4.7 音频分析可行性结论

**可行，但只能作为增强证据，不能作为唯一真值。**

原因：

- 当前实现只能用 MIC 路径，见 `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt:49`-`60`
- 录音质量依赖免提是否成功开启，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1597`-`1612`
- MIUI 等 ROM 可能对音频路由、后台录音、焦点切换有额外限制

建议：

- 保留音频能量 + 关键词识别
- 但在结果引擎中把它们标记为 `secondary evidence`
- 当音频证据缺失时，系统仍能依靠 Telephony + CallLog + 人工确认闭环运行

---

## 5. 问题 2：自动免提不稳定的根因分析

### 5.1 当前不稳定的根本原因

#### 根因 1：主路径没有完全收敛到默认拨号器 + InCallService

虽然项目已经有 `DefaultDialerHelper`、`PhoneConnectionService`、`AutoSpeakerInCallService`，但 `AutoDialService` 仍允许在 `InCallService` 不可用时退回 Accessibility / AudioManager，见 `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:481`-`535`。

这意味着：

- 在很多设备上，真正执行通话 UI 和音频路由控制的仍是系统电话 App
- 你的应用只能“旁路干预”
- 旁路干预天然不稳定

#### 根因 2：Accessibility 方案依赖 UI 时序与 ROM 实现

`AutoSpeakerAccessibilityService` 依赖窗口事件、节点树、按钮文本/描述，见 `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt:38`-`62`、`128`-`187`。

MIUI/HyperOS 上常见问题：

- 电话 UI 包名、层级、按钮文案变化
- 窗口事件到达时节点尚未稳定
- 锁屏/后台弹出限制导致无障碍事件不完整
- 电池优化导致服务存活但不工作

#### 根因 3：AudioManager 方式会被系统电话 App 抢回路由

`CallHelper.enableSpeakerphoneAsync()` 虽然做了 6 层防护，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:171`-`263`，但只要当前通话不在本应用的 InCall 控制上下文里，系统电话 App 或 ROM 策略就可能把路由改回听筒。

#### 根因 4：缺少 MIUI 健康检查与权限引导闭环

当前代码里没有看到以下能力被系统化实现：

- 电池优化白名单检测
- 自启动权限引导
- 后台弹出界面权限引导
- 锁屏显示权限引导
- Accessibility 事件健康度检测
- 默认拨号器状态健康度检测

这会导致“早上能用，晚上失效”这类典型 ROM 管控问题无法被定位。

### 5.2 MIUI / HyperOS 的高概率根因假设

按概率排序：

1. **应用未持续保持默认拨号器主路径**，实际仍由系统电话 App 接管音频路由。
2. **无障碍服务被系统节电策略冻结或事件投递不稳定**。
3. **后台弹出界面/锁屏显示权限不足**，导致电话 UI 事件不完整。
4. **自启动/后台保活不足**，前台服务虽在，但辅助服务被系统回收。
5. **电话 App UI 变体导致 speaker 节点查找失败**。
6. **音频路由被蓝牙/耳机/系统策略重置**，而当前验证闭环不够强。

---

## 6. 问题 2 整改方案：自动免提分层策略

### 6.1 总体策略

建议明确分层：

### L1：默认拨号器 + InCallService（主路径，必须优先）

目标：

- 让自动免提在系统支持的正式通话框架内完成
- 使用 `setAudioRoute(ROUTE_SPEAKER)` 作为主控制手段

当前基础已具备：

- 默认拨号器申请：`android/app/src/main/java/com/callcenter/app/util/DefaultDialerHelper.kt:46`-`80`
- InCallService：`android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt:17`

整改要求：

1. 把“是否为默认拨号器”提升为自动免提功能的**一级健康项**。
2. 在设置页明确提示：**若未成为默认拨号器，自动免提仅为兼容模式，不保证稳定。**
3. 在自动拨号开始前做一次能力检查：
   - 默认拨号器是否已持有
   - InCallService 最近一次是否成功激活
   - 若否，给出显式风险提示

### L2：InCallService 内部路由闭环加固

虽然 `AutoSpeakerInCallService` 已较强，但还应继续加固：

1. **增加 route verify loop 的状态上报**
   - 当前只写日志，建议把最近一次 speaker route、失败次数、最后失败原因暴露给 UI
2. **区分“设置成功”与“实际生效”**
   - `setAudioRoute()` 返回后，必须等待 `onCallAudioStateChanged()` 观察到 `ROUTE_SPEAKER`
3. **增加蓝牙/有线耳机冲突处理**
   - 若检测到蓝牙或有线耳机已连接，应给出明确状态，不要盲目重试 speaker
4. **在 `STATE_ACTIVE` 后再做一次延迟校验**
   - 某些 ROM 在 `DIALING/CONNECTING` 阶段设置无效，`ACTIVE` 后才真正可切换

### L3：AccessibilityService 兜底路径重构

Accessibility 不应再是“默认可接受方案”，而应是**兼容兜底方案**。

建议改造：

1. **引入节点稳定等待**
   - 收到窗口事件后，不立即点击；延迟 200~500ms 再抓树
2. **多轮点击 + 每轮后验证**
   - 点击后检查 `AudioManager.isSpeakerphoneOn` / route 是否变化
3. **建立 ROM 适配表**
   - 维护常见电话 App 的包名、资源 ID、contentDescription 模板
4. **增加失败原因埋点**
   - 无 root window
   - 未找到 speaker 节点
   - 找到但不可点击
   - 点击后路由未变化
5. **增加服务健康心跳**
   - 记录最近一次收到电话 UI 事件的时间戳
   - 若长时间无事件，提示用户重新开启无障碍或关闭电池优化

### L4：AudioManager / setCommunicationDevice 末级兜底

`CallHelper` 现有实现可保留，但定位应改为：

- **仅在 InCallService 不可用且 Accessibility 失败时兜底**
- 不再把它视为“稳定方案”

同时建议：

- `checkSpeakerStatus()` 不要只看 `isSpeakerphoneOn`，见 `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt:268`-`279`
- Android 12+ 应优先检查 `communicationDevice?.type == TYPE_BUILTIN_SPEAKER`

### 6.2 MIUI 专项加固方案

建议新增 `MiuiCompatibilityChecker`，启动时输出健康报告：

1. 是否默认拨号器
2. 是否开启无障碍服务
3. 是否忽略电池优化
4. 是否允许自启动
5. 是否允许后台弹出界面
6. 是否允许锁屏显示
7. 前台服务是否在运行
8. 最近一次 InCallService 激活时间
9. 最近一次 Accessibility 电话事件时间
10. 最近一次 speaker route 成功时间

并在 UI 中给出“去设置”入口。

### 6.3 健康检查 UI 设计

建议新增“通话能力健康检查”页面，至少展示：

#### 基础能力

- 默认拨号器：已开启 / 未开启
- 通话权限：已授权 / 未授权
- 录音权限：已授权 / 未授权
- 通话日志权限：已授权 / 未授权
- 悬浮窗权限：已授权 / 未授权
- 无障碍服务：已开启 / 未开启

#### 运行时健康

- InCallService：最近激活时间
- AccessibilityService：最近事件时间
- 最近一次自动免提成功：时间 + 路由
- 最近一次自动免提失败：原因
- 最近一次音频分析是否拿到有效样本

#### MIUI 专项提示

- 电池优化：是否已忽略
- 自启动：请手动确认
- 后台弹出界面：请手动确认
- 锁屏显示：请手动确认

#### 一键诊断

- “测试默认拨号器链路”
- “测试 InCallService 路由切换”
- “测试 Accessibility 点击 speaker”
- “导出最近 20 次通话诊断日志”

---

## 7. 代码变更计划

### Phase 1：结果引擎收敛（高优先级）

1. 新增 `CallOutcomeType` / `CallOutcome`
2. 新增 `CallSessionTracker`
3. 新增 `CallOutcomeEngine`
4. 把 `AutoDialService` 中以下逻辑改为统一调用引擎：
   - `waitForCallEndOrTimeout()`：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1476`
   - `performFinalCallClassificationSync()`：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:908`
   - `performFinalCallClassification()`：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1056`
5. 升级 `CallResultClassifier` 为多结果类型

### Phase 2：CallLog / InCall 证据接入（高优先级）

1. 新增 `CallLogVerifier`
2. 在通话结束后做后验校正
3. 为 `AutoSpeakerInCallService` 增加事件上报接口
4. 把 InCall 事件写入 `CallSessionTracker`

### Phase 3：自动免提主路径收敛（高优先级）

1. 在设置页和自动拨号启动前强校验默认拨号器状态
2. 若不是默认拨号器，明确标记为“兼容模式”
3. 在 `AutoDialService.enableSpeakerphoneWithRetry()` 中增加能力诊断输出，位置：`android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:485`
4. 在 `AutoSpeakerInCallService` 中增加“设置成功 -> 实际路由成功”的闭环状态上报

### Phase 4：Accessibility 兜底重构（中优先级）

1. 为 `AutoSpeakerAccessibilityService` 增加延迟抓树与多轮验证
2. 增加 ROM 适配模板
3. 增加失败原因埋点
4. 增加最近事件时间戳与健康状态暴露

### Phase 5：健康检查 UI（中优先级）

1. 新增“通话能力健康检查”页面
2. 新增 MIUI 专项引导卡片
3. 新增诊断日志导出

### Phase 6：数据闭环与阈值校准（中优先级）

1. 记录自动判定结果、置信度、证据、用户最终修正结果
2. 统计不同 ROM / 运营商 / 时长区间的误判率
3. 迭代时长阈值、音频阈值、关键词词库

---

## 8. 建议修改的关键文件

### 现有文件

- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt`
  - 收敛状态机与结果判定主流程
- `android/app/src/main/java/com/callcenter/app/util/call/CallResultClassifier.kt`
  - 升级为多结果类型融合器
- `android/app/src/main/java/com/callcenter/app/util/call/AudioEnergyAnalyzer.kt`
  - 增强有效性标记与路由校验信息
- `android/app/src/main/java/com/callcenter/app/util/call/KeywordDetector.kt`
  - 增加 IVR / 语音信箱 / 真人问候词分层输出
- `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt`
  - 增加路由成功闭环与状态上报
- `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerAccessibilityService.kt`
  - 增加节点稳定等待、点击验证、失败埋点
- `android/app/src/main/java/com/callcenter/app/util/CallHelper.kt`
  - 降级为末级兜底，并增强 route 校验
- `android/app/src/main/java/com/callcenter/app/util/DefaultDialerHelper.kt`
  - 增加更明确的能力说明与失败引导

### 建议新增文件

- `android/app/src/main/java/com/callcenter/app/util/call/CallOutcomeEngine.kt`
- `android/app/src/main/java/com/callcenter/app/util/call/CallSessionTracker.kt`
- `android/app/src/main/java/com/callcenter/app/util/call/CallLogVerifier.kt`
- `android/app/src/main/java/com/callcenter/app/util/device/MiuiCompatibilityChecker.kt`
- `android/app/src/main/java/com/callcenter/app/ui/.../CallCapabilityHealthViewModel.kt`
- `android/app/src/main/java/com/callcenter/app/ui/.../CallCapabilityHealthScreen.kt`

---

## 9. 优先级行动清单

### P0：必须先做

1. **把默认拨号器 + InCallService 定义为自动免提主路径**
2. **把 `OFFHOOK != 已接听` 固化进统一状态机**
3. **把“响铃未接”纳入统一结果模型，而不是散落在外围 if/else**
4. **引入 CallLog 后验校正**
5. **增加健康检查 UI，至少能看出默认拨号器 / 无障碍 / InCallService 是否真的活着**

### P1：强烈建议

6. **重构 Accessibility 兜底链路，增加节点稳定等待与点击后验证**
7. **把 IVR 从“已接听”中拆出来单列**
8. **记录自动判定证据与用户修正结果，形成可校准数据集**

### P2：持续优化

9. **按 ROM / 运营商 / 机型做阈值调优**
10. **扩充关键词词库与多语言模型**
11. **增加导出诊断日志与一键测试工具**

---

## 10. 最终建议

如果目标是“工程上可持续、可解释、可维护地解决这两个问题”，建议明确以下原则：

1. **通话状态识别不要再追求单一 API 真值**，而要接受“多信号融合 + 置信度 + 人工确认闭环”的现实。
2. **自动免提必须把默认拨号器 + InCallService 作为主路径**；Accessibility 只能是兼容兜底，不能是稳定性承诺来源。
3. **MIUI 问题本质不是某一行代码失效，而是系统角色、后台策略、UI 自动化、音频路由控制权四者叠加造成的不确定性。**
4. **先补健康检查与可观测性，再做阈值调优**；否则会一直停留在“偶现、难复现、难解释”的状态。

以上方案能在不依赖 Root 的前提下，把“真人接听 / 响铃未接 / 语音信箱”识别能力提升到可运营水平，并显著提高自动免提在 MIUI / HyperOS 上的可诊断性与成功率。
