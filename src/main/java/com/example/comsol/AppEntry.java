package com.example.comsol;

import com.example.comsol.auth.AuthService;

/**
 * COMSOL App Java 扩展统一入口。
 * COMSOL Method 通过此类调用所有 Java 扩展功能。
 *
 * <pre>
 * 使用方式（在 COMSOL Method 中）：
 *
 *   // 启动时验证
 *   if (!AppEntry.validateLicense()) {
 *       model.dialog().message(AppEntry.getLicenseErrorMessage());
 *       model.app().close();
 *       return;
 *   }
 *
 *   // 绘制图表
 *   BufferedImage img = AppEntry.renderChart(series, config);
 *
 *   // 传递参数
 *   AppEntry.transferParams(params, (k, v) -> model.param().set(k, v));
 * </pre>
 */
public class AppEntry {

    private AppEntry() {} // 工具类，禁止实例化

    /**
     * 硬件指纹 License 验证。App 启动时调用。
     * 仅返回验证结果，不弹窗不退出。COMSOL Method 需自行处理失败流程。
     *
     * @return true 验证通过
     */
    public static boolean validateLicense() {
        return AuthService.validate();
    }

    /**
     * License 验证失败时的错误消息，供 COMSOL Method 弹窗显示。
     */
    public static String getLicenseErrorMessage() {
        return AuthService.getErrorMessage();
    }

    /**
     * 在线试用期校验。
     * @return true 通过，false 未联网或到期
     */
    public static boolean checkTrial() {
        return com.example.comsol.auth.TrialService.check();
    }

    /**
     * 试用期校验失败时的错误消息。
     */
    public static String getTrialErrorMessage() {
        return com.example.comsol.auth.TrialService.getErrorMessage();
    }

}
