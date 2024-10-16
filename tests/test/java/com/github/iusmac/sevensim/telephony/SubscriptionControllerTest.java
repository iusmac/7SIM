package com.github.iusmac.sevensim.telephony;

import android.graphics.Color;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerHiddenApi;
import com.github.iusmac.sevensim.test.TestUtils;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class SubscriptionControllerTest extends MockitoHiltAndroidTestBase {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Inject
    SubscriptionManager mSubscriptionManager;

    @Inject
    SubscriptionController mSubscriptionController;

    @Inject
    SubscriptionsImpl mSubscriptions;

    @Test
    public void test_setUiccApplicationsEnabled_ShouldUnconditionallyNotifyListenerOnNonexistentSubscription() {
        final var result = new boolean[1];
        mSubscriptions.addOnSubscriptionsChangedListener(() -> result[0] = true);

        final boolean listenerWasUnconditionallyNotified = assertFutureDone(EXECUTOR.submit(() -> {
            mSubscriptionController.setUiccApplicationsEnabled(1, true);
            return result[0];
        }));
        assertTrue(listenerWasUnconditionallyNotified);
    }

    @Test
    public void test_setUiccApplicationsEnabled_ShouldNotPreacquireBlockSyncFlagOnNonexistentSubscription() {
        final boolean blockSyncFlagWasPreacquired = assertFutureDone(EXECUTOR.submit(() -> {
            mSubscriptionController.setUiccApplicationsEnabled(1, false);
            return mSubscriptions.mBlockSubscriptionsSyncFlag.get();
        }));
        assertFalse(blockSyncFlagWasPreacquired);
    }

    @Test
    public void test_setUiccApplicationsEnabled_ShouldAbortOnNonexistentSubscription() {
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionController
                    .setUiccApplicationsEnabled(1, false)));
        verify(mSubscriptionManager, never()).setUiccApplicationsEnabled(anyInt(), anyBoolean());
    }

    @Test
    public void test_setUiccApplicationsEnabled_ShouldPreacquireBlockSyncFlag() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final var result = new boolean[1];
        doAnswer((invocation) -> {
            result[0] = mSubscriptions.mBlockSubscriptionsSyncFlag.get();
            return null;
        }).when(mSubscriptionManager).setUiccApplicationsEnabled(anyInt(), anyBoolean());

        final boolean wantedEnabled = !subInfo.areUiccApplicationsEnabled();
        final boolean blockSyncFlagWasPreacquired = assertFutureDone(EXECUTOR.submit(() -> {
            mSubscriptionController.setUiccApplicationsEnabled(subInfo.getSubscriptionId(),
                    wantedEnabled);
            return result[0];
        }));

        verify(mSubscriptionManager, times(1))
            .setUiccApplicationsEnabled(subInfo.getSubscriptionId(), wantedEnabled);

        assertTrue(blockSyncFlagWasPreacquired);
    }

    @Test
    @Config(shadows = ShadowSubscriptionManagerHiddenApi.class)
    public void test_setUiccApplicationsEnabled_ShouldSetWantedEnabledStateImmediately() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        final boolean wantedEnabled = !subInfo.areUiccApplicationsEnabled();
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionController
                    .setUiccApplicationsEnabled(subInfo.getSubscriptionId(), wantedEnabled)));

        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSubId(subInfo.getSubscriptionId())));
        assertThat(sub.get().isSimEnabled(), is(wantedEnabled));
    }

    @Test
    @Config(shadows = ShadowSubscriptionManagerHiddenApi.class)
    public void test_setUiccApplicationsEnabled_ShouldReleaseBlockSyncFlagOnCompletion() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfos(subInfo);

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionController
                    .setUiccApplicationsEnabled(subInfo.getSubscriptionId(),
                        !subInfo.areUiccApplicationsEnabled())));

        assertFalse(mSubscriptions.mBlockSubscriptionsSyncFlag.get());
    }

    @Test
    public void test_setUiccApplicationsEnabled_ShouldPersistSubscriptionBeforeEnabling() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setIconTint(Color.GREEN)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo, false);
        setAvailableSubscriptionInfos(subInfo);

        final var result = new Subscription[1];
        doAnswer((invocation) -> {
            result[0] = mSubscriptions.getSubscriptionForSubId(subInfo.getSubscriptionId()).get();
            return null;
        }).when(mSubscriptionManager).setUiccApplicationsEnabled(anyInt(), anyBoolean());

        final var now = LocalDateTime.of(2007, 1, 1, 13, 0);
        final boolean wantedEnabled = !subInfo.areUiccApplicationsEnabled();
        @SuppressWarnings("ReturnValueIgnored") // in mock callback
        final Subscription sub = assertFutureDone(EXECUTOR.submit(() -> {
            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now);
                mSubscriptionController.setUiccApplicationsEnabled(subInfo.getSubscriptionId(),
                        wantedEnabled);
            }
            return result[0];
        }));

        final var expectedSub = new Subscription();
        expectedSub.setId(subInfo.getSubscriptionId());
        // At this stage the state is not applied yet, so we use the actual state to pass the
        // "equals" by instance test
        expectedSub.setSimState(SimState.DISABLED);
        expectedSub.setLastActivatedTime(now);
        expectedSub.setIconTint(subInfo.getIconTint());
        expectedSub.setSimName(subInfo.getDisplayName().toString());

        assertThat(sub, is(expectedSub));
    }

    @Test
    public void test_setUiccApplicationsEnabled_ShouldPersistSubscriptionBeforeDisabling() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setIconTint(Color.GREEN)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo, true);
        setAvailableSubscriptionInfos(subInfo);

        final var result = new Subscription[1];
        doAnswer((invocation) -> {
            result[0] = mSubscriptions.getSubscriptionForSubId(subInfo.getSubscriptionId()).get();
            return null;
        }).when(mSubscriptionManager).setUiccApplicationsEnabled(anyInt(), anyBoolean());

        final var now = LocalDateTime.of(2007, 1, 1, 13, 0);
        final boolean wantedEnabled = !subInfo.areUiccApplicationsEnabled();
        @SuppressWarnings("ReturnValueIgnored") // in mock callback
        final Subscription sub = assertFutureDone(EXECUTOR.submit(() -> {
            try (final var mock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
                mock.when(() -> LocalDateTime.now(ZoneId.systemDefault())).thenReturn(now);
                mSubscriptionController.setUiccApplicationsEnabled(subInfo.getSubscriptionId(),
                        wantedEnabled);
            }
            return result[0];
        }));

        final var expectedSub = new Subscription();
        expectedSub.setId(subInfo.getSubscriptionId());
        // At this stage the state is not applied yet, so we use the actual state to pass the
        // "equals" by instance test
        expectedSub.setSimState(SimState.ENABLED);
        expectedSub.setLastDeactivatedTime(now);
        expectedSub.setIconTint(subInfo.getIconTint());
        expectedSub.setSimName(subInfo.getDisplayName().toString());

        assertThat(sub, is(expectedSub));
    }

    private void setAvailableSubscriptionInfos(final SubscriptionInfo... subInfos) {
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfos);
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
