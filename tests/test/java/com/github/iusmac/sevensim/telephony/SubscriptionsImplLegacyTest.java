package com.github.iusmac.sevensim.telephony;

import android.graphics.Color;
import android.util.ExceptionUtils;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.SysProp;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;
import org.robolectric.RobolectricTestRunner;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.github.iusmac.sevensim.telephony.Subscription.DEFAULT_ICON_TINT;
import static com.github.iusmac.sevensim.telephony.Subscription.DEFAULT_SIM_NAME;
import static com.github.iusmac.sevensim.test.SubscriptionPropertyMatchers.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class SubscriptionsImplLegacyTest extends SubscriptionsTest {
    @Inject
    SubscriptionsImplLegacy mSubscriptions;

    @Inject
    @Named("Telephony/SimSubId") SysProp mSimSubIdSysProp;

    @Inject
    @Named("Telephony/SimState") SysProp mSimStateSysProp;

    @Inject
    @Named("Telephony/SimIconTint") SysProp mSimIconTintSysProp;

    @Inject
    @Named("Telephony/SimName") SysProp mSimNameSysProp;

    @Override
    Subscriptions provideSubscriptionsImpl() {
        return mSubscriptions;
    }

    @Test
    public void test_iterator_ShouldBeEmptyWhenNoActiveSimSlots() {
        setActiveModemCount(0);
        assertThat(mSubscriptions, is(emptyIterable()));
    }

    @Test
    public void test_iterator_ShouldContainOnlySubscriptionsWithSlotIndex() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(INVALID_SIM_SLOT_INDEX)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        setActiveModemCount(2);

        assertFutureDone(EXECUTOR.submit(() -> assertThat(mSubscriptions,
                        contains(both(withSubId(subInfo1.getSubscriptionId()))
                            .and(withSlotIndex(subInfo1.getSimSlotIndex()))))));
    }

    @Test
    public void test_iterator_ManuallyMovingIterator() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        setActiveModemCount(2);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var it = mSubscriptions.iterator();
            assertThat(it.next(), is(both(withSubId(subInfo1.getSubscriptionId()))
                        .and(withSlotIndex(subInfo1.getSimSlotIndex()))));
            assertTrue(it.hasNext());
            assertThat(it.next(), is(both(withSubId(subInfo2.getSubscriptionId()))
                        .and(withSlotIndex(subInfo2.getSimSlotIndex()))));
            assertFalse(it.hasNext());
        }));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_FoundMatchesForSlotIndex() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(INVALID_SIM_SLOT_INDEX)
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);

        setActiveModemCount(2);

        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSimSlotIndex(subInfo2.getSimSlotIndex())));
        assertThat(sub, is(not(Optional.empty())));
        assertThat(sub.get().getId(), is(subInfo2.getSubscriptionId()));
        assertThat(sub.get().getSlotIndex(), is(subInfo2.getSimSlotIndex()));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_NoMatchesForSlotIndex() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1);

        final var sub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(subInfo1.getSimSlotIndex() + 1)));
        assertThat(sub, is(Optional.empty()));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithoutSubscriptionInfoWhenDisabled() {
        // Fill in the fields that will be restored from volatile memory
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setIconTint(Color.GREEN);
        sub.setSimState(SimState.DISABLED);
        sub.setSimName("Work");
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(not(Optional.empty())));
        assertThat(actualSub.get(), is(sub));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithoutPresenceInDatabase() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        // Simulate the user manually wiped the app storage, or "hard" re-installed the app
        assertFutureDone(EXECUTOR.submit(() -> mAppDatabase.clearAllTables()));

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(not(Optional.empty())));
        assertThat(actualSub.get(), is(sub));
    }

    @Test(expected = InvalidSubscriptionIdException.class)
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithInvalidSubscriptionIdInVolatileMemory()
        throws Throwable {

        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        mSimSubIdSysProp.set(Optional.of(String.valueOf(INVALID_SUBSCRIPTION_ID)),
                sub.getSlotIndex());

        try {
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                        .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        } catch (RuntimeException e) {
            throw ExceptionUtils.getRootCause(e);
        }
    }

    @Test(expected = InvalidSubscriptionIdException.class)
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithBadSubscriptionIdInVolatileMemory()
        throws Throwable {

        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        // Completely mangle the data to fail the Integer parsing
        mSimSubIdSysProp.set(Optional.of("junk"), sub.getSlotIndex());

        try {
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                        .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        } catch (RuntimeException e) {
            throw ExceptionUtils.getRootCause(e);
        }
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithUnknownSimStateInVolatileMemory() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        mSimStateSysProp.set(Optional.of(String.valueOf(SimState.UNKNOWN)),
                sub.getSlotIndex());

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(Optional.empty()));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithInvalidSimStateInVolatileMemory() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        // Completely mangle the data to fail the Integer parsing
        mSimStateSysProp.set(Optional.of("junk"), sub.getSlotIndex());

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(Optional.empty()));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithInvalidIconTintInVolatileMemory() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        // Completely mangle the data to fail the Integer parsing
        mSimIconTintSysProp.set(Optional.of("junk"), sub.getSlotIndex());

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(not(Optional.empty())));
        assertThat(actualSub.get().getIconTint(), is(DEFAULT_ICON_TINT));
    }

    @Test
    public void test_getSubscriptionForSimSlotIndex_RestoreSubscriptionWithAllFieldsEmptyInVolatileMemory() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);
        sub.setSimState(SimState.DISABLED);
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.persistSubscription(sub)));

        mSimIconTintSysProp.set(Optional.empty(), sub.getSlotIndex());
        mSimNameSysProp.set(Optional.empty(), sub.getSlotIndex());

        final var actualSub = assertFutureDone(EXECUTOR.submit(() -> mSubscriptions
                    .getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(actualSub, is(not(Optional.empty())));
        assertThat(actualSub.get().getIconTint(), is(DEFAULT_ICON_TINT));
        assertThat(actualSub.get().getSimName(), is(DEFAULT_SIM_NAME));
    }

    @Test
    public void test_createSubscription_Impl() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.createSubscription(subInfo)));

        assertThat(sub.getSlotIndex(), is(subInfo.getSimSlotIndex()));
        assertThat(sub.getSimState(), is(SimState.ENABLED));
    }

    @Test
    public void test_syncSubscriptions_ShouldSyncWhenWasEnabledBefore() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSlotIndex(0);

        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(SimState.ENABLED)), sub.getId());
        mUsableSimSubIdsSysProp.set(Optional.of(String.valueOf(sub.getId())));

        // Persist as DISABLED in volatile memory to trigger restoring during syncing
        mSimSubIdSysProp.set(Optional.of(String.valueOf(sub.getId())), sub.getSlotIndex());
        mSimStateSysProp.set(Optional.of(String.valueOf(SimState.DISABLED)), sub.getSlotIndex());
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionsDao.upsert(sub)));

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptions.syncSubscriptions(DATE_TIME)));

        final var sub_ = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptions.getSubscriptionForSimSlotIndex(sub.getSlotIndex())));
        assertThat(sub_.get().getLastActivatedTime(), is(LocalDateTime.MIN));
        assertThat(sub_.get().getLastDeactivatedTime(), is(DATE_TIME));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), sub.getId()),
                is(Optional.of(String.valueOf(SimState.DISABLED))));
    }

    @Test
    public void test_syncSubscriptions_ShouldSyncWithCurrentStateWhenInvalidPersistedSubscriptionState() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();

        final var sub2 = new Subscription();
        sub2.setId(2);
        sub2.setSlotIndex(1);

        setAvailableSubscriptionInfoList(subInfo1);
        setActiveModemCount(2);

        // Persist as DISABLED in volatile memory to trigger restoring during syncing
        mSimSubIdSysProp.set(Optional.of(String.valueOf(sub2.getId())), sub2.getSlotIndex());
        mSimStateSysProp.set(Optional.of(String.valueOf(SimState.DISABLED)), sub2.getSlotIndex());
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionsDao.upsert(sub2)));

        mSubscriptionStateSysProp.set(Optional.of("junk"), subInfo1.getSubscriptionId());
        mSubscriptionStateSysProp.set(Optional.of("-1"), sub2.getId());

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subInfo1.getSubscriptionId()),
                is(Optional.of(String.valueOf(SimState.ENABLED))));
        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), sub2.getId()),
                is(Optional.of(String.valueOf(SimState.DISABLED))));
    }
}
