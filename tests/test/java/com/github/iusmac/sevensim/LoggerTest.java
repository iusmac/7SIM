package com.github.iusmac.sevensim;

import android.util.Log;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.Mock;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.shadows.ShadowLog;

import static com.github.iusmac.sevensim.test.LogItemPropertyMatchers.*;
import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(Enclosed.class)
public class LoggerTest extends MockitoHiltAndroidTestBase {
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ChattyLogging extends Base {
        @Mock
        private Process mProcessMock;

        @Inject
        Runtime mRuntime;

        @Override
        public void setUp() {
            super.setUp();

            Logger.IS_LOGCAT_CHATTY_ENABLED.set(false);
        }

        @Test
        public void test_chattyLoggingIsAlreadyEnabled()
            throws InterruptedException, IOException {

            doReturn(mProcessMock).when(mRuntime).exec(any(String[].class));

            mFactory.create(TAG);
            verify(mProcessMock, times(1)).waitFor();

            mFactory.create(TAG);
            verifyNoMoreInteractions(mProcessMock);
        }

        @Test
        public void test_chattyLoggingCannotBeEnabledInNonDebugBuilds()
            throws InterruptedException, IOException {

            overrideSysPropDebugEnabled(false);
            mFactory.create(TAG);

            assertThat(Logger.IS_LOGCAT_CHATTY_ENABLED.get(), is(equalTo(false)));
            verifyNoInteractions(mProcessMock);
        }

        @Test
        public void test_succeededToEnableChattyLogging() throws InterruptedException, IOException {
            doReturn(mProcessMock).when(mRuntime).exec(any(String[].class));

            mFactory.create(TAG);

            verify(mProcessMock, times(1)).waitFor();
        }

        @Test
        public void test_failedToEnableChattyLogging() throws InterruptedException, IOException {
            when(mProcessMock.waitFor()).thenThrow(InterruptedException.class);
            doReturn(mProcessMock).when(mRuntime).exec(any(String[].class));

            mFactory.create(TAG);

            verify(mProcessMock, times(1)).waitFor();
            assertThat(ShadowLog.getLogs(), containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ERROR),
                            withMsg(equalTo("Failed to disable app log suppression in logcat.")))));
        }
    }

    /** Test logging with {@link Log#VERBOSE} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class VerboseLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(not(logged()))), false, false },
                { given(Log.DEBUG), expected(is(not(logged()))), false, true },
                { given(Log.DEBUG), expected(is(logged())), true, false },
                { given(Log.DEBUG), expected(is(logged())), true, true },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard verbose message.";
            mLogger.v(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.VERBOSE),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard verbose null-argument message.";
            mLogger.v(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.VERBOSE),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.v("Message '%s' w/ arg=%s.", "verbose", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.VERBOSE),
                            withMsg(equalTo("Message 'verbose' w/ arg=arg1.")))))
                    .or(mExpected.value));
        }
    }

    /** Test logging with {@link Log#DEBUG} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class DebugLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(logged())), false, false },
                { given(Log.DEBUG), expected(is(logged())), false, true },
                { given(Log.INFO), expected(is(not(logged()))), false, false },
                { given(Log.INFO), expected(is(not(logged()))), false, true },
                { given(Log.INFO), expected(is(logged())), true, false },
                { given(Log.INFO), expected(is(logged())), true, true },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard debug message.";
            mLogger.d(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.DEBUG),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard debug null-argument message.";
            mLogger.d(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.DEBUG),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.d("Message '%s' w/ arg=%s.", "debug", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.DEBUG),
                            withMsg(equalTo("Message 'debug' w/ arg=arg1.")))))
                    .or(mExpected.value));
        }
    }

    /** Test logging with {@link Log#INFO} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class InfoLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(logged())), false, false },
                { given(Log.DEBUG), expected(is(logged())), false, true },
                { given(Log.INFO), expected(is(logged())), false, false },
                { given(Log.INFO), expected(is(logged())), false, true },
                { given(Log.WARN), expected(is(not(logged()))), false, false },
                { given(Log.WARN), expected(is(not(logged()))), false, true },
                { given(Log.WARN), expected(is(logged())), true, false },
                { given(Log.WARN), expected(is(logged())), true, true },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard info message.";
            mLogger.i(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.INFO),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard info null-argument message.";
            mLogger.i(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.INFO),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.i("Message '%s' w/ arg=%s.", "info", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.INFO),
                            withMsg(equalTo("Message 'info' w/ arg=arg1.")))))
                    .or(mExpected.value));
        }
    }

    /** Test logging with {@link Log#WARN} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class WarnLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(logged())), false, false },
                { given(Log.DEBUG), expected(is(logged())), false, true },
                { given(Log.INFO), expected(is(logged())), false, false },
                { given(Log.INFO), expected(is(logged())), false, true },
                { given(Log.WARN), expected(is(logged())), false, false },
                { given(Log.WARN), expected(is(logged())), false, true },
                { given(Log.ERROR), expected(is(not(logged()))), false, false },
                { given(Log.ERROR), expected(is(not(logged()))), false, true },
                { given(Log.ERROR), expected(is(logged())), true, false },
                { given(Log.ERROR), expected(is(logged())), true, true },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard warn message.";
            mLogger.w(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.WARN),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard warn null-argument message.";
            mLogger.w(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.WARN),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.w("Message '%s' w/ arg=%s.", "warn", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.WARN),
                            withMsg(equalTo("Message 'warn' w/ arg=arg1.")))))
                    .or(mExpected.value));
        }
    }

    /** Test logging with {@link Log#ERROR} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class ErrorLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(logged())), false, false },
                { given(Log.DEBUG), expected(is(logged())), false, true },
                { given(Log.INFO), expected(is(logged())), false, false },
                { given(Log.INFO), expected(is(logged())), false, true },
                { given(Log.WARN), expected(is(logged())), false, false },
                { given(Log.WARN), expected(is(logged())), false, true },
                { given(Log.ERROR), expected(is(logged())), false, false },
                { given(Log.ERROR), expected(is(logged())), false, true },
                { given(Log.ASSERT), expected(is(not(logged()))), false, false },
                { given(Log.ASSERT), expected(is(not(logged()))), false, true },
                { given(Log.ASSERT), expected(is(logged())), true, false },
                { given(Log.ASSERT), expected(is(logged())), true, true },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard error message.";
            mLogger.e(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ERROR),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard error null-argument message.";
            mLogger.e(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ERROR),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.e("Message '%s' w/ arg=%s.", "error", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ERROR),
                            withMsg(equalTo("Message 'error' w/ arg=arg1.")))))
                    .or(mExpected.value));

            final var tr = new IllegalArgumentException();
            final var throwableMsg = "Standard error message w/ throwable.";
            mLogger.e(throwableMsg, tr);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ERROR),
                            withMsg(equalTo(throwableMsg)),
                            withThrowable(tr))))
                    .or(mExpected.value));
        }
    }

    /** Test logging with {@link Log#ASSERT} level. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class AssertLogging extends LoggingBase {
        @Parameters(name = "{0} as system log level, {1} when debug={2}, debugIsPersistent={3})")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(Log.VERBOSE), expected(is(logged())), false, false },
                { given(Log.VERBOSE), expected(is(logged())), false, true },
                { given(Log.DEBUG), expected(is(logged())), false, false },
                { given(Log.DEBUG), expected(is(logged())), false, true },
                { given(Log.INFO), expected(is(logged())), false, false },
                { given(Log.INFO), expected(is(logged())), false, true },
                { given(Log.WARN), expected(is(logged())), false, false },
                { given(Log.WARN), expected(is(logged())), false, true },
                { given(Log.ERROR), expected(is(logged())), false, false },
                { given(Log.ERROR), expected(is(logged())), false, true },
                { given(Log.ASSERT), expected(is(logged())), false, false },
                { given(Log.ASSERT), expected(is(logged())), false, true },
                // Simulate "SUPPRESS" log level
                { given(Log.ASSERT + 1), expected(is(not(logged()))), false, false },
                { given(Log.ASSERT + 1), expected(is(not(logged()))), false, false },
                { given(Log.ASSERT + 1), expected(is(logged())), true, false },
                { given(Log.ASSERT + 1), expected(is(logged())), true, false },
            });
        }

        @Test
        public void test() {
            final var standardMsg = "Standard assert message.";
            mLogger.wtf(standardMsg);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ASSERT),
                            withMsg(equalTo(standardMsg)))))
                    .or(mExpected.value));

            final var nullArgMsg = "Standard assert null-argument message.";
            mLogger.wtf(nullArgMsg, (Object[]) null);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ASSERT),
                            withMsg(equalTo(nullArgMsg)))))
                    .or(mExpected.value));

            mLogger.wtf("Message '%s' w/ arg=%s.", "assert", "arg1");
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ASSERT),
                            withMsg(equalTo("Message 'assert' w/ arg=arg1.")))))
                    .or(mExpected.value));

            final var tr = new IllegalArgumentException();
            mLogger.wtf(tr);
            assertThat(ShadowLog.getLogs(), either(containsInRelativeOrder(allOf(
                            withTag(endsWith(TAG)),
                            withType(Log.ASSERT),
                            withThrowable(tr))))
                    .or(mExpected.value));
        }
    }

    public static abstract class LoggingBase extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Object>> mExpected;

        @Parameter(2)
        public boolean mDebug;

        @Parameter(3)
        public boolean mDebugIsPersistent;

        @Override
        public void setUp() {
            super.setUp();

            overrideSysPropDebugEnabled(mDebug, mDebugIsPersistent);
            ShadowLog.setLoggable("7SIM." + TAG, mGiven.value);

            mLogger = mFactory.create(TAG);
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
        final String TAG = getClass().getSimpleName();

        @Inject
        Logger.Factory mFactory;

        Logger mLogger;
    }

    /**
     * Override debug state through {@link SysProp} which is read by {@link Logger} upon creation.
     */
    private static void overrideSysPropDebugEnabled(final boolean enabled,
            final boolean isPersistent) {

        new SysProp("debug", isPersistent).set(Optional.of(enabled ? "1" : "0"));
    }

    private static void overrideSysPropDebugEnabled(final boolean enabled) {
        overrideSysPropDebugEnabled(enabled, false);
    }

    /**
     * A matcher to be used in {@code either}/{@code or} combination to pass or fail the test when
     * evaluating the {@code or} block.
     */
    private static <T> Matcher<T> logged() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object o) {
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("logged");
            }
        };
    }
}
