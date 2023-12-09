package com.github.iusmac.sevensim.scheduler;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.ForegroundService;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;

import javax.inject.Inject;

/**
 * This static broadcast receiver will be triggered exclusively by the {@link AlarmManager}, with
 * the aim of initiating an ordinary iteration in which the scheduler will process all weekly repeat
 * schedules at the stated time.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public final class AlarmReceiver extends Hilt_AlarmReceiver {
    @Inject
    Logger.Factory loggerFactory;

    private Logger mLogger;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);

        mLogger = loggerFactory.create(getClass().getSimpleName());

        mLogger.d("onReceive() : intent=" + intent);

        final LocalDateTime now = LocalDateTime.now();

        // Normally, we keep the SIM card disabled after a reboot if it was manually disabled by the
        // user, but the schedules should reset it when it comes time to process them at the stated
        // time
        final boolean overrideUserPreference = true;
        ForegroundService.syncAllSubscriptionsEnabledState(context, now, overrideUserPreference);

        // Schedule the next iteration processing of weekly repeat schedules to happen no earlier
        // than one minute from now as we already processed schedules at this time
        ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(context, now.plusMinutes(1));
    }
}
