package com.github.iusmac.sevensim;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;

/**
 * The responsibility of this class is provide overall information about package application.
 */
public final class ApplicationInfo {
    private final Resources mResources;
    private final String mPackageName;
    private final PackageManager mPackageManager;

    @Inject
    public ApplicationInfo(final @ApplicationContext Context context) {
        mResources = context.getResources();
        mPackageName = context.getPackageName();
        mPackageManager = context.getPackageManager();
    }

    /**
     * Verify whether the application has been signed using public Android platform signature.
     *
     * @see https://android.googlesource.com/platform/build/+/refs/heads/master/target/product/security/
     *
     * @return {@code true} if the application has been signed with the AOSP platform signature,
     * {@code false} otherwise.
     */
    public boolean hasAospPlatformSignature() {
        final Signature aospSignature = new Signature(mResources
                .getString(R.string.aosp_platform_signature));

        for (Signature signature : getPackageSigners()) {
            if (signature.equals(aospSignature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The string containing the package version.
     */
    public @NonNull String getPackageVersionName() {
        try {
            final PackageInfo packageInfo = getPackageInfo(0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * @return {@code true} if the application is classified by the OS as a "built-in system"
     * application, {@code false} otherwise.
     */
    public boolean isSystemApplication() {
        try {
            final android.content.pm.ApplicationInfo appInfo = getApplicationInfo(0);
            // Note that, an application is considered as a system application when it's either
            // pre-installed in the device's system partition as part of the ROM, or be deliberately
            // placed by the user under the system{_ext}/priv-app folder. Additionally, the system
            // application can be updated by being installed as any other app; in such case, the
            // application will exist in both the system and data partitions, and will be considered
            // as an updated system application
            return appInfo.isSystemApp() || appInfo.isUpdatedSystemApp();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Get the activity intent to show screen of details about this application within the built-in
     * Settings app.
     */
    public Intent getAppDetailsSettingsActivityIntent() {
        final Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + mPackageName));
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /**
     * @param context The application context for accessing {@link PackageManager}.
     * @param packageName The name of the package to retrieve overall information of.
     * @return The array containing package {@link Signature}s.
     */
    private Signature[] getPackageSigners() {
        try {
            final int flags = PackageManager.GET_SIGNING_CERTIFICATES;
            final PackageInfo packageInfo = getPackageInfo(flags);
            return packageInfo.signingInfo.getApkContentsSigners();
        } catch (PackageManager.NameNotFoundException e) {
            return new Signature[0];
        }
    }

    /**
     * @param flags The combination of flag bits for {@link PackageInfo}.
     * @return A {@link PackageInfo} containing info about this package.
     */
    private PackageInfo getPackageInfo(final int flags)
        throws PackageManager.NameNotFoundException {

        if (Utils.IS_AT_LEAST_T) {
            return Api33Impl.getPackageInfo(mPackageManager, mPackageName, flags);
        } else {
            return ApiDeprecated.getPackageInfo(mPackageManager, mPackageName, flags);
        }
    }

    /**
     * @param flags The combination of flag bits for {@link ApplicationInfo}.
     * @return A {@link ApplicationInfo} containing info about this application.
     */
    private android.content.pm.ApplicationInfo getApplicationInfo(final int flags)
            throws PackageManager.NameNotFoundException {

        if (Utils.IS_AT_LEAST_T) {
            return Api33Impl.getApplicationInfo(mPackageManager, mPackageName, flags);
        } else {
            return ApiDeprecated.getApplicationInfo(mPackageManager, mPackageName, flags);
        }
    }

    /**
     * Nested class to avoid verification errors for methods introduced in Android 13 (API 33).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class Api33Impl {
        private static PackageInfo getPackageInfo(final PackageManager pm, final String packageName,
                final int flags) throws PackageManager.NameNotFoundException {

            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags));
        }

        private static android.content.pm.ApplicationInfo getApplicationInfo(
                final PackageManager pm, final String packageName, final int flags)
                throws PackageManager.NameNotFoundException {

            return pm.getApplicationInfo(packageName,
                    PackageManager.ApplicationInfoFlags.of(flags));
        }
    }

    /**
     * Nested class to suppress warnings only for API methods annotated as Deprecated.
     */
    @SuppressWarnings("deprecation")
    private static class ApiDeprecated {
        private static PackageInfo getPackageInfo(final PackageManager pm, final String packageName,
                final int flags) throws PackageManager.NameNotFoundException {

            return pm.getPackageInfo(packageName, flags);
        }

        private static android.content.pm.ApplicationInfo getApplicationInfo(
                final PackageManager pm, final String packageName, final int flags)
                throws PackageManager.NameNotFoundException {

            return pm.getApplicationInfo(packageName, flags);
        }
    }
}
