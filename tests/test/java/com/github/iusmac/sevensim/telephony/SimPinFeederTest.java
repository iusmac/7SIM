package com.github.iusmac.sevensim.telephony;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.telephony.PinResult;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowITelephony;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerHiddenApi;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerOnSubscriptionsChangedListener;
import com.github.iusmac.sevensim.test.ShadowTelephonyManagerHiddenApi;
import com.github.iusmac.sevensim.test.TestUtils;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.hamcrest.Matcher;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import static android.telephony.TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED;
import static android.telephony.TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED;

import static com.github.iusmac.sevensim.test.TestUtils.encrypted;
import static com.github.iusmac.sevensim.test.TestUtils.invalid;

import static java.lang.Thread.State.*;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class SimPinFeederTest extends MockitoHiltAndroidTestBase {
    private static final long TASK_WAIT_TIMEOUT_MILLIS = 3_000L;
    private static final Duration TASK_WAIT_TIMEOUT_DURATION =
        Duration.ofMillis(TASK_WAIT_TIMEOUT_MILLIS);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Inject
    SubscriptionManager mSubscriptionManager;

    @Inject
    TelephonyManager mTelephonyManager;

    @Inject
    SimPinFeeder.Factory mSimPinFeederFactory;

    @Inject
    PinStorage mPinStorage;

    @Inject
    ITelephony mITelephony;

    private SimPinFeeder mTask;

    @Test(expected = AssertionError.class)
    public void test_ShouldNotAllowCreationWithCorruptedEntities() {
        final var pinEntityCorrupted = new PinEntity();
        pinEntityCorrupted.setSubscriptionId(1);
        pinEntityCorrupted.setCorrupted(true);
        createTaskWith(List.of(pinEntityCorrupted));
    }

    @Test(expected = AssertionError.class)
    public void test_ShouldNotAllowCreationWithEncryptedEntities() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            return pinEntity;
        }));
        assertThat(pinEntityEncrypted, is(encrypted()));
        createTaskWith(List.of(pinEntityEncrypted));
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldFinishFastWithoutUsablePinEntities() throws InterruptedException {
        runTaskWith(Collections.emptyList()).join();
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldFinishFastWhenPreemptivelyCancelled() throws InterruptedException {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");
        runTaskWith(List.of(pinEntity)).cancel();
        mTask.join();
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldFinishWhenPreemptivelyCancelled() throws InterruptedException {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");
        runTaskWith(List.of(pinEntity)).cancel();
        mTask.join();
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldFinishWhenCancelledWhileWaitingForSimStatusChangeEvent()
            throws InterruptedException {

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        assertTaskInState(TIMED_WAITING);
        mTask.cancel();
        mTask.join();
    }

    @Test
    public void test_ShouldNotFinishWhenInterruptedWhileWaitingForSimStatusChangeEvent() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        assertTaskInState(TIMED_WAITING);
        mTask.interrupt();
        assertTaskTimedOut();
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldUnregisterOnSimStatusChangedListenerWhenFinishedFastWithoutUsablePinEntities()
            throws InterruptedException {

        runTaskWith(Collections.emptyList()).join();

        assertThat(findCarrierConfigChangedReceiver(), is(Optional.empty()));
    }

    @Test(timeout = TASK_WAIT_TIMEOUT_MILLIS)
    public void test_ShouldUnregisterOnSimStatusChangedListenerWhenCancelledWhileWaitingForSimStatusChangeEvent()
            throws InterruptedException {

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        assertTaskInState(TIMED_WAITING);
        mTask.cancel();
        mTask.join();

        assertThat(findCarrierConfigChangedReceiver(), is(Optional.empty()));
    }

    @Test
    public void test_ShouldFinishAfterExceedingFixedWaitTimeoutForSimStatusChangeEvent() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        assertTaskInState(TIMED_WAITING);
        assertTaskTimedOut();
    }

    @Test
    @Config(shadows = ShadowSubscriptionManagerOnSubscriptionsChangedListener.class)
    public void test_ShouldFinishAfterExceedingFixedWaitTimeoutForSimStatusChangeEventWhenAvailableSimCardsDoNotRequirePin() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        assertTaskInState(TIMED_WAITING);
        assertTaskTimedOut();
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldNotUnlockWhenAttemptsRemainingIsLessThanThree() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 2);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've not
        // been unlocked because of too low attempts remaining, the PinEntity should've been
        // consumed and the task should be terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, never()).supplyIccLockPin(pinEntity.getClearPin());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S, shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldUnlockWhenAttemptsRemainingIsAtLeastThree_SinceS() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // unlocked with it, the PinEntity should've been consumed and the task should be terminated
        // at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity.getClearPin());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R, shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    @SuppressWarnings("deprecation")
    public void test_ShouldUnlockWhenAttemptsRemainingIsAtLeastThree_R() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyPinReportResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // unlocked with it, the PinEntity should've been consumed and the task should be terminated
        // at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyPinReportResult(pinEntity.getClearPin());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R, shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    @SuppressWarnings("deprecation")
    public void test_ShouldNotUnlockOnPinAttemptsRemainingRetrievalFailure_R() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card shouldn't have
        // been unlocked with it, the PinEntity should've been consumed and the task should be
        // terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, never()).supplyPinReportResult(pinEntity.getClearPin());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q, shadows = {
        ShadowITelephony.class,
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldUnlockWhenAttemptsRemainingIsAtLeastThree_Q() throws RemoteException {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyPinReportResultForSubscriber(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // unlocked with it, the PinEntity should've been consumed and the task should be terminated
        // at this point
        assertTaskInState(TERMINATED);
        verify(mITelephony, times(1)).supplyPinReportResultForSubscriber(
                subInfo.getSubscriptionId(), pinEntity.getClearPin());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q, shadows = {
        ShadowITelephony.class,
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldNotUnlockOnPinAttemptsRemainingRetrievalFailure_Q()
            throws RemoteException {

        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card shouldn't have
        // been unlocked with it, the PinEntity should've been consumed and the task should be
        // terminated at this point
        assertTaskInState(TERMINATED);
        verify(mITelephony, never()).supplyPinReportResultForSubscriber(subInfo.getSubscriptionId(),
                pinEntity.getClearPin());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q, shadows = {
        ShadowITelephony.class,
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldRetryUnlockOnRemoteException_Q() throws RemoteException {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyPinReportResultForSubscriber(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        doAnswer((invocation) -> {
            throw new RemoteException();
        }).when(mITelephony).supplyPinReportResultForSubscriber(subInfo.getSubscriptionId(),
            pinEntity.getClearPin());

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // attempted to be unlocked with it for exactly 3 times, the PinEntity should've been
        // consumed and the task should be terminated at this point
        assertTaskInState(TERMINATED);
        verify(mITelephony, times(3)).supplyPinReportResultForSubscriber(
                subInfo.getSubscriptionId(), pinEntity.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldUnflagPinEntityWhenIsNotInvalidAnymore() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntityPersisted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
            pinEntity.setInvalid(true);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            mPinStorage.storePin(pinEntity);
            assertTrue(mPinStorage.decrypt(pinEntity));
            return pinEntity;
        }));
        assertThat(pinEntityPersisted.getId(), is(greaterThan(0L)));
        assertThat(pinEntityPersisted, is(not(encrypted())));

        runTaskWith(List.of(pinEntityPersisted));
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntityPersisted.getClearPin());

        final var pinEntityUpdated = assertFutureDone(EXECUTOR.submit(() ->
                    mPinStorage.getPin(pinEntityPersisted.getSubscriptionId())));
        assertThat(pinEntityUpdated, is(not(Optional.empty())));
        assertThat(pinEntityUpdated.get(), is(not(invalid())));
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldIgnoreDisabledSimSubscriptions() {
        Shadow.<ShadowSubscriptionManagerHiddenApi>extract(mSubscriptionManager)
            .setCanDisablePhysicalSubscription(true);

        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo1, true);

        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo2, true);

        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1, subInfo2);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo1.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo2.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity1 = new PinEntity();
        pinEntity1.setSubscriptionId(subInfo1.getSubscriptionId());
        pinEntity1.setClearPin("1234");
        final var pinEntity2 = new PinEntity();
        pinEntity2.setSubscriptionId(subInfo2.getSubscriptionId());
        pinEntity2.setClearPin("5678");

        doAnswer((invocation) -> {
            // Simulate the SIM subscription had a state change (e.g., disabled by the scheduler)
            // while supplying PIN for the first SIM card
            mSubscriptionManager.setUiccApplicationsEnabled(subInfo2.getSubscriptionId(), false);
            shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_NOT_READY);
            return invocation.callRealMethod();
        }).when(mTelephonyManager).supplyIccLockPin(pinEntity1.getClearPin());

        runTaskWith(List.of(pinEntity1, pinEntity2));
        assertTaskInState(TIMED_WAITING);
        sendSimCardStateChanged(subInfo2.getSimSlotIndex(),
                TelephonyManager.SIM_STATE_NOT_READY);
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity1.getClearPin());
        verify(mTelephonyManager, never()).supplyIccLockPin(pinEntity2.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldFinishWhenConsumedAllPinEntities() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1, subInfo2);
        shadowOf(mTelephonyManager).setActiveModemCount(2);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo1.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo2.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity1 = new PinEntity();
        pinEntity1.setSubscriptionId(subInfo1.getSubscriptionId());
        pinEntity1.setClearPin("1234");

        runTaskWith(List.of(pinEntity1));
        // Since we have 2 SIM cards that require a PIN but only one PinEntity was provided, so
        // after unlocking the first SIM card, it should've been consumed and the task should be
        // terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity1.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldNotUnlockRemainingSimCardsWhenCancelledWhileUnlocking() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1, subInfo2);
        shadowOf(mTelephonyManager).setActiveModemCount(2);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo1.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo2.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity1 = new PinEntity();
        pinEntity1.setSubscriptionId(subInfo1.getSubscriptionId());
        pinEntity1.setClearPin("1234");
        final var pinEntity2 = new PinEntity();
        pinEntity2.setSubscriptionId(subInfo2.getSubscriptionId());
        pinEntity2.setClearPin("5678");

        // Simulate some long work when supplying PIN for SIM card 1, so that we have time to cancel
        // the task in order to prevent supplying the PIN for the remaining SIM card
        final var supplyPin1Delay = Duration.ofMillis(1_500L);
        doAnswer((invocation) -> {
            try {
                Thread.sleep(supplyPin1Delay.toMillis());
            } catch (InterruptedException ignored) { /* @SuppressWarnings("EmptyCatch") */ }
            return invocation.callRealMethod();
        }).when(mTelephonyManager).supplyIccLockPin(pinEntity1.getClearPin());

        runTaskWith(List.of(pinEntity1, pinEntity2));
        assertTaskInState(TIMED_WAITING);
        mTask.cancel();
        assertTaskInState(TERMINATED);

        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity1.getClearPin());
        verify(mTelephonyManager, never()).supplyIccLockPin(pinEntity2.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldRetryUnlockOnPinResultFailure() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_FAILURE, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // attempted to be unlocked with it for exactly 3 times, the PinEntity should've been
        // consumed and the task should be terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(3)).supplyIccLockPin(pinEntity.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldDelegateHandleBadPinEntityToPinStorageWhenIncorrectPin() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_INCORRECT, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // attempted to be unlocked with it for exactly 1 time, the PinEntity should've been
        // consumed and the task should be terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity.getClearPin());
        verify(mPinStorage, times(1)).handleBadPinEntity(pinEntity);

        await()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(1))
            .until(() -> pinEntity.isInvalid());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldNotifyUserWhenUnlockFailed() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_FAILURE, 3);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setClearPin("1234");

        runTaskWith(List.of(pinEntity));
        // Since this was the only PinEntity provided and the corresponding SIM card should've been
        // attempted to be unlocked with it for exactly 3 times, the PinEntity should've been
        // consumed and the task should be terminated at this point
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(3)).supplyIccLockPin(pinEntity.getClearPin());
        assertLastNotificationTitle(containsString(mApplicationContext
                    .getString(R.string.sim_pin_operation_failed)));
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldWaitSimCardEnterIntoPinStateToUnlock() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        // Simulate only one subscription is visible in the system for now
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1);
        shadowOf(mTelephonyManager).setActiveModemCount(2);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo1.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo2.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity1 = new PinEntity();
        pinEntity1.setSubscriptionId(subInfo1.getSubscriptionId());
        pinEntity1.setClearPin("1234");
        final var pinEntity2 = new PinEntity();
        pinEntity2.setSubscriptionId(subInfo2.getSubscriptionId());
        pinEntity2.setClearPin("5678");

        runTaskWith(List.of(pinEntity1, pinEntity2));
        // Since only the first SIM card should've been unlocked, there's another PinEntity to
        // consume, but because the corresponding SIM card is not visible yet, the task should start
        // listening for the SIM state change events
        assertTaskInState(TIMED_WAITING);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity1.getClearPin());
        verify(mTelephonyManager, never()).supplyIccLockPin(pinEntity2.getClearPin());

        // Make appear the second subscription in the system
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1, subInfo2);

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(TASK_WAIT_TIMEOUT_DURATION)
            .pollInterval(Duration.ofMillis(50))
            .until(() -> {
                if (mTask.getState() == TIMED_WAITING) {
                    sendSimCardStateChanged(subInfo2.getSimSlotIndex(),
                            TelephonyManager.SIM_STATE_PIN_REQUIRED);
                }
                return mTask.getState() == TERMINATED;
            });
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity2.getClearPin());
    }

    @Test
    @Config(shadows = {
        ShadowTelephonyManagerHiddenApi.class,
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
    })
    public void test_ShouldNotWaitForSimStatusChangeEventWhenAlreadyChangedWhileUnlocking() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        // Simulate only one subscription is visible in the system for now
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1);
        shadowOf(mTelephonyManager).setActiveModemCount(2);

        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo1.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager)
            .setTelephonyManagerForSubscriptionId(subInfo2.getSubscriptionId(), mTelephonyManager);
        shadowOf(mTelephonyManager).setSimState(TelephonyManager.SIM_STATE_PIN_REQUIRED);

        setSupplyIccLockPinResult(PinResult.PIN_RESULT_TYPE_SUCCESS, 3);

        final var pinEntity1 = new PinEntity();
        pinEntity1.setSubscriptionId(subInfo1.getSubscriptionId());
        pinEntity1.setClearPin("1234");
        final var pinEntity2 = new PinEntity();
        pinEntity2.setSubscriptionId(subInfo2.getSubscriptionId());
        pinEntity2.setClearPin("5678");

        doAnswer((invocation) -> {
            // Make appear the second subscription in the system and enter into a short timed
            // waiting state to allow us to send a SIM status change event from the main thread
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo1, subInfo2);
            Thread.sleep(500L);
            return invocation.callRealMethod();
        }).when(mTelephonyManager).supplyIccLockPin(pinEntity1.getClearPin());

        runTaskWith(List.of(pinEntity1, pinEntity2));
        assertTaskInState(TIMED_WAITING);
        sendSimCardStateChanged(subInfo2.getSimSlotIndex(),
                TelephonyManager.SIM_STATE_PIN_REQUIRED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity1.getClearPin());
        verify(mTelephonyManager, never()).supplyIccLockPin(pinEntity2.getClearPin());
        assertTaskInState(TERMINATED);
        verify(mTelephonyManager, times(1)).supplyIccLockPin(pinEntity2.getClearPin());
    }

    private SimPinFeeder createTaskWith(final List<PinEntity> decryptedPinEntities) {
        return mTask = mSimPinFeederFactory.create(TASK_WAIT_TIMEOUT_DURATION,
                decryptedPinEntities);
    }

    private SimPinFeeder runTaskWith(final List<PinEntity> decryptedPinEntities) {
        createTaskWith(decryptedPinEntities).start();
        return mTask;
    }

    private void setSupplyIccLockPinResult(final @PinResult.PinResultType int result,
            int attemptsRemaining) {

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSupplyIccLockPinPinResult(new PinResult(result, attemptsRemaining));
    }

    private void setSupplyPinReportResult(final @PinResult.PinResultType int result,
            int attemptsRemaining) {

        Shadow.<ShadowTelephonyManagerHiddenApi>extract(mTelephonyManager)
            .setSupplyPinReportResult(new int[] { result, attemptsRemaining });
    }

    private void setSupplyPinReportResultForSubscriber(final @PinResult.PinResultType int result,
            int attemptsRemaining) {

        ShadowITelephony.asShadowInterface(mITelephony)
            .setSupplyPinReportResultForSubscriber(new int[] { result, attemptsRemaining });
    }

    private Optional<BroadcastReceiver> findCarrierConfigChangedReceiver() {
        return shadowOf((Application) mApplicationContext).getRegisteredReceivers().stream()
            .filter((wrapper) -> wrapper.intentFilter != null &&
                    wrapper.intentFilter.hasAction(ACTION_SIM_CARD_STATE_CHANGED)
                    && wrapper.intentFilter.hasAction(ACTION_SIM_APPLICATION_STATE_CHANGED))
            .map((wrapper) -> wrapper.broadcastReceiver)
            .findFirst();
    }

    private void assertLastNotificationTitle(final Matcher<String> matcher) {
        final var shadowNotificationManager = shadowOf(mApplicationContext
                .getSystemService(NotificationManager.class));
        final var notif = shadowNotificationManager.getAllNotifications().stream().findFirst();
        assertThat(notif, is(not(Optional.empty())));
        final var title = notif.get().extras.getString(Notification.EXTRA_TITLE);
        assertThat(title, matcher);
    }

    private void sendSimCardStateChanged(final int slotIndex, final int state) {
        mApplicationContext.sendBroadcast(new Intent(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED)
                .putExtra(PhoneConstants.SLOT_KEY, slotIndex)
                .putExtra(TelephonyManager.EXTRA_SIM_STATE, state));
        shadowOf(mApplicationContext.getMainLooper()).idle();
    }

    private void assertTaskInState(final Thread.State state) {
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(TASK_WAIT_TIMEOUT_DURATION)
            .pollInterval(Duration.ofMillis(50))
            .until(() -> mTask.getState() == state);
    }

    private void assertTaskTimedOut() {
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(TASK_WAIT_TIMEOUT_DURATION.plusMillis(250L))
            .atLeast(Duration.ofSeconds(3))
            .until(() -> mTask.getState() == TERMINATED);
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
