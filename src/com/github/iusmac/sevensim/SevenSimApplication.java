package com.github.iusmac.sevensim;

import android.app.Application;

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

    @Override
    public void onCreate() {
        super.onCreate();

        final Logger logger = mLoggerFactory.create(getClass().getSimpleName());

        final ApplicationInfo appInfo = mApplicationInfoProvider.get();
        mHasAospPlatformSignature = appInfo.hasAospPlatformSignature();

        logger.d("onCreate() : mHasAospPlatformSignature=%s.", mHasAospPlatformSignature);
    }

    /**
     * @return {@code true} if the application has been signed with the AOSP platform signature,
     * {@code false} otherwise.
     */
    public boolean hasAospPlatformSignature() {
        return mHasAospPlatformSignature;
    }
}
