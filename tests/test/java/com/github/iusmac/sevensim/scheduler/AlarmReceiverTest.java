package com.github.iusmac.sevensim.scheduler;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver.PendingResult;
import android.os.Bundle;
import android.os.Looper;

import com.github.iusmac.sevensim.ForegroundService;
import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;

import org.robolectric.RobolectricTestRunner;

import static com.github.iusmac.sevensim.test.SameBundleMatcher.sameBundle;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class AlarmReceiverTest extends MockitoHiltAndroidTestBase {
    private static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 1, 1);

    private static final Bundle CLEAR_PIN_CODES = new Bundle(3);
    static {
        CLEAR_PIN_CODES.putString("1", "12345");
        CLEAR_PIN_CODES.putString("2", "23467");
        CLEAR_PIN_CODES.putString("3", "81723");
    }

    private AlarmReceiver mReceiver;

    @Inject
    PinStorage mPinStorage;

    @Inject
    SubscriptionScheduler mSubscriptionScheduler;

    private MockedStatic<ForegroundService> mForegroundServiceMock;
    private MockedStatic<LocalDateTime> mLocalDateTimeMock;

    @Captor
    private ArgumentCaptor<List<PinEntity>> mPinEntitiesCaptor;

    @Inject
    Provider<ActivityManager> mActivityManagerProvider;

    @Override
    @SuppressWarnings("ReturnValueIgnored") // in mock callbacks
    public void setUp() {
        super.setUp();

        mReceiver = shadowOf((Application) mApplicationContext).getRegisteredReceivers()
            .stream()
            .filter((wrapper) -> wrapper.broadcastReceiver.getClass() == AlarmReceiver.class)
            .map((wrapper) -> (AlarmReceiver) wrapper.broadcastReceiver)
            .findFirst().get();

        mForegroundServiceMock = mockStatic(ForegroundService.class);
        mLocalDateTimeMock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
        mLocalDateTimeMock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(NOW);
    }

    @Test
    public void onReceive_ShouldScheduleAllSubscriptionsEnabledStateSync() {
        onReceive(new Intent());

        mForegroundServiceMock.verify(() ->
                ForegroundService.syncAllSubscriptionsEnabledState(any(Context.class),
                    eq(NOW), /*overrideUserPreference=*/ eq(false)), times(1));
    }

    @Test
    public void onReceive_ShouldNotScheduleUnlockingSimCardsWhenNoClearPinCodes() {
        final var i = new Intent();
        onReceive(i);

        mForegroundServiceMock.verify(() -> ForegroundService.unlockSimCards(any(Context.class),
                    any()), never());

        i.putExtras(new Bundle());
        onReceive(i);

        mForegroundServiceMock.verify(() -> ForegroundService.unlockSimCards(any(Context.class),
                    any(Bundle.class)), never());
    }

    @Test
    public void onReceive_ShouldScheduleUnlockingSimCardsWhenHavingClearPinCodes() {
        final var i = new Intent().putExtras(CLEAR_PIN_CODES);
        onReceive(i);

        final var clearPinCodesCaptor = ArgumentCaptor.forClass(Bundle.class);
        mForegroundServiceMock.verify(() -> ForegroundService.unlockSimCards(any(Context.class),
                    clearPinCodesCaptor.capture()), times(1));

        assertThat(clearPinCodesCaptor.getValue(), sameBundle(i.getExtras()));
    }

    @Test
    public void onReceive_ShouldScheduleUpdateNextWeeklyRepeatScheduleProcessingIterWhenBackgroundNotRestricted() {
        onReceive(new Intent());

        mForegroundServiceMock.verify(() ->
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(any(Context.class),
                    eq(NOW.plusMinutes(1)), isNull()), times(1));
    }

    @Test
    public void onReceive_ShouldNotScheduleUpdateNextWeeklyRepeatScheduleProcessingIterWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        onReceive(new Intent());
        assertTrue(shadowOf(mReceiver).wentAsync());

        mForegroundServiceMock.verify(() ->
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(any(Context.class),
                    any(LocalDateTime.class), any()), never());
    }

    @Test
    public void onReceive_ShouldGoAsyncWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        onReceive(new Intent());
        assertTrue(shadowOf(mReceiver).wentAsync());
    }

    @Test
    public void onReceive_WithEmptyClearPinCodesWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        onReceive(new Intent());

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mSubscriptionScheduler, times(1))
                    .updateNextWeeklyRepeatScheduleProcessingIter(NOW.plusMinutes(1), null));

        assertPendingResultFinished();
    }

    @Test
    public void onReceive_WithNoPinEntitiesWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        final var i = new Intent();
        i.putExtras(CLEAR_PIN_CODES);
        onReceive(i);

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mSubscriptionScheduler, times(1))
                    .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW.plusMinutes(1)),
                        mPinEntitiesCaptor.capture()));

        assertThat("Should've called with an empty list since PinStorage has no PinEntity's.",
                mPinEntitiesCaptor.getValue(), is(empty()));
        assertPendingResultFinished();
    }

    @Test
    public void onReceive_WithAllPinEntitiesMatchingAllPinStorageEntitiesWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return toPinEntityList(CLEAR_PIN_CODES);
        }).given(mPinStorage).getPinEntities();

        final var i = new Intent();
        i.putExtras(CLEAR_PIN_CODES);
        onReceive(i);

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mSubscriptionScheduler, times(1))
                    .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW.plusMinutes(1)),
                        mPinEntitiesCaptor.capture()));

        assertThat("Should've called with all clear PinEntity's containing in PinStorage.",
                mPinEntitiesCaptor.getValue(), is(toPinEntityList(CLEAR_PIN_CODES, true)));
        assertPendingResultFinished();
    }

    @Test
    public void onReceive_WithAllPinEntitiesMatchingAllPinStorageEntitiesExceptOneWhenBackgroundRestricted() {
        shadowOf(mActivityManagerProvider.get()).setBackgroundRestricted(true);

        final var encryptedPinEntities = toPinEntityList(CLEAR_PIN_CODES);
        final var extraPinEntity = new PinEntity();
        extraPinEntity.setSubscriptionId(encryptedPinEntities.size() + 1);
        encryptedPinEntities.add(extraPinEntity);
        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return encryptedPinEntities;
        }).given(mPinStorage).getPinEntities();

        final var i = new Intent();
        i.putExtras(CLEAR_PIN_CODES);
        onReceive(i);

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(2))
            .pollInterval(Duration.ofMillis(50))
            .untilAsserted(() -> verify(mSubscriptionScheduler, times(1))
                    .updateNextWeeklyRepeatScheduleProcessingIter(eq(NOW.plusMinutes(1)),
                        mPinEntitiesCaptor.capture()));

        final var expectedDecryptedPinEntities = toPinEntityList(CLEAR_PIN_CODES, true);
        expectedDecryptedPinEntities.add(extraPinEntity);
        assertThat("Should've called with all clear PinEntity's containing in PinStorage, " +
                "leaving the extra PinEntity encrypted.", mPinEntitiesCaptor.getValue(),
                is(expectedDecryptedPinEntities));
        assertPendingResultFinished();
    }

    @After
    public void tearDown() {
        mForegroundServiceMock.reset();
        mForegroundServiceMock.close();
        mLocalDateTimeMock.reset();
        mLocalDateTimeMock.close();
    }

    /** Launch and wait for the receiver to complete. */
    private void onReceive(final Intent i) {
        // Note that we need to launch through the instrumentation layer so that the PendingResult
        // is set for goAsync()
        mApplicationContext.sendBroadcast(new Intent(i)
                .setComponent(new ComponentName(mApplicationContext, AlarmReceiver.class)));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Assert that {@link PendingResult#finish} has been called. */
    private void assertPendingResultFinished() {
        assertTrue(shadowOf(shadowOf(mReceiver).getOriginalPendingResult()).getFuture().isDone());
    }

    private static List<PinEntity> toPinEntityList(final Bundle clearPinCodes,
            final boolean decrypt) {

        return clearPinCodes.keySet().stream().map((subId) -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(Integer.valueOf(subId));
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
