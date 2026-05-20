# 试用期时间限制 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 TrialManager + TrialStore，实现试用期运行时长限制（120h），启动/运行中/关闭时校验，到期弹窗退出。

**Architecture:** TrialStore 负责 AES 加密持久化（双位置冗余），TrialManager 负责 nanoTime 累加 + 定时校验 + ShutdownHook，AppEntry 薄封装对 COMSOL Method 暴露。

**Tech Stack:** Java 1.8, JUnit 5, AES-128 + HMAC-SHA256, SwingUtilities, 仅 Windows

---

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/example/comsol/auth/TrialStore.java` | AES 加密持久化，双位置冗余读写 |
| 新建 | `src/main/java/com/example/comsol/auth/TrialManager.java` | nanoTime 累加，定时校验，ShutdownHook |
| 新建 | `src/test/java/com/example/comsol/auth/TrialStoreTest.java` | TrialStore 单元测试 |
| 新建 | `src/test/java/com/example/comsol/auth/TrialManagerTest.java` | TrialManager 单元测试 |
| 修改 | `src/main/java/com/example/comsol/AppEntry.java` | 增加 checkAndStartTrial() / getTrialErrorMessage() |
| 修改 | `docs/mph-methods/startup.txt` | 增加试用期检查调用 |

---

### Task 1: TrialStore — 加密持久化

**Files:**
- Create: `src/main/java/com/example/comsol/auth/TrialStore.java`
- Create: `src/test/java/com/example/comsol/auth/TrialStoreTest.java`

- [ ] **Step 1: 创建 TrialStore 骨架和测试**

```java
// src/main/java/com/example/comsol/auth/TrialStore.java
package com.example.comsol.auth;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class TrialStore {

    // 每个用户构建时修改
    static final byte[] AES_KEY = "Chang3Th1sK3y!!".getBytes(); // 16 bytes

    // 仅 Windows 路径
    static final Path MAIN_PATH = Paths.get(System.getenv("APPDATA"), ".app_data", ".ph");
    static final Path BACKUP_PATH = Paths.get(System.getProperty("java.io.tmpdir"), ".sys", ".jc");

    // 测试用 injectable 路径
    Path mainPath;
    Path backupPath;

    public TrialStore() {
        this.mainPath = MAIN_PATH;
        this.backupPath = BACKUP_PATH;
    }

    /** 测试用 */
    TrialStore(Path main, Path backup) {
        this.mainPath = main;
        this.backupPath = backup;
    }

    /** 写入双位置冗余 */
    public void save(long totalRuntimeSec) {
        byte[] payload = pack(totalRuntimeSec);
        writeFile(mainPath, payload);
        writeFile(backupPath, payload);
    }

    /** 读取双位置交叉校验。任一缺失或不一致时，若另一可用则自动修复。均不可用返回 -1。 */
    public long load() {
        byte[] mainData = readFile(mainPath);
        byte[] backupData = readFile(backupPath);

        if (mainData == null && backupData == null) return -1;

        Long mainVal = (mainData != null) ? unpack(mainData) : null;
        Long backupVal = (backupData != null) ? unpack(backupData) : null;

        if (mainVal != null && backupVal != null && mainVal.equals(backupVal)) {
            return mainVal;
        }
        // 降级修复
        if (mainVal != null && backupVal == null) {
            writeFile(backupPath, pack(mainVal));
            return mainVal;
        }
        if (backupVal != null && mainVal == null) {
            writeFile(mainPath, pack(backupVal));
            return backupVal;
        }
        // 两者都有但不一致 — 均视为损坏
        return -1;
    }

    // === 内部方法 ===

    byte[] pack(long value) {
        try {
            byte[] plain = longToBytes(value);
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
            byte[] encrypted = c.doFinal(plain);
            byte[] hmac = hmac(encrypted);
            // 格式: [encrypted_len:4][encrypted][hmac:32]
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(intToBytes(encrypted.length));
            bos.write(encrypted);
            bos.write(hmac);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    Long unpack(byte[] data) {
        try {
            if (data.length < 36) return null;
            int encLen = bytesToInt(data, 0);
            byte[] encrypted = new byte[encLen];
            System.arraycopy(data, 4, encrypted, 0, encLen);
            byte[] expectedHmac = new byte[32];
            System.arraycopy(data, 4 + encLen, expectedHmac, 0, 32);
            if (!MessageDigest.isEqual(hmac(encrypted), expectedHmac)) return null;

            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
            byte[] plain = c.doFinal(encrypted);
            return bytesToLong(plain);
        } catch (Exception e) {
            return null;
        }
    }

    byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(AES_KEY, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            return new byte[32];
        }
    }

    void writeFile(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            // Windows 隐藏文件
            Files.setAttribute(path, "dos:hidden", true);
        } catch (Exception ignored) {}
    }

    byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
        }
    }

    // === 字节转换 ===
    static byte[] longToBytes(long v) {
        return new byte[]{(byte)(v>>>56),(byte)(v>>>48),(byte)(v>>>40),(byte)(v>>>32),
                          (byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }
    static long bytesToLong(byte[] b) {
        return ((long)(b[0]&0xFF)<<56)|((long)(b[1]&0xFF)<<48)|((long)(b[2]&0xFF)<<40)|
               ((long)(b[3]&0xFF)<<32)|((long)(b[4]&0xFF)<<24)|((long)(b[5]&0xFF)<<16)|
               ((long)(b[6]&0xFF)<<8)|(b[7]&0xFF);
    }
    static byte[] intToBytes(int v) {
        return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }
    static int bytesToInt(byte[] b, int off) {
        return ((b[off]&0xFF)<<24)|((b[off+1]&0xFF)<<16)|((b[off+2]&0xFF)<<8)|(b[off+3]&0xFF);
    }
}
```

```java
// src/test/java/com/example/comsol/auth/TrialStoreTest.java
package com.example.comsol.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;

class TrialStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoad_roundTrip() {
        Path main = tempDir.resolve("main.dat");
        Path backup = tempDir.resolve("backup.dat");
        TrialStore store = new TrialStore(main, backup);

        store.save(12345);
        assertEquals(12345, store.load());
    }

    @Test
    void load_fileNotExist_returnsMinusOne() {
        Path main = tempDir.resolve("nonexistent.dat");
        Path backup = tempDir.resolve("nonexistent2.dat");
        TrialStore store = new TrialStore(main, backup);

        assertEquals(-1, store.load());
    }

    @Test
    void load_differentValues_returnsMinusOne() {
        Path main = tempDir.resolve("main.dat");
        Path backup = tempDir.resolve("backup.dat");
        TrialStore store = new TrialStore(main, backup);

        store.save(100);
        // 篡改 backup
        store.mainPath = main;
        store.backupPath = backup;
        store.save(200);  // main=200, then manually corrupt
        // 只写 main 为不同值
        new TrialStore(main, main).save(999); // 让 main 变 999，backup 仍是 200

        // main=999, backup=200 → 不一致
        assertEquals(-1, store.load());
    }

    @Test
    void load_mainMissing_restoreFromBackup() {
        Path main = tempDir.resolve("main.dat");
        Path backup = tempDir.resolve("backup.dat");
        TrialStore store = new TrialStore(main, backup);
        store.save(500);

        // 删除 main
        main.toFile().delete();
        assertEquals(500, store.load());
        // main 应被恢复
        assertTrue(main.toFile().exists());
    }

    @Test
    void saveThenLoad_multipleTimes() {
        Path main = tempDir.resolve("main.dat");
        Path backup = tempDir.resolve("backup.dat");
        TrialStore store = new TrialStore(main, backup);

        for (long v : new long[]{0, 100, 43200, 432000}) {
            store.save(v);
            assertEquals(v, store.load());
        }
    }

    @Test
    void load_tamperedData_returnsMinusOne() {
        Path main = tempDir.resolve("main.dat");
        Path backup = tempDir.resolve("backup.dat");
        TrialStore store = new TrialStore(main, backup);
        store.save(100);

        // 直接修改文件字节
        byte[] data = java.nio.file.Files.readAllBytes(main);
        data[10] ^= 0xFF; // flip bits
        java.nio.file.Files.write(main, data);

        assertEquals(-1, store.load());
    }
}
```

- [ ] **Step 2: 编译测试，确认失败**

```bash
mvn test -pl . -Dtest=TrialStoreTest
```
Expected: compile error (TrialStore.java 尚未创建) → 创建文件后 → 测试通过

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=TrialStoreTest
```
Expected: 6 tests PASS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/comsol/auth/TrialStore.java src/test/java/com/example/comsol/auth/TrialStoreTest.java
git commit -m "feat: add TrialStore for encrypted dual-location runtime persistence"
```

---

### Task 2: TrialManager — 试用期管理核心

**Files:**
- Create: `src/main/java/com/example/comsol/auth/TrialManager.java`
- Create: `src/test/java/com/example/comsol/auth/TrialManagerTest.java`

- [ ] **Step 1: 创建 TrialManager**

```java
// src/main/java/com/example/comsol/auth/TrialManager.java
package com.example.comsol.auth;

import java.util.concurrent.*;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class TrialManager {

    public static final boolean TRIAL_ENABLED = true;     // 构建时开关
    public static final long TRIAL_LIMIT_SEC = 432000;    // 120h
    static final int CHECK_INTERVAL_SEC = 600;            // 10min

    private final TrialStore store;
    private final ScheduledExecutorService scheduler;
    private volatile long totalRuntimeSec;
    private volatile long startNanos;
    private volatile boolean expired;

    /** 到期回调：弹窗 + 退出。测试可替换。 */
    volatile Runnable onExpired = () -> {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "试用期已到期，App将关闭。",
                    "试用期提示", JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        });
    };

    /** 测试用 */
    TrialManager(TrialStore store, long totalRuntimeSec) {
        this.store = store;
        this.totalRuntimeSec = totalRuntimeSec;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trial-checker");
            t.setDaemon(true);
            return t;
        });
        this.onExpired = () -> expired = true; // 测试不弹窗不退出
    }

    public TrialManager() {
        this(new TrialStore(), -1);
    }

    /**
     * 启动时调用。读取存储、校验、启动守护线程和 ShutdownHook。
     * @return true 校验通过，false 到期或存储损坏
     */
    public boolean checkAndStart() {
        if (!TRIAL_ENABLED) return true;
        if (totalRuntimeSec < 0) {
            long loaded = store.load();
            if (loaded < 0) {
                onExpired.run();
                return false;
            }
            totalRuntimeSec = loaded;
        }
        if (totalRuntimeSec >= TRIAL_LIMIT_SEC) {
            onExpired.run();
            return false;
        }

        startNanos = System.nanoTime();

        // ShutdownHook: 最终持久化
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            accumulateAndPersist();
        }, "trial-shutdown"));

        // 定时校验线程
        scheduler.scheduleWithFixedDelay(() -> {
            boolean ok = accumulateAndCheck();
            if (!ok) {
                scheduler.shutdown();
            }
        }, CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);

        return true;
    }

    /**
     * 累加 nanoTime 增量，持久化，检查超限。
     * @return true 继续，false 到期
     */
    synchronized boolean accumulateAndCheck() {
        if (expired) return false;
        long deltaSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
        totalRuntimeSec += deltaSec;
        store.save(totalRuntimeSec);
        startNanos = System.nanoTime();

        if (totalRuntimeSec >= TRIAL_LIMIT_SEC) {
            expired = true;
            onExpired.run();
            return false;
        }
        return true;
    }

    /** ShutdownHook 调用：只持久化不弹窗 */
    synchronized void accumulateAndPersist() {
        if (expired) return;
        long deltaSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
        totalRuntimeSec += deltaSec;
        store.save(totalRuntimeSec);
        startNanos = System.nanoTime();
    }

    // 测试用 getter
    long getTotalRuntimeSec() { return totalRuntimeSec; }
    boolean isExpired() { return expired; }
    void shutdown() { scheduler.shutdownNow(); }
}
```

- [ ] **Step 2: 创建 TrialManagerTest**

```java
// src/test/java/com/example/comsol/auth/TrialManagerTest.java
package com.example.comsol.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;

class TrialManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void checkAndStart_underLimit_returnsTrue() {
        TrialStore store = new TrialStore(tempDir.resolve("m"), tempDir.resolve("b"));
        store.save(0);
        TrialManager mgr = new TrialManager(store, -1);

        assertTrue(mgr.checkAndStart());
        assertEquals(0, mgr.getTotalRuntimeSec());
        assertFalse(mgr.isExpired());
        mgr.shutdown();
    }

    @Test
    void checkAndStart_overLimit_returnsFalse() {
        TrialStore store = new TrialStore(tempDir.resolve("m"), tempDir.resolve("b"));
        store.save(TrialManager.TRIAL_LIMIT_SEC); // 已到 120h
        TrialManager mgr = new TrialManager(store, -1);

        assertFalse(mgr.checkAndStart());
        assertTrue(mgr.isExpired());
    }

    @Test
    void checkAndStart_fileMissing_returnsFalse() {
        Path m = tempDir.resolve("noexist");
        Path b = tempDir.resolve("noexist2");
        TrialStore store = new TrialStore(m, b);
        TrialManager mgr = new TrialManager(store, -1);

        assertFalse(mgr.checkAndStart());
    }

    @Test
    void accumulateAndCheck_incrementsTime() {
        TrialStore store = new TrialStore(tempDir.resolve("m"), tempDir.resolve("b"));
        store.save(0);
        TrialManager mgr = new TrialManager(store, 0);
        mgr.checkAndStart();  // starts nanoTime tracking

        // 用注入值跳过 real nanoTime
        mgr.totalRuntimeSec = 0;
        mgr.startNanos = System.nanoTime();

        // 等待 1 秒后累加
        try { Thread.sleep(1100); } catch (Exception e) {}
        boolean ok = mgr.accumulateAndCheck();
        assertTrue(ok);
        assertTrue(mgr.getTotalRuntimeSec() > 0);
        mgr.shutdown();
    }

    @Test
    void accumulateAndCheck_atLimit_returnsFalse() {
        TrialStore store = new TrialStore(tempDir.resolve("m"), tempDir.resolve("b"));
        store.save(0);
        TrialManager mgr = new TrialManager(store, TrialManager.TRIAL_LIMIT_SEC - 1);
        mgr.checkAndStart();

        // 等待 2 秒确保超限
        try { Thread.sleep(2100); } catch (Exception e) {}
        boolean ok = mgr.accumulateAndCheck();
        assertFalse(ok);
        assertTrue(mgr.isExpired());
        mgr.shutdown();
    }

    @Test
    void shutdownHook_persistsTime() {
        TrialStore store = new TrialStore(tempDir.resolve("m"), tempDir.resolve("b"));
        store.save(0);
        TrialManager mgr = new TrialManager(store, 0);
        mgr.checkAndStart();

        // 等待 1s 后触发 persist
        try { Thread.sleep(1100); } catch (Exception e) {}
        mgr.accumulateAndPersist();

        long persisted = store.load();
        assertTrue(persisted > 0);
        mgr.shutdown();
    }

    @Test
    void checkAndStart_dualLocation_restoreWorks() {
        Path main = tempDir.resolve("main");
        Path backup = tempDir.resolve("backup");
        TrialStore store = new TrialStore(main, backup);
        store.save(100);

        // 删除 main
        main.toFile().delete();

        TrialManager mgr = new TrialManager(store, -1);
        assertTrue(mgr.checkAndStart());
        assertEquals(100, mgr.getTotalRuntimeSec());
        // main 被恢复
        assertTrue(main.toFile().exists());
        mgr.shutdown();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=TrialManagerTest
```
Expected: 7 tests PASS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/comsol/auth/TrialManager.java src/test/java/com/example/comsol/auth/TrialManagerTest.java
git commit -m "feat: add TrialManager for trial period enforcement"
```

---

### Task 3: AppEntry 变更 + startup Method

**Files:**
- Modify: `src/main/java/com/example/comsol/AppEntry.java`
- Modify: `docs/mph-methods/startup.txt`

- [ ] **Step 1: 修改 AppEntry.java**

在现有 `AppEntry.java` 末尾（`}` 之前）新增：

```java
    private static volatile TrialManager trialManager;
    private static final String TRIAL_ERROR_MSG = "试用期验证失败";
    private static final String TRIAL_EXPIRED_MSG = "试用期已到期，App将关闭。";
    private static final String TRIAL_MISSING_MSG = "试用信息丢失，无法验证试用状态。";

    /**
     * 启动时校验试用期。通过后启动守护线程和 ShutdownHook。
     * @return true 可通过，false 需弹窗退出
     */
    public static boolean checkAndStartTrial() {
        trialManager = new TrialManager();
        return trialManager.checkAndStart();
    }

    /**
     * 试用期校验失败时的错误消息。
     */
    public static String getTrialErrorMessage() {
        if (trialManager != null && trialManager.isExpired()) {
            return TRIAL_EXPIRED_MSG;
        }
        return TRIAL_MISSING_MSG;
    }
```

- [ ] **Step 2: 修改 startup.txt**

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

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行全部测试**

```bash
mvn test
```
Expected: all tests PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/comsol/AppEntry.java docs/mph-methods/startup.txt
git commit -m "feat: wire trial period check into AppEntry and startup method"
```

---

### Task 4: 集成验证

- [ ] **Step 1: 打包 JAR 并验证 size**

```bash
mvn package
ls -la target/comsol-ext-1.0.0.jar
```

- [ ] **Step 2: 反编译确认常量存在**

```bash
javap -c -p -classpath target/comsol-ext-1.0.0.jar com.example.comsol.auth.TrialManager | grep TRIAL_LIMIT
```
Expected: 输出包含 `TRIAL_LIMIT_SEC` 常量

- [ ] **Step 3: 提交**

```bash
git commit -m "chore: build and verify trial module JAR" --allow-empty
```

---

## 测试覆盖总结

| 测试 | 覆盖要求 |
|------|---------|
| `TrialStoreTest.saveAndLoad_roundTrip` | 加密写入 → 解密读取一致 |
| `TrialStoreTest.load_fileNotExist_returnsMinusOne` | 文件不存在 → -1 |
| `TrialStoreTest.load_differentValues_returnsMinusOne` | 双位置不一致 → -1 |
| `TrialStoreTest.load_mainMissing_restoreFromBackup` | 冗余恢复 |
| `TrialStoreTest.saveThenLoad_multipleTimes` | 多次写入覆盖 |
| `TrialStoreTest.load_tamperedData_returnsMinusOne` | HMAC 防篡改 |
| `TrialManagerTest.checkAndStart_underLimit_returnsTrue` | 未超限 → 通过 |
| `TrialManagerTest.checkAndStart_overLimit_returnsFalse` | 已超限 → 拒绝 |
| `TrialManagerTest.checkAndStart_fileMissing_returnsFalse` | 存储丢失 → 拒绝 |
| `TrialManagerTest.accumulateAndCheck_incrementsTime` | nanoTime Δ 累加 |
| `TrialManagerTest.accumulateAndCheck_atLimit_returnsFalse` | 累加后超限 → 拒绝 |
| `TrialManagerTest.shutdownHook_persistsTime` | ShutdownHook 持久化 |
| `TrialManagerTest.checkAndStart_dualLocation_restoreWorks` | 单文件丢失恢复 |
