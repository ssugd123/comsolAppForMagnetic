package com.example.comsol.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 采集当前设备的硬件指纹：MAC地址、CPU序列号、硬盘序列号。
 */
public class HardwareCollector {

    private static final Logger LOG = Logger.getLogger(HardwareCollector.class.getName());

    public String getMac() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i],
                                i < mac.length - 1 ? "-" : ""));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to get MAC address", e);
            return "";
        }
        return "";
    }

    public String getCpuSerial() {
        if (isWindows()) {
            return execAndRead("wmic cpu get ProcessorId");
        } else {
            return execAndRead("cat /proc/cpuinfo | grep Serial | cut -d ':' -f 2");
        }
    }

    public String getHddSerial() {
        if (isWindows()) {
            return execAndRead("wmic diskdrive where index=0 get SerialNumber");
        } else {
            return execAndRead("lsblk -n -o SERIAL | head -1");
        }
    }

    private String execAndRead(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    isWindows()
                            ? new String[]{"cmd.exe", "/c", command}
                            : new String[]{"/bin/sh", "-c", command});
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (first && isWindows()) {
                        first = false;
                        continue;
                    }
                    sb.append(line);
                }
                p.waitFor(10, TimeUnit.SECONDS);
                return sb.toString().trim();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to execute: " + command, e);
            return "";
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
