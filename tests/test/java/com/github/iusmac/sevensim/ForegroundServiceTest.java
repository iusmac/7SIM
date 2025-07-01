package com.github.iusmac.sevensim;

import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.test.ShadowContextHiddenApi;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;
import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.telephony.SimPinFeeder;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPowerManager;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowSystemClock;

import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.github.iusmac.sevensim.test.SameBundleMatcher.sameBundle;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.robolectric.Robolectric.buildService;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.setStaticField;

@RunWith(Enclosed.class)
public class ForegroundServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 8, 0);
    private static final long SERVICE_TIMEOUT_MS_DEFAULT = 3 * 60 * 1000L;

    private static final String EXTRA_TIME_KEY = "time";
    private static final String EXTRA_OVERRIDE_USER_PREFERENCE = "override_user_preference";
    private static final String EXTRA_DECRYPT_PIN_STORAGE = "decrypt_pin_storage";
    private static final String EXTRA_CLEAR_PIN_CODES = "clear_pin_codes";

    private static final Bundle CLEAR_PIN_CODES = new Bundle(3);
    static {
        CLEAR_PIN_CODES.putString("1", "12345");
        CLEAR_PIN_CODES.putString("2", "23467");
        CLEAR_PIN_CODES.putString("3", "81723");
    }

    /** Test {@link PowerManager.WakeLock} lifecycle used by the service. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class WakeLockLifecycle extends Base {
        @Override
        public void setUp() {
            super.setUp();

            setStaticField(ForegroundService.class, "sWakeLock", null);
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

        /**
         * Force expire the pre-acquired {@link PowerManager.WakeLock} used by the service.
         *
         * <p><strong>NOTE:</strong> ensure to start an action using one of the available public
         * APIs or through reflection ({@link #startDummyAction()}) before using this.
         */
        private void expireWakeLockNow() {
            ShadowSystemClock.advanceBy(Duration.ofMillis(SERVICE_TIMEOUT_MS_DEFAULT)
                    // WakeLock will be released 30 seconds later to allow the service to terminate
                    .plusSeconds(30 + 1));
        }
    }

    /** Test service's lifecycle with a timeout. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ServiceLifecycle extends Base {
        @Test
        public void test_startAction_onCreate_startCommand_ShouldSafelyTerminate() {
            final int startId = 1;
            startDummyAction();
            mController.create().startCommand(0, startId);

            awaitServiceStoppedBySelfWithinTime(mShadowService, Duration.ofSeconds(1),
                    Duration.ofMillis(50));

            assertTrue(mController.get().isServiceTerminatedSafely());
            assertThat(mShadowService.getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_startAction_onCreate_ShouldUnsafelyTerminateOnTimeout() {
            startDummyAction();
            mController.create();

            timeoutServiceNow();

            assertTrue(mShadowService.isStoppedBySelf());
            assertFalse(mController.get().isServiceTerminatedSafely());
        }

        @Test
        public void test_onStartCommand_ShouldBeTerminatedSafelyWhenRestartedWithRetryFlag() {
            final int startId = 1;
            mController.create().startCommand(Service.START_FLAG_RETRY, startId);

            awaitServiceStoppedBySelfWithinTime(mShadowService, Duration.ofSeconds(1),
                    Duration.ofMillis(50));

            assertTrue(mController.get().isServiceTerminatedSafely());
            assertThat(mShadowService.getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_onStartCommand_ShouldBeTerminatedSafelyWhenRestartedWithRedeliveryFlag() {
            final int startId = 1;
            mController.create().startCommand(Service.START_FLAG_REDELIVERY, startId);

            awaitServiceStoppedBySelfWithinTime(mShadowService, Duration.ofSeconds(1),
                    Duration.ofMillis(50));

            assertTrue(mController.get().isServiceTerminatedSafely());
            assertThat(mShadowService.getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_onStartCommand_ShouldUnsafelyTerminateOnTimeoutWhenRestartedWithRetryFlag() {
            mController.create().startCommand(Service.START_FLAG_RETRY, 1);

            timeoutServiceNow();

            assertTrue(mShadowService.isStoppedBySelf());
            assertFalse(mController.get().isServiceTerminatedSafely());
        }

        @Test
        public void test_onStartCommand_ShouldUnsafelyTerminateOnTimeoutWhenRestartedWithRedeliveryFlag() {
            mController.create().startCommand(Service.START_FLAG_REDELIVERY, 1);

            timeoutServiceNow();

            assertTrue(mShadowService.isStoppedBySelf());
            assertFalse(mController.get().isServiceTerminatedSafely());
        }
    }

    /** Test the foreground notification lifecycle. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ForegroundNotificationLifecycle extends Base {
        private static final int FOREGROUND_NOTIFICATION_ID = 1;

        @Test
        public void test_onCreate_ForegroundNotificationIsAttached() {
            // Ensure the startForeground() is called even if background is restricted
            shadowOf(mActivityManager).setBackgroundRestricted(true);

            mController.create();
            assertTrue(mShadowService.isLastForegroundNotificationAttached());
            assertThat(mShadowService.getLastForegroundNotificationId(),
                    is(FOREGROUND_NOTIFICATION_ID));
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
        public void test_onStartCommand_NoNewTasksShouldBeAcceptedAfterServiceTimeout()
            throws InterruptedException {

            final var lastStartId = 3;
            startDummyAction(); // Initialize WakeLock & service timeout
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

            // Reach the time when the service should timeout
            timeoutServiceNow();

            mController.startCommand(0, lastStartId + 1);
            Thread.sleep(250L);

            assertTrue(mShadowService.isStoppedBySelf());
            assertThat(mShadowService.getStopSelfResultId(), is(lastStartId));
        }

        @Test
        @SuppressWarnings("UnnecessaryAssignment") // use spied logger
        public void test_onStartCommand_HandleInvalidTaskAction() {
            final ForegroundService service = mController.get();
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
        @Test
        public void test_onCreate_onStartCommand_onDestroy_LooperShouldBeDrainedWhenRestartedWithRetryFlag() {
            mController.create().startCommand(Service.START_FLAG_RETRY, 1).destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }

        @Test
        public void test_onCreate_onStartCommand_onDestroy_LooperShouldBeDrainedWhenRestartedWithRedeliveryFlag() {
            mController.create().startCommand(Service.START_FLAG_REDELIVERY, 1).destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }

        @Test
        public void test_startAction_onCreate_onStartCommand_onDestroy_LooperShouldBeDrained() {
            startDummyAction();
            mController.create().startCommand(0, 1).destroy();

            assertThat(mShadowMainLooper.getNextScheduledTaskTime(), is(Duration.ZERO));
        }
    }

    /** Test {@link ForegroundService#syncAllSubscriptionsEnabledState} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class SyncAllSubscriptionsEnabledStateAction extends Base {
        private static final String ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE =
            "ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE";

        @Test
        public void test_WithoutDateTimeAndWithOverrideUserPreferenceFlagDisabled() {
            final var overrideUserPreference = false;
            awaitStarted(startSyncAllSubscriptionsEnabledStateAction(null, overrideUserPreference));

            verifyNoInteractions(mSubscriptionScheduler);
        }

        @Test
        public void test_WithDateTimeAndOverrideUserPreferenceFlagDisabled() {
            final var overrideUserPreference = false;
            awaitStarted(startSyncAllSubscriptionsEnabledStateAction(NOW, overrideUserPreference));

            verify(mSubscriptionScheduler, times(1)).syncAllSubscriptionsEnabledState(NOW,
                    overrideUserPreference);
        }

        @Test
        public void test_IntentWithoutDateTime() {
            final Intent i = startSyncAllSubscriptionsEnabledStateAction(null, false);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(nullValue()));
            assertFalse(i.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, true));
        }

        @Test
        public void test_IntentWithDateTimeAndOverrideUserPreferenceFlag() {
            final Intent i = startSyncAllSubscriptionsEnabledStateAction(NOW, true);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_SYNC_ALL_SUBSCRIPTIONS_ENABLED_STATE));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertTrue(i.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, false));
        }

        /** Call {@link ForegroundService#syncAllSubscriptionsEnabledState} and get the started
         * intent. */
        private Intent startSyncAllSubscriptionsEnabledStateAction(final LocalDateTime compareTime,
                final boolean overrideUserPreference) {

            ForegroundService.syncAllSubscriptionsEnabledState(mApplicationContext,
                    compareTime, overrideUserPreference);

            return mShadowApplication.getNextStartedService();
        }
    }

    /** Test {@link ForegroundService#syncSubscriptionEnabledState} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class SyncSubscriptionEnabledStateAction extends Base {
        private static final String ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE =
            "ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE";

        @Test
        public void test_WithoutDateTimeAndWithInvalidSubscriptionId() {
            final var subId = INVALID_SUBSCRIPTION_ID;
            awaitStarted(startSyncSubscriptionEnabledStateAction(subId, null, false));

            verifyNoInteractions(mSubscriptionScheduler);
        }

        @Test
        public void test_WithDateTimeAndWithInvalidSubscriptionId() {
            final var subId = INVALID_SUBSCRIPTION_ID;
            awaitStarted(startSyncSubscriptionEnabledStateAction(subId, NOW, false));

            verifyNoInteractions(mSubscriptionScheduler);
        }

        @Test
        public void test_WithDateTimeAndWithSubscriptionId() {
            final var subId = 2;
            awaitStarted(startSyncSubscriptionEnabledStateAction(subId, NOW, true));

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(subId, NOW, true);
        }

        @Test
        public void test_IntentWithoutDateTimeAndWithInvalidSubscriptionId() {
            final var subId = INVALID_SUBSCRIPTION_ID;
            final Intent i = startSyncSubscriptionEnabledStateAction(subId, null, false);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
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
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_SYNC_SUBSCRIPTION_ENABLED_STATE));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertThat(i.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, INVALID_SUBSCRIPTION_ID), is(subId));
            assertTrue(i.getBooleanExtra(EXTRA_OVERRIDE_USER_PREFERENCE, true));
        }

        /**
         * Call {@link ForegroundService#syncSubscriptionEnabledState} and get the started intent.
         */
        private Intent startSyncSubscriptionEnabledStateAction(final int subId,
                final LocalDateTime compareTime, final boolean overrideUserPreference) {

            ForegroundService.syncSubscriptionEnabledState(mApplicationContext, subId, compareTime,
                    overrideUserPreference);

            return mShadowApplication.getNextStartedService();
        }
    }

    /** Test {@link ForegroundService#updateNextWeeklyRepeatScheduleProcessingIter} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class UpdateNextWeeklyRepeatScheduleProcessingIterAction extends Base {
        private static final String ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER =
            "ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER";

        @Test
        public void test_WithoutDateTimeAndWithoutClearPinCodes() {
            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(null, null));

            verifyNoInteractions(mSubscriptionScheduler);
        }

        @Test
        public void test_WithDateTimeAndWithoutClearPinCodes() {
            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW, null));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(NOW, null);
        }

        @Test
        public void test_WithDateTimeAndClearPinCodesButEmptyPinStorage() {
            doNothing().when(mSubscriptionScheduler)
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW),
                        mPinEntitiesCaptor.capture());

            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW,
                        CLEAR_PIN_CODES));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW), anyList());

            assertThat("Should've called with an empty list since PinStorage has no PinEntity's.",
                    mPinEntitiesCaptor.getValue(), is(empty()));
        }

        @Test
        public void test_WithDateTimeAndClearPinCodesMatchingAllPinStorageEntities() {
            doNothing().when(mSubscriptionScheduler)
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW),
                        mPinEntitiesCaptor.capture());
            willAnswer((invocation) -> {
                invocation.callRealMethod();
                return toPinEntityList(CLEAR_PIN_CODES);
            }).given(mPinStorage).getPinEntities();

            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW,
                        CLEAR_PIN_CODES));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW), anyList());

            assertThat("Should've called with all clear PinEntity's containing in PinStorage.",
                    mPinEntitiesCaptor.getValue(), is(toPinEntityList(CLEAR_PIN_CODES, true)));
        }

        @Test
        public void test_WithDateTimeAndClearPinCodesAndMatchingAllPinStorageEntitiesExceptOne() {
            final var encryptedPinEntities = toPinEntityList(CLEAR_PIN_CODES);
            final var extraPinEntity = new PinEntity();
            extraPinEntity.setSubscriptionId(encryptedPinEntities.size() + 1);
            encryptedPinEntities.add(extraPinEntity);

            doNothing().when(mSubscriptionScheduler)
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW),
                        mPinEntitiesCaptor.capture());
            willAnswer((invocation) -> {
                invocation.callRealMethod();
                return encryptedPinEntities;
            }).given(mPinStorage).getPinEntities();

            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW,
                        CLEAR_PIN_CODES));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW), anyList());

            final var expectedDecryptedPinEntities = toPinEntityList(CLEAR_PIN_CODES, true);
            expectedDecryptedPinEntities.add(extraPinEntity);
            assertThat("Should've called with all clear PinEntity's containing in PinStorage, " +
                    "leaving the extra PinEntity encrypted.", mPinEntitiesCaptor.getValue(),
                    is(expectedDecryptedPinEntities));
        }

        @Test
        public void test_WithDateTimeAndDecryptPinStorageFlagButEmptyPinStorage() {
            doNothing().when(mSubscriptionScheduler)
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW),
                        mPinEntitiesCaptor.capture());

            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW, true));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW), anyList());

            assertThat("Should've called with an empty list since PinStorage has no PinEntity's.",
                    mPinEntitiesCaptor.getValue(), is(empty()));
        }

        @Test
        public void test_WithDateTimeAndDecryptPinStorageFlagMatchingAllPinStorageEntities() {
            doNothing().when(mSubscriptionScheduler)
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW),
                        mPinEntitiesCaptor.capture());
            willAnswer((invocation) -> {
                invocation.callRealMethod();
                return toPinEntityList(CLEAR_PIN_CODES);
            }).given(mPinStorage).getPinEntities();

            willAnswer((invocation) -> {
                invocation.callRealMethod();
                final var pinEntity = invocation.getArgument(0, PinEntity.class);
                pinEntity.setClearPin(CLEAR_PIN_CODES.getString(String.valueOf(pinEntity
                                .getSubscriptionId())));
                return true;
            }).given(mPinStorage).decrypt(any(PinEntity.class));

            awaitStarted(startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW, true));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW), anyList());

            assertThat("Should've called with all decrypted PinEntity's containing in PinStorage.",
                    mPinEntitiesCaptor.getValue(), is(toPinEntityList(CLEAR_PIN_CODES, true)));
        }

        @Test
        public void test_IntentWithDateTimeOnly() {
            final Intent i = startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertThat(i.getBundleExtra(EXTRA_CLEAR_PIN_CODES), is(nullValue()));
            assertFalse(i.getBooleanExtra(EXTRA_DECRYPT_PIN_STORAGE, true));
        }

        @Test
        public void test_IntentWithoutDateTime() {
            final Intent i = startUpdateNextWeeklyRepeatScheduleProcessingIterAction(null);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(nullValue()));
            assertThat(i.getBundleExtra(EXTRA_CLEAR_PIN_CODES), is(nullValue()));
            assertFalse(i.getBooleanExtra(EXTRA_DECRYPT_PIN_STORAGE, true));
        }

        @Test
        public void test_IntentWithDateTimeAndClearPinCodes() {
            final Intent i = startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW,
                    CLEAR_PIN_CODES);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertThat(i.getBundleExtra(EXTRA_CLEAR_PIN_CODES), sameBundle(CLEAR_PIN_CODES));
        }

        @Test
        public void test_IntentWithDateTimeAndDecryptPinStorageFlag() {
            final Intent i = startUpdateNextWeeklyRepeatScheduleProcessingIterAction(NOW, true);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(),
                    is(ACTION_UPDATE_NEXT_WEEKLY_REPEAT_SCHEDULE_PROCESSING_ITER));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
            assertTrue(i.getBooleanExtra(EXTRA_DECRYPT_PIN_STORAGE, false));
        }

        /** Call {@link ForegroundService#updateNextWeeklyRepeatScheduleProcessingIter} and get the
         * started intent. */
        private Intent startUpdateNextWeeklyRepeatScheduleProcessingIterAction(
                final LocalDateTime compareTime, final Bundle clearPinCodes) {

            ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                    compareTime, clearPinCodes);

            return mShadowApplication.getNextStartedService();
        }

        private Intent startUpdateNextWeeklyRepeatScheduleProcessingIterAction(
                final LocalDateTime compareTime, final boolean decryptPinStorage) {

            ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                    compareTime, decryptPinStorage);

            return mShadowApplication.getNextStartedService();
        }

        private Intent startUpdateNextWeeklyRepeatScheduleProcessingIterAction(
                final LocalDateTime compareTime) {

            ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                    compareTime);

            return mShadowApplication.getNextStartedService();
        }
    }

    /** Test {@link ForegroundService#onSubscriptionsChanged} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class SubscriptionsChangedAction extends Base {
        private static final String ACTION_SUBSCRIPTIONS_CHANGED = "ACTION_SUBSCRIPTIONS_CHANGED";

        @Test
        public void test_WithoutDateTime() {
            awaitStarted(startOnSubscriptionsChangedAction(null));

            verifyNoInteractions(mSubscriptions);
        }

        @Test
        public void test_WithDateTime() {
            awaitStarted(startOnSubscriptionsChangedAction(NOW));

            verify(mSubscriptions, times(1)).syncSubscriptions(NOW);
        }

        @Test
        public void test_IntentWithoutDateTime() {
            final Intent i = startOnSubscriptionsChangedAction(null);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_SUBSCRIPTIONS_CHANGED));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(nullValue()));
        }

        @Test
        public void test_IntentWithDateTime() {
            final Intent i = startOnSubscriptionsChangedAction(NOW);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_SUBSCRIPTIONS_CHANGED));
            assertThat(i.getStringExtra(EXTRA_TIME_KEY), is(NOW.toString()));
        }

        /** Call {@link ForegroundService#onSubscriptionsChanged} and get the started intent. */
        private Intent startOnSubscriptionsChangedAction(final LocalDateTime compareTime) {
            ForegroundService.onSubscriptionsChanged(mApplicationContext, compareTime);

            return mShadowApplication.getNextStartedService();
        }
    }

    /** Test {@link ForegroundService#unlockSimCards} action call. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class UnlockSimCards extends Base {
        private static final String ACTION_UNLOCK_SIM_CARDS = "ACTION_UNLOCK_SIM_CARDS";

        @Test
        public void test_WithoutClearPinCodes() {
            awaitStarted(startUnlockSimCardsAction(null));

            verifyNoInteractions(mSimPinFeederFactory);
        }

        @Test
        public void test_WithClearPinCodesButEmptyPinStorage() {
            doAnswer((invocation) -> {
                // Actually create it to run the internal sanity check on the provided PIN entities
                invocation.callRealMethod();
                return mSimPinFeederMock;
            }).when(mSimPinFeederFactory).create(mPinEntitiesCaptor.capture());

            awaitStarted(startUnlockSimCardsAction(CLEAR_PIN_CODES));

            verify(mSimPinFeederFactory, times(1)).create(anyList());

            assertThat("Should've called with an empty list since PinStorage has no PinEntity's",
                    mPinEntitiesCaptor.getValue(), is(empty()));
        }

        @Test
        public void test_WithClearPinCodesMatchingAllPinStorageEntities() {
            willAnswer((invocation) -> {
                invocation.callRealMethod();
                return toPinEntityList(CLEAR_PIN_CODES);
            }).given(mPinStorage).getPinEntities();

            doAnswer((invocation) -> {
                // Actually create it to run the internal sanity check on the provided PIN entities
                invocation.callRealMethod();
                return mSimPinFeederMock;
            }).when(mSimPinFeederFactory).create(mPinEntitiesCaptor.capture());

            awaitStarted(startUnlockSimCardsAction(CLEAR_PIN_CODES));

            verify(mSimPinFeederFactory, times(1)).create(anyList());

            assertThat("Should've called with all clear PinEntity's containing in PinStorage.",
                    mPinEntitiesCaptor.getValue(), is(toPinEntityList(CLEAR_PIN_CODES, true)));
        }

        @Test
        public void test_WithClearPinCodesMatchingAllPinStorageEntitiesIgnoredUnsableEntity() {
            final var encryptedPinEntities = toPinEntityList(CLEAR_PIN_CODES);
            final var unusablePinEntity = new PinEntity();
            unusablePinEntity.setSubscriptionId(encryptedPinEntities.size() + 1);
            encryptedPinEntities.add(unusablePinEntity);
            willAnswer((invocation) -> {
                invocation.callRealMethod();
                return encryptedPinEntities;
            }).given(mPinStorage).getPinEntities();

            doAnswer((invocation) -> {
                // Actually create it to run the internal sanity check on the provided PIN entities
                invocation.callRealMethod();
                return mSimPinFeederMock;
            }).when(mSimPinFeederFactory).create(mPinEntitiesCaptor.capture());

            awaitStarted(startUnlockSimCardsAction(CLEAR_PIN_CODES));

            verify(mSimPinFeederFactory, times(1)).create(anyList());

            final var expectedUsablePinEntities = toPinEntityList(CLEAR_PIN_CODES, true);
            assertThat("Should've called with all clear PinEntity's containing in PinStorage, " +
                    "leaving the extra PinEntity encrypted.", mPinEntitiesCaptor.getValue(),
                    is(expectedUsablePinEntities));
        }

        @Test
        @SuppressWarnings("UnnecessaryAssignment") // use spied SimPinFeeder.Factory for service
        public void test_ShouldBeCanceledOnServiceTimeout() throws InterruptedException {
            final Intent i = startUnlockSimCardsAction(CLEAR_PIN_CODES);
            final var controller = buildService(ForegroundService.class, i);
            final ForegroundService service = controller.get();
            service.inject();
            service.mSimPinFeederFactory = mSimPinFeederFactory;

            doAnswer((invocation) -> {
                // Actually create it to run the internal sanity check on the provided PIN entities
                invocation.callRealMethod();
                return mSimPinFeederMock;
            }).when(mSimPinFeederFactory).create(anyList());

            // Simulate a prolonged work on the SIM PIN Feeder task with a sleep on the Worker's
            // Thread, so that when the latter will join it we can send an interrupt signal with the
            // service timeout
            doAnswer((invocation) -> {
                Thread.sleep(Long.MAX_VALUE);
                return null;
            }).when(mSimPinFeederMock).join();

            final var startId = 1;
            controller.create().startCommand(0, startId);

            // Ensure the SIM PIN Feeder task is started
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> verify(mSimPinFeederMock, times(1)).start());

            // Reach the time when the service should time out to force the Worker to shutdown
            timeoutServiceNow();

            // Ensure the Worker shutdown has also canceled the SIM PIN Feeder task
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> verify(mSimPinFeederMock, times(1)).cancel());

            // Ensure the service was properly stopped after it has timed out
            awaitServiceStoppedBySelfWithinTime(controller.get(), Duration.ofSeconds(1),
                    Duration.ofMillis(50));
            assertThat(shadowOf(service).getStopSelfResultId(), is(startId));
        }

        @Test
        public void test_IntentWithoutClearPinCodes() {
            final Intent i = startUnlockSimCardsAction(null);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_UNLOCK_SIM_CARDS));
            assertThat(i.getBundleExtra(EXTRA_CLEAR_PIN_CODES), is(nullValue()));
        }

        @Test
        public void test_IntentWithClearPinCodes() {
            final Intent i = startUnlockSimCardsAction(CLEAR_PIN_CODES);
            assertThat(i.getComponent(), is(new ComponentName(mApplicationContext,
                            ForegroundService.class)));
            assertThat(i.getAction(), is(ACTION_UNLOCK_SIM_CARDS));
            assertThat(i.getBundleExtra(EXTRA_CLEAR_PIN_CODES), sameBundle(CLEAR_PIN_CODES));
        }

        /** Call {@link ForegroundService#unlockSimCards} and get the started intent. */
        private Intent startUnlockSimCardsAction(final Bundle clearPinCodes) {
            ForegroundService.unlockSimCards(mApplicationContext, clearPinCodes);

            return mShadowApplication.getNextStartedService();
        }
    }

    @Config(shadows = ShadowContextHiddenApi.class)
    static class Base extends MockitoHiltAndroidTestBase {
        final ServiceController<ForegroundService> mController =
            buildService(ForegroundService.class);

        final ShadowService mShadowService = shadowOf(mController.get());
        final ShadowLooper mShadowMainLooper = shadowOf(Looper.getMainLooper());

        @Inject
        SubscriptionScheduler mSubscriptionScheduler;

        @Inject
        PinStorage mPinStorage;

        @Inject
        Subscriptions mSubscriptions;

        @Mock
        SimPinFeeder mSimPinFeederMock;

        @Inject
        SimPinFeeder.Factory mSimPinFeederFactory;

        @Captor
        ArgumentCaptor<List<PinEntity>> mPinEntitiesCaptor;

        @Inject
        ActivityManager mActivityManager;

        ShadowApplication mShadowApplication;

        @CallSuper
        @Override
        @SuppressWarnings("UnnecessaryAssignment") // spy on the mSimPinFeederFactory
        public void setUp() {
            super.setUp();

            mShadowApplication = shadowOf((Application) mApplicationContext);
            mSimPinFeederFactory = spy(mSimPinFeederFactory);
        }

        /**
         * <p>Force initiate the pre-established timeout of the service.
         *
         * <p><strong>NOTE:</strong> ensure to start an action using one of the available public
         * APIs or through reflection ({@link #startDummyAction()}) before using this.
         */
        void timeoutServiceNow() {
            mShadowMainLooper.idleFor(Duration.ofMillis(SERVICE_TIMEOUT_MS_DEFAULT + 1));
        }

        /** Synchronously await for the intent started via {@link Context#startService(Intent)}. */
        @SuppressWarnings("UnnecessaryAssignment") // use spied SimPinFeeder.Factory for service
        void awaitStarted(final Intent i) {
            final var controller = buildService(ForegroundService.class, i);
            final ForegroundService service = controller.get();
            service.inject();
            service.mSimPinFeederFactory = mSimPinFeederFactory;

            final var startId = 1;
            controller.create().startCommand(0, startId);

            awaitServiceStoppedBySelfWithinTime(controller.get(), Duration.ofSeconds(2),
                    Duration.ofMillis(50));
            assertThat(shadowOf(service).getStopSelfResultId(), is(startId));
        }

        /**
         * Start a "dummy" action through the static {@link ForegroundService#startAction} method.
         */
        static void startDummyAction(final Context context) {
            ForegroundService.startAction(context, new Intent("dummy"));
        }

        void startDummyAction() {
            startDummyAction(mApplicationContext);
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

    private static void awaitServiceStoppedBySelfWithinTime(final Service service,
            final Duration atMostTime, final Duration pollIntervalTime) {

        awaitServiceStoppedBySelfWithinTime(shadowOf(service), atMostTime, pollIntervalTime);
    }

    private static List<PinEntity> toPinEntityList(final Bundle clearPinCodes,
            final boolean decrypt) {

        return clearPinCodes.keySet().stream().map((subId) -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(Integer.valueOf(subId));

            pinEntity.setData(new byte[0]);
            pinEntity.setIV(new byte[0]);

            if (decrypt) {
                pinEntity.setClearPin(clearPinCodes.getString(subId));
            }
            return pinEntity;
        }).collect(Collectors.toList());
    }

    private static List<PinEntity> toPinEntityList(final Bundle clearPinCodes) {
        return toPinEntityList(clearPinCodes, false);
    }
}
