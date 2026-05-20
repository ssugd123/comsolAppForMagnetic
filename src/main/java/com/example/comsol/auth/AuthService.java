package com.example.comsol.auth;

/**
 * 硬件指纹 License 验证。
 * 将授权设备的硬件指纹以常量形式存储，运行时采集当前设备信息进行比对。
 * 每个用户构建 App 时修改 EXPECTED_* 常量。
 */
public class AuthService {

    // === 授权设备硬件指纹（每个用户构建时修改以下常量） ===
    private static final String EXPECTED_MAC = "AA-BB-CC-DD-EE-FF";
    private static final String EXPECTED_CPU_SERIAL = "YOUR_CPU_SERIAL";
    private static final String EXPECTED_HDD_SERIAL = "YOUR_HDD_SERIAL";

    private static final String LICENSE_ERROR_MSG =
            "License验证错误，当前设备与授权设备不匹配";

    /**
     * 采集当前设备硬件指纹并与授权指纹比对。
     * 不弹窗、不退出，仅返回验证结果。
     * COMSOL Method 根据返回值决定是否继续启动。
     *
     * @return true 验证通过，false 硬件不匹配
     */
    public static boolean validate() {
        HardwareCollector hw = new HardwareCollector();
        return hw.getMac().equalsIgnoreCase(EXPECTED_MAC.trim())
                && hw.getCpuSerial().equalsIgnoreCase(EXPECTED_CPU_SERIAL.trim())
                && hw.getHddSerial().equalsIgnoreCase(EXPECTED_HDD_SERIAL.trim());
    }

    /**
     * 返回 License 验证失败时的错误消息，供 COMSOL Method 弹窗使用。
     */
    public static String getErrorMessage() {
        return LICENSE_ERROR_MSG;
    }

    /**
     * @deprecated 请使用 {@link #validate()}，功能完全相同。
     */
    @Deprecated
    public static boolean checkOnly() {
        return validate();
    }
}
