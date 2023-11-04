package com.github.iusmac.sevensim;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

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
     * Nested class to avoid verification errors for methods introduced in Android 13 (API 33).
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class Api33Impl {
        private static PackageInfo getPackageInfo(final PackageManager pm, final String packageName,
                final int flags) throws PackageManager.NameNotFoundException {

            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags));
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
    }
}
