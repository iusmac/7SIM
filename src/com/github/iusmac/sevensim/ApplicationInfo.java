package com.github.iusmac.sevensim;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;

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
            final PackageInfo packageInfo = mPackageManager.getPackageInfo(mPackageName, 0);
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
            final android.content.pm.ApplicationInfo appInfo =
                mPackageManager.getApplicationInfo(mPackageName, 0);
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
     * Get the activity intent to show screen of battery settings for this application within the
     * built-in Settings app.
     */
    public Intent getAppBatterySettingsActivityIntent() {
        final String action = Utils.IS_AT_LEAST_S ? Settings.ACTION_VIEW_ADVANCED_POWER_USAGE_DETAIL
            : "android.settings.APP_BATTERY_SETTINGS";
        final Intent i = new Intent(action, Uri.parse("package:" + mPackageName));
        // Highlight the preference fragment when launched
        i.putExtra("request_ignore_background_restriction", true);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
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
            final PackageInfo packageInfo = mPackageManager.getPackageInfo(mPackageName, flags);
            return packageInfo.signingInfo.getApkContentsSigners();
        } catch (PackageManager.NameNotFoundException e) {
            return new Signature[0];
        }
    }
}
