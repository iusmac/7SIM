package com.android.settingslib.widget.theme.flags;

import android.os.Build;

public class Flags {
    public static boolean isExpressiveDesignEnabled() {
        // Android 16 QPR1 (Baklava) ships with Material 3 Expressive design by default
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }
}
