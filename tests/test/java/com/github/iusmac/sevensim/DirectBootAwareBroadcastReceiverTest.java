package com.github.iusmac.sevensim;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;
import javax.inject.Named;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.hamcrest.Matcher;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static android.telephony.CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED;
import static android.telephony.CarrierConfigManager.EXTRA_REBROADCAST_ON_UNLOCK;
import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(Enclosed.class)
public class DirectBootAwareBroadcastReceiverTest {
    /** Test receiver behavior on incorrect/unexpected intents. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class IncorrectIntent extends Base {
        @Parameter(0)
        public GivenHolder<Intent> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameters(name = "{0}, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(new Intent()), expected(is(emptyString())) },
                { given(new Intent("unexpected.action")), expected(is("unexpected.action")) },
            });
        }

        @Captor
        private ArgumentCaptor<String> mActionCaptor;

        @Test
        @SuppressWarnings("UnnecessaryAssignment") // use spied logger
        public void test_onReceive() {
            // Hijack logger creation to use a spied one instead to test on
            final var loggerFactory = mReceiver.mLoggerFactory;
            mReceiver.mLoggerFactory = (tag) -> spy(loggerFactory.create(tag));

            onReceive(mGiven.value);

            verify(mReceiver.mLogger, times(1)).e(any(String.class), mActionCaptor.capture());
            assertThat((String) mActionCaptor.getValue(), is(mExpected.value));
        }
    }

    /** Test receiver behavior on locked boot completed intent. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class LockedBootCompletedIntent extends Base {
        @Override
        public void setUp() {
            super.setUp();

            onReceive(new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED));
        }

        @Test
        public void test_onReceive() {
            assertTrue(mLockedBootCompletedSysProp.isTrue());

            mForegroundServiceMock.verify(() ->
                    ForegroundService.syncAllSubscriptionsEnabledState(mApplicationContext, NOW,
                        false), times(1));

            mForegroundServiceMock.verify(() -> {
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                        NOW.plusMinutes(1));
            }, times(1));
        }
    }

    /** Test receiver behavior on carrier config changed intent. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class CarrierConfigChangedIntent extends Base {
        @Parameter(0)
        public boolean mIsLockedBootCompleted;

        @Parameter(1)
        public Boolean mRebroadcastOnUnlock;

        @Parameter(2)
        public Integer mSubId;

        @Parameters(name = "isLockedBootCompleted={0}, rebroadcastOnUnlock={1}, subId={2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { false, null, null },
                { true, null, null },
                { true, null, 5 },
                { true, false, null },
                { true, false, 5 },
                { true, true, null },
                { true, true, 5 },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            if (mIsLockedBootCompleted) {
                mLockedBootCompletedSysProp.set(Optional.of("1"));
            }

            final Intent i = new Intent(ACTION_CARRIER_CONFIG_CHANGED);
            if (mRebroadcastOnUnlock != null) {
                i.putExtra(EXTRA_REBROADCAST_ON_UNLOCK, mRebroadcastOnUnlock.booleanValue());
            }
            if (mSubId != null) {
                i.putExtra(EXTRA_SUBSCRIPTION_INDEX, mSubId.intValue());
            }
            onReceive(i);
        }

        @Test
        @Config(minSdk = Build.VERSION_CODES.R)
        public void test_onReceive_SinceR() {
            if (!mIsLockedBootCompleted || (mRebroadcastOnUnlock != null && mRebroadcastOnUnlock)) {
                mForegroundServiceMock.verifyNoInteractions();
            } else {
                validate();
            }
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.Q)
        public void test_onReceive_Q() {
            if (!mIsLockedBootCompleted) {
                mForegroundServiceMock.verifyNoInteractions();
            } else {
                validate();
            }
        }

        private void validate() {
            mForegroundServiceMock.verify(() ->
                    ForegroundService.onSubscriptionsChanged(mApplicationContext, NOW), times(1));

            final int subId = mSubId == null ? INVALID_SUBSCRIPTION_ID : mSubId;
            mForegroundServiceMock.verify(() ->
                    ForegroundService.syncSubscriptionEnabledState(mApplicationContext, subId, NOW,
                        false), times(1));

            mForegroundServiceMock.verify(() -> {
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                        NOW.plusMinutes(1));
            }, times(1));
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
        protected static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 8, 0, 0);

        protected final DirectBootAwareBroadcastReceiver mReceiver =
            new DirectBootAwareBroadcastReceiver();

        protected MockedStatic<ForegroundService> mForegroundServiceMock;
        private MockedStatic<LocalDateTime> mLocalDateTimeNowMock;

        @Inject @Named("LockedBootCompleted") SysProp mLockedBootCompletedSysProp;

        @Override
        @SuppressWarnings("ReturnValueIgnored") // in mock callbacks
        public void setUp() {
            super.setUp();

            mReceiver.inject(mApplicationContext);

            mForegroundServiceMock = mockStatic(ForegroundService.class);
            mLocalDateTimeNowMock = mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);
            mLocalDateTimeNowMock.when(() ->
                    LocalDateTime.now(ZoneId.systemDefault())).thenReturn(NOW);
        }

        @After
        @CallSuper
        public void tearDown() {
            mForegroundServiceMock.reset();
            mForegroundServiceMock.close();
            mLocalDateTimeNowMock.reset();
            mLocalDateTimeNowMock.close();
        }

        protected void onReceive(final Intent i) {
            mReceiver.onReceive(mApplicationContext, i);
        }
    }
}
