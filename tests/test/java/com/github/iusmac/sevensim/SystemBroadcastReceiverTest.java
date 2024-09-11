package com.github.iusmac.sevensim;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.launcher.LauncherIconVisibilityManager;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.hamcrest.Matcher;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import static org.robolectric.Shadows.shadowOf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(Enclosed.class)
public class SystemBroadcastReceiverTest {
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
        public void test_onReceive() {
            // Hijack logger creation to use a spied one instead to test on
            final var loggerFactory = mReceiver.mLoggerFactory;
            mReceiver.mLoggerFactory = (tag) -> spy(loggerFactory.create(tag));

            onReceive(mGiven.value);

            verify(mReceiver.mLogger, times(1)).e(any(String.class), mActionCaptor.capture());
            assertThat((String) mActionCaptor.getValue(), is(mExpected.value));
        }
    }

    /** Test receiver behavior on boot completed intent. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class BootCompletedIntent extends Base {
        @Parameter(0)
        public int mEncryptionStatus;

        @Parameter(1)
        public boolean mIsDeviceSecure;

        @Parameters(name = "encryptionStatus={0}, isDeviceSecure={1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                // Note: since the device is booted and the encryption is active, we don't need to
                // check whether the device is secured as we know the user is already authenticated
                // at this stage
                { DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, false },

                { DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED, false },
                { DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE, false },
                { DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY, false },

                { DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED, true },
                { DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE, true },
                { DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY, true },
            });
        }

        private MockedStatic<UserAuthenticationObserverService>
            mUserAuthenticationObserverServiceMock;

        @Override
        public void setUp() {
            super.setUp();

            mUserAuthenticationObserverServiceMock =
                mockStatic(UserAuthenticationObserverService.class);

            shadowOf(mReceiver.mDevicePolicyManagerProvider.get())
                .setStorageEncryptionStatus(mEncryptionStatus);
            shadowOf(mReceiver.mKeyguardManagerProvider.get()).setIsDeviceSecure(mIsDeviceSecure);
            onReceive(new Intent(Intent.ACTION_BOOT_COMPLETED));
        }

        @Test
        public void test_onReceive() {
            verify(mLauncherIconVisibilityManagerMock, times(1)).updateVisibility();

            // When the device is secure, we want to wait for the user to authenticate first
            if (mIsDeviceSecure) {
                mUserAuthenticationObserverServiceMock.verify(() -> {
                    UserAuthenticationObserverService
                        .updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                                NOW.plusMinutes(1), true);
                }, times(1));
                mForegroundServiceMock.verifyNoInteractions();
            } else {
                mForegroundServiceMock.verify(() -> {
                    ForegroundService
                        .updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                                NOW.plusMinutes(1), true);
                }, times(1));
                mUserAuthenticationObserverServiceMock.verifyNoInteractions();
            }
        }

        @Override
        public void tearDown() {
            super.tearDown();

            mUserAuthenticationObserverServiceMock.reset();
            mUserAuthenticationObserverServiceMock.close();
        }
    }

    /** Test receiver behavior when this package has been replaced. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class MyPackageReplacedIntent extends Base {
        @Override
        public void setUp() {
            super.setUp();

            onReceive(new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        }

        @Test
        public void test_onReceive() {
            verify(mLauncherIconVisibilityManagerMock, times(1)).updateVisibility();
        }
    }

    /** Test receiver behavior on any alterations to the system time. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class TimeOrTimeZoneIntent extends Base {
        @Parameter(0)
        public String mAction;

        @Parameters(name = "{0}")
        public static List<?> params() {
            return Arrays.asList(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED);
        }

        @Override
        public void setUp() {
            super.setUp();

            onReceive(new Intent(mAction));
        }

        @Test
        public void test_onReceive() {
            mForegroundServiceMock.verify(() -> {
                ForegroundService.syncAllSubscriptionsEnabledState(mApplicationContext, NOW, false);
            }, times(1));

            mForegroundServiceMock.verify(() -> {
                ForegroundService.updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                        NOW.plusMinutes(1));
            }, times(1));
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
        protected static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 8, 0, 0);

        @Mock
        protected LauncherIconVisibilityManager mLauncherIconVisibilityManagerMock;

        protected final SystemBroadcastReceiver mReceiver = new SystemBroadcastReceiver();

        protected MockedStatic<ForegroundService> mForegroundServiceMock;
        private MockedStatic<LocalDateTime> mLocalDateTimeNowMock;

        @Override
        @SuppressWarnings("ReturnValueIgnored") // in mock callbacks
        public void setUp() {
            super.setUp();

            mReceiver.inject(mApplicationContext);

            mForegroundServiceMock = mockStatic(ForegroundService.class);
            mLocalDateTimeNowMock = mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);
            mLocalDateTimeNowMock.when(() ->
                    LocalDateTime.now(ZoneId.systemDefault())).thenReturn(NOW);
            // In this unit test suite we just want to know if LauncherIconVisibilityManager has
            // been called or not, so let the provider return a mock instead
            mReceiver.mLauncherIconVisibilityManagerProvider = () -> mLauncherIconVisibilityManagerMock;
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
