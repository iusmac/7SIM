package com.github.iusmac.sevensim.scheduler;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.hamcrest.Matcher;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static java.util.Calendar.SUNDAY; // 1
import static java.util.Calendar.MONDAY; // 2
import static java.util.Calendar.TUESDAY; // 3
import static java.util.Calendar.WEDNESDAY; // 4
import static java.util.Calendar.THURSDAY; // 5
import static java.util.Calendar.FRIDAY; // 6
import static java.util.Calendar.SATURDAY; // 7

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertThrows;

@RunWith(Enclosed.class)
public class DaysOfWeekTest {
    private static final Locale SATURDAY_BASED_LOCALE = new Locale("ar", "EG"); // Egypt
    private static final Locale SUNDAY_BASED_LOCALE = Locale.US;
    private static final Locale MONDAY_BASED_LOCALE = Locale.ITALY;

    /** Testing only the constructor behavior when instantiating through the factory. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class Constructor extends Base {
        @Test
        public void test_emptyConstructor() {
            final var daysOfWeek = mFactory.create();
            assertThat(daysOfWeek.getBits(), is(0));
            assertThat(daysOfWeek.getCount(), is(0));
            assertThat(daysOfWeek.isRepeating(), is(not(true)));
            assertThat(daysOfWeek.isFullWeek(), is(not(true)));
            assertThat(daysOfWeek.getDistanceToPreviousDayOfWeek(SUNDAY), is(OptionalInt.empty()));
            assertThat(daysOfWeek.getDistanceToNextDayOfWeek(SUNDAY), is(OptionalInt.empty()));
            assertThat(daysOfWeek.toString(/*useLongNames=*/ false), is(emptyString()));
            assertThat(daysOfWeek.toString(/*useLongNames=*/ true), is(emptyString()));
            assertThat(IntStream
                    .rangeClosed(SUNDAY, SATURDAY)
                    .filter(daysOfWeek::isBitOn).count(), is(0L));
            assertThat(daysOfWeek, is(greaterThan(mFactory.create(/*Sunday*/ 1<<0))));
            assertThat(daysOfWeek, is(mFactory.create(/*bits=*/ 0)));
            assertThat(daysOfWeek.hashCode(), is(0));
            assertThat(daysOfWeek.toString(), is(equalTo("[]")));
        }

        @Test
        public void test_constructingUsingBits() {
            final var expectedBits = /*Wednesday*/ (1<<3) | /*Thursday*/ (1<<4) | /*Friday*/ (1<<5);
            final var daysOfWeek = mFactory.create(expectedBits);
            assertThat(daysOfWeek.getBits(), is(expectedBits));
        }

        @Test
        public void test_constructingUsingDaysOfWeek() {
            final var daysOfWeek = mFactory.create(new int[] { WEDNESDAY, THURSDAY, FRIDAY });
            final var expectedBits = /*Wednesday*/ (1<<3) | /*Thursday*/ (1<<4) | /*Friday*/ (1<<5);
            assertThat(daysOfWeek.getBits(), is(expectedBits));
        }

        @Test
        public void test_constructingUsingBitsAndDaysOfWeek_BitsTakePrecedence() {
            final var expectedBits = /*Sunday*/ (1<<0) | /*Monday*/ (1<<1) | /*Tuesday*/ (1<<2);
            final var daysOfWeekAsInts = new Integer[] { WEDNESDAY, THURSDAY, FRIDAY };
            final var daysOfWeek = mFactory.create(expectedBits, daysOfWeekAsInts);
            assertThat(daysOfWeek.getBits(), is(expectedBits));
        }

        @Test
        public void test_constructingInvalidBits() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(-1)).getMessage(),
                    startsWith("Invalid extra bit(s) enabled 0b"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(1<<7)).getMessage(),
                    startsWith("Invalid extra bit(s) enabled 0b"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(/*Saturday*/ (1<<6) | (1<<7))).getMessage(),
                    startsWith("Invalid extra bit(s) enabled 0b"));
        }

        @Test
        public void test_constructingWithInvalidDaysOfWeek() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(new int[] { 0 })).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(new int[] { 8 })).getMessage(),
                    startsWith("Invalid day of week:"));

            // Test mixing valid & invalid days of week
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mFactory.create(new int[] { SATURDAY, 8 })).getMessage(),
                    startsWith("Invalid day of week:"));
        }
    }

    /** Test interaction with nonexistent days of the week. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class NonexistentDaysOfWeek extends Base {
        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(new int[] { SUNDAY, MONDAY, TUESDAY });
        }

        @Test
        public void test_constructingWithMixedDaysOfWeekAndNullValues() {
            final var withMixed = mFactory.create(new Integer[] { null, SUNDAY, null, MONDAY });
            assertThat(IntStream
                    .rangeClosed(SUNDAY, SATURDAY)
                    .mapToObj(withMixed::isBitOn)
                    .toArray(Boolean[]::new),
                is(arrayContaining(true, true, false, false, false, false, false)));
        }

        @Test
        public void test_isBitOn() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mDaysOfWeek.isBitOn(0)).getMessage(), startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mDaysOfWeek.isBitOn(8)).getMessage(), startsWith("Invalid day of week:"));
        }

        @Test
        public void test_getDistanceToPreviousDayOfWeek() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mDaysOfWeek.getDistanceToPreviousDayOfWeek(0)).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        mDaysOfWeek.getDistanceToPreviousDayOfWeek(8)).getMessage(),
                    startsWith("Invalid day of week:"));
        }

        @Test
        public void test_getDistanceToNextDayOfWeek() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                    mDaysOfWeek.getDistanceToNextDayOfWeek(0)).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                    mDaysOfWeek.getDistanceToNextDayOfWeek(8)).getMessage(),
                    startsWith("Invalid day of week:"));
        }

        @Test
        public void test_getDisplayName() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        DaysOfWeek.getDisplayName(0, true)).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                    DaysOfWeek.getDisplayName(0, true, Locale.getDefault())).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                    DaysOfWeek.getDisplayName(8, false)).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                    DaysOfWeek.getDisplayName(8, false, Locale.getDefault())).getMessage(),
                    startsWith("Invalid day of week:"));
        }

        @Test
        public void test_getNarrowDisplayName() {
            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        DaysOfWeek.getNarrowDisplayName(0)).getMessage(),
                    startsWith("Invalid day of week:"));

            assertThat(assertThrows(IllegalArgumentException.class, () ->
                        DaysOfWeek.getNarrowDisplayName(8)).getMessage(),
                    startsWith("Invalid day of week:"));
        }
    }

    /** Test if the given days of the week are on. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class DaysAreEnabled extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Boolean[]>> mExpected;

        @Parameters(name = "{0} as days of week, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(null), expected(is(arrayContaining(false, false, false, false, false, false,
                                false))) },

                { given(new int[0]), expected(is(arrayContaining(false, false, false, false, false,
                                false, false))) },

                { given(new int[] { SUNDAY }), expected(is(arrayContaining(true, false, false,
                                false, false, false, false))) },

                { given(new int[] { SUNDAY, MONDAY }), expected(is(arrayContaining(true, true,
                                false, false, false, false, false))) },

                { given(new int[] { SUNDAY, MONDAY, TUESDAY }), expected(is(arrayContaining(true,
                                true, true, false, false, false, false))) },

                { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }),
                    expected(is(arrayContaining(true, true, true, true, false, false, false))) },

                { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY }),
                    expected(is(arrayContaining(true, true, true, true, true, false, false))) },

                { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }),
                    expected(is(arrayContaining(true, true, true, true, true, true, false))) },

                { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY }),
                      expected(is(arrayContaining(true, true, true, true, true, true, true))) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_isBitOn() {
            assertThat(IntStream
                    .rangeClosed(SUNDAY, SATURDAY)
                    .mapToObj(mDaysOfWeek::isBitOn)
                    .toArray(Boolean[]::new), mExpected.value);
        }
    }

    /** Test the correct encoding of {@link DayOfWeek} values to {@link DaysOfWeek#getBits}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class BitsEncoding extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer>> mExpected;

        @Parameters(name = "{0} as days of week, {1} as bits")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(null), expected(is(0)) },
                    { given(new int[0]), expected(is(0)) },
                    { given(new int[] { SUNDAY }), expected(is(0b0000001)) },
                    { given(new int[] { SUNDAY, MONDAY }), expected(is(0b0000011)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY } ), expected(is(0b0000111)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY } ),
                        expected(is(0b0001111)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY } ),
                        expected(is(0b0011111)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY } ),
                        expected(is(0b0111111)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
                                          SATURDAY } ), expected(is(0b1111111)) }
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_getBits() {
            assertThat(mDaysOfWeek.getBits(), mExpected.value);
        }
    }

    /** Test whether the given encoded weekly repeat cycle using bits is correctly decoded. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class BitsDecoding extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer[]>> mExpected;

        @Parameters(name = "{0} as bits, {1} as days of week")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(0), expected(is(emptyArray())) },
                    { given(1<<0), expected(is(arrayContaining(SUNDAY))) },
                    { given((1<<0) | (1<<1)), expected(is(arrayContaining(SUNDAY, MONDAY))) },
                    { given((1<<0) | (1<<1) | (1<<2)), expected(is(arrayContaining(SUNDAY, MONDAY,
                                    TUESDAY))) },
                    { given((1<<0) | (1<<1) | (1<<2) | (1<<3)), expected(is(arrayContaining(SUNDAY,
                                    MONDAY, TUESDAY, WEDNESDAY))) },
                    { given((1<<0) | (1<<1) | (1<<2) | (1<<3) | (1<<4)),
                        expected(is(arrayContaining(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY))) },
                    { given((1<<0) | (1<<1) | (1<<2) | (1<<3) | (1<<4) | (1<<5)),
                        expected(is(arrayContaining(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY,
                                        FRIDAY))) },
                    { given((1<<0) | (1<<1) | (1<<2) | (1<<3) | (1<<4) | (1<<5) | (1<<6)),
                        expected(is(arrayContaining(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY,
                                        FRIDAY, SATURDAY))) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_getBits() {
            assertThat(IntStream
                    .rangeClosed(SUNDAY, SATURDAY)
                    .filter(mDaysOfWeek::isBitOn)
                    .boxed()
                    .toArray(Integer[]::new), mExpected.value);
        }
    }

    /** Test repetition of the weekly repeat cycle. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class WeeklyCycleRepeation extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Boolean>> mExpected;

        @Parameters(name = "{0} as days of week, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(null), expected(is(false)) },
                    { given(new int[0]), expected(is(false)) },
                    { given(new int[] { SUNDAY }), expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY }), expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY }), expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }), expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY }),
                        expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }),
                        expected(is(true)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
                                          SATURDAY }), expected(is(true)) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_isRepeating() {
            assertThat(mDaysOfWeek.isRepeating(), mExpected.value);
        }
    }

    /** Test if is full week when all days of the week are enabled. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class FullWeek extends Base {
        @Parameter(0)
        public GivenHolder<Integer[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Boolean>> mExpected;

        @Parameters(name = "{0} as days of week, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(null), expected(is(false)) },
                    { given(new Integer[0]), expected(is(false)) },
                    { given(new Integer[] { SUNDAY }), expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY }), expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY }), expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }),
                        expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY }),
                        expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }),
                        expected(is(false)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
                                              SATURDAY }), expected(is(true)) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_isFullWeek() {
            assertThat(mDaysOfWeek.isFullWeek(), mExpected.value);
        }
    }

    /** Test counting of enabled {@link DayOfWeek} values. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class CountingEnabledDays extends Base {
        @Parameter(0)
        public GivenHolder<Integer[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer>> mExpected;

        @Parameters(name = "{0} as days of week, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(null), expected(is(0)) },
                    { given(new Integer[0]), expected(is(0)) },
                    { given(new Integer[] { SUNDAY }), expected(is(1)) },
                    { given(new Integer[] { SUNDAY, MONDAY }), expected(is(2)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY }), expected(is(3)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }), expected(is(4)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY }),
                        expected(is(5)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }),
                        expected(is(6)) },
                    { given(new Integer[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
                                              SATURDAY }), expected(is(7)) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_getCount() {
            assertThat(mDaysOfWeek.getCount(), mExpected.value);
        }
    }

    /** Test finding the distance to the previous enabled day of week. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class DistanceToPreviousDayOfWeek extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public GivenHolder<int[]> mEnabledDaysOfWeek;

        @Parameter(2)
        public ExpectedHolder<Matcher<OptionalInt>> mExpected;

        @Parameters(name = "{0} as compare day of week and {1} as enabled days of week, {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                   { given(SUNDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(MONDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(TUESDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(WEDNESDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(THURSDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(FRIDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(SATURDAY), given(null), expected(is(OptionalInt.empty())) },

                   { given(SUNDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(7))) },
                   { given(MONDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(1))) },
                   { given(TUESDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(2))) },
                   { given(WEDNESDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(3))) },
                   { given(THURSDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(4))) },
                   { given(FRIDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(5))) },
                   { given(SATURDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(6))) },

                   { given(SUNDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(3))) },
                   { given(MONDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(4))) },
                   { given(TUESDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(1))) },
                   { given(WEDNESDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(2))) },
                   { given(THURSDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(3))) },
                   { given(FRIDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(1))) },
                   { given(SATURDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(2))) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mEnabledDaysOfWeek.value);
        }

        @Test
        public void test_getDistanceToPreviousDayOfWeek() {
            assertThat(mDaysOfWeek.getDistanceToPreviousDayOfWeek(mGiven.value), mExpected.value);
        }
    }

    /** Test finding the distance to the next enabled day of week. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class DistanceToNextDayOfWeek extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public GivenHolder<int[]> mEnabledDaysOfWeek;

        @Parameter(2)
        public ExpectedHolder<Matcher<OptionalInt>> mExpected;

        @Parameters(name = "{0} as compare day of week and {1} as enabled days of week, {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                   { given(SUNDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(MONDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(TUESDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(WEDNESDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(THURSDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(FRIDAY), given(null), expected(is(OptionalInt.empty())) },
                   { given(SATURDAY), given(null), expected(is(OptionalInt.empty())) },

                   { given(SUNDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(0))) },
                   { given(MONDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(6))) },
                   { given(TUESDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(5))) },
                   { given(WEDNESDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(4))) },
                   { given(THURSDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(3))) },
                   { given(FRIDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(2))) },
                   { given(SATURDAY), given(new int[] { SUNDAY }), expected(is(OptionalInt.of(1))) },

                   { given(SUNDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(1))) },
                   { given(MONDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(0))) },
                   { given(TUESDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(2))) },
                   { given(WEDNESDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(1))) },
                   { given(THURSDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(0))) },
                   { given(FRIDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(3))) },
                   { given(SATURDAY), given(new int[] { MONDAY, THURSDAY }),
                       expected(is(OptionalInt.of(2))) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mEnabledDaysOfWeek.value);
        }

        @Test
        public void test_getDistanceToNextDayOfWeek() {
            assertThat(mDaysOfWeek.getDistanceToNextDayOfWeek(mGiven.value), mExpected.value);
        }
    }


    /** Test conversion to command-separate, human-readable format. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class ConversionToHumanReadableString extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameter(2)
        public boolean mUseLongNames;

        @Parameter(3)
        public Locale mLocale;

        @Parameters(name = "{0} as enabled days of week, {1} when useLongNames: {2}, locale: {3}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(null), expected(is(emptyString())), true, SUNDAY_BASED_LOCALE },
                { given(new int[0]), expected(is(emptyString())), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { SUNDAY }), expected(is("Sunday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { MONDAY }), expected(is("Monday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY }), expected(is("Tuesday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY }), expected(is("Wednesday")), true,
                    SUNDAY_BASED_LOCALE },
                { given(new int[] { THURSDAY }), expected(is("Thursday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { FRIDAY }), expected(is("Friday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { SATURDAY }), expected(is("Saturday")), true, SUNDAY_BASED_LOCALE },

                { given(new int[] { FRIDAY, SUNDAY }), expected(is("Sunday, Friday")), true,
                    SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY }),
                    expected(is("Sunday, Tuesday, Friday")), true, SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY, WEDNESDAY, SATURDAY, MONDAY, THURSDAY }),
                    expected(is("Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday")),
                    true, SUNDAY_BASED_LOCALE },

                { given(null), expected(is(emptyString())), false, SUNDAY_BASED_LOCALE },
                { given(new int[0]), expected(is(emptyString())), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { SUNDAY }), expected(is("Sun")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { MONDAY }), expected(is("Mon")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY }), expected(is("Tue")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY }), expected(is("Wed")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { THURSDAY }), expected(is("Thu")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { FRIDAY }), expected(is("Fri")), false, SUNDAY_BASED_LOCALE },
                { given(new int[] { SATURDAY }), expected(is("Sat")), false, SUNDAY_BASED_LOCALE },

                { given(new int[] { FRIDAY, SUNDAY }), expected(is("Sun, Fri")), false,
                    SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY }), expected(is("Sun, Tue, Fri")),
                    false, SUNDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY, WEDNESDAY, SATURDAY, MONDAY, THURSDAY }),
                    expected(is("Sun, Mon, Tue, Wed, Thu, Fri, Sat")), false, SUNDAY_BASED_LOCALE },

                { given(null), expected(is(emptyString())), true, MONDAY_BASED_LOCALE },
                { given(new int[0]), expected(is(emptyString())), true, MONDAY_BASED_LOCALE },
                { given(new int[] { MONDAY }), expected(is("lunedì")), true, MONDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY }), expected(is("martedì")), true, MONDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY }), expected(is("mercoledì")), true,
                    MONDAY_BASED_LOCALE },
                { given(new int[] { THURSDAY }), expected(is("giovedì")), true, MONDAY_BASED_LOCALE },
                { given(new int[] { FRIDAY }), expected(is("venerdì")), true, MONDAY_BASED_LOCALE },
                { given(new int[] { SATURDAY }), expected(is("sabato")), true, MONDAY_BASED_LOCALE },
                { given(new int[] { SUNDAY }), expected(is("domenica")), true, MONDAY_BASED_LOCALE },

                { given(new int[] { FRIDAY, SUNDAY }), expected(is("venerdì, domenica")), true,
                    MONDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY, SATURDAY, MONDAY }), expected(is("lunedì, mercoledì, sabato")),
                    true, MONDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY, WEDNESDAY, SATURDAY, MONDAY, THURSDAY }),
                    expected(is("lunedì, martedì, mercoledì, giovedì, venerdì, sabato, domenica")),
                    true, MONDAY_BASED_LOCALE },

                { given(null), expected(is(emptyString())), false, MONDAY_BASED_LOCALE },
                { given(new int[0]), expected(is(emptyString())), false, MONDAY_BASED_LOCALE },
                { given(new int[] { MONDAY }), expected(is("lun")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY }), expected(is("mar")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY }), expected(is("mer")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { THURSDAY }), expected(is("gio")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { FRIDAY }), expected(is("ven")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { SATURDAY }), expected(is("sab")), false, MONDAY_BASED_LOCALE },
                { given(new int[] { SUNDAY }), expected(is("dom")), false, MONDAY_BASED_LOCALE },

                { given(new int[] { FRIDAY, SUNDAY }), expected(is("ven, dom")), false,
                    MONDAY_BASED_LOCALE },
                { given(new int[] { WEDNESDAY, SATURDAY, MONDAY }), expected(is("lun, mer, sab")),
                    false, MONDAY_BASED_LOCALE },
                { given(new int[] { TUESDAY, FRIDAY, SUNDAY, WEDNESDAY, SATURDAY, MONDAY, THURSDAY }),
                    expected(is("lun, mar, mer, gio, ven, sab, dom")), false, MONDAY_BASED_LOCALE },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            Locale.setDefault(mLocale);

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_toString() {
            assertThat(mDaysOfWeek.toString(mUseLongNames), mExpected.value);
        }
    }

    /** Test querying textual representation of a {@link DayOfWeek}, such as "Fri" or "Friday". */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class QueryingDisplayNames extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameter(2)
        public boolean mUseLongName;

        @Parameter(3)
        public Locale mLocale;

        @Parameters(name = "{0} as day of week, {1} when useLongName: {2}, locale: {3}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(SUNDAY), expected(is("Sunday")), true, SUNDAY_BASED_LOCALE },
                { given(MONDAY), expected(is("Monday")), true, SUNDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("Tuesday")), true, SUNDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("Wednesday")), true, SUNDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("Thursday")), true, SUNDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("Friday")), true, SUNDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("Saturday")), true, SUNDAY_BASED_LOCALE },

                { given(SUNDAY), expected(is("Sun")), false, SUNDAY_BASED_LOCALE },
                { given(MONDAY), expected(is("Mon")), false, SUNDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("Tue")), false, SUNDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("Wed")), false, SUNDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("Thu")), false, SUNDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("Fri")), false, SUNDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("Sat")), false, SUNDAY_BASED_LOCALE },

                { given(MONDAY), expected(is("lunedì")), true, MONDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("martedì")), true, MONDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("mercoledì")), true, MONDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("giovedì")), true, MONDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("venerdì")), true, MONDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("sabato")), true, MONDAY_BASED_LOCALE },
                { given(SUNDAY), expected(is("domenica")), true, MONDAY_BASED_LOCALE },

                { given(MONDAY), expected(is("lun")), false, MONDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("mar")), false, MONDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("mer")), false, MONDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("gio")), false, MONDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("ven")), false, MONDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("sab")), false, MONDAY_BASED_LOCALE },
                { given(SUNDAY), expected(is("dom")), false, MONDAY_BASED_LOCALE },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            Locale.setDefault(mLocale);
        }

        @Test
        public void test_getDisplayName() {
            assertThat(DaysOfWeek.getDisplayName(mGiven.value, mUseLongName), mExpected.value);
            assertThat(DaysOfWeek.getDisplayName(mGiven.value, mUseLongName, mLocale),
                    mExpected.value);
        }
    }

    /** Test querying textual representation of a {@link DayOfWeek}, such as "S", "M", etc. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class QueryingNarrowDisplayNames extends Base {
        @Parameter(0)
        public GivenHolder<Integer> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameter(2)
        public Locale mLocale;

        @Parameters(name = "{0} as day of week, {1} when locale: {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(SUNDAY), expected(is("S")), SUNDAY_BASED_LOCALE },
                { given(MONDAY), expected(is("M")), SUNDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("T")), SUNDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("W")), SUNDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("T")), SUNDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("F")), SUNDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("S")), SUNDAY_BASED_LOCALE },

                { given(MONDAY), expected(is("L")), MONDAY_BASED_LOCALE },
                { given(TUESDAY), expected(is("M")), MONDAY_BASED_LOCALE },
                { given(WEDNESDAY), expected(is("M")), MONDAY_BASED_LOCALE },
                { given(THURSDAY), expected(is("G")), MONDAY_BASED_LOCALE },
                { given(FRIDAY), expected(is("V")), MONDAY_BASED_LOCALE },
                { given(SATURDAY), expected(is("S")), MONDAY_BASED_LOCALE },
                { given(SUNDAY), expected(is("D")), MONDAY_BASED_LOCALE },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            Locale.setDefault(mLocale);
        }

        @Test
        public void test_getNarrowDisplayName() {
            assertThat(DaysOfWeek.getNarrowDisplayName(mGiven.value), mExpected.value);
        }
    }

    /** Test {@link Iterator}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class Iteration extends Base {
        @Parameter(0)
        public GivenHolder<Locale> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer[]>> mExpected;

        @Parameters(name = "{0} as default locale, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(SUNDAY_BASED_LOCALE), expected(is(arrayContaining(SUNDAY, MONDAY, TUESDAY,
                                WEDNESDAY, THURSDAY, FRIDAY, SATURDAY))) },

                { given(MONDAY_BASED_LOCALE), expected(is(arrayContaining(MONDAY, TUESDAY,
                                WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY))) },

                { given(SATURDAY_BASED_LOCALE), expected(is(arrayContaining(SATURDAY, SUNDAY,
                                MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY))) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            Locale.setDefault(mGiven.value);

            mDaysOfWeek = mFactory.create();
        }

        @Test
        public void test_iterator() {
            final var it = mDaysOfWeek.iterator();

            assertThat(Stream
                    .iterate(it, Iterator::hasNext, UnaryOperator.identity())
                    .map(Iterator::next)
                    .map((x) -> {
                        // Iterator is unable to remove this element after call to next()
                        assertThrows(UnsupportedOperationException.class, () -> it.remove());
                        return x;
                    })
                    .toArray(Integer[]::new), mExpected.value);

            // No more elements remaining
            assertThrows(NoSuchElementException.class, () -> it.next());
        }
    }

    /** Test querying the {@link DayOfWeek} value from a date-time object. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class QueryingDayOfWeekFromLocalDateTime extends Base {
        @Parameter(0)
        public GivenHolder<LocalDateTime> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer>> mExpected;

        @Parameters(name = "{0}, {1}")
        public static Collection<Object[]> params() {
            final var ldtOnSunday = LocalDateTime.of(2024, 8, 25, 8, 0, 0);
            return Arrays.asList(new Object[][] {
                { given(ldtOnSunday), expected(is(SUNDAY)) },
                { given(ldtOnSunday.plusDays(1)), expected(is(MONDAY)) },
                { given(ldtOnSunday.plusDays(2)), expected(is(TUESDAY)) },
                { given(ldtOnSunday.plusDays(3)), expected(is(WEDNESDAY)) },
                { given(ldtOnSunday.plusDays(4)), expected(is(THURSDAY)) },
                { given(ldtOnSunday.plusDays(5)), expected(is(FRIDAY)) },
                { given(ldtOnSunday.plusDays(6)), expected(is(SATURDAY)) },
            });
        }

        @Test
        public void test_getDayOfWeekFrom() {
            assertThat(DaysOfWeek.getDayOfWeekFrom(mGiven.value), mExpected.value);
        }
    }

    /** Test the comparison via {@link DaysOfWeek#compareTo(int)}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class Comparison extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven1;

        @Parameter(1)
        public GivenHolder<int[]> mGiven2;

        @Parameter(2)
        public ExpectedHolder<Matcher<Integer>> mExpected;

        @Parameter(3)
        public Locale mLocale;

        @Parameters(name = "{0} and {1} as enabled days of week, {2} when locale: {3}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                // Sunday-based comparisons
                {
                    given(null),
                    given(null),
                    expected(comparesEqualTo(0)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[0]),
                    expected(comparesEqualTo(0)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { SUNDAY }),
                    expected(comparesEqualTo(0)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[0]),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[] { SUNDAY }),
                    expected(greaterThanOrEqualTo(1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { MONDAY }),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[] { SUNDAY }),
                    expected(greaterThanOrEqualTo(0)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { SATURDAY }),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[] { SUNDAY }),
                    expected(greaterThanOrEqualTo(1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY, MONDAY }),
                    given(new int[] { SUNDAY }),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { SUNDAY, MONDAY }),
                    expected(greaterThanOrEqualTo(1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY, MONDAY, TUESDAY }),
                    given(new int[] { WEDNESDAY, THURSDAY, FRIDAY }),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { WEDNESDAY, THURSDAY, FRIDAY }),
                    given(new int[] { SUNDAY, MONDAY, TUESDAY }),
                    expected(greaterThanOrEqualTo(1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }),
                    given(new int[] { TUESDAY, FRIDAY, SATURDAY, SUNDAY }),
                    expected(lessThanOrEqualTo(-1)), SUNDAY_BASED_LOCALE
                },
                {
                    given(new int[] { TUESDAY, FRIDAY, SATURDAY, SUNDAY }),
                    given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY }),
                    expected(greaterThanOrEqualTo(1)), SUNDAY_BASED_LOCALE
                },

                // Monday-based comparisons
                {
                    given(null),
                    given(null),
                    expected(comparesEqualTo(0)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[0]),
                    expected(comparesEqualTo(0)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[] { MONDAY }),
                    expected(comparesEqualTo(0)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[0]),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[] { MONDAY }),
                    expected(greaterThanOrEqualTo(1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[] { TUESDAY }),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { TUESDAY }),
                    given(new int[] { MONDAY }),
                    expected(greaterThanOrEqualTo(1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[] { SUNDAY }),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { MONDAY }),
                    expected(greaterThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY, TUESDAY }),
                    given(new int[] { MONDAY }),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY }),
                    given(new int[] { MONDAY, TUESDAY }),
                    expected(greaterThanOrEqualTo(1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY, TUESDAY, WEDNESDAY }),
                    given(new int[] { THURSDAY, FRIDAY, SATURDAY }),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { THURSDAY, FRIDAY, SATURDAY }),
                    given(new int[] { MONDAY, TUESDAY, WEDNESDAY }),
                    expected(greaterThanOrEqualTo(1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { MONDAY, WEDNESDAY, FRIDAY, SUNDAY }),
                    given(new int[] { TUESDAY, THURSDAY, SATURDAY, SUNDAY }),
                    expected(lessThanOrEqualTo(-1)), MONDAY_BASED_LOCALE
                },
                {
                    given(new int[] { TUESDAY, THURSDAY, SATURDAY, SUNDAY }),
                    given(new int[] { MONDAY, WEDNESDAY, FRIDAY, SUNDAY }),
                    expected(greaterThanOrEqualTo(1)), MONDAY_BASED_LOCALE
                },

                // Saturday-based comparisons
                {
                    given(null),
                    given(null),
                    expected(comparesEqualTo(0)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[0]),
                    expected(comparesEqualTo(0)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[] { SATURDAY }),
                    expected(comparesEqualTo(0)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[0]),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[0]),
                    given(new int[] { SATURDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[] { SUNDAY }),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY }),
                    given(new int[] { SATURDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[] { FRIDAY }),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { FRIDAY }),
                    given(new int[] { SATURDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY, SUNDAY }),
                    given(new int[] { SATURDAY }),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY }),
                    given(new int[] { SATURDAY, SUNDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY, SUNDAY }),
                    given(new int[] { SATURDAY, SUNDAY }),
                    expected(comparesEqualTo(0)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY, SUNDAY, MONDAY }),
                    given(new int[] { TUESDAY, WEDNESDAY, THURSDAY }),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { TUESDAY, WEDNESDAY, THURSDAY }),
                    given(new int[] { SATURDAY, SUNDAY, MONDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SATURDAY, MONDAY, WEDNESDAY, SUNDAY }),
                    given(new int[] { SUNDAY, TUESDAY, THURSDAY, FRIDAY }),
                    expected(lessThanOrEqualTo(-1)), SATURDAY_BASED_LOCALE
                },
                {
                    given(new int[] { SUNDAY, TUESDAY, THURSDAY, FRIDAY }),
                    given(new int[] { SATURDAY, MONDAY, WEDNESDAY, SUNDAY }),
                    expected(greaterThanOrEqualTo(1)), SATURDAY_BASED_LOCALE
                },
            });
        }

        private DaysOfWeek mDaysOfWeek2;

        @Override
        public void setUp() {
            super.setUp();

            Locale.setDefault(mLocale);

            mDaysOfWeek = mFactory.create(mGiven1.value);
            mDaysOfWeek2 = mFactory.create(mGiven2.value);
        }

        @Test
        public void test_compareTo() {
            assertThat(mDaysOfWeek.compareTo(mDaysOfWeek2), mExpected.value);
        }
    }

    /** Test the equality via {@link DaysOfWeek#equals}. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class Equality extends Base {
        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(new int[] { SUNDAY, MONDAY });
        }

        @Test
        public void test_equals() {
            assertThat(mDaysOfWeek, is(mDaysOfWeek));
            assertThat(mDaysOfWeek, is(mFactory.create(mDaysOfWeek.getBits())));
            assertThat(mDaysOfWeek, is(not(equalTo(null))));
            assertThat(mDaysOfWeek, is(not(new Object())));
            assertThat(mDaysOfWeek, is(not(mFactory.create())));
        }
    }

    /** Test the hash code number generation via {@link DaysOfWeek#hashCode}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class HashCode extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<Integer>> mExpected;

        @Parameters(name = "{0} as days of week, {1} as hash code number")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                    { given(null), expected(is(0)) },
                    { given(new int[0]), expected(is(0)) },
                    { given(new int[] { SUNDAY }), expected(is(1)) },
                    { given(new int[] { SUNDAY, MONDAY }), expected(is(3)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY } ), expected(is(7)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY } ), expected(is(15)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY } ),
                        expected(is(31)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY } ),
                        expected(is(63)) },
                    { given(new int[] { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY,
                                          SATURDAY } ), expected(is(127)) }
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_getBits() {
            assertThat(mDaysOfWeek.getBits(), mExpected.value);
        }
    }

    /** Test conversion to string via {@link DaysOfWeek#toString()} for debugging purpose. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class ConversionToStringForDebugging extends Base {
        @Parameter(0)
        public GivenHolder<int[]> mGiven;

        @Parameter(1)
        public ExpectedHolder<Matcher<String>> mExpected;

        @Parameters(name = "{0} as day of week, {1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(null), expected(is("[]")) },
                { given(new int[0]), expected(is("[]")) },
                { given(new int[] { SUNDAY } ), expected(is("[Su]")) },
                { given(new int[] { MONDAY } ), expected(is("[M]")) },
                { given(new int[] { TUESDAY } ), expected(is("[T]")) },
                { given(new int[] { WEDNESDAY } ), expected(is("[W]")) },
                { given(new int[] { THURSDAY } ), expected(is("[Th]")) },
                { given(new int[] { FRIDAY } ), expected(is("[F]")) },
                { given(new int[] { SATURDAY } ), expected(is("[Sa]")) },

                { given(new int[] { SUNDAY, MONDAY } ), expected(is("[Su M]")) },
                { given(new int[] { MONDAY, TUESDAY } ), expected(is("[M T]")) },
                { given(new int[] { TUESDAY, WEDNESDAY } ), expected(is("[T W]")) },
                { given(new int[] { WEDNESDAY, THURSDAY } ), expected(is("[W Th]")) },
                { given(new int[] { THURSDAY, FRIDAY } ), expected(is("[Th F]")) },
                { given(new int[] { FRIDAY, SATURDAY } ), expected(is("[F Sa]")) },
                { given(new int[] { SATURDAY, SUNDAY } ), expected(is("[Su Sa]")) },
            });
        }

        @Override
        public void setUp() {
            super.setUp();

            mDaysOfWeek = mFactory.create(mGiven.value);
        }

        @Test
        public void test_getNarrowDisplayName() {
            assertThat(mDaysOfWeek.toString(), mExpected.value);
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
        @Inject
        DaysOfWeek.Factory mFactory;

        protected DaysOfWeek mDaysOfWeek;
    }
}
