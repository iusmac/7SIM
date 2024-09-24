package com.github.iusmac.sevensim;

import android.app.ActivityManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.test.ShadowContextHiddenApi;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowSystemClock;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Robolectric.buildService;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.setStaticField;

@RunWith(Enclosed.class)
public class UserAuthenticationObserverServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 8, 0);

    private static final String EXTRA_TIME_KEY = "time";
    private static final String EXTRA_DECRYPT_PIN_STORAGE = "decrypt_pin_storage";

    /** Test {@link PowerManager.WakeLock} lifecycle used by the service. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class WakeLockLifecycle extends Base {
        private static final long WAKE_LOCK_REACQUIRE_INTERVAL_MS = 15 * 1000L;

        @Override
        public void setUp() {
            super.setUp();

            setStaticField(UserAuthenticationObserverService.class, "sWakeLock", null);
        }

        @Test
        public void test_startAction_WakeLockIsNotReferenceCounted() {
            startDummyAction();

            assertFalse(shadowOf(ShadowPowerManager.getLatestWakeLock()).isReferenceCounted());
        }

        @Test
        public void test_startAction_WakeLockShouldBeReleased() {
            startDummyAction();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_startAction_WakeLockShouldBeReleasedWhenCalledSynchronously() {
            startDummyAction();
            startDummyAction();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_startAction_WakeLockShouldBeReleasedWhenCalledAsynchronously()
            throws InterruptedException {

            final var latch = new CountDownLatch(2);
            final Runnable action = () -> {
                startDummyAction();
                latch.countDown();
            };
            new Thread(action).start();
            new Thread(action).start();

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_WakeLockShouldBeReacquired() {
            mController.create().startCommand(0, 1);
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_startAction_WakeLockShouldNotBeReleased() {
            mController.create().startCommand(0, 1);
            startDummyAction();
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_onDestroy_WakeLockShouldBeReleased() {
            mController.create().startCommand(0, 1).destroy();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onDestroy_WakeLockShouldNotBeAcquired() {
            mController.create().destroy();
            assertThat(ShadowPowerManager.getLatestWakeLock(), is(nullValue()));
        }

        @Test
        public void test_startAction_onCreate_onDestroy_WakeLockShouldNotBeReacquired() {
            startDummyAction();
            mController.create().destroy();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onStartCommand_WakeLockShouldBeReleasedOnTimeout() {
            mController.create().startCommand(0, 1);

            ShadowSystemClock.advanceBy(Duration.ofMillis(WAKE_LOCK_REACQUIRE_INTERVAL_MS + 1));
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onStartCommand_reacquireWakeLockShouldOccurBeforeTimeout() {
            mController.create().startCommand(0, 1);

            final long wakeLockTimeoutTime = WAKE_LOCK_REACQUIRE_INTERVAL_MS;
            // Reacquire will occur slightly before the WakeLock is released
            final long reacquireTime = wakeLockTimeoutTime - 3000L;

            // Reach the scheduled time when the WakeLock should be reacquired before it will
            // timeout
            mShadowMainLooper.idleFor(Duration.ofMillis(reacquireTime));

            // Reach the time when the WakeLock had to be released due to timeout, but is expected
            // to be reacquired
            ShadowSystemClock.advanceBy(Duration.ofMillis(wakeLockTimeoutTime - reacquireTime + 1));
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }
    }

    /** Test the foreground notification lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ForegroundNotificationLifecycle extends Base {
        private static final int UNLOCK_TO_CONTINUE_NOTIFICATION_ID = 4;

        @Test
        public void test_onCreate_ForegroundNotificationIsAttached() {
            // Ensure the startForeground() is called even if background is restricted
            shadowOf(mActivityManager).setBackgroundRestricted(true);

            mController.create();
            assertTrue(mShadowService.isLastForegroundNotificationAttached());
            assertThat(mShadowService.getLastForegroundNotificationId(),
                    is(UNLOCK_TO_CONTINUE_NOTIFICATION_ID));
        }

        @Test
        public void test_onDestroy_ForegroundNotificationIsRemoved() {
            mController.create().destroy();
            assertTrue(mShadowService.getNotificationShouldRemoved());
        }
    }

    /** Test the background restricted notification lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class BackgroundRestrictedNotificationLifecycle extends Base {
        private static final int BACKGROUND_RESTRICTED_NOTIFICATION_ID = 2;

        @Override
        public void setUp() {
            super.setUp();

            shadowOf(mActivityManager).setBackgroundRestricted(true);
            mController.create();
        }

        @Test
        public void test_onCreate_NotificationShouldBeShownOnBackgroundRestricted() {
            final var shadowNotificationManager = shadowOf(mApplicationContext
                    .getSystemService(android.app.NotificationManager.class));
            assertThat(shadowNotificationManager
                    .getNotification(BACKGROUND_RESTRICTED_NOTIFICATION_ID),
                    isA(Notification.class));
        }

        @Test
        public void test_onStartCommand_ShouldStopSelfWhenBackgroundRestricted() {
            mController.startCommand(0, 1);

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat("SHOULD stop self with stopSelf() and not with stopSelfResultId().",
                    mShadowService.getStopSelfResultId(), is(0));
        }
    }

    /** Test the user locked/unlocked lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class UserLockedUnlockedLifecycle extends Base {
        @Override
        public void setUp() {
            super.setUp();

            shadowOf(mKeyguardManager).setIsDeviceLocked(true);
            mController.create();
        }

        @Test
        public void test_onCreate_UserUnlockedReceiverShouldBeRegisteredWhenDeviceLocked() {
            assertThat(findUserUnlockedBroadcastReceiver(), is(not(Optional.empty())));
        }

        @Test
        public void test_onCreate_UserUnlockedReceiverShouldBeUnregisteredOnUserPresentIntent() {
            sendBroadcastUserPresentIntent();
            mShadowApplication.assertNoBroadcastListenersOfActionRegistered((Application)
                    mApplicationContext, Intent.ACTION_USER_PRESENT);
        }

        @Test
        public void test_onCreate_UserUnlockedReceiverShouldNotUnregisterOnInvalidIntents() {
            findUserUnlockedBroadcastReceiver().get()
                .onReceive(mApplicationContext, new Intent("dummy"));
            assertThat(findUserUnlockedBroadcastReceiver(), is(not(Optional.empty())));
        }

        @Test
        public void test_onDestroy_UserUnlockedReceiverShouldBeUnregistered() {
            mController.destroy();
            mShadowApplication.assertNoBroadcastListenersOfActionRegistered((Application)
                    mApplicationContext, Intent.ACTION_USER_PRESENT);
        }

        private Optional<BroadcastReceiver> findUserUnlockedBroadcastReceiver() {
            return mShadowApplication.getRegisteredReceivers().stream()
                .filter((wrapper) -> wrapper.intentFilter != null &&
                        wrapper.intentFilter.hasAction(Intent.ACTION_USER_PRESENT))
                .map((wrapper) -> wrapper.broadcastReceiver)
                .findFirst();
        }
    }

    /** Test the tasks' lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class TaskRunningLifecycle extends Base {
        @Test
        public void test_onCreate_onStartCommand_TasksShouldBeSuspendedWhenDeviceLocked()
            throws InterruptedException {

            shadowOf(mKeyguardManager).setIsDeviceLocked(true);

            mController.create()
                .startCommand(0, 1)
                .startCommand(0, 2)
                .startCommand(0, 3);

            Thread.sleep(500L);
            assertFalse(mShadowService.isStoppedBySelf());
        }

        @Test
        public void test_onStartCommand_TasksShouldRunAfterUserPresent() {
            shadowOf(mKeyguardManager).setIsDeviceLocked(true);

            final var lastStartId = 3;
            mController.create()
                .startCommand(0, 1)
                .startCommand(0, 2)
                .startCommand(0, lastStartId);

            sendBroadcastUserPresentIntent();

            awaitServiceStoppedBySelfWithinTime(mShadowService, Duration.ofSeconds(1),
                    Duration.ofMillis(50));
            assertThat(mShadowService.getStopSelfResultId(), is(lastStartId));
        }

        @Test
        public void test_onStartCommand_TasksShouldBeAbortedWhenDestroyedAndUserPresentAfterwards()
            throws InterruptedException {

            // We need to suspend tasks by "locking" the device, otherwise they will all finish
            // before the service is destroyed
            shadowOf(mKeyguardManager).setIsDeviceLocked(true);

            mController.create()
                .startCommand(0, 1)
                .startCommand(0, 2)
                .startCommand(0, 3)
                .destroy();

            sendBroadcastUserPresentIntent();

            Thread.sleep(500L);
            assertFalse(mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(0));
        }

        @Test
        public void test_onStartCommand_NoNewTasksShouldBeAcceptedAfterDestroyed()
            throws InterruptedException {

            final var lastStartId = 3;
            mController.create()
                .startCommand(0, 1)
                .startCommand(0, 2)
                .startCommand(0, lastStartId);

            // Wait until all scheduled tasks finish
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(1))
                .pollInterval(Duration.ofMillis(50))
                .until(mShadowService::getStopSelfResultId, is(lastStartId));

            mController.destroy().startCommand(0, lastStartId + 1);
            Thread.sleep(250L);

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(lastStartId));
        }

        @Test
        public void test_onStartCommand_HandleInvalidTaskAction() {
            final UserAuthenticationObserverService service = mController.get();
            service.inject();
            // Hijack logger creation to use a spied one instead to test on
            final var loggerFactory = service.mLoggerFactory;
            service.mLoggerFactory = (tag) -> spy(loggerFactory.create(tag));

            mController.create().startCommand(0, 1);

            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(1))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    final var actionCaptor = ArgumentCaptor.forClass(Object.class);
                    verify(service.mLogger, times(1)).e(any(String.class), actionCaptor.capture());
                    assertThat("SHOULD capture error log of an unhandled (empty) action.",
                            (String) actionCaptor.getValue(), is(emptyString()));
                });
        }

        @Test
        public void test_onStartCommand_ShouldStopSelfOnTaskFinish() {
            final int startId = 5;
            mController.create().startCommand(0, startId);

            awaitServiceStoppedBySelfWithinTime(mShadowService, Duration.ofSeconds(1),
                    Duration.ofMillis(50));
            assertThat("SHOULD stop self with stopSelfResultId() and not with stopSelf().",
                    mShadowService.getStopSelfResultId(), is(startId));
        }
    }

    /** Tests for {@link Binder}. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class Binder extends Base {
        @Test
        public void test_onBind_NullBinder() {
            assertThat(mController.get().onBind(new Intent()), is(nullValue()));
        }
    }

    /** Tests for main {@link Looper}. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class MainLooperLifecycle extends Base {
        @Override
        public void setUp() {
            super.setUp();

            mController.create();
        }

        @Test
        public void test_onCreate_onDestroy_LooperShouldBeDrained() {
            mController.destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }

        @Test
        public void test_onCreate_onStartCommand_onDestroy_LooperShouldBeDrained() {
            mController.startCommand(0, 1).destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }
    }

    /** Test {@link UserAuthenticationObserverService#updateNextWeeklyRepeatScheduleProcessingIter}
     * action call. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class UpdateNextWeeklyRepeatScheduleProcessingIterAction extends Base {
        private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
            "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

        @Parameter(0)
        public LocalDateTime mNow;

        @Parameter(1)
        public boolean mDecryptPinStorage;

        @Parameters(name = "now={0} decryptPinStorage={1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { null, true },
                { null, false },
                { NOW, true },
                { NOW, false },
            });
        }

        private Intent mIntent;

        @Override
        public void setUp() {
            super.setUp();

            UserAuthenticationObserverService
                .updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext, mNow,
                        mDecryptPinStorage);

            mIntent = mShadowApplication.getNextStartedService();
        }

        @Test
        public void test_compareStartedIntent() {
            assertThat(mIntent.getComponent(), is(new ComponentName(mApplicationContext,
                            UserAuthenticationObserverService.class)));
            assertThat(mIntent.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(mIntent.getStringExtra(EXTRA_TIME_KEY), is(Objects.toString(mNow, null)));
            assertThat(mIntent.getBooleanExtra(EXTRA_DECRYPT_PIN_STORAGE, !mDecryptPinStorage),
                    is(mDecryptPinStorage));
        }

        @Test
        public void test_onStartCommand() {
            final var controller = buildService(UserAuthenticationObserverService.class, mIntent);
            final int startId = 2;
            final var shadowService = shadowOf(controller.create().startCommand(0, startId).get());

            awaitServiceStoppedBySelfWithinTime(shadowService, Duration.ofSeconds(2),
                    Duration.ofMillis(100));
            assertThat(shadowService.getStopSelfResultId(), is(startId));

            final Intent i = mShadowApplication.getNextStartedService();
            assertThat(i, is(notNullValue()));
            assertThat("Expected ForegroundService to be started after the action is consumed.",
                    i.getComponent(), is(new ComponentName(mApplicationContext,
                                    ForegroundService.class)));
        }
    }

    @Config(shadows = ShadowContextHiddenApi.class)
    static class Base extends MockitoHiltAndroidTestBase {
        final ServiceController<UserAuthenticationObserverService> mController =
            buildService(UserAuthenticationObserverService.class);

        final ShadowService mShadowService = shadowOf(mController.get());
        final ShadowLooper mShadowMainLooper = shadowOf(Looper.getMainLooper());

        @Inject
        ActivityManager mActivityManager;

        @Inject
        KeyguardManager mKeyguardManager;

        ShadowApplication mShadowApplication;

        @CallSuper
        @Override
        public void setUp() {
            super.setUp();

            mShadowApplication = shadowOf((Application) mApplicationContext);
        }

        void sendBroadcastUserPresentIntent() {
            mApplicationContext.sendBroadcast(new Intent(Intent.ACTION_USER_PRESENT));
            mShadowMainLooper.idle();
        }

        /**
         * Start a "dummy" action through the static {@link UserAuthenticationObserverService#startAction}
         * method.
         */
        void startDummyAction() {
            UserAuthenticationObserverService.startAction(mApplicationContext, new Intent("dummy"));
        }
    }

    /**
     * Await the service to stop self with {@link Service#stopSelf} or
     * {@link Service#stopSelfResult} within a duration time, otherwise fail with exception.
     *
     * Use this when the service is going to process a real task that is performed using a
     * worker thread, so we need to poll periodically to ensure the result is visible on the
     * main thread.
     */
    private static void awaitServiceStoppedBySelfWithinTime(final ShadowService shadowService,
            final Duration atMostTime, final Duration pollIntervalTime) {

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(atMostTime)
            .pollInterval(pollIntervalTime)
            .until(shadowService::isStoppedBySelf);
    }
}
