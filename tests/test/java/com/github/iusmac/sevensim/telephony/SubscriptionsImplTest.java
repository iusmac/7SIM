package com.github.iusmac.sevensim.telephony;

import com.github.iusmac.sevensim.test.TestUtils;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import org.robolectric.RobolectricTestRunner;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;

import static com.github.iusmac.sevensim.test.SubscriptionPropertyMatchers.withSubId;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class SubscriptionsImplTest extends SubscriptionsTest {
    @Inject
    SubscriptionsImpl mSubscriptions;

    @Override
    Subscriptions provideSubscriptionsImpl() {
        return mSubscriptions;
    }

    @Test
    public void test_iterator_ShouldContainOnlyNonEmbeddedSubscriptions() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setIsEmbedded(false)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setIsEmbedded(true)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        assertFutureDone(EXECUTOR.submit(() -> assertThat(mSubscriptions,
                        contains(withSubId(subInfo1.getSubscriptionId())))));
    }

    @Test
    public void test_iterator_ManuallyMovingIterator() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setIsEmbedded(false)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setIsEmbedded(false)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var it = mSubscriptions.iterator();
            assertThat(it.next(), is(withSubId(subInfo1.getSubscriptionId())));
            assertTrue(it.hasNext());
            assertThat(it.next(), is(withSubId(subInfo2.getSubscriptionId())));
            assertFalse(it.hasNext());
        }));
    }

    @Test
    public void test_createSubscription_Impl() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.createSubscription(subInfo)));

        assertThat("Shouldn't assign SIM slot index for this Subscriptions implementation.",
                sub.getSlotIndex(), is(INVALID_SIM_SLOT_INDEX));
        assertThat(sub.getSimState(), is(SimState.DISABLED));
    }

    @Test
    public void test_syncSubscriptions_ShouldSyncWhenWasEnabledBefore() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo, false);
        setAvailableSubscriptionInfoList(subInfo);

        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(SimState.ENABLED)),
                subInfo.getSubscriptionId());
        mUsableSimSubIdsSysProp.set(Optional.of(String.valueOf(subInfo.getSubscriptionId())));

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.syncSubscriptions(DATE_TIME)));

        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSubId(subInfo.getSubscriptionId())));
        assertThat(sub.get().getLastActivatedTime(), is(LocalDateTime.MIN));
        assertThat(sub.get().getLastDeactivatedTime(), is(DATE_TIME));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subInfo.getSubscriptionId()),
                is(Optional.of(String.valueOf(SimState.DISABLED))));
    }

    @Test
    public void test_syncSubscriptions_ShouldSyncWithCurrentStateWhenInvalidPersistedSubscriptionState() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo1, false);

        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo2, true);

        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        mSubscriptionStateSysProp.set(Optional.of("junk"), subInfo1.getSubscriptionId());
        mSubscriptionStateSysProp.set(Optional.of("-1"), subInfo2.getSubscriptionId());

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subInfo1.getSubscriptionId()),
                is(Optional.of(String.valueOf(SimState.DISABLED))));
        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subInfo2.getSubscriptionId()),
                is(Optional.of(String.valueOf(SimState.ENABLED))));
    }
}
