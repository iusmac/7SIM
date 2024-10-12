package com.github.iusmac.sevensim.telephony;

import dagger.hilt.android.testing.HiltAndroidTest;

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
}
