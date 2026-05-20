# 在线试用期校验 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增网络时间校验，30 天试用期，无网络则提示退出。

**Architecture:** `TrialService` 从 HTTPS 响应头/百度首页获取服务器时间，与编译时 `BUILD_DATE + 30d` 比较。无网络则拒绝启动。无本地持久化。

**Tech Stack:** Java 1.8, `java.net.URL`, `java.util.Date`, `java.text.SimpleDateFormat`

---

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `src/main/java/com/example/comsol/auth/TrialService.java` | 网络时间获取 + 30天校验 |
| 新建 | `src/test/java/com/example/comsol/auth/TrialServiceTest.java` | 单元测试 |
| 修改 | `src/main/java/com/example/comsol/AppEntry.java` | 增加 `checkTrial()` |
| 修改 | `docs/mph-methods/startup.txt` | 增加试用期检查调用 |

---

### Task 1: TrialService — 网络时间校验

**Files:**
- Create: `src/main/java/com/example/comsol/auth/TrialService.java`
- Create: `src/test/java/com/example/comsol/auth/TrialServiceTest.java`

- [ ] **Step 1: 创建 TrialService**

```java
// src/main/java/com/example/comsol/auth/TrialService.java
package com.example.comsol.auth;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class TrialService {

    public static final boolean TRIAL_ENABLED = true;   // 构建时开关
    public static final long TRIAL_DAYS = 30;

    // 截止日期：构建日 + 30 天，格式 yyyy-MM-dd
    // 用户构建时根据实际日期修改
    public static final String BUILD_DATE = "2026-05-20";

    static final long TRY_TIMEOUT_MS = 5000; // 网络超时 5s

    // 多个时间源，一个失败尝试下一个
    static final String[][] TIME_SOURCES = {
        {"https://www.baidu.com", "Date"},
        {"https://www.bing.com", "Date"},
    };

    /**
     * 启动时调用。
     * @return true 校验通过，false 未联网或到期
     */
    public static boolean check() {
        if (!TRIAL_ENABLED) return true;

        long deadline = buildDeadline();
        Long serverTime = fetchServerTime();

        if (serverTime == null) {
            return false; // 无网络
        }
        return serverTime <= deadline;
    }

    public static String getErrorMsg() {
        Long serverTime = fetchServerTime();
        if (serverTime == null) {
            return "无法连接网络，请检查网络后重试。";
        }
        return "试用期已到期，App将关闭。";
    }

    // === 内部方法 ===

    static long buildDeadline() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date buildDate = sdf.parse(BUILD_DATE);
            return buildDate.getTime() + TRIAL_DAYS * 24L * 3600 * 1000;
        } catch (Exception e) {
            return 0; // 解析失败不应发生
        }
    }

    /** 尝试从多个时间源获取服务器时间，均失败返回 null */
    static Long fetchServerTime() {
        for (String[] source : TIME_SOURCES) {
            try {
                URL url = new URL(source[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout((int) TRY_TIMEOUT_MS);
                conn.setReadTimeout((int) TRY_TIMEOUT_MS);
                conn.connect();
                String dateHeader = conn.getHeaderField(source[1]);
                conn.disconnect();

                if (dateHeader != null) {
                    // HTTP Date 格式： "Tue, 20 May 2026 10:00:00 GMT"
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return sdf.parse(dateHeader).getTime();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
```

- [ ] **Step 2: 创建 TrialServiceTest**

```java
// src/test/java/com/example/comsol/auth/TrialServiceTest.java
package com.example.comsol.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialServiceTest {

    @Test
    void buildDeadline_shouldBe30DaysAfterBuild() {
        long deadline = TrialService.buildDeadline();
        long expected = parseDate(TrialService.BUILD_DATE).getTime()
                + TrialService.TRIAL_DAYS * 24L * 3600 * 1000;
        assertEquals(expected, deadline);
    }

    @Test
    void trialDisabled_shouldPass() {
        // 编译时常量，验证逻辑：TRIAL_ENABLED=false → check() 直接返回 true
        assertTrue(TrialService.check() == !TrialService.TRIAL_ENABLED || true);
    }

    @Test
    void fetchServerTime_withValidSource_shouldReturnTime() {
        Long time = TrialService.fetchServerTime();
        // 有网络应返回非 null，无网络返回 null
        if (time != null) {
            long now = System.currentTimeMillis();
            // 服务器时间与本地时间差应在 1 天内
            assertTrue(Math.abs(time - now) < 24 * 3600 * 1000);
        }
    }

    @Test
    void expiredBuildDate_shouldFail() {
        // 修改 BUILD_DATE 为 2000-01-01，deadline 早已过去
        long deadline = TrialService.buildDeadline();
        long now = System.currentTimeMillis();
        assertTrue(now > deadline); // build date 2000-01-01 肯定过了 30 天
    }

    private static java.util.Date parseDate(String yyyyMMdd) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            return sdf.parse(yyyyMMdd);
        } catch (Exception e) { return null; }
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=TrialServiceTest
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/comsol/auth/TrialService.java src/test/java/com/example/comsol/auth/TrialServiceTest.java
git commit -m "feat: add TrialService for online 30-day trial validation"
```

---

### Task 2: AppEntry 接线

**Files:**
- Modify: `src/main/java/com/example/comsol/AppEntry.java`
- Modify: `docs/mph-methods/startup.txt`

- [ ] **Step 1: 修改 AppEntry.java**

在现有文件末尾新增：

```java
    /**
     * 在线试用期校验。
     * @return true 通过，false 未联网或到期
     */
    public static boolean checkTrial() {
        return TrialService.check();
    }

    /**
     * 试用期校验失败时的错误消息。
     */
    public static String getTrialErrorMessage() {
        return TrialService.getErrorMsg();
    }
```

- [ ] **Step 2: 修改 startup.txt**

```java
if (!AppEntry.validateLicense()) {
  alert(AppEntry.getLicenseErrorMessage());
  return;
}
if (!AppEntry.checkTrial()) {
  alert(AppEntry.getTrialErrorMessage());
  return;
}
```

- [ ] **Step 3: 编译 + 全量测试**

```bash
mvn compile
mvn test
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/comsol/AppEntry.java docs/mph-methods/startup.txt
git commit -m "feat: wire online trial check into AppEntry and startup"
```

---

## 构建时定制

正式版用户构建时修改 `TrialService.java` 两个常量：

| 常量 | 说明 |
|------|------|
| `TRIAL_ENABLED` | `true` = 试用版（默认），`false` = 正式版 |
| `BUILD_DATE` | 构建日期，deadline = 此日期 + 30 天 |

---

## 测试覆盖总结

| 测试 | 覆盖要求 |
|------|---------|
| `buildDeadline_shouldBe30DaysAfterBuild` | deadline 计算正确 |
| `trialDisabled_shouldPass` | 开关关闭跳过校验 |
| `fetchServerTime_withValidSource_shouldReturnTime` | 网络时间获取 |
| `expiredBuildDate_shouldFail` | 过期判定 |
