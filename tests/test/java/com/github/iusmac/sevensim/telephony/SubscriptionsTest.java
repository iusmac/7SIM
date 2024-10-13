package com.github.iusmac.sevensim.telephony;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ExceptionUtils;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.junit.Test;

import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import static org.awaitility.Awaitility.await;

import static org.junit.Assert.assertFalse;

import static org.robolectric.Shadows.shadowOf;

/** Tests for the {@link Subscriptions} and children classes that should extend it. */
public abstract class SubscriptionsTest extends MockitoHiltAndroidTestBase {
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Inject
    SubscriptionManager mSubscriptionManager;

    @Inject
    TelephonyManager mTelephonyManager;

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
}
