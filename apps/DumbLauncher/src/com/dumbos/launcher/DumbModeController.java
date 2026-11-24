package com.dumbos.launcher;

import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

public final class DumbModeController {

    public static final String SETTINGS_KEY_MODE = "dumbos_mode";

    public static final String MODE_NORMAL = "Normal";
    public static final String MODE_DND = "Fokus (DND)";
    public static final String MODE_CALL_ONLY = "Hanya Telepon & SMS";
    public static final String MODE_NIGHT_BW = "Malam (Hitam-Putih)";

    public static final String[] MODES = new String[] {
        MODE_NORMAL,
        MODE_DND,
        MODE_CALL_ONLY,
        MODE_NIGHT_BW
    };

    private DumbModeController() {}

    public static String getCurrentMode(Context context) {
        try {
            String v = Settings.System.getString(context.getContentResolver(), SETTINGS_KEY_MODE);
            if (v != null && !v.isEmpty()) {
                for (String m : MODES) {
                    if (m.equals(v)) {
                        return v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return MODE_NORMAL;
    }

    public static String nextMode(Context context) {
        String current = getCurrentMode(context);
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(current)) {
                return MODES[(i + 1) % MODES.length];
            }
        }
        return MODE_NORMAL;
    }

    public static String cycleMode(Context context) {
        String next = nextMode(context);
        applyMode(context, next);
        return next;
    }

    public static void applyMode(Context context, String mode) {
        if (MODE_DND.equals(mode)) {
            setWifiAndData(context, true);
            setDndPriority(context, true);
            setMonochrome(context, false);
            setBatterySaver(context, false);
        } else if (MODE_CALL_ONLY.equals(mode)) {
            setWifiAndData(context, false);
            setDndPriority(context, false);
            setMonochrome(context, false);
            setBatterySaver(context, true);
        } else if (MODE_NIGHT_BW.equals(mode)) {
            setWifiAndData(context, true);
            setDndPriority(context, true);
            setMonochrome(context, true);
            setBatterySaver(context, true);
        } else {
            mode = MODE_NORMAL;
            setWifiAndData(context, true);
            setDndPriority(context, false);
            setMonochrome(context, false);
            setBatterySaver(context, false);
        }
        try {
            Settings.System.putString(context.getContentResolver(), SETTINGS_KEY_MODE, mode);
        } catch (Throwable ignored) {
        }
    }

    public static void lockScreen(Context context) {
        try {
            PowerManager pm = context.getSystemService(PowerManager.class);
            if (pm != null) {
                pm.goToSleep(SystemClock.uptimeMillis());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setWifiAndData(Context context, boolean enable) {
        try {
            WifiManager wm = context.getSystemService(WifiManager.class);
            if (wm != null) {
                wm.setWifiEnabled(enable);
            }
        } catch (Throwable ignored) {
        }
        try {
            PackageManager pkgMgr = context.getPackageManager();
            if (pkgMgr != null && pkgMgr.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA)) {
                TelephonyManager tm = context.getSystemService(TelephonyManager.class);
                if (tm != null) {
                    int subId = SubscriptionManager.getDefaultDataSubscriptionId();
                    if (SubscriptionManager.isValidSubscriptionId(subId)) {
                        tm.createForSubscriptionId(subId).setDataEnabledForReason(
                                TelephonyManager.DATA_ENABLED_REASON_USER, enable);
                    } else {
                        tm.setDataEnabledForReason(
                                TelephonyManager.DATA_ENABLED_REASON_USER, enable);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setDndPriority(Context context, boolean priorityOnly) {
        try {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.setInterruptionFilter(priorityOnly
                        ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        : NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setMonochrome(Context context, boolean enable) {
        try {
            Settings.Secure.putInt(context.getContentResolver(),
                    "accessibility_display_daltonizer", 0);
            Settings.Secure.putInt(context.getContentResolver(),
                    "accessibility_display_daltonizer_enabled", enable ? 1 : 0);
        } catch (Throwable ignored) {
        }
    }

    private static void setBatterySaver(Context context, boolean enable) {
        try {
            PowerManager pm = context.getSystemService(PowerManager.class);
            if (pm != null) {
                pm.setPowerSaveModeEnabled(enable);
            }
        } catch (Throwable ignored) {
        }
    }
}
