package com.github.iusmac.sevensim.launcher;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

/**
 * <p>The purpose of this service is to align application icon visibility in the launcher's app list
 * with the user's preference when the application process goes out of lifecycle.
 *
 * <p>This approach aims to address the issue when the app is getting closed after disabling the
 * {@link LauncherActivity} component through
 * {@link PackageManager#setComponentEnabledSetting(ComponentName,int,int)}. This is because
 * disabling the "front-door" activity, that is also the root task, will remove all activities from
 * the stack above the root activity.
 */
@AndroidEntryPoint(Service.class)
public final class LauncherIconVisibilityChangerService
    extends Hilt_LauncherIconVisibilityChangerService {

    @Inject
    LauncherIconVisibilityManager mLauncherIconVisibilityManager;

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        mLauncherIconVisibilityManager.updateVisibility();
        stopSelf();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
