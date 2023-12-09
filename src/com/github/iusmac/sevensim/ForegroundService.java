package com.github.iusmac.sevensim;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.os.UserHandle;

import androidx.annotation.GuardedBy;
import androidx.core.content.ContextCompat;

import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;

import dagger.Lazy;
import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

/**
 * <p>This foreground service is designed to perform <em>short-lived</em> tasks on a separate thread
 * in the same order it receives them, while the application is in the background, and die as soon
 * as there are no more tasks left.
 *
 * <p>This service will also show a sticky notification to inform the user that the app is working
 * in the background and is consuming system resources.
 */
@AndroidEntryPoint(Service.class)
public final class ForegroundService extends Hilt_ForegroundService {
    /** The lock object to synchronize on when acquiring/releasing the {@link WakeLock}. */
    private static final Object sWakeLockSyncLock = new Object();

    /** The {@link WakeLock} to keep the system awake while this foreground service is running. */
    @GuardedBy("sWakeLockSyncLock")
    private static WakeLock sWakeLock;

    /**
     * Action to update the next weekly repeat schedule processing iteration to a different time.
     */
    private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
        "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

    /**
     * Action to trigger syncing of the enabled state of all SIM subscriptions found on the
     * device with their existing weekly repeat schedules.
     */
    private static final String ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE =
        "ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE";

    /**
     * Action to trigger syncing of the enabled state of a SIM subscription with its existing
     * weekly repeat schedules.
     */
    private static final String ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE =
        "ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE";

    /** Key holding the stringified value of the {@link LocalDateTime} in the Intent's payload. */
    private static final String EXTRA_TIME_KEY = "time";

    /**
     * Key holding a {@link Boolean} of whether the user's preference should NOT take precedence
     * over schedules when synchronizing the SIM subscription(s) enabled state.
     */
    private static final String EXTRA_OVERRIDE_USER_PREFERENCE = "override_user_preference";

    /**
     * Indicates the waiting time, in milliseconds, after which this service should initiate an
     * immediate termination. A timeout of 3 minutes should be enough to complete all tasks and die
     * under any circumstances.
     */
    private static final long SERVICE_TIMEOUT_MS_DEFAULT = 3 * 60 * 1000L;

    /**
     * Indicates the time in the future, in milliseconds, at which the service should start an
     * unsafe termination. This value is expected to be calculated dynamically right after acquiring
     * a wake lock.
     */
    @GuardedBy("sWakeLockSyncLock")
    private static OptionalLong SERVICE_STOP_AT_TIME_MS = OptionalLong.empty();

    /**
     * Flag indicating whether the service termination was initiated in an orderly and safe manner.
     * Under normal circumstances, the service will *always* terminate as soon as the worker's queue
     * runs out of tasks.
     */
    private boolean mIsServiceTerminatedSafely = true;

    private final Object mServiceTimeoutToken = new Object();

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    Lazy<SubscriptionScheduler> mSubscriptionSchedulerLazy;

    @Inject
    NotificationManager mNotificationManager;

    private Logger mLogger;
    private Worker mWorker;

    /** {@link SubscriptionScheduler#syncAllSubscriptionsEnabledState(LocalDateTime,boolean)}. */
    public static void syncAllSubscriptionsEnabledState(final Context context,
            final LocalDateTime compareTime, final boolean overrideUserPreference) {

        final Intent i = new Intent(ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE);
        if (compareTime != null) {
            i.putExtra(EXTRA_TIME_KEY, compareTime.toString());
        }
        i.putExtra(EXTRA_OVERRIDE_USER_PREFERENCE, overrideUserPreference);
        startAction(context, i);
    }

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

        mWorker = new Worker();

        startForeground(NotificationManager.FOREGROUND_NOTIFICATION_ID,
                mNotificationManager.buildForegroundServiceNotification());

        // Set the timeout to ensure this foreground service will be recycled whatsoever
        synchronized (sWakeLockSyncLock) {
            SERVICE_STOP_AT_TIME_MS.ifPresent((millis) -> updateServiceTimeout(millis));
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        mLogger.d("onStartCommand(intent=%s,flags=%d,startId=%d).", intent, flags, startId);

        if (intent == null) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if ((flags & (START_FLAG_RETRY | START_FLAG_REDELIVERY)) != 0) {
            synchronized (sWakeLockSyncLock) {
                // Re-acquire wake lock if restarted
                acquire(this);
                updateServiceTimeout(SERVICE_STOP_AT_TIME_MS.getAsLong());
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
            case ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER:
                mWorker.execute(() -> dateTime.ifPresent((ldt) -> mSubscriptionSchedulerLazy.get()
                            .updateNextWeeklyRepeatScheduleProcessingIter(ldt)), startId);
                break;

            case ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE:
                mWorker.execute(() -> dateTime.ifPresent((ldt) -> mSubscriptionSchedulerLazy.get()
                            .syncAllSubscriptionsEnabledState(ldt, overrideUserPreference)),
                        startId);
                break;

            case ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE:
                mWorker.execute(() -> dateTime.ifPresent((ldt) -> {
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        mSubscriptionSchedulerLazy.get().syncSubscriptionEnabledState(subId, ldt,
                                overrideUserPreference);
                    }
                }), startId);
                break;

            default:
                mLogger.e("onStartCommand() : Unhandled action=%s.", action);
        }

        if (mWorker.getQueueSize() == 0) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        try {
            mLogger.d("onDestroy().");

            getMainThreadHandler().removeCallbacksAndMessages(mServiceTimeoutToken);
            if (mWorker != null) {
                mWorker.shutdown(mIsServiceTerminatedSafely);
            }
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

    /** Set the timeout to initiate an unsafe termination of this service at the stated time. */
    private void updateServiceTimeout(final long uptimeMillis) {
        getMainThreadHandler().removeCallbacksAndMessages(mServiceTimeoutToken);
        getMainThreadHandler().postAtTime(() -> {
            mIsServiceTerminatedSafely = false;
            stopSelf();

            mLogger.w("Service reached timeout of %dms. Starting an unsafe service termination.",
                    SERVICE_TIMEOUT_MS_DEFAULT);
        }, mServiceTimeoutToken, uptimeMillis);
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
                intent.setClass(context, ForegroundService.class);
                context.startForegroundServiceAsUser(intent, UserHandle.CURRENT);
            } catch (Exception e) {
                sWakeLock.release();
                throw e;
            }
        }
    }

    /**
     * <p>Helper function to acquire a partial wake lock that timeouts after the amount of time
     * specified by {@link #SERVICE_TIMEOUT_MS_DEFAULT}.
     *
     * <p>You can call this function multiple times to re-acquire the wake lock, thus update the
     * timeout; it won't increment reference counter.
     */
    @GuardedBy("sWakeLockSyncLock")
    private static void acquire(final Context context) {
        if (sWakeLock == null) {
            final PowerManager pm = ContextCompat.getSystemService(context, PowerManager.class);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    BuildConfig.APPLICATION_ID + ":" + ForegroundService.class.getSimpleName());
            sWakeLock.setReferenceCounted(false);
        }
        // Make sure we don't indefinitely hold the wake lock under any circumstances. Note
        // that, for reliability, we add an extra time span of 30s to ensure that we don't fall
        // asleep along the way when terminating this service
        sWakeLock.acquire(SERVICE_TIMEOUT_MS_DEFAULT + 30 * 1000L);
        SERVICE_STOP_AT_TIME_MS = OptionalLong.of(SystemClock.uptimeMillis() +
                SERVICE_TIMEOUT_MS_DEFAULT);
    }

    /**
     * A simple worker that offloads work onto separate thread.
     */
    private final class Worker {
        final Handler mHandler;
        {
            final HandlerThread handlerThread = new HandlerThread(ForegroundService.this
                    .getClass().getSimpleName() + "WorkerThread");
            handlerThread.setDaemon(true);
            handlerThread.start();
            mHandler = Handler.createAsync(handlerThread.getLooper());
        }

        final AtomicInteger mQueueSize = new AtomicInteger();

        /**
         * @param callback The task callback to offload onto separate thread.
         * @param taskId The task ID for which to call {@link #stopSelfResult(int)} on completion.
         */
        void execute(final Runnable callback, final int taskId) {
            mLogger.d("Worker.execute(taskId=%d) Add : mQueueSize=%d.", taskId,
                    mQueueSize.getAndIncrement());

            mHandler.post(() -> {
                mLogger.d("Worker.execute(taskId=%d) Start : mQueueSize=%d.", taskId,
                        mQueueSize.get());

                callback.run();

                mLogger.d("Worker.execute(taskId=%d) Finish : mQueueSize=%d.", taskId,
                        mQueueSize.decrementAndGet());

                // Since we return the START_REDELIVER_INTENT flag, we have to mark this task as
                // completed to prevent its potential re-delivery in case another parallel task has
                // been scheduled and killed before completing. This will prevent from delivering
                // again all completed tasks, and ensure that only the most recent uncompleted tasks
                // are re-scheduled. Otherwise, an orderly termination of the service will be
                // initiated if this is the last task
                stopSelfResult(taskId);
            });
        }

        /**
         * @return The number of tasks in the queue list.
         */
        int getQueueSize() {
            return mQueueSize.get();
        }

        /**
         * Shutdown the worker. No new tasks will be accepted.
         *
         * @param safe Whether to initiate an orderly and safe shutdown of the worker.
         */
        void shutdown(final boolean safe) {
            if (safe) {
                mHandler.getLooper().quitSafely();
            } else {
                mHandler.getLooper().quit();
                mHandler.getLooper().getThread().interrupt();
            }
            mQueueSize.set(0);
        }
    }
}
