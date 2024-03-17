package com.github.iusmac.sevensim;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.PowerManager.WakeLock;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import androidx.annotation.GuardedBy;
import androidx.core.content.ContextCompat;

import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.inject.Inject;

/**
 * <p>This foreground service ensures to execute tasks (even when the app is in the background), in
 * the same order it receives them, but only after the phone call ended, and die as soon as there
 * are no more tasks left.
 *
 * <p>This service will also show a sticky notification to inform the user that the app is working
 * in the background and is consuming system resources.
 */
@AndroidEntryPoint(Service.class)
public final class PhoneCallEndObserverService extends Hilt_PhoneCallEndObserverService {
    /** The lock object to synchronize on when acquiring/releasing the {@link WakeLock}. */
    private static final Object sWakeLockSyncLock = new Object();

    /** The {@link WakeLock} to keep the system awake while this foreground service is running. */
    @GuardedBy("sWakeLockSyncLock")
    private static WakeLock sWakeLock;

    /**
     * Action to trigger syncing of the enabled state of a SIM subscription with its existing
     * weekly repeat schedules on phone call ended.
     */
    private static final String ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE =
        "ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE";

    /**
     * Action to update the next weekly repeat schedule processing iteration to a different time on
     * phone call ended.
     */
    private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
        "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

    /** The polling interval, in milliseconds, when checking the phone "in call" state. */
    private static final long PHONE_IN_CALL_STATE_POLL_INTERVAL_MS = 15 * 1000L;

    /** Key holding the stringified value of the {@link LocalDateTime} in the Intent's payload. */
    private static final String EXTRA_TIME_KEY = "time";

    /**
     * Key holding a {@link Boolean} of whether the user's preference should NOT take precedence
     * over schedules when synchronizing the SIM subscription(s) enabled state.
     */
    private static final String EXTRA_OVERRIDE_USER_PREFERENCE = "override_user_preference";

    private final Object mPhoneCallEndedPollToken = new Object();

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    ActivityManager mActivityManager;

    @Inject
    NotificationManager mNotificationManager;

    @Inject
    TelephonyUtils mTelephonyUtils;

    private Logger mLogger;

    /** {@link SubscriptionScheduler#syncSubscriptionEnabledState(int,LocalDateTime,boolean)}. */
    public static void syncSubscriptionEnabledState(final Context context, final int subId,
            final LocalDateTime compareTime, final boolean overrideUserPreference) {

        final Intent i = new Intent(ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE);
        if (compareTime != null) {
            i.putExtra(EXTRA_TIME_KEY, compareTime.toString());
        }
        i.putExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        i.putExtra(EXTRA_OVERRIDE_USER_PREFERENCE, overrideUserPreference);
        startAction(context, i);
    }

    /**
     * {@link SubscriptionScheduler#updateNextWeeklyRepeatScheduleProcessingIter(LocalDateTime)}.
     */
    public static void updateNextWeeklyRepeatScheduleProcessingIter(final Context context,
            final LocalDateTime compareTime) {

        final Intent i = new Intent(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER);
        if (compareTime != null) {
            i.putExtra(EXTRA_TIME_KEY, compareTime.toString());
        }
        startAction(context, i);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate().");

        startForeground(NotificationManager.CALL_IN_PROGRESS_NOTIFICATION_ID,
                mNotificationManager.buildCallInProgressForegroundNotification());

        if (mActivityManager.isBackgroundRestricted()) {
            mLogger.e("Cannot initialize service due to background restriction.");
            mNotificationManager.showBackgroundRestrictedNotification();
            stopSelf();
            return;
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        mLogger.d("onStartCommand(intent=%s,flags=%d,startId=%d).", intent, flags, startId);

        if (intent == null) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if (mActivityManager.isBackgroundRestricted()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if ((flags & (START_FLAG_RETRY | START_FLAG_REDELIVERY)) != 0) {
            synchronized (sWakeLockSyncLock) {
                // Re-acquire wake lock if restarted
                acquire(this);
            }
        }

        final Optional<LocalDateTime> dateTime =
            DateTimeUtils.parseDateTime(intent.getStringExtra(EXTRA_TIME_KEY));
        final boolean overrideUserPreference =
            intent.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, false);
        final int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        final String action = intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE:
                onCallEnded(() -> ForegroundService.syncSubscriptionEnabledState(this, subId,
                            dateTime.orElse(null), overrideUserPreference), startId);
                break;

            case ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER:
                onCallEnded(() ->
                        ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(this,
                            dateTime.orElse(null)), startId);
                break;

            default:
                mLogger.e("onStartCommand() : Unhandled action=%s.", action);
                stopSelfResult(startId);
                return START_NOT_STICKY;
        }

        return START_REDELIVER_INTENT;
    }

    /**
     * @param callback The callback to invoke on the main thread when the phone call ended.
     * @param taskId The task ID for which to call {@link #stopSelfResult(int)} on completion.
     */
    private void onCallEnded(final Runnable callback, final int taskId) {
        synchronized (sWakeLockSyncLock) {
            // Re-acquire wake lock until the phone call ended
            acquire(this);
        }

        final boolean isInCall = mTelephonyUtils.isInCall();

        mLogger.v("onCallEnded(taskId=%d) : isInCall=%s.", taskId, isInCall);

        if (!isInCall) {
            callback.run();
            stopSelfResult(taskId);
        } else {
            // Poll the call state within the interval
            getMainThreadHandler().postDelayed(() -> onCallEnded(callback, taskId),
                    mPhoneCallEndedPollToken, PHONE_IN_CALL_STATE_POLL_INTERVAL_MS);
        }
    }

    @Override
    public void onDestroy() {
        try {
            mLogger.d("onDestroy().");

            getMainThreadHandler().removeCallbacksAndMessages(mPhoneCallEndedPollToken);

            stopForeground(STOP_FOREGROUND_REMOVE);
        } finally {
            synchronized (sWakeLockSyncLock) {
                if (sWakeLock != null && sWakeLock.isHeld()) {
                    sWakeLock.release();
                }
            }
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    /**
     * Helper function to properly start and facilitate communication with this foreground service.
     *
     * @param context The context to request this service to be started.
     * @param intent The intent containing action and payload data. Note that, this function will
     * take care of intent context and other fields.
     */
    private static void startAction(final Context context, Intent intent) {
        synchronized (sWakeLockSyncLock) {
            // Hold wake lock to ensure that the service will start and operate till termination
            acquire(context);
            try {
                intent = new Intent(intent);
                intent.setClass(context, PhoneCallEndObserverService.class);
                context.startForegroundServiceAsUser(intent, UserHandle.CURRENT);
            } catch (Exception e) {
                sWakeLock.release();
                throw e;
            }
        }
    }

    /**
     * <p>Helper function to acquire a partial wake lock that timeouts after approximately
     * {@link #PHONE_IN_CALL_STATE_POLL_INTERVAL_MS}.
     *
     * <p>You can call this function multiple times to re-acquire the wake lock, thus update the
     * timeout; it won't increment reference counter.
     */
    @GuardedBy("sWakeLockSyncLock")
    private static void acquire(final Context context) {
        if (sWakeLock == null) {
            final PowerManager pm = ContextCompat.getSystemService(context, PowerManager.class);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    BuildConfig.APPLICATION_ID + ":" +
                    PhoneCallEndObserverService.class.getSimpleName());
            sWakeLock.setReferenceCounted(false);
        }
        // Make sure we don't indefinitely hold the wake lock under any circumstances. Note that,
        // for reliability, we add an extra time span of 10s to ensure that we don't fall asleep
        // when scheduling a new phone call state poll request
        sWakeLock.acquire(PHONE_IN_CALL_STATE_POLL_INTERVAL_MS + 10 * 1000L);
    }
}
