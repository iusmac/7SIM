package com.github.iusmac.sevensim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.iusmac.sevensim.launcher.LauncherIconVisibilityManager;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * <p>A broadcast receiver aimed to receive system-wide events that occur once the device boots to
 * the Home screen, and notify application components interested in these events.
 *
 * <p>For the events that occur after the device finished booting, but before the user unlocked the
 * device, see {@link DirectBootAwareBroadcastReceiver}.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public class SystemBroadcastReceiver extends Hilt_SystemBroadcastReceiver {
    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    Provider<LauncherIconVisibilityManager> mLauncherIconVisibilityManagerProvider;

    private Logger mLogger;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onReceive() : intent=" + intent);

        final String action = intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                // Need to update launcher icon's visibility on device boot completed. This handles
                // the case when the application was converted from system app to user app (icon
                // visibility will be restored as of Android Q), then if converted back to system
                // app, the user's preference will be kept and launcher icon will be hidden again.
                // This also ensures proper app backup data restore
                mLauncherIconVisibilityManagerProvider.get().updateVisibility();
                break;

            case Intent.ACTION_MY_PACKAGE_REPLACED:
                // Need to update launcher icon's visibility when this app package has been
                // replaced. This handles the case when the user hides the launcher icon, then wipes
                // app data and re-installs the app. The launcher icon's visibility should be
                // restored as the user's preference has been cleared
                mLauncherIconVisibilityManagerProvider.get().updateVisibility();
                break;

            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_TIME_CHANGED:
                final LocalDateTime now = LocalDateTime.now();

                // Need to sync the enabled state of all SIM subscriptions available on the device
                // with their existing weekly repeat schedules on any alteration to the system
                // time
                ForegroundService.syncAllSubscriptionsEnabledState(context, now,
                        /*overrideUserPreference=*/ true);

                // Need to reschedule the next weekly repeat schedule processing iteration, as it
                // relies on a RTC-based alarm, which, in turn is independent of any alteration to
                // the system time. Note that, this call should only happen after syncing
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(context,
                        now.plusMinutes(1));
                break;

            default:
                mLogger.d("onReceive() : Unhandled action: %s." , action);
                return;
        }
    }
}
