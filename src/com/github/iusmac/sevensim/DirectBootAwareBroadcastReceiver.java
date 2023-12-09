package com.github.iusmac.sevensim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;

import javax.inject.Inject;

/**
 * <p>This static broadcast receiver is marked as being {@link ComponentInfo#directBootAware}, and
 * will be triggered in response to various alterations in the system that occur after the device
 * has finished booting, but before the user has unlocked the device.
 *
 * <p>For the events that occur after the device is unlocked, see {@link SystemBroadcastReceiver}.
 */
@AndroidEntryPoint(BroadcastReceiver.class)
public final class DirectBootAwareBroadcastReceiver extends Hilt_DirectBootAwareBroadcastReceiver {
    @Inject
    Logger.Factory mLoggerFactory;

    private Logger mLogger;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onReceive() : intent=" + intent);

        final LocalDateTime now = LocalDateTime.now();
        final String action = intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                // Need to sync the enabled state of all SIM subscriptions available on the device
                // with their existing weekly repeat schedules after the device has finished booting
                ForegroundService.syncAllSubscriptionsEnabledState(context, now,
                        /*overrideUserPreference=*/ false);

                // Need to schedule the next iteration processing of weekly repeat schedules after
                // the device has finished booting. Note that, this call should only happen after
                // syncing
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(context, now);
                break;

            case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED:
                // Avoid rebroadcast after unlocking the device as we're direct boot aware
                if (Utils.IS_AT_LEAST_R && intent.getBooleanExtra(CarrierConfigManager
                            .EXTRA_REBROADCAST_ON_UNLOCK, false)) {

                    mLogger.d("onReceive() : Ignoring carrier config rebroadcast on unlock.");
                    break;
                }

                final int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                // Need to sync the enabled state of a SIM subscription with its existing weekly
                // repeat schedules on SIM addition/removal or SIM state alterations
                ForegroundService.syncSubscriptionEnabledState(context, subId, now,
                        /*overrideUserPreference=*/ false);

                // Need to re-schedule the next iteration processing of weekly repeat schedules
                // after syncing. Note that, if the SIM card was ejected, the SIM subscription won't
                // exist as well, so there will be nothing to sync, but we still need to do
                // re-scheduling as there can be other SIM cards in the system with their schedules
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(context, now);
                break;

            default:
                mLogger.e("onReceive() : Unhandled action: %s.", action);
                return;
        }
    }
}
