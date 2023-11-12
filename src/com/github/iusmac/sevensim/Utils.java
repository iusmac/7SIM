package com.github.iusmac.sevensim;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

public final class Utils {
    public static final boolean IS_OLDER_THAN_S = Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
    public static final boolean IS_AT_LEAST_R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    public static final boolean IS_AT_LEAST_S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    public static final boolean IS_AT_LEAST_T =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;

    /**
     * This a backport of the {@code #BundleCompat.getParcelable} from <b>AndroidX Core v1.10.0</b>
     * library to support SDK 33+ APIs.
     * @see https://issuetracker.google.com/issues/242048899
     */
    @Nullable
    @SuppressWarnings({"deprecation"})
    public static <T> T getParcelable(final @NonNull Bundle in, final @Nullable String key,
            final @NonNull Class<T> clazz) {

        // Even though API was introduced in 33, we use 34 as 33 is bugged in some scenarios.
        if (Build.VERSION.SDK_INT >= 34) {
            return Api33Impl.getParcelable(in, key, clazz);
        } else {
            final T parcelable = in.getParcelable(key);
            return clazz.isInstance(parcelable) ? parcelable : null;
        }
    }

    /**
     * @param context The application context.
     * @param msg The toast message to show.
     */
    public static void makeToast(final Context context, final String msg) {
        ContextCompat.getMainExecutor(context).execute(() -> Toast.makeText(context, msg,
                    Toast.LENGTH_LONG).show());
    }

    /**
     * Enable or disable a component in the manifest.
     *
     * @param context The application context.
     * @param cn The {@link ComponentName} to set enabled/disabled setting for.
     * @param enabled {@code true} if the component should be enabled, {@code false} if should be
     * disabled.
     * @return Whether or not the setting was actually changed.
     */
    public static boolean setComponentEnabledSetting(final Context context, final ComponentName cn,
            final boolean enabled) {

        final PackageManager pm = context.getPackageManager();
        final int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        if (pm.getComponentEnabledSetting(cn) != state) {
            pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
            return true;
        }
        return false;
    }

    /**
     * Nested class to avoid verification errors for methods introduced in Android 13 (API 33).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class Api33Impl {
        private static <T> T getParcelable(final Bundle in, final String key,
                final Class<T> clazz) {
            return in.getParcelable(key, clazz);
        }
    }

    /** Do not initialize. */
    private Utils() {}
}
