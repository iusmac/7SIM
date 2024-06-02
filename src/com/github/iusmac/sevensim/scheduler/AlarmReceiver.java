package com.github.iusmac.sevensim.scheduler;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.ForegroundService;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * This static broadcast receiver will be triggered exclusively by the {@link AlarmManager}, with
 * the aim of initiating an ordinary iteration in which the scheduler will process all weekly repeat
 * schedules at the stated time.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public final class AlarmReceiver extends Hilt_AlarmReceiver {
    @Inject
    Logger.Factory loggerFactory;

    @Inject
    ActivityManager mActivityManager;

    @Inject
    Provider<SubscriptionScheduler> mSubscriptionSchedulerProvider;

    @Inject
    Provider<PinStorage> mPinStorageProvider;

    private Logger mLogger;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);

        mLogger = loggerFactory.create(getClass().getSimpleName());

        final boolean isBgRestricted = mActivityManager.isBackgroundRestricted();

        mLogger.d("onReceive() : isBgRestricted=%s,intent=%s.", isBgRestricted, intent);

        final LocalDateTime now = LocalDateTime.now();
        final Bundle clearPinCodes = intent.getExtras();

        // Normally, we keep the SIM card disabled after a reboot if it was manually disabled by the
        // user, but the schedules should reset it when it comes time to process them at the stated
        // time
        final boolean overrideUserPreference = true;
        ForegroundService.syncAllSubscriptionsEnabledState(context, now, overrideUserPreference);

        // If we have the clear SIM PIN codes, then trigger the process of unlocking all the SIM
        // cards that are in the PIN state
        if (clearPinCodes != null && !clearPinCodes.isEmpty()) {
            ForegroundService.unlockSimCards(context, clearPinCodes);
        }

        // Schedule the next iteration processing of weekly repeat schedules to happen no earlier
        // than one minute from now as we already processed schedules at this time. Note that, if
        // the background usage is restricted, we won't be able to re-schedule using foreground
        // service. Therefore, we're going to do this here, otherwise when the user will remove
        // the background restriction for the app, the next schedule iteration processing will
        // actually never happen again because it was never re-scheduled.
        // Also note that, we *DO NOT* want to execute the above tasks here. This to avoid holding
        // this BroadcastReceiver for too long, as it could cause the system to consider it
        // non-responsive and ANR the entire app. Instead, we *want* them to be handled by the
        // foreground service, which will detect background restriction and inform the user about
        // the issue via a notification.
        if (!isBgRestricted) {
            ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(context,
                    now.plusMinutes(1), clearPinCodes);
        } else {
            final PendingResult result = goAsync();
            AsyncHandler.post(() -> {
                try {
                    List<PinEntity> pinEntities = null;
                    if (clearPinCodes != null) {
                        pinEntities = mPinStorageProvider.get().getPinEntities();
                        for (final PinEntity pinEntity : pinEntities) {
                            final String clearPin = clearPinCodes.getString(String.valueOf(
                                        pinEntity.getSubscriptionId()));
                            if (clearPin != null) {
                                pinEntity.setClearPin(clearPin);
                            }
                        }
                    }
                    mSubscriptionSchedulerProvider.get()
                        .updateNextWeeklyRepeatScheduleProcessingIter(now.plusMinutes(1),
                                    pinEntities);
                } finally {
                    result.finish();
                }
            });
        }
    }

    private static class AsyncHandler {
        static final Handler sHandler;

        static {
            final HandlerThread handlerThread = new HandlerThread(
                    AlarmReceiver.class.getSimpleName() + "Thread");
            handlerThread.start();
            sHandler = Handler.createAsync(handlerThread.getLooper());
        }

        static void post(final Runnable r) {
            sHandler.post(r);
        }
    }
}
