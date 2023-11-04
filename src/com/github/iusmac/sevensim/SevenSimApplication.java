package com.github.iusmac.sevensim;

import android.app.Application;

import androidx.annotation.NonNull;

import dagger.hilt.android.HiltAndroidApp;

import javax.inject.Inject;
import javax.inject.Provider;

@HiltAndroidApp(Application.class)
public final class SevenSimApplication extends Hilt_SevenSimApplication {
    @Inject
    Provider<ApplicationInfo> mApplicationInfoProvider;

    @Inject
    Logger.Factory mLoggerFactory;

    private boolean mHasAospPlatformSignature;
    private String mApplicationVersion;

    @Override
    public void onCreate() {
        super.onCreate();

        final Logger logger = mLoggerFactory.create(getClass().getSimpleName());

        final ApplicationInfo appInfo = mApplicationInfoProvider.get();
        mHasAospPlatformSignature = appInfo.hasAospPlatformSignature();
        mApplicationVersion = appInfo.getPackageVersionName();

        logger.d("onCreate() : mHasAospPlatformSignature=%s,mApplicationVersion=%s.",
                mHasAospPlatformSignature, mApplicationVersion);
    }

    /**
     * @return {@code true} if the application has been signed with the AOSP platform signature,
     * {@code false} otherwise.
     */
    public boolean hasAospPlatformSignature() {
        return mHasAospPlatformSignature;
    }

    /**
     * @return The string containing the package version.
     */
    public @NonNull String getPackageVersionName() {
        return mApplicationVersion;
    }
}
