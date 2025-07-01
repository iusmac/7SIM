package com.github.iusmac.sevensim;

import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.test.ShadowContextHiddenApi;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;

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

import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.robolectric.Robolectric.buildService;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.setStaticField;

@RunWith(Enclosed.class)
public class PhoneCallEndObserverServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 8, 0);

    private static final long PHONE_IN_CALL_STATE_POLL_INTERVAL_MS = 15 * 1000L;

    private static final String EXTRA_TIME_KEY = "time";
    private static final String EXTRA_OVERRIDE_USER_PREFERENCE = "override_user_preference";

    /** Test {@link PowerManager.WakeLock} lifecycle used by the service. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class WakeLockLifecycle extends Base {
        @Override
        public void setUp() {
            super.setUp();

            setStaticField(PhoneCallEndObserverService.class, "sWakeLock", null);
        }

        @Test
        public void test_startAction_WakeLockIsNotReferenceCounted() {
            startDummyAction();

            assertFalse(shadowOf(ShadowPowerManager.getLatestWakeLock()).isReferenceCounted());
        }

        @Test
        public void test_startAction_WakeLockShouldNotBeReleased() {
            startDummyAction();
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_startAction_WakeLockShouldBeReleasedOnException() {
            final var context = spy(mApplicationContext);
            doAnswer((invocation) -> {
                throw new Exception();
            }).when(context).startForegroundServiceAsUser(any(Intent.class), any(UserHandle.class));

            try {
                startDummyAction(context);
                fail("SHOULD throw an exception instead.");
            } catch (Exception ignored) { /* @SuppressWarnings("EmptyCatch") */ }

            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_WakeLockShouldBeReacquiredWhenRestartedWithRetryFlag() {
            mController.create().startCommand(Service.START_FLAG_RETRY, 1);
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_WakeLockShouldBeReacquiredWhenRestartedWithRedeliveryFlag() {
            mController.create().startCommand(Service.START_FLAG_REDELIVERY, 1);
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_startAction_WakeLockShouldNotBeReleasedWhenRestartedWithRetryFlag() {
            mController.create().startCommand(Service.START_FLAG_RETRY, 1);
            startDummyAction();
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_startAction_WakeLockShouldNotBeReleasedWhenRestartedWithRedeliveryFlag() {
            mController.create().startCommand(Service.START_FLAG_REDELIVERY, 1);
            startDummyAction();
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_onDestroy_WakeLockShouldBeReleased() {
            mController.create().startCommand(Service.START_FLAG_RETRY, 1).destroy();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_WakeLockShouldNotBeReacquiredWhenNotRestarted() {
            mController.create().startCommand(0, 1);
            assertThat(ShadowPowerManager.getLatestWakeLock(), is(nullValue()));
        }

        @Test
        public void test_onCreate_onDestroy_WakeLockShouldNotBeAcquired() {
            mController.create().destroy();
            assertThat(ShadowPowerManager.getLatestWakeLock(), is(nullValue()));
        }

        @Test
        public void test_startAction_onStartCommand_WakeLockShouldBeReleasedWhenExpired() {
            startDummyAction();
            mController.create().startCommand(0, 1);

            expireWakeLockNow();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_onCreate_onStartCommand_onDestroy_WakeLockShouldNotBeReacquiredWhenExpired() {
            startDummyAction();
            mController.create().startCommand(0, 1);

            expireWakeLockNow();

            mController.destroy();
            assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        @Test
        public void test_WakeLockShouldBeReaquiredUntilPhoneCallEnded() {
            startDummyAction();
            mController.create();

            expireWakeLockNow();

            final int startId = 1;
            onCallEnded(() -> {}, startId);

            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld());
        }

        /**
         * Force expire the pre-acquired {@link PowerManager.WakeLock} used by the service.
         *
         * <p><strong>NOTE:</strong> ensure to start an action using one of the available public
         * APIs or through reflection ({@link #startDummyAction()}) before using this.
         */
        private void expireWakeLockNow() {
            ShadowSystemClock.advanceBy(Duration.ofMillis(PHONE_IN_CALL_STATE_POLL_INTERVAL_MS)
                    // WakeLock will be released 10 seconds later to allow the service to schedule a
                    // new phone call state poll request
                    .plusSeconds(10 + 1));
        }
    }

    /** Test the foreground notification lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ForegroundNotificationLifecycle extends Base {
        static final int CALL_IN_PROGRESS_NOTIFICATION_ID = 3;

        @Test
        public void test_onCreate_ForegroundNotificationIsAttached() {
            // Ensure the startForeground() is called even if background is restricted
            shadowOf(mActivityManager).setBackgroundRestricted(true);

            mController.create();
            assertTrue(mShadowService.isLastForegroundNotificationAttached());
            assertThat(mShadowService.getLastForegroundNotificationId(),
                    is(CALL_IN_PROGRESS_NOTIFICATION_ID));
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

    /** Test the tasks' lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class TaskRunningLifecycle extends Base {
        @Mock
        TelephonyUtils mTelephonyUtilsMock;

        @Override
        @SuppressWarnings("UnnecessaryAssignment") // use mocked TelephonyUtils for service
        public void setUp() {
            super.setUp();

            final PhoneCallEndObserverService service = mController.get();
            service.inject();
            service.mTelephonyUtils = mTelephonyUtilsMock;
        }

        @Test
        @SuppressWarnings("UnnecessaryAssignment") // use spied logger
        public void test_onStartCommand_HandleInvalidTaskAction() {
            final PhoneCallEndObserverService service = mController.get();
            service.inject();
            // Hijack logger creation to use a spied one instead to test on
            final var loggerFactory = service.mLoggerFactory;
            service.mLoggerFactory = (tag) -> spy(loggerFactory.create(tag));

            mController.create().startCommand(0, 1);

            final var actionCaptor = ArgumentCaptor.forClass(Object.class);
            verify(service.mLogger, times(1)).e(any(String.class), actionCaptor.capture());
            assertThat("SHOULD capture error log of an unhandled (empty) action.",
                    (String) actionCaptor.getValue(), is(emptyString()));
        }

        @Test
        public void test_onCallEnded_ShouldInvokeCallbackWhenPhoneCallAlreadyEnded() {
            mController.create();

            final Runnable callbackMock = mock(Runnable.class);

            onCallEnded(callbackMock, /*startId*/ 5);

            verify(callbackMock, times(1)).run();
        }

        @Test
        public void test_onCreate_onCallEnded_ShouldInvokeCallbackWhenPhoneCallEndedAfterPause() {
            mController.create();

            final Runnable callbackMock = mock(Runnable.class);

            onCallEnded(callbackMock, /*startId*/ 5);

            verify(callbackMock, times(1)).run();
        }

        @Test
        public void test_onCreate_onCallEnded_ShouldStopSelfOnPhoneCallEnded() {
            mController.create();

            final int startId = 5;
            onCallEnded(() -> {}, startId);

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat("SHOULD stop self with stopSelfResultId() and not with stopSelf().",
                    mShadowService.getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_onCreate_onCallEnded_ShouldNotStopSelfWhileStillInCall() {
            mController.create();

            when(mTelephonyUtilsMock.isInCall()).thenReturn(true);

            final int startId = 5;
            onCallEnded(() -> {}, startId);

            assertFalse(mShadowService.isStoppedBySelf());
        }

        @Test
        public void test_onCreate_onCallEnded_ShouldPostponeCallbackInvocationWhileStillInCall() {
            mController.create();

            when(mTelephonyUtilsMock.isInCall()).thenReturn(true);

            final int startId = 5;
            onCallEnded(() -> {}, startId);

            when(mTelephonyUtilsMock.isInCall()).thenReturn(false);

            // Reach the scheduled time when the service should poll the call state
            mShadowMainLooper.idleFor(Duration.ofMillis(PHONE_IN_CALL_STATE_POLL_INTERVAL_MS));

            assertTrue("SHOULD stop self when not being in call anymore.",
                    mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_onCreate_onCallEnded_ShouldStopSelfInOrderOnMultipleCalls() {
            mController.create();

            when(mTelephonyUtilsMock.isInCall()).thenReturn(true);

            onCallEnded(() -> {}, /*startId*/ 1);
            ShadowSystemClock.advanceBy(Duration.ofMillis(1));
            onCallEnded(() -> {}, /*startId*/ 2);

            when(mTelephonyUtilsMock.isInCall()).thenReturn(false);

            // Reach the scheduled time of the first callback
            mShadowMainLooper.idleFor(Duration.ofMillis(PHONE_IN_CALL_STATE_POLL_INTERVAL_MS - 1));

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(1));

            // Reach the schedule time of the second callback
            mShadowMainLooper.idleFor(Duration.ofMillis(1));

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(2));
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
        @Test
        public void test_onCreate_onCallEnded_onDestroy_LooperShouldBeDrained() {
            mController.create();
            onCallEnded(() -> {}, /*startId*/ 1);
            mController.destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }
    }

    /** Test {@link PhoneCallEndObserverService#syncSubscriptionEnabledState} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class SyncSubscriptionEnabledStateAction extends Base {
        private static final String ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE =
            "ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE";

        @Test
        public void test_WithoutDateTimeAndWithInvalidSubscriptionId() {
            final var subId = INVALID_SUBSCRIPTION_ID;
            final PhoneCallEndObserverService service =
                awaitStarted(startSyncSubscriptionEnabledStateAction(subId, null, false)).get();

            mForegroundServiceMock.verify(() ->
                    ForegroundService.syncSubscriptionEnabledState(service, subId, null, false),
                    times(1));
        }

        @Test
        public void test_WithDateTimeAndWithSubscriptionId() {
            final var subId = 2;
            final PhoneCallEndObserverService service =
                awaitStarted(startSyncSubscriptionEnabledStateAction(subId, NOW, true)).get();

            mForegroundServiceMock.verify(() ->
                    ForegroundService.syncSubscriptionEnabledState(service, subId, NOW, true),
                    times(1));
        }

        @Test
        public void test_IntentWithoutDateTimeAndWithInvalidSubscriptionId() {
            final var subId = INVALID_SUBSCRIPTION_ID;
            final Intent i = startSyncSubscriptionEnabledStateAction(subId, null, false);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            PhoneCallEndObserverService.class)));
            assertThat(i.getAction(), is(ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(nullValue()));
            assertThat(i.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, 2), is(subId));
            assertFalse(i.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, false));
        }

        @Test
        public void test_IntentWithDateTimeAndSubscriptionId() {
            final var subId = 2;
            final Intent i = startSyncSubscriptionEnabledStateAction(subId, NOW, true);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            PhoneCallEndObserverService.class)));
            assertThat(i.getAction(), is(ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertThat(i.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, INVALID_SUBSCRIPTION_ID), is(subId));
            assertTrue(i.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, true));
        }

        /**
         * Call {@link PhoneCallEndObserverService#syncSubscriptionEnabledState} and get the started
         * intent.
         */
        private Intent startSyncSubscriptionEnabledStateAction(final int subId,
                final LocalDateTime compareTime, final boolean overrideUserPreference) {

            PhoneCallEndObserverService.syncSubscriptionEnabledState(mApplicationContext, subId,
                    compareTime, overrideUserPreference);

            return mShadowApplication.getNextStartedService();
        }
    }

    /** Test {@link ForegroundService#updateNextWeeklyRepeatScheduleProcessingIter} action call. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class UpdateNextWeeklyRepeatScheduleProcessingIterAction extends Base {
        private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
            "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

        @Parameter
        public LocalDateTime mCompareTime;

        @Parameters(name = "compareTime={0}")
        public static List<?> params() {
            return Arrays.asList(null, NOW);
        }

        @Test
        public void test() {
            final PhoneCallEndObserverService service = awaitStarted(
                    startUpdateNextWeeklyRepeatScheduleProcessingIterAction(mCompareTime)).get();

            mForegroundServiceMock.verify(() ->
                    ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(service,
                        mCompareTime), times(1));
        }

        @Test
        public void test_Intent() {
            final Intent i = startUpdateNextWeeklyRepeatScheduleProcessingIterAction(mCompareTime);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            PhoneCallEndObserverService.class)));
            assertThat(i.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(Objects.toString(mCompareTime, null)));
        }

        /** Call {@link ForegroundService#updateNextWeeklyRepeatScheduleProcessingIter} and get the
         * started intent. */
        private Intent startUpdateNextWeeklyRepeatScheduleProcessingIterAction(
                final LocalDateTime compareTime) {

            PhoneCallEndObserverService
                .updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext, compareTime);

            return mShadowApplication.getNextStartedService();
        }
    }

    @Config(shadows = ShadowContextHiddenApi.class)
    static class Base extends MockitoHiltAndroidTestBase {
        final ServiceController<PhoneCallEndObserverService> mController =
            buildService(PhoneCallEndObserverService.class);

        final ShadowService mShadowService = shadowOf(mController.get());
        final ShadowLooper mShadowMainLooper = shadowOf(Looper.getMainLooper());

        @Inject
        ActivityManager mActivityManager;

        ShadowApplication mShadowApplication;

        MockedStatic<ForegroundService> mForegroundServiceMock;

        @CallSuper
        @Override
        public void setUp() {
            super.setUp();

            mShadowApplication = shadowOf((Application) mApplicationContext);

            mForegroundServiceMock = mockStatic(ForegroundService.class);
        }

        @After
        public void tearDown() {
            mForegroundServiceMock.reset();
            mForegroundServiceMock.close();
        }

        /**
         * Start a "dummy" action through the static {@link PhoneCallEndObserverService#startAction}
         * method.
         */
        static void startDummyAction(final Context context) {
            PhoneCallEndObserverService.startAction(context, new Intent("dummy"));
        }

        void startDummyAction() {
            startDummyAction(mApplicationContext);
        }

        /**
         * Post a task on the private {@link PhoneCallEndObserverService#onCallEnded} method.
         */
        void onCallEnded(final Runnable callback, final int taskId) {
            mController.get().onCallEnded(callback, taskId);
        }

        /** Synchronously await for the intent started via {@link Context#startService(Intent)}. */
        ServiceController<PhoneCallEndObserverService> awaitStarted(final Intent i) {
            final var controller = buildService(PhoneCallEndObserverService.class, i);
            final PhoneCallEndObserverService service = controller.get();

            final var startId = 1;
            controller.create().startCommand(0, startId);
            assertThat(shadowOf(service).getStopSelfResultId(), is(startId));

            return controller;
        }
    }
}
