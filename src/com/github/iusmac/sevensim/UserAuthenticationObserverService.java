package com.github.iusmac.sevensim;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UserHandle;

import androidx.annotation.GuardedBy;
import androidx.core.content.ContextCompat;

import dagger.hilt.android.AndroidEntryPoint;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import javax.inject.Inject;

/**
 * <p>This foreground service ensures to execute tasks (even when the app is in the background), in
 * the same order it receives them, but only after the user unlocks the device, which gives access
 * to the credential-encrypted (CE) storage, and die as soon as there are no more tasks left. By
 * default, tasks will continue execution even if user locks the device again.
 *
 * <p>This service will also show a sticky notification to inform the user that the app is working
 * in the background and is consuming system resources.
 */
@AndroidEntryPoint(Service.class)
public final class UserAuthenticationObserverService extends Hilt_UserAuthenticationObserverService {
    /** The lock object to synchronize on when acquiring/releasing the {@link WakeLock}. */
    private static final Object sWakeLockSyncLock = new Object();

    /** The {@link WakeLock} to keep the system awake while this foreground service is running. */
    @GuardedBy("sWakeLockSyncLock")
    private static WakeLock sWakeLock;

    private final Object mWakeLockReacquireToken = new Object();

    /**
     * Action to update the next weekly repeat schedule processing iteration to a different time
     * when user unlocks the device.
     */
    private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
        "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

    /** The interval, in milliseconds, to re-acquire wake lock. */
    private static final long WAKE_LOCK_REACQUIRE_INTERVAL_MS = 15 * 1000L;

    /** Key holding the stringified value of the {@link LocalDateTime} in the Intent's payload. */
    private static final String EXTRA_TIME_KEY = "time";

    /** Key holding a {@link Boolean} of whether to trigger decryption of the {@link PinStorage}. */
    private static final String EXTRA_DECRYPT_PIN_STORAGE = "decrypt_pin_storage";

    private final Worker mWorker = new Worker();
    private BroadcastReceiver mUserUnlockedReceiver;

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    ActivityManager mActivityManager;

    @Inject
    NotificationManager mNotificationManager;

    @Inject
    KeyguardManager mKeyguardManager;

    private Logger mLogger;

    /**
     * {@link ForegroundService#updateNextWeeklyRepeatScheduleProcessingIter(Context,LocalDateTime,boolean)}.
     */
    public static void updateNextWeeklyRepeatScheduleProcessingIter(final Context context,
            final LocalDateTime compareTime, final boolean decryptPinStorage) {

        final Intent i = new Intent(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER);
        if (compareTime != null) {
            i.putExtra(EXTRA_TIME_KEY, compareTime.toString());
        }
        i.putExtra(EXTRA_DECRYPT_PIN_STORAGE, decryptPinStorage);
        startAction(context, i);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate().");

        startForeground(NotificationManager.UNLOCK_TO_CONTINUE_NOTIFICATION_ID,
                mNotificationManager.buildUnlockToContinueNotification());

        if (mActivityManager.isBackgroundRestricted()) {
            mLogger.e("Cannot initialize service due to background restriction.");
            mNotificationManager.showBackgroundRestrictedNotification();
            stopSelf();
            return;
        }

        if (mKeyguardManager.isDeviceLocked()) {
            final IntentFilter userFilter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            mUserUnlockedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                        unregisterReceiver(mUserUnlockedReceiver);
                        mUserUnlockedReceiver = null;
                        mWorker.start();
                    }
                }
            };
            ContextCompat.registerReceiver(this, mUserUnlockedReceiver, userFilter,
                    ContextCompat.RECEIVER_EXPORTED);
        } else {
            mWorker.start();
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

        // Although this service will be able to gain CPU time since the screen will be on after
        // unlocking, but we need to ensure that we stay awake in case the user unlocks the device
        // and immediately turns off the screen, which may have also turned off due to of some other
        // (system) events
        reacquire();

        mWorker.schedule(new PendingTask(startId, intent));

        return START_REDELIVER_INTENT;
    }

    /**
     * @param intent The pending intent received from onStartCommand to process.
     */
    private void handleIntent(final Intent intent) {
        mLogger.d("handleIntent(intent=%s).", intent);

        final Optional<LocalDateTime> dateTime =
            DateTimeUtils.parseDateTime(intent.getStringExtra(EXTRA_TIME_KEY));
        final boolean decryptPinStorage = intent.getBooleanExtra(EXTRA_DECRYPT_PIN_STORAGE, false);

        final String action = intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER:
                    ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(this,
                        dateTime.orElse(null), decryptPinStorage);
                break;

            default:
                mLogger.e("handleIntent() : Unhandled action=%s.", action);
        }
    }

    /** Re-acquire wake lock before it's about to expire. */
    private void reacquire() {
        mLogger.v("reacquire().");

        synchronized (sWakeLockSyncLock) {
            acquire(this);
        }

        getMainThreadHandler().postDelayed(this::reacquire, mWakeLockReacquireToken,
                WAKE_LOCK_REACQUIRE_INTERVAL_MS - 3000L);
    }

    @Override
    public void onDestroy() {
        try {
            mLogger.d("onDestroy().");

            mWorker.shutdown();
            if (mUserUnlockedReceiver != null) {
                unregisterReceiver(mUserUnlockedReceiver);
            }
            getMainThreadHandler().removeCallbacksAndMessages(mWakeLockReacquireToken);

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
     * A {@link LinkedBlockingQueue}-based worker that will run all {@link PendingTask}s using a
     * separate thread after its {@link #start()} method has been invoked. New tasks can be added
     * after the worker is started.
     */
    private final class Worker extends Thread {
        final LinkedBlockingQueue<PendingTask> mPendingTasks = new LinkedBlockingQueue<>();
        volatile boolean mReleased;

        /**
         * @param task The {@link PendingTask} to offload onto a separate thread.
         */
        void schedule(final PendingTask task) {
            mPendingTasks.offer(task);
        }

        @Override
        public void run() {
            while (!mReleased) {
                try {
                    final PendingTask task = mPendingTasks.take();
                    mLogger.d("Worker : Start processing %s.", task);
                    handleIntent(task.intent);
                    stopSelfResult(task.id);
                    mLogger.d("Worker : Finish processing %s.", task);
                } catch (InterruptedException ignored) {
                    if (mReleased) {
                        return;
                    }
                }
            }
        }

        /** Shutdown the worker. No new tasks will be accepted. */
        void shutdown() {
            mReleased = true;
            interrupt();
            mPendingTasks.clear();
        }
    }

    /**
     * A holder class representing a pending intent associated with its ID (aka startId), that will
     * be processed via {@link #handleIntent(Intent)} when the {@link Worker} starts.
     */
    private final class PendingTask {
        final int id;
        final Intent intent;

        PendingTask(final int id, final Intent intent) {
            this.id = id;
            this.intent = intent;
        }

        @Override
        public String toString() {
            return "PendingTask {" + " id=" + id + " intent=" + intent + " }";
        }
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
                intent.setClass(context, UserAuthenticationObserverService.class);
                context.startForegroundServiceAsUser(intent, UserHandle.CURRENT);
            } finally {
                sWakeLock.release();
            }
        }
    }

    /**
     * <p>Helper function to acquire a partial wake lock that timeouts after approximately
     * {@link #WAKE_LOCK_REACQUIRE_INTERVAL_MS}.
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
                    UserAuthenticationObserverService.class.getSimpleName());
            sWakeLock.setReferenceCounted(false);
        }
        sWakeLock.acquire(WAKE_LOCK_REACQUIRE_INTERVAL_MS);
    }
}
