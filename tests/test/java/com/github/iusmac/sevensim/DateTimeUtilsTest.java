package com.github.iusmac.sevensim;

import android.icu.text.DisplayContext;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.ULocale;
import android.os.Build;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.Locale;
import java.util.Optional;

import org.hamcrest.Matcher;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertThrows;

@RunWith(Enclosed.class)
public class DateTimeUtilsTest {
    /** Test if always getting an {@link Optional} object when parsing a stringified date-time
     * object. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class DateTimeParsing extends Base {
        @Parameter(0)
        public GivenHolder<String> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Optional<LocalDateTime>>> mExpected;

        @Parameters(name = "{0} as stringified date-time object, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(null), expected(is(Optional.empty())) },
                { given("junk"), expected(is(Optional.empty())) },
                { given("2007-12-03T10:15:30"), expected(is(Optional.of(LocalDateTime.of(2007, 12,
                                    3, 10, 15, 30)))) },
            });
        }

        @Test
        public void test_parseDateTime() {
            assertThat(DateTimeUtils.parseDateTime(mGiven.value), mExpected.value);
        }
    }

    /** Test obtaining a string formatted like "[relative time/date], [time]", that describes the
     * 'time' as a time relative to 'now'. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    // Note that, we want to test all SDKs we support as Android relies on ICU library that formats
    // dates/times slightly differently (e.g., narrow no-break space "U+202F" may be used instead of
    // a normal ASCII space "U+0020") on each Android version despite the APIs remained the same
    @Config(minSdk = Build.VERSION_CODES.Q)
    public static final class RelativeDateTimeSpanString extends Base {
        @Parameter(0)
        public GivenHolder<LocalDateTime> mGivenTime;

        @Parameter(1)
        public GivenHolder<LocalDateTime> mGivenNow;

        @Parameter(2)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameters(name = "{0} is ''time'' and {1} is ''now'', {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                // Zero day difference
                {
                    given(LocalDateTime.of(2007, 12, 3, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("today, 9:15")).and(endsWith("AM"))))
                },
                {
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("today, 10:15")).and(endsWith("AM"))))
                },
                {
                    given(LocalDateTime.of(2007, 12, 3, 11, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("today, 11:15")).and(endsWith("AM"))))
                },
                {   // Ensure we're d9 *NOT* fall into the "At least one year difference" case
                    given(LocalDateTime.of(2008, 1, 1, 0, 15)),
                    given(LocalDateTime.of(2007, 12, 31, 23, 5)),
                    expected(is(both(startsWith("tomorrow, 12:15")).and(endsWith("AM"))))
                },
                // Exactly one day difference
                {
                    given(LocalDateTime.of(2007, 12, 2, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("yesterday, 9:15")).and(endsWith("AM"))))
                },
                {
                    given(LocalDateTime.of(2007, 12, 4, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("tomorrow, 9:15")).and(endsWith("AM"))))
                },
                // At least two days difference
                {
                    given(LocalDateTime.of(2007, 12, 5, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("Wed, Dec 5, 9:15")).and(endsWith("AM"))))
                },
                {
                    given(LocalDateTime.of(2007, 12, 1, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("Sat, Dec 1, 9:15")).and(endsWith("AM"))))
                },
                // At least one year difference
                {
                    given(LocalDateTime.of(2008, 1, 3, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("Thu, 1/3/2008, 9:15")).and(endsWith("AM"))))
                },
                {
                    given(LocalDateTime.of(2006, 12, 3, 9, 15)),
                    given(LocalDateTime.of(2007, 12, 3, 10, 15)),
                    expected(is(both(startsWith("Sun, 12/3/2006, 9:15")).and(endsWith("AM"))))
                },
            });
        }

        private static final StringBuilder STRING_BUILDER = new StringBuilder(30);
        private static final Formatter FORMATTER = new Formatter(STRING_BUILDER, Locale.US);
        private static final RelativeDateTimeFormatter RELATIVE_FORMATTER =
            RelativeDateTimeFormatter.getInstance(ULocale.forLocale(FORMATTER.locale()),
                    /*NumberFormat*/ null, RelativeDateTimeFormatter.Style.SHORT,
                    DisplayContext.CAPITALIZATION_NONE);

        @Test
        public void test_getRelativeDateTimeSpanString() {
            assertThat(DateTimeUtils.getRelativeDateTimeSpanString(mApplicationContext, FORMATTER,
                        RELATIVE_FORMATTER, mGivenTime.value, mGivenNow.value).toString(),
                    mExpected.value);
        }

        @After
        public void tearDown() {
            // Reset the common StringBuilder length between tests to avoid results aggregation
            STRING_BUILDER.setLength(0);
        }
    }

    /** Test parsing of a stringified time as seen on a wall clock. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class WallClockParsing extends Base {
        @Test
        public void test_parseWallClockTime() {
            assertThrows(DateTimeParseException.class, () -> DateTimeUtils.parseWallClockTime("junk"));
            assertThat(DateTimeUtils.parseWallClockTime("8:15"), is(LocalTime.of(8, 15)));
            assertThat(DateTimeUtils.parseWallClockTime("08:15"), is(LocalTime.of(8, 15)));
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
    }
}
