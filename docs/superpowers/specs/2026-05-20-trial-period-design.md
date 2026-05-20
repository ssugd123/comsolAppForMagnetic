# 试用期时间限制功能 — 设计文档

**日期**：2026-05-20  
**状态**：待评审

## 需求

在无外部网络环境下，License 模块增加试用期时间限制：到达试用期后提示并退出 App。

### 约束

- 无网络，所有校验本地完成
- 防运行时修改系统时钟绕过
- 防删除存储文件重置试用期
- 时长限制：120 小时（实际运行时长）
- 启动时校验 + 运行时每 10 分钟校验 + 关闭时持久化

## 架构

```
com.example.comsol.auth
├── AuthService.java          # 已有 — 硬件指纹验证
├── HardwareCollector.java    # 已有 — 采集硬件信息
├── TrialManager.java         # 新增 — 试用期管理
└── TrialStore.java           # 新增 — 加密存储读写

AppEntry.java                 # 修改 — 增加 checkTrial() 静态方法
docs/mph-methods/startup.txt  # 修改 — 增加试用期检查调用
```

## 组件设计

### TrialManager

单例/静态工具类，负责全部试用期逻辑。

**常量：**
- `TRIAL_ENABLED = true` — 构建时开关，false 则跳过全部试用期逻辑
- `TRIAL_LIMIT_SEC = 432000` (120 小时)
- `CHECK_INTERVAL_SEC = 600` (10 分钟)
- `STORE_FILE` — 隐蔽存储路径，系统临时目录下伪装名

**状态：**
- `startNanos` — 启动/上次累加时的 `System.nanoTime()`
- `totalRuntimeSec` — 已累计运行秒数
- `scheduler` — `ScheduledExecutorService`（1 线程守护）

**公开方法：**

```java
// 启动时调用。校验通过后启动守护线程和 ShutdownHook。
// 返回 false 表示试用到期，调用方负责弹窗退出。
boolean checkAndStart()
```

**内部方法：**

```java
// 累加 nanoTime 增量到 totalRuntimeSec，检查是否超限，持久化。
// 超限返回 false。
boolean accumulateAndCheck()

// 写入加密文件（双位置冗余）。
void persist()

// 读取解密，双位置交叉校验。文件不存在返回 -1。
long load()
```

**流程：**

```
checkAndStart()
  ├─ totalRuntimeSec = load()
  ├─ totalRuntimeSec == -1 → 文件丢失 → 弹窗退出
  ├─ totalRuntimeSec >= TRIAL_LIMIT_SEC → 到期 → 弹窗退出
  ├─ 通过
  │   ├─ startNanos = System.nanoTime()
  │   ├─ 注册 ShutdownHook → persist()
  │   └─ 启动 scheduler(每10min) → accumulateAndCheck()
  └─

accumulateAndCheck()
  ├─ delta = (System.nanoTime() - startNanos) / 1e9
  ├─ totalRuntimeSec += delta
  ├─ persist()
  ├─ startNanos = System.nanoTime()
  ├─ totalRuntimeSec >= TRIAL_LIMIT_SEC
  │   ├─ 到期 → 弹窗 → 调用 model.app().close() 或 System.exit(0)
  │   └─ 否则继续
  └─
```

### TrialStore

加密持久化工具。

```java
// 加密写入 total_runtime_sec 到双位置
void save(long totalRuntimeSec)

// 读取解密，双位置交叉校验。任一缺失或不一致返回 -1
long load()
```

**加密方案：** AES-128，密钥硬编码在 JAR 中（与构建常量一起，每个用户构建时修改）。

**存储位置（仅 Windows）：**
- `%APPDATA%\.app_data\.ph` — 主位置
- `%TEMP%\.sys\.jc` — 冗余位置

文件内容格式：`{totalRuntimeSec}|{加密的totalRuntimeSec}|{HMAC}`，防手动篡改。

**降级：** 任一位置不可读时，如果能从另一位置恢复，则自动修复。两位置均不可读时返回 -1。

### 与 COMSOL 交互

启动时校验由 `startup` Method 调用 `AppEntry`：

```java
if (!AppEntry.validateLicense()) {
  alert(AppEntry.getLicenseErrorMessage());
  return;
}
if (!AppEntry.checkAndStartTrial()) {
  alert(AppEntry.getTrialErrorMessage());
  return;
}
```

`AppEntry.checkAndStartTrial()` 内部：
1. 创建 `TrialManager` 实例（持有 `model` 引用）
2. 调用 `trialManager.checkAndStart()` → 返回 false 则拒绝
3. 返回 true 表示守护线程已启动

运行时到期弹窗：COMSOL 无公开 Java 弹窗 API。COMSOL Desktop 是 Swing 应用，直接使用标准 Java Swing：

```java
SwingUtilities.invokeLater(() -> {
    JOptionPane.showMessageDialog(null, "试用期已到期，App将关闭。",
        "试用期提示", JOptionPane.WARNING_MESSAGE);
    System.exit(0);
});
```

后台线程中必须用 `SwingUtilities.invokeLater` 确保在 EDT 弹窗，避免死锁。

> **COMSOL model 引用传入方式：** `startup` Method 将 `model` 对象传给 `AppEntry.setModel(model)`，`AppEntry` 创建 `TrialManager` 时注入。

## AppEntry 变更

```java
// 新增静态字段
private static Model model;  // COMSOL Model 引用，startup 注入

// 新增方法
public static void setModel(Model m)           // startup 注入 model
public static boolean checkAndStartTrial()      // 启动时校验
public static String getTrialErrorMessage()    // 错误消息
```

## 测试

JUnit 5 测试，无 COMSOL 依赖。

| 测试用例 | 输入 | 预期 |
|---------|------|------|
| 文件不存在 | 删除存储文件 | `load()` 返回 -1 |
| 试用期未到 | `totalRuntimeSec = 0` | `checkAndStart()` 返回 true |
| 试用期已到 | `totalRuntimeSec = 432000` | `checkAndStart()` 返回 false |
| 双位置不一致 | 只改一个文件 | `load()` 返回 -1 |
| nanoTime 累加 | `accumulateAndCheck()` 调用两次间隔 1s | totalRuntimeSec += 1 |
| ShutdownHook 触发 | 调用 `persist()` | 文件写入最新值 |
| 定时校验超限 | `totalRuntimeSec` 设为 431995，等 10min | 弹窗退出 |
| 冗余恢复 | 删除主文件，保留冗余 | `load()` 从冗余恢复并修复主文件 |

## 构建集成

用户构建时需修改 `TrialManager` 中的：
- `TRIAL_LIMIT_SEC` — 试用时长
- `AES_KEY` — 加密密钥（16 字节随机字符串）
- `STORE_FILE` 路径中的盐值

## 安全评估

| 攻击 | 防护 |
|------|------|
| 修改系统时钟 | 使用 `nanoTime()`，不受系统时钟影响 |
| 删除存储文件 | 双位置冗余 + 文件不存在拒绝启动 |
| 篡改存储文件 | AES 加密 + HMAC 校验 |
| 替换 JAR | 硬件指纹已绑定（已有 AuthService） |
| 运行时内存修改 | 需 root/JVM 注入，不在防护范围内 |

## 待定项

1. 与硬件指纹分发的集成方式（构建脚本自动化替换常量）
