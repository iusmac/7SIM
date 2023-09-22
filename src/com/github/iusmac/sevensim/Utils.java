package com.github.iusmac.sevensim;

import android.os.Build;

public final class Utils {
    public static final boolean IS_AT_LEAST_R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    public static final boolean IS_AT_LEAST_S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    /** Do not initialize. */
    private Utils() {}
}
