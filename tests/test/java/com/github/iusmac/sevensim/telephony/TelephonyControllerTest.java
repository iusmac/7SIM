package com.github.iusmac.sevensim.telephony;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.PhoneConstants;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowTelephonyManagerHiddenApi;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;
import org.robolectric.shadows.ShadowToast;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class TelephonyControllerTest extends MockitoHiltAndroidTestBase {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    @Inject
    SubscriptionManager mSubscriptionManager;

    @Inject
    TelephonyManager mTelephonyManager;

    @Inject
    TelephonyController mTelephonyController;

    @Inject
    SubscriptionsImplLegacy mSubscriptions;

    @Test
    public void test_setSimState_ShouldAbortPreemptivelyWhenEnablingInAirplaneMode() {
        Settings.Global.putInt(mApplicationContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);

        assertFutureDone(EXECUTOR.submit(() -> mTelephonyController.setSimState(0, true, false)));

        verify(mTelephonyManager, never())
            .setSimPowerStateForSlot(anyInt(), anyInt(), any(), any());
    }

    @Test
    public void test_setSimState_ShouldShowToastMessageWhenEnablingInAirplaneMode() {
        Settings.Global.putInt(mApplicationContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);

        assertFutureDone(EXECUTOR.submit(() -> mTelephonyController.setSimState(0, true, false)));

        shadowOf(Looper.getMainLooper()).runOneTask();
        assertThat(ShadowToast.getTextOfLatestToast(),
                is(mApplicationContext.getString(R.string.airplane_mode_enabled)));
    }

    @Test
    public void test_setSimState_ShouldUnconditionallyNotifyListenerWhenEnablingInAirplaneMode() {
        Settings.Global.putInt(mApplicationContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);

        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(0, true, false);
            return result[0];
        }));
        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    public void test_setSimState_ShouldNotPreacquireBlockSyncFlagWhenEnablingInAirplaneMode() {
        Settings.Global.putInt(mApplicationContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);

        final boolean blockSyncFlagWasPreacquired = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(0, true, false);
            return mSubscriptions.mBlockSubscriptionsSyncFlag.get();
        }));
        assertFalse(blockSyncFlagWasPreacquired);
    }

    @Test
    public void test_setSimState_ShouldUnconditionallyNotifyListenerOnNonexistentSubscription() {
        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(0, true, false);
            return result[0];
        }));
        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    public void test_setSimState_ShouldNotPreacquireBlockSyncFlagOnNonexistentSubscription() {
        final boolean blockSyncFlagWasPreacquired = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(0, true, false);
            return mSubscriptions.mBlockSubscriptionsSyncFlag.get();
        }));
        assertFalse(blockSyncFlagWasPreacquired);
    }

    @Test
    public void test_setSimState_ShouldAbortPreemptivelyOnNonexistentSubscription() {
        assertFutureDone(EXECUTOR.submit(() -> mTelephonyController.setSimState(0, true, false)));

        verify(mTelephonyManager, never())
            .setSimPowerStateForSlot(anyInt(), anyInt(), any(), any());
    }

    @Test
    public void test_setSimState_ShouldAbortPreemptivelyWhenAlreadyInState() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        assertFutureDone(EXECUTOR.submit(() ->
                    mTelephonyController.setSimState(subInfo.getSimSlotIndex(), true, false)));

        verify(mTelephonyManager, never())
            .setSimPowerStateForSlot(anyInt(), anyInt(), any(), any());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldPreacquireBlockSyncFlag() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var result = new boolean[1];
        doAnswer((invocation) -> {
            result[0] = mSubscriptions.mBlockSubscriptionsSyncFlag.get();
            return invocation.callRealMethod();
        }).when(mTelephonyManager).setSimPowerStateForSlot(anyInt(), anyInt(), any(), any());

        final boolean blockSyncFlagWasPreacquired = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false);
            return result[0];
        }));

        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                eq(TelephonyManager.CARD_POWER_DOWN), any(), any());

        assertTrue(blockSyncFlagWasPreacquired);
    }

    @Test
    @Config(shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldReleaseBlockSyncFlagOnCompletion() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        assertFutureDone(EXECUTOR.submit(() ->
            mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false)));

        assertFalse(mSubscriptions.mBlockSubscriptionsSyncFlag.get());
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored") // in mock callbacks
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldPersistSubscriptionBeforeEnabling() {
        final var now = LocalDateTime.of(2007, 1, 1, 13, 0);
        final var disabledSub = new Subscription();
        disabledSub.setId(1);
        disabledSub.setSlotIndex(0);
        disabledSub.setSimState(SimState.DISABLED);
        disabledSub.setLastActivatedTime(LocalDateTime.MIN);
        disabledSub.setLastDeactivatedTime(now);
        disabledSub.keepDisabledAcrossBoots(true);

        // Persist as DISABLED to trigger restoring when looking for this subscription
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(disabledSub)));

        assertFutureDone(EXECUTOR.submit(() -> {
            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now);
                mTelephonyController.setSimState(disabledSub.getSlotIndex(), true,
                        !disabledSub.getKeepDisabledAcrossBoots());
            }
        }));

        // Assume SIM subscription appeared in the system after being enabled
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(disabledSub.getId())
            .setSimSlotIndex(disabledSub.getSlotIndex())
            .setIconTint(disabledSub.getIconTint())
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSimSlotIndex(subInfo.getSimSlotIndex())));

        final var expectedSub = new Subscription();
        expectedSub.setId(subInfo.getSubscriptionId());
        expectedSub.setSlotIndex(subInfo.getSimSlotIndex());
        expectedSub.setSimState(SimState.ENABLED);
        expectedSub.setIconTint(subInfo.getIconTint());
        expectedSub.setLastActivatedTime(now);
        expectedSub.setLastDeactivatedTime(LocalDateTime.MIN);
        expectedSub.keepDisabledAcrossBoots(!disabledSub.getKeepDisabledAcrossBoots());

        assertThat(future.get(), is(expectedSub));
    }

    @Test
    @SuppressWarnings("ReturnValueIgnored") // in mock callbacks
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldPersistSubscriptionBeforeDisabling() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .setIconTint(Color.GREEN)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var now = LocalDateTime.of(2007, 1, 1, 13, 0);
        final var enabledSub = new Subscription();
        enabledSub.setId(subInfo.getSubscriptionId());
        enabledSub.setSlotIndex(subInfo.getSimSlotIndex());
        enabledSub.setIconTint(subInfo.getIconTint());
        enabledSub.setLastActivatedTime(now);
        enabledSub.setLastDeactivatedTime(LocalDateTime.MIN);
        enabledSub.keepDisabledAcrossBoots(false);

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(enabledSub)));

        assertFutureDone(EXECUTOR.submit(() -> {
            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now);
                mTelephonyController.setSimState(enabledSub.getSlotIndex(), false,
                        !enabledSub.getKeepDisabledAcrossBoots());
            }
        }));

        // Assume SIM subscription disappeared from the system after being disabled
        setAvailableSubscriptionInfos((SubscriptionInfo[]) null);

        final var future = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSimSlotIndex(subInfo.getSimSlotIndex())));

        final var expectedSub = new Subscription();
        expectedSub.setId(subInfo.getSubscriptionId());
        expectedSub.setSlotIndex(subInfo.getSimSlotIndex());
        expectedSub.setSimState(SimState.DISABLED);
        expectedSub.setIconTint(subInfo.getIconTint());
        expectedSub.setLastActivatedTime(LocalDateTime.MIN);
        expectedSub.setLastDeactivatedTime(now);
        expectedSub.keepDisabledAcrossBoots(!enabledSub.getKeepDisabledAcrossBoots());

        assertThat(future.get(), is(expectedSub));
    }

    @Test
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldRevertPreemptivelyPersistedSubscriptionWhenTimedOut()
            throws ExecutionException, InterruptedException {

        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .setIconTint(Color.GREEN)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false);
            return mSubscriptions.getSubscriptionForSimSlotIndex(subInfo.getSimSlotIndex()).get();
        });

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .atLeast(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);

        final var expectedSub = new Subscription();
        expectedSub.setId(subInfo.getSubscriptionId());
        expectedSub.setSlotIndex(subInfo.getSimSlotIndex());
        expectedSub.setSimState(SimState.ENABLED);
        expectedSub.setIconTint(subInfo.getIconTint());

        assertThat(future.get(), is(expectedSub));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldRevertPreemptivelyPersistedSubscriptionOnRequestFailure()
            throws ExecutionException, InterruptedException {

        final var disabledSub = new Subscription();
        disabledSub.setId(1);
        disabledSub.setSlotIndex(0);
        disabledSub.setSimState(SimState.DISABLED);
        disabledSub.keepDisabledAcrossBoots(true);

        // Persist as DISABLED to trigger restoring when looking for this subscription
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(disabledSub)));

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSimPowerStateResult(TelephonyManager.SET_SIM_POWER_STATE_SIM_ERROR);

        final var future = EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(disabledSub.getSlotIndex(), true, false);
            return mSubscriptions.getSubscriptionForSimSlotIndex(disabledSub.getSlotIndex()).get();
        });

        final var expectedSub = new Subscription();
        expectedSub.setId(disabledSub.getId());
        expectedSub.setSlotIndex(disabledSub.getSlotIndex());
        expectedSub.setSimState(SimState.DISABLED);
        expectedSub.keepDisabledAcrossBoots(disabledSub.getKeepDisabledAcrossBoots());

        assertThat(future.get(), is(expectedSub));
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldTimeoutAndNotRevertStateWhenEnabling() throws ExecutionException,
           InterruptedException {

        final var disabledSub = new Subscription();
        disabledSub.setId(1);
        disabledSub.setSlotIndex(0);
        disabledSub.setSimState(SimState.DISABLED);

        // Persist as DISABLED to trigger restoring when looking for this subscription
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(disabledSub)));

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(disabledSub.getSlotIndex(), true, false));

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .atLeast(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);

        future.get();

        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(disabledSub.getSlotIndex()),
                anyInt());
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldRevertStateWhenTimedOut()
            throws ExecutionException, InterruptedException {

        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .atLeast(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);

        future.get();

        final var powerStateCaptor = ArgumentCaptor.forClass(int.class);
        verify(mTelephonyManager, times(2)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                powerStateCaptor.capture());

        assertThat(powerStateCaptor.getAllValues(), is(contains(TelephonyManager.CARD_POWER_DOWN,
                        TelephonyManager.CARD_POWER_UP)));
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldRevertStateOnRequestFailure() throws ExecutionException,
           InterruptedException {

        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        // Wait for the SIM power state change request to be reached
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));

        // We want to wait for the ACTION_SIM_CARD_STATE_CHANGED to be received and processed within
        // 3 seconds after performing SIM power state change request, otherwise we'll enter the
        // timeout logic, which is not what we want and means something went wrong
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(100))
            .until(() -> {
                if (!future.isDone()) {
                    sendSimCardStateChanged(subInfo.getSimSlotIndex(),
                            TelephonyManager.SIM_STATE_CARD_IO_ERROR);
                }
                return future.isDone();
            });

        future.get();

        final var powerStateCaptor = ArgumentCaptor.forClass(int.class);
        verify(mTelephonyManager, times(2)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                powerStateCaptor.capture());

        assertThat(powerStateCaptor.getAllValues(), is(contains(TelephonyManager.CARD_POWER_DOWN,
                        TelephonyManager.CARD_POWER_UP)));
    }

    @Test
    @Config(shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldShowToastMessageOnRequestFailure() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final int resCode = TelephonyManager.SET_SIM_POWER_STATE_NOT_SUPPORTED;
        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSimPowerStateResult(resCode);

        assertFutureDone(EXECUTOR.submit(() ->
                    mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false)));

        shadowOf(Looper.getMainLooper()).runOneTask();
        assertThat(ShadowToast.getTextOfLatestToast(), is(mApplicationContext.getString(
                        R.string.sim_state_change_request_failed, resCode)));
    }

    @Test
    @Config(shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldUnconditionallyNotifyListenerOnRequestFailure() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSimPowerStateResult(TelephonyManager.SET_SIM_POWER_STATE_NOT_SUPPORTED);

        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false);
            return result[0];
        }));

        // Ensure the state was reverted as well
        verify(mTelephonyManager, times(2)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt(), any(), any());

        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldUnconditionallyNotifyListenerWhenEnabling() {
        final var disabledSub = new Subscription();
        disabledSub.setId(1);
        disabledSub.setSlotIndex(0);
        disabledSub.setSimState(SimState.DISABLED);

        // Persist as DISABLED to trigger restoring when looking for this subscription
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(disabledSub)));

        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(disabledSub.getSlotIndex(), true, false);
            return result[0];
        }));

        // Ensure state was not reverted
        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(disabledSub.getSlotIndex()),
                anyInt(), any(), any());

        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    @Config(shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldUnconditionallyNotifyListenerWhenAlreadyInState() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSimPowerStateResult(TelephonyManager.SET_SIM_POWER_STATE_ALREADY_IN_STATE);

        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false);
            return result[0];
        }));

        // Ensure state was not reverted
        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt(), any(), any());

        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    @Config(shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldNotRevertPreemptivelySetStateOnUnexpectedResultCode() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSimPowerStateResult(Integer.MAX_VALUE);

        assertFutureDone(EXECUTOR.submit(() ->
                    mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false)));

        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt(), any(), any());
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldAssumeStateChangedOnAcquireWaitInterrupted() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        // Wait for the SIM power state change request to be reached
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));

        // Interrupt the thread that is currently waiting for a SIM power state change event within
        // 3 seconds
        assertTrue(future.cancel(true));

        // Observe the subscription remaining in the wanted state (DISABLED) even after a 3-second
        // delay after cancellation, so that we can ensure that a timeout has not occurred, which on
        // the other hand would consider the request as a failure and revert the state
        await()
            .dontCatchUncaughtExceptions()
            .timeout(Duration.ofSeconds(5))
            .atLeast(Duration.ofSeconds(1))
            .pollDelay(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldNotRevertPreemptivelySetStateOnSpuriousThreadWakeup() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        // Wait for the SIM power state change request to be reached
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));

        // Simulate a spurious thread wakeup for 3 times that is currently waiting for a SIM power
        // state change event
        for (int i = 0; i < 3; i++) {
            synchronized (mTelephonyController.mRequestMetadata) {
                mTelephonyController.mRequestMetadata.notifyAll();
            }
        }

        assertFalse(future.isDone());

        sendSimCardStateChanged(subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_ABSENT);

        // Check whether the subscription remained in the wanted state (DISABLED) upon completion,
        // so that we can ensure that a timeout has not occurred due to a spurious thread wakeup,
        // which on the other hand would consider the request as a failure and revert the state
        assertFutureDone(future);
        verify(mTelephonyManager, times(1)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt());
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldFilterOutAllIrrelevantSimStateChangeEvents() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        // Wait for the SIM power state change request to be reached
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));

        sendSimCardStateChanged(subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_UNKNOWN);
        sendSimCardStateChanged(subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_PIN_REQUIRED);
        sendSimCardStateChanged(subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_PUK_REQUIRED);

        assertFutureDone(future);

        // Ensure the state was reverted after timeout is reached, since we only sent irrelevant SIM
        // state change events
        verify(mTelephonyManager, times(2)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt());
    }

    @Test
    @SuppressWarnings("deprecation")
    @Config(maxSdk = Build.VERSION_CODES.R, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldFilterOutConcurrentSimStateChangeEvents() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var future = EXECUTOR.submit(() ->
                mTelephonyController.setSimState(subInfo.getSimSlotIndex(), false, false));

        // Wait for the SIM power state change request to be reached
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mTelephonyManager, times(1))
                    .setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()), anyInt()));

        sendSimCardStateChanged(subInfo.getSimSlotIndex() + 1, TelephonyManager.SIM_STATE_PRESENT);
        sendSimCardStateChanged(subInfo.getSimSlotIndex() + 1, TelephonyManager.SIM_STATE_PRESENT);
        sendSimCardStateChanged(subInfo.getSimSlotIndex() + 1, TelephonyManager.SIM_STATE_PRESENT);

        assertFutureDone(future);

        // Ensure the state was reverted after timeout is reached, since we only sent SIM
        // state change events for a different SIM card
        verify(mTelephonyManager, times(2)).setSimPowerStateForSlot(eq(subInfo.getSimSlotIndex()),
                anyInt());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S, shadows = ShadowTelephonyManagerHiddenApi.class)
    public void test_setSimState_ShouldExecuteParallelRequestsInOrderedSequence() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .setIconTint(Color.GREEN)
            .setDisplayName("Personal")
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .setIconTint(Color.GRAY)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo1, subInfo2);
        shadowOf(mTelephonyManager).setActiveModemCount(2);

        final var latch = new CountDownLatch(1);
        final var slotIndexCaptor = ArgumentCaptor.forClass(int.class);
        final var powerStateCaptor = ArgumentCaptor.forClass(int.class);
        doAnswer((invocation) -> {
            // Allow the second request to begin
            latch.countDown();
            invocation.callRealMethod();
            return null;
        }).when(mTelephonyManager).setSimPowerStateForSlot(slotIndexCaptor.capture(),
                powerStateCaptor.capture(), any(), any());

        final var now1 = LocalDateTime.of(2007, 1, 1, 13, 0);
        @SuppressWarnings("ReturnValueIgnored") // in mock callback
        final var future1 = EXECUTOR.submit(() -> {
            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now1);
                mTelephonyController.setSimState(subInfo1.getSimSlotIndex(), false, false);
            }
        });

        final var now2 = now1.plusSeconds(1);
        @SuppressWarnings("ReturnValueIgnored") // in mock callback
        final var future2 = EXECUTOR.submit(() -> {
            try {
                // Ensure we continue only after the first request has started
                latch.await();
            } catch (InterruptedException e) { /* @SuppressWarnings("EmptyCatch") */ }

            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now2);
                mTelephonyController.setSimState(subInfo2.getSimSlotIndex(), false, true);
            }
        });

        assertFutureDone(future1);
        assertFutureDone(future2);

        // Assume all SIM subscriptions disappeared from the system after being disabled
        setAvailableSubscriptionInfos((SubscriptionInfo[]) null);

        assertThat(slotIndexCaptor.getAllValues(), is(contains(subInfo1.getSimSlotIndex(),
                        subInfo2.getSimSlotIndex())));
        assertThat(powerStateCaptor.getAllValues(),
                everyItem(is(TelephonyManager.CARD_POWER_DOWN)));

        final var expectedSub1 = new Subscription();
        expectedSub1.setId(subInfo1.getSubscriptionId());
        expectedSub1.setSlotIndex(subInfo1.getSimSlotIndex());
        expectedSub1.setSimState(SimState.DISABLED);
        expectedSub1.setSimName(subInfo1.getDisplayName().toString());
        expectedSub1.setIconTint(subInfo1.getIconTint());
        expectedSub1.setLastActivatedTime(LocalDateTime.MIN);
        expectedSub1.setLastDeactivatedTime(now1);
        expectedSub1.keepDisabledAcrossBoots(false);

        final var actualSub1 = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(subInfo1.getSimSlotIndex())));
        assertThat(actualSub1.get(), is(expectedSub1));

        final var expectedSub2 = new Subscription();
        expectedSub2.setId(subInfo2.getSubscriptionId());
        expectedSub2.setSlotIndex(subInfo2.getSimSlotIndex());
        expectedSub2.setSimState(SimState.DISABLED);
        expectedSub2.setSimName(subInfo2.getDisplayName().toString());
        expectedSub2.setIconTint(subInfo2.getIconTint());
        expectedSub2.setLastActivatedTime(LocalDateTime.MIN);
        expectedSub2.setLastDeactivatedTime(now2);
        expectedSub2.keepDisabledAcrossBoots(true);

        final var actualSub2 = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                .getSubscriptionForSimSlotIndex(subInfo2.getSimSlotIndex())));
        assertThat(actualSub2.get(), is(expectedSub2));
    }

    private void setAvailableSubscriptionInfos(final SubscriptionInfo... subInfos) {
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfos);
    }

    private void sendSimCardStateChanged(final int slotIndex, final int state) {
        mApplicationContext.sendBroadcast(new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)
                .putExtra(PhoneConstants.SLOT_KEY, slotIndex)
                .putExtra(TelephonyManager.EXTRA_SIM_STATE, state));
        shadowOf(mApplicationContext.getMainLooper()).idle();
    }

    private static <T> T assertFutureDone(final Future<T> future) {
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
