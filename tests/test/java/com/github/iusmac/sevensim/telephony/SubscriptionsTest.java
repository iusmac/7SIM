package com.github.iusmac.sevensim.telephony;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.graphics.Color;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ExceptionUtils;

import com.android.internal.telephony.PhoneConstants;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.Test;

import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import static android.os.Build.VERSION_CODES.Q;
import static android.telephony.TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED;
import static android.telephony.TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

/** Tests for the {@link Subscriptions} and children classes that should extend it. */
public abstract class SubscriptionsTest extends MockitoHiltAndroidTestBase {
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    static final LocalDateTime DATE_TIME = LocalDateTime.of(2007, 1, 1, 13, 0);

    @Inject
    SubscriptionManager mSubscriptionManager;

    @Inject
    TelephonyManager mTelephonyManager;

    @Inject
    AppDatabaseDE mAppDatabase;

    @Inject
    @Named("Telephony/SubState") SysProp mSubscriptionStateSysProp;

    @Inject
    @Named("Telephony/UsableSubIds") SysProp mUsableSimSubIdsSysProp;

    SubscriptionsDao mSubscriptionsDao;

    @Override
    public void setUp() {
        super.setUp();

        mSubscriptionsDao = mAppDatabase.subscriptionsDao();
    }

    abstract Subscriptions provideSubscriptionsImpl();

    @Test
    public final void test_iterator_ShouldBeEmptyWhenSubscriptionInfoListIsEmpty() {
        assertThat(provideSubscriptionsImpl(), is(emptyIterable()));
    }

    @Test
    public final void test_iterator_ShouldBeEmptyWhenSubscriptionInfoListIsNull() {
        setAvailableSubscriptionInfoList((List<SubscriptionInfo>) null);
        assertThat(provideSubscriptionsImpl(), is(emptyIterable()));
    }

    @Test(expected = NoSuchElementException.class)
    public final void test_iterator_NextOnEmptyIteratorWithCallToHasNextToPrepareNextValue()
            throws Throwable {

        try {
            assertFutureDone(EXECUTOR.submit(() -> {
                final var it = provideSubscriptionsImpl().iterator();
                assertFalse(it.hasNext());
                it.next();
            }));
        } catch (RuntimeException e) {
            throw ExceptionUtils.getRootCause(e);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public final void test_iterator_Removal() throws Throwable {
        try {
            assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl().iterator().remove()));
        } catch (RuntimeException e) {
            throw ExceptionUtils.getRootCause(e);
        }
    }

    @Test
    public final void test_getSubscription() {
        final var subInfo1 = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        final var subInfo2 = SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        setAvailableSubscriptionInfoList(subInfo1, subInfo2);
        setActiveModemCount(2);

        final var sub = assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl()
                    .getSubscription((sub_) -> sub_.getId() == subInfo2.getSubscriptionId() &&
                        subInfo2.getDisplayName().toString().equals(sub_.getSimName()))));
        assertThat(sub, is(not(Optional.empty())));
        assertThat(sub.get().getId(), is(subInfo2.getSubscriptionId()));
        assertThat(sub.get().getSimName(), is(subInfo2.getDisplayName()));
    }

    @Test
    public final void test_getSubscriptionForSubId() {
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

        final var sub = assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl()
                    .getSubscriptionForSubId(subInfo2.getSubscriptionId())));
        assertThat(sub, is(not(Optional.empty())));
        assertThat(sub.get().getId(), is(subInfo2.getSubscriptionId()));
    }

    @Test
    public final void test_createSubscription() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setIconTint(Color.GREEN)
            .setDisplayName("Work")
            .buildSubscriptionInfo();
        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().createSubscription(subInfo)));

        assertThat("Should've called Subscriptions implementation to assign subscription ID.",
                sub.getId(), is(subInfo.getSubscriptionId()));
        assertThat(sub.getIconTint(), is(subInfo.getIconTint()));
        assertThat(sub.getSimName(), is(subInfo.getDisplayName()));
        assertThat(sub.getLastActivatedTime(), is(LocalDateTime.MIN));
        assertThat(sub.getLastDeactivatedTime(), is(LocalDateTime.MIN));
        assertThat(sub.getKeepDisabledAcrossBoots(), is(nullValue()));
    }

    @Test
    public final void test_createSubscription_UndefinedSimName() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder().setId(1).buildSubscriptionInfo();
        final var sub = assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().createSubscription(subInfo)));

        assertThat(sub.getSimName(), is(emptyString()));
    }

    @Test
    public final void test_createSubscription_PopulateUsingFieldsFromDatabase() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .buildSubscriptionInfo();

        final var now = LocalDateTime.of(2007, 1, 1, 13, 0);
        final var sub = new Subscription();
        sub.setId(subInfo.getSubscriptionId());
        sub.setLastActivatedTime(now);
        sub.setLastDeactivatedTime(now);
        sub.keepDisabledAcrossBoots(true);

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionsDao.upsert(sub)));

        final var sub1 = assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().createSubscription(subInfo)));
        assertThat(sub1.getLastActivatedTime(), is(sub.getLastActivatedTime()));
        assertThat(sub1.getLastDeactivatedTime(), is(sub.getLastDeactivatedTime()));
        assertThat(sub1.getKeepDisabledAcrossBoots(), is(sub.getKeepDisabledAcrossBoots()));
    }

    @Test
    public final void test_persistSubscription() {
        final var sub = new Subscription();
        sub.setId(1);

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().persistSubscription(sub)));

        final var sub1 = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptionsDao.findBySubscriptionId(sub.getId())));
        assertThat(sub1, is(not(Optional.empty())));
        assertThat(sub1.get(), is(sub));
    }

    @Test
    public final void test_persistSubscription_ShouldPersistEnabledStateInVolatileMemory() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setSimState(SimState.ENABLED);

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().persistSubscription(sub)));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), sub.getId()),
                is(Optional.of(String.valueOf(sub.getSimState()))));
    }

    @Test
    public final void test_addOnSubscriptionsChangedListener_ShouldNotStartListeningToSubscriptionManagerOnNullListeners() {
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(null);
        assertFalse(isListeningToSubscriptionManager());
    }

    @Test
    @Config(minSdk = Q)
    public final void test_addOnSubscriptionsChangedListener_ShouldStartListeningToSubscriptionManagerOnNonNullListeners() {
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> {});
        assertTrue(isListeningToSubscriptionManager());
    }

    @Test
    @Config(minSdk = Q)
    public final void test_addOnSubscriptionsChangedListener_ShouldTriggerListenerWhenAlreadyListeningToSubscriptionManager() {
        // Add the first listener that should be triggered once initially after we'll start
        // listening to SubscriptionManager
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> {});

        // Add another listener that should be triggered this time by us since we're already
        // listening to the SubscriptionManager
        final var result = new boolean[1];
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> result[0] = true);
        assertTrue(result[0]);
    }

    @Test
    public final void test_addOnSubscriptionsChangedListener_ShouldNotRegisterDuplicateListeners() {
        final var calls = new int[1];
        final Subscriptions.OnSubscriptionsChangedListener listener = () -> calls[0]++;
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(listener);
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(listener);
        assertThat(calls[0], is(1));
    }

    @Test
    public final void test_removeOnSubscriptionsChangedListener_ShouldNotStopListeningToSubscriptionManagerOnNullListeners() {
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> {});
        provideSubscriptionsImpl().removeOnSubscriptionsChangedListener(null);
        assertTrue(isListeningToSubscriptionManager());
    }

    @Test
    public final void test_removeOnSubscriptionsChangedListener_ShouldNotStopListeningToSubscriptionManagerWhenHaveActiveSubscribers() {
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> {});
        final Subscriptions.OnSubscriptionsChangedListener listener = () -> {};
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(listener);
        provideSubscriptionsImpl().removeOnSubscriptionsChangedListener(listener);
        assertTrue(isListeningToSubscriptionManager());
    }

    @Test
    public final void test_removeOnSubscriptionsChangedListener_ShouldStopListeningToSubscriptionManagerWhenNoMoreActiveSubscribers() {
        final Subscriptions.OnSubscriptionsChangedListener listener = () -> {};
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(listener);
        provideSubscriptionsImpl().removeOnSubscriptionsChangedListener(listener);
        assertFalse(isListeningToSubscriptionManager());
    }

    @Test
    public final void test_ShouldResumeListeningToSubscriptionManagerAfterBeingStoppedAndNewListenerAdded() {
        final Subscriptions.OnSubscriptionsChangedListener listener = () -> {};
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(listener);
        provideSubscriptionsImpl().removeOnSubscriptionsChangedListener(listener);
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> {});
        assertTrue(isListeningToSubscriptionManager());
    }

    @Test
    public final void test_syncSubscriptions_ShouldNotSyncWhenBlockFlagIsSet() {
        final String subId = "1";

        mUsableSimSubIdsSysProp.set(Optional.of(subId));

        provideSubscriptionsImpl().mBlockSubscriptionsSyncFlag.set(true);
        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        assertThat(mUsableSimSubIdsSysProp.get(Optional.empty()), is(Optional.of(subId)));
    }

    @Test
    public final void test_syncSubscriptions_ShouldHaveEmptyUsableSimSubIdListWhenNoSubscriptions() {
        provideSubscriptionsImpl().syncSubscriptions(DATE_TIME);
        assertThat(mUsableSimSubIdsSysProp.get(Optional.empty()), is(Optional.empty()));
    }

    @Test
    public final void test_syncSubscriptions_ShouldContainUsableSimSubIdsWhenHaveAvailableSubscriptions() {
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

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        assertThat(mUsableSimSubIdsSysProp.get(Optional.empty()),
                is(Optional.of(subInfo1.getSubscriptionId() + "," + subInfo2.getSubscriptionId())));
    }

    @Test
    public final void test_syncSubscriptions_ShouldSyncWhenWasDisabledBefore() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo, true);
        setAvailableSubscriptionInfoList(subInfo);

        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(SimState.DISABLED)),
                subInfo.getSubscriptionId());
        mUsableSimSubIdsSysProp.set(Optional.of(String.valueOf(subInfo.getSubscriptionId())));

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        final var sub = assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl()
                    .getSubscriptionForSubId(subInfo.getSubscriptionId())));
        assertThat(sub.get().getLastActivatedTime(), is(DATE_TIME));
        assertThat(sub.get().getLastDeactivatedTime(), is(LocalDateTime.MIN));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subInfo.getSubscriptionId()),
                is(Optional.of(String.valueOf(SimState.ENABLED))));
    }

    @Test
    public final void test_syncSubscriptions_ShouldNotSyncWhenCurrentStateUnchanged() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        TestUtils.setAreUiccApplicationsEnabled(subInfo, true);
        setAvailableSubscriptionInfoList(subInfo);

        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(SimState.ENABLED)),
                subInfo.getSubscriptionId());
        mUsableSimSubIdsSysProp.set(Optional.of(String.valueOf(subInfo.getSubscriptionId())));

        final var preSyncSub = assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl()
                    .getSubscriptionForSubId(subInfo.getSubscriptionId())));
        preSyncSub.get().setLastActivatedTime(DATE_TIME.minusHours(1));
        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionsDao.upsert(preSyncSub.get())));

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        final var postSyncSub = assertFutureDone(EXECUTOR.submit(() -> provideSubscriptionsImpl()
                    .getSubscriptionForSubId(subInfo.getSubscriptionId())));
        assertThat(postSyncSub.get().getLastActivatedTime(),
                is(preSyncSub.get().getLastActivatedTime()));
    }

    @Test
    public final void test_syncSubscriptions_ShouldProcessRemovedSubscriptionsWithoutPresenceInDatabase() {
        final int subId = 1;
        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(SimState.ENABLED)), subId);
        // While we're at it, mix the comma-separated list of usable SIM subscriptions IDs with the
        // invalid integer values to test the parsability
        mUsableSimSubIdsSysProp.set(Optional.of(subId + ",junk,-1"));

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), subId),
                is(Optional.of(String.valueOf(SimState.UNKNOWN))));
        assertThat(mUsableSimSubIdsSysProp.get(Optional.empty()), is(Optional.empty()));
    }

    @Test
    public final void test_syncSubscriptions_ShouldProcessRemovedSubscriptionsWithPresenceInDatabase() {
        final var sub = new Subscription();
        sub.setId(1);
        sub.setLastActivatedTime(DATE_TIME);
        sub.setSimState(SimState.ENABLED);

        mSubscriptionStateSysProp.set(Optional.of(String.valueOf(sub.getSimState())), sub.getId());
        // While we're at it, mix the comma-separated list of usable SIM subscriptions IDs with the
        // invalid integer values to test the parsability
        mUsableSimSubIdsSysProp.set(Optional.of(sub.getId() + ",junk,-1"));

        assertFutureDone(EXECUTOR.submit(() -> mSubscriptionsDao.upsert(sub)));

        assertFutureDone(EXECUTOR.submit(() ->
                    provideSubscriptionsImpl().syncSubscriptions(DATE_TIME)));

        final var postSyncSub = assertFutureDone(EXECUTOR.submit(() ->
                    mSubscriptionsDao.findBySubscriptionId(sub.getId())));
        assertThat(postSyncSub.get().getLastActivatedTime(), is(LocalDateTime.MIN));
        assertThat(postSyncSub.get().getLastDeactivatedTime(), is(LocalDateTime.MIN));

        assertThat(mSubscriptionStateSysProp.get(Optional.empty(), sub.getId()),
                is(Optional.of(String.valueOf(SimState.UNKNOWN))));
        assertThat(mUsableSimSubIdsSysProp.get(Optional.empty()), is(Optional.empty()));
    }

    @Test
    public final void test_notifyAllListeners() {
        final var calls = new int[2];
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> calls[0]++);
        provideSubscriptionsImpl().addOnSubscriptionsChangedListener(() -> calls[1]++);
        provideSubscriptionsImpl().notifyAllListeners();

        // First trigger per listener when added, second trigger per listener when manually notified
        assertThat(calls, is(new int[] { 2, 2 }));
    }

    @Test
    public final void test_addOnSimStatusChangedListener_ShouldNotStartListeningForCarrierConfigChangesOnNullListeners() {
        provideSubscriptionsImpl().addOnSimStatusChangedListener(null);
        assertThat(findCarrierConfigChangedReceiver(), is(Optional.empty()));
    }

    @Test
    public final void test_addOnSimStatusChangedListener_ShouldStartListeningForCarrierConfigChangesOnNonNullListeners() {
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> {});
        assertThat(findCarrierConfigChangedReceiver(), is(not(Optional.empty())));
    }

    @Test
    public final void test_addOnSimStatusChangedListener_ShouldNotRegisterDuplicateListeners() {
        final var calls = new int[1];
        final Subscriptions.OnSimStatusChangedListener listener = (slotIndex, state) -> calls[0]++;
        provideSubscriptionsImpl().addOnSimStatusChangedListener(listener);
        provideSubscriptionsImpl().addOnSimStatusChangedListener(listener);

        mApplicationContext.sendBroadcast(new Intent(ACTION_SIM_CARD_STATE_CHANGED));
        shadowOf(mApplicationContext.getMainLooper()).idle();

        assertThat(calls[0], is(1));
    }

    @Test
    public final void test_DispatchAllOnSimStatusChangedListeners_UndefinedAction() {
        final var results = new int[2][2];
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[0] =
                new int[] { slotIndex, state });
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[1] =
                new int[] { slotIndex, state });

        findCarrierConfigChangedReceiver().get().onReceive(mApplicationContext,
                new Intent("dummy"));

        assertThat(results, is(new int[][] { { 0, 0 }, { 0, 0 } }));
    }

    @Test
    public final void test_DispatchAllOnSimStatusChangedListenersWithDefaults_ACTION_SIM_CARD_STATE_CHANGED() {
        final var results = new int[2][2];
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[0] =
                new int[] { slotIndex, state });
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[1] =
                new int[] { slotIndex, state });

        mApplicationContext.sendBroadcast(new Intent(ACTION_SIM_CARD_STATE_CHANGED));
        shadowOf(mApplicationContext.getMainLooper()).idle();

        assertThat(results, is(new int[][] {
            { -1, TelephonyManager.SIM_STATE_UNKNOWN },
            { -1, TelephonyManager.SIM_STATE_UNKNOWN },
        }));
    }

    @Test
    public final void test_DispatchAllOnSimStatusChangedListenersWithDefaults_ACTION_SIM_APPLICATION_STATE_CHANGED() {
        final var results = new int[2][2];
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[0] =
                new int[] { slotIndex, state });
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> results[1] =
                new int[] { slotIndex, state });

        mApplicationContext.sendBroadcast(new Intent(ACTION_SIM_APPLICATION_STATE_CHANGED));
        shadowOf(mApplicationContext.getMainLooper()).idle();

        assertThat(results, is(new int[][] {
            { -1, TelephonyManager.SIM_STATE_UNKNOWN },
            { -1, TelephonyManager.SIM_STATE_UNKNOWN },
        }));
    }

    @Test
    public final void test_DispatchAllOnSimStatusChangedWithData_ACTION_SIM_CARD_STATE_CHANGED() {
        final var result = new int[2][2];
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> result[0] =
                new int[] { slotIndex, state });
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> result[1] =
                new int[] { slotIndex, state });

        final int expectedSlotIndex = 1;
        final int expectedSimState = TelephonyManager.SIM_STATE_READY;
        mApplicationContext.sendBroadcast(new Intent(ACTION_SIM_CARD_STATE_CHANGED)
                .putExtra(PhoneConstants.SLOT_KEY, expectedSlotIndex)
                .putExtra(TelephonyManager.EXTRA_SIM_STATE, expectedSimState));
        shadowOf(mApplicationContext.getMainLooper()).idle();

        assertThat(result, is(new int[][] {
            { expectedSlotIndex, expectedSimState },
            { expectedSlotIndex, expectedSimState },
        }));
    }

    @Test
    public final void test_DispatchAllOnSimStatusChangedWithData_ACTION_SIM_APPLICATION_STATE_CHANGED() {
        final var result = new int[2][2];
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> result[0] =
                new int[] { slotIndex, state });
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> result[1] =
                new int[] { slotIndex, state });

        final int expectedSlotIndex = 1;
        final int expectedSimState = TelephonyManager.SIM_STATE_READY;
        mApplicationContext.sendBroadcast(new Intent(ACTION_SIM_APPLICATION_STATE_CHANGED)
                .putExtra(PhoneConstants.SLOT_KEY, expectedSlotIndex)
                .putExtra(TelephonyManager.EXTRA_SIM_STATE, expectedSimState));
        shadowOf(mApplicationContext.getMainLooper()).idle();

        assertThat(result, is(new int[][] {
            { expectedSlotIndex, expectedSimState },
            { expectedSlotIndex, expectedSimState },
        }));
    }

    @Test
    public final void test_removeOnSimStatusChangedListener_ShouldNotStopListeningForCarrierConfigChangesOnNullListeners() {
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> {});
        provideSubscriptionsImpl().removeOnSimStatusChangedListener(null);
        assertThat(findCarrierConfigChangedReceiver(), is(not(Optional.empty())));
    }

    @Test
    public final void test_removeOnSimStatusChangedListener_ShouldNotStopListeningForCarrierConfigChangesWhenHaveActiveSubscribers() {
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> {});
        final Subscriptions.OnSimStatusChangedListener listener = (slotIndex, state) -> {};
        provideSubscriptionsImpl().addOnSimStatusChangedListener(listener);
        provideSubscriptionsImpl().removeOnSimStatusChangedListener(listener);
        assertThat(findCarrierConfigChangedReceiver(), is(not(Optional.empty())));
    }

    @Test
    public final void test_removeOnSimStatusChangedListener_ShouldStopListeningForCarrierConfigChangesWhenNoMoreActiveSubscribers() {
        final Subscriptions.OnSimStatusChangedListener listener = (slotIndex, state) -> {};
        provideSubscriptionsImpl().addOnSimStatusChangedListener(listener);
        provideSubscriptionsImpl().removeOnSimStatusChangedListener(listener);
        assertThat(findCarrierConfigChangedReceiver(), is(Optional.empty()));
    }

    @Test
    public final void test_ShouldResumeListeningForCarrierConfigChangesAfterBeingStoppedAndNewListenerAdded() {
        final Subscriptions.OnSimStatusChangedListener listener = (slotIndex, state) -> {};
        provideSubscriptionsImpl().addOnSimStatusChangedListener(listener);
        provideSubscriptionsImpl().removeOnSimStatusChangedListener(listener);
        provideSubscriptionsImpl().addOnSimStatusChangedListener((slotIndex, state) -> {});
        assertThat(findCarrierConfigChangedReceiver(), is(not(Optional.empty())));
    }

    static <T> T assertFutureDone(final Future<T> future) {
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

    void setAvailableSubscriptionInfoList(final SubscriptionInfoBuilder... subInfoBuilders) {
        final var subInfos = Arrays.asList(subInfoBuilders)
            .stream().map(SubscriptionInfoBuilder::buildSubscriptionInfo).toList();

        setAvailableSubscriptionInfoList(subInfos);
    }

    void setAvailableSubscriptionInfoList(final SubscriptionInfo... subInfos) {
        setAvailableSubscriptionInfoList(Arrays.asList(subInfos));
    }

    void setAvailableSubscriptionInfoList(final List<SubscriptionInfo> subInfos) {
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfoList(subInfos);
    }

    void setActiveModemCount(final int count) {
        shadowOf(mTelephonyManager).setActiveModemCount(count);
    }

    private boolean isListeningToSubscriptionManager() {
        return shadowOf(mSubscriptionManager).hasOnSubscriptionsChangedListener(
                provideSubscriptionsImpl().mSubscriptionManagerListener);
    }

    private Optional<BroadcastReceiver> findCarrierConfigChangedReceiver() {
        return shadowOf((Application) mApplicationContext).getRegisteredReceivers().stream()
            .filter((wrapper) -> wrapper.intentFilter != null &&
                    wrapper.intentFilter.hasAction(ACTION_SIM_CARD_STATE_CHANGED)
                    && wrapper.intentFilter.hasAction(ACTION_SIM_APPLICATION_STATE_CHANGED))
            .map((wrapper) -> wrapper.broadcastReceiver)
            .findFirst();
    }
}
