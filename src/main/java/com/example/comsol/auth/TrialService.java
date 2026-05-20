package com.example.comsol.auth;

import java.net.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;

public class TrialService {

    public static final boolean TRIAL_ENABLED = true;
    public static final long TRIAL_DAYS = 30;
    public static final String BUILD_DATE = "2026-05-20"; // yyyy-MM-dd, 用户构建时修改

    static final long TIMEOUT_MS = 5000;

    private static final Logger LOG = Logger.getLogger(TrialService.class.getName());

    // 多时间源，一个失败尝试下一个
    static final String[][] TIME_SOURCES = {
        {"https://www.baidu.com", "Date"},
        {"https://www.bing.com", "Date"},
    };

    /** 启动时调用。@return true 通过，false 未联网或到期 */
    public static boolean check() {
        if (!TRIAL_ENABLED) return true;

        long deadline = buildDeadline();
        Long serverTime = fetchServerTime();
        if (serverTime == null) return false;
        return serverTime <= deadline;
    }

    /** 错误消息，供 Method 弹窗 */
    public static String getErrorMessage() {
        Long serverTime = fetchServerTime();
        if (serverTime == null) return "无法连接网络，请检查网络后重试。";
        return "试用期已到期，App将关闭。";
    }

    // === internal ===

    static long buildDeadline() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf.parse(BUILD_DATE).getTime() + TRIAL_DAYS * 24L * 3600 * 1000;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse BUILD_DATE: " + BUILD_DATE, e);
            return 0;
        }
    }

    static Long fetchServerTime() {
        for (String[] s : TIME_SOURCES) {
            try {
                URL url = new URL(s[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout((int) TIMEOUT_MS);
                conn.setReadTimeout((int) TIMEOUT_MS);
                conn.connect();
                String header = conn.getHeaderField(s[1]);
                conn.disconnect();
                if (header != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return sdf.parse(header).getTime();
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to fetch time from " + s[0], e);
            }
        }
        return null;
    }
}
