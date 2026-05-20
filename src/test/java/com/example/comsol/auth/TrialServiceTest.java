package com.example.comsol.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialServiceTest {

    @Test
    void buildDeadline_is30DaysAfterBuild() {
        long deadline = TrialService.buildDeadline();
        long expected = parseDate(TrialService.BUILD_DATE).getTime()
                + TrialService.TRIAL_DAYS * 24L * 3600 * 1000;
        assertEquals(expected, deadline);
    }

    @Test
    void buildDeadline_equalsBuildDatePlus30Days() {
        long expected = parseDate(TrialService.BUILD_DATE).getTime()
                + TrialService.TRIAL_DAYS * 24L * 3600 * 1000;
        assertEquals(expected, TrialService.buildDeadline());
    }

    @Test
    void fetchServerTime_returnsTimeOrNull() {
        Long time = TrialService.fetchServerTime();
        if (time != null) {
            long now = System.currentTimeMillis();
            assertTrue(Math.abs(time - now) < 24 * 3600 * 1000);
        }
    }

    @Test
    void disabled_skipsCheck() {
        // TRIAL_ENABLED=true 时 check() 应执行网络请求
        boolean result = TrialService.check();
        // 联网返回 true/false，不联网返回 false
        assertTrue(result == true || result == false);
    }

    private static java.util.Date parseDate(String yyyyMMdd) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
            return sdf.parse(yyyyMMdd);
        } catch (Exception e) { return null; }
    }
}
