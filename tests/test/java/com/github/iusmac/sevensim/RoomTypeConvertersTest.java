package com.github.iusmac.sevensim;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.scheduler.DaysOfWeek;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import java.time.LocalDateTime;
import java.time.LocalTime;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertThrows;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class RoomTypeConvertersTest extends MockitoHiltAndroidTestBase {
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2007, 1, 1, 10, 15, 5);
    private static final int DAYS_OF_WEEK_AS_BITS = /*Monday*/ 1<<2 | /*Friday*/ 1<<5;

    @Inject
    DaysOfWeek.Factory mDaysOfWeekFactory;

    @Inject
    RoomTypeConverters mRoomTypeConverters;

    @Test
    public void test_fromLocalDateTimeToString() {
        assertThat(mRoomTypeConverters.fromLocalDateTimeToString(null), is(nullValue()));
        assertThat(mRoomTypeConverters.fromLocalDateTimeToString(DATE_TIME.toString()),
                is(DATE_TIME));
        assertThat(mRoomTypeConverters.fromLocalDateTimeToString("junk"), is(nullValue()));
    }

    @Test
    public void test_fromStringToLocalDateTime() {
        assertThat(mRoomTypeConverters.fromStringToLocalDateTime(DATE_TIME),
                is(equalTo(DATE_TIME.toString())));
    }

    @Test
    public void test_fromBitsToDaysOfWeek() {
        assertThat(mRoomTypeConverters.fromBitsToDaysOfWeek(null).getBits(), equalTo(0));
        assertThat(mRoomTypeConverters.fromBitsToDaysOfWeek(DAYS_OF_WEEK_AS_BITS).getBits(),
                equalTo(DAYS_OF_WEEK_AS_BITS));
        assertThrows(IllegalArgumentException.class, () ->
                mRoomTypeConverters.fromBitsToDaysOfWeek(-1));
        assertThat(assertThrows(IllegalArgumentException.class, () ->
                mRoomTypeConverters.fromBitsToDaysOfWeek(DAYS_OF_WEEK_AS_BITS |
                    /*Invalid extra bit*/ 1<<7)).getMessage(),
                startsWith("Invalid extra bit(s) enabled 0b"));
    }

    @Test
    public void test_toDaysOfWeekBits() {
        assertThat(mRoomTypeConverters.toDaysOfWeekBits(null), is(nullValue()));
        final var daysOfWeek = mDaysOfWeekFactory.create(DAYS_OF_WEEK_AS_BITS);
        assertThat(mRoomTypeConverters.toDaysOfWeekBits(daysOfWeek), equalTo(DAYS_OF_WEEK_AS_BITS));
    }

    @Test
    public void test_fromMinutesSinceMidnight() {
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(null), is(nullValue()));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(-1), is(nullValue()));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(1440), is(nullValue()));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(0),
                is(both(equalTo(LocalTime.MIDNIGHT)).and(equalTo(LocalTime.MIN))));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(600), is(LocalTime.of(10, 0)));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(720), is(LocalTime.NOON));
        assertThat(mRoomTypeConverters.fromMinutesSinceMidnight(1439), is(LocalTime.of(23, 59)));
    }

    @Test
    public void test_toMinutesSinceMidnight() {
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(null), is(nullValue()));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.MIDNIGHT), is(0));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.MIN), is(0));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.of(10, 0)), is(600));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.NOON), is(720));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.of(23, 59)), is(1439));
        assertThat(mRoomTypeConverters.toMinutesSinceMidnight(LocalTime.MAX), is(1439));
    }
}
