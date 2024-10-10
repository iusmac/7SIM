package com.github.iusmac.sevensim.scheduler;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.os.UserManager;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.ExceptionUtils;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.PhoneCallEndObserverService;
import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.telephony.SimState;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.SubscriptionsImpl;
import com.github.iusmac.sevensim.telephony.TelephonyController;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerHiddenApi;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerOnSubscriptionsChangedListener;
import com.github.iusmac.sevensim.test.ShadowTelephonyManagerHiddenApi;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import org.hamcrest.Matcher;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;

import static org.awaitility.Awaitility.await;

import static com.github.iusmac.sevensim.scheduler.DayOfWeek.FRIDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.MONDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.SATURDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.SUNDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.THURSDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.TUESDAY;
import static com.github.iusmac.sevensim.scheduler.DayOfWeek.WEDNESDAY;
import static com.github.iusmac.sevensim.scheduler.SubscriptionScheduler.getDateTimeAfter;
import static com.github.iusmac.sevensim.scheduler.SubscriptionScheduler.getDateTimeBefore;
import static com.github.iusmac.sevensim.scheduler.SubscriptionScheduler.getSubscriptionExpectedEnabledState;
import static com.github.iusmac.sevensim.test.SameBundleMatcher.sameBundle;
import static com.github.iusmac.sevensim.test.TestUtils.expected;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.robolectric.Shadows.shadowOf;

@RunWith(Enclosed.class)
public final class SubscriptionSchedulerTest {
    private static final LocalDateTime MONDAY_01_00_55PM = LocalDateTime.of(2007, 1, 1, 13, 0, 55);

    /** Test for all CRUD operations. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class CRUDOperations extends BaseWithDatabaseAccess {
        private static final SubscriptionScheduleEntity SCHED_START_SUN_12AM_SUB1 =
            new SubscriptionScheduleEntity();

        @Captor
        ArgumentCaptor<List<PinEntity>> mPinEntitiesCaptor;

        @Inject
        KeyguardManager mKeyguardManager;

        @Override
        public void setUp() {
            super.setUp();

            SCHED_START_SUN_12AM_SUB1.setSubscriptionId(1);
            SCHED_START_SUN_12AM_SUB1.setSubscriptionEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_START_SUN_12AM_SUB1, SUNDAY);
            insert(SCHED_START_SUN_12AM_SUB1);

            // Add an extra schedule for reliability when the target schedule will be altered
            final var extraSchedule = new SubscriptionScheduleEntity();
            extraSchedule.setSubscriptionId(2);
            extraSchedule.setSubscriptionEnabled(true);
            extraSchedule.setEnabled(true);
            extraSchedule.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(extraSchedule, SUNDAY);
            insert(extraSchedule);
        }

        @Test
        public void test_add_ShouldAssignId() {
            final var entity = new SubscriptionScheduleEntity();
            entity.setSubscriptionId(1);
            entity.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(entity, SUNDAY);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler.add(entity)));
            assertThat(entity.getId(), is(greaterThan(0L)));
        }

        @Test(expected = SQLiteConstraintException.class)
        public void test_add_DuplicatePrimaryKey() throws Throwable {
            try {
                assertFutureDone(EXECUTOR.submit(() ->
                            mSubscriptionScheduler.add(SCHED_START_SUN_12AM_SUB1)));
            } catch (RuntimeException e) {
                throw ExceptionUtils.getRootCause(e);
            }
        }

        @Test
        public void test_add_ShouldInvokePostProcessing() {
            final var entity = new SubscriptionScheduleEntity();
            entity.setSubscriptionId(1);
            entity.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(entity, SUNDAY);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler.add(entity)));

            verify(mSubscriptionScheduler, times(1)).postProcessOnScheduleEntityChanged(entity);
        }

        @Test
        public void test_update() {
            SCHED_START_SUN_12AM_SUB1.setEnabled(!SCHED_START_SUN_12AM_SUB1.getEnabled());

            assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.update(SCHED_START_SUN_12AM_SUB1)));

            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionSchedulesDao
                    .findAllBySubscriptionId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId())));

            assertThat(result, hasItem(SCHED_START_SUN_12AM_SUB1));
        }

        @Test
        public void test_update_ShouldInvokePostProcessing() {
            SCHED_START_SUN_12AM_SUB1.setSubscriptionId(99);

            assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.update(SCHED_START_SUN_12AM_SUB1)));

            verify(mSubscriptionScheduler, times(1))
                .postProcessOnScheduleEntityChanged(SCHED_START_SUN_12AM_SUB1);
        }

        @Test
        public void test_delete() {
            assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.delete(SCHED_START_SUN_12AM_SUB1)));

            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionSchedulesDao
                    .findAllBySubscriptionId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId())));

            assertThat(result, not(hasItem(SCHED_START_SUN_12AM_SUB1)));
        }

        @Test
        public void test_delete_ShouldInvokeCommonAction() {
            assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.delete(SCHED_START_SUN_12AM_SUB1)));

            verify(mSubscriptionScheduler, times(1))
                .postProcessOnScheduleEntityChanged(SCHED_START_SUN_12AM_SUB1);
        }

        @Test
        public void test_postProcessOnScheduleEntityChanged_ShouldInvokePostProcessing() {
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .postProcessOnScheduleEntityChanged(SCHED_START_SUN_12AM_SUB1)));

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(eq(SCHED_START_SUN_12AM_SUB1.getSubscriptionId()),
                        mDateTimeCaptor.capture(), eq(true));
            verify(mSubscriptionScheduler, times(1)).updateNextWeeklyRepeatScheduleProcessingIter(
                    eq(mDateTimeCaptor.getValue().plusMinutes(1)), anyList());
        }

        @Test
        public void test_postProcessOnScheduleEntityChanged_ShouldNotUseClearPinEntitiesWhenAuthIsRequired() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .postProcessOnScheduleEntityChanged(SCHED_START_SUN_12AM_SUB1)));

            verify(mSubscriptionScheduler, times(1))
                .updateNextWeeklyRepeatScheduleProcessingIter(any(LocalDateTime.class), isNull());
        }

        @Test
        public void test_postProcessOnScheduleEntityChanged_ShouldUseClearPinEntitiesWhenAvailableAndAuthIsNotRequired() {
            final var pinEntity = new PinEntity();
            pinEntity.setClearPin("12345");
            assertFutureDone(EXECUTOR.submit(() -> {
                mPinStorage.encrypt(pinEntity);
                mPinStorage.storePin(pinEntity);
            }));

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .postProcessOnScheduleEntityChanged(SCHED_START_SUN_12AM_SUB1)));

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(eq(SCHED_START_SUN_12AM_SUB1.getSubscriptionId()),
                        mDateTimeCaptor.capture(), eq(true));

            verify(mSubscriptionScheduler, times(1)).updateNextWeeklyRepeatScheduleProcessingIter(
                    eq(mDateTimeCaptor.getValue().plusMinutes(1)), mPinEntitiesCaptor.capture());

            assertThat(mPinEntitiesCaptor.getValue().get(0).getClearPin(),
                    is(pinEntity.getClearPin()));
        }

        @Test
        public void test_findAllBySubscriptionId_HasOccurrences() {
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .findAllBySubscriptionId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId())));
            assertThat(result, contains(SCHED_START_SUN_12AM_SUB1));
        }

        @Test
        public void test_findAllBySubscriptionId_ZeroOccurrences() {
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .findAllBySubscriptionId(999)));
            assertThat(result, is(empty()));
        }

        @Test
        public void test_getCountBySubscriptionId() {
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .getCountBySubscriptionId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId())));
            assertThat(result, is(1));
        }
    }

    /** Test for {@link SubscriptionScheduler#findNearestAfterDateTime}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class NearestScheduleAfterDateTimeLookup extends BaseNearestScheduleDateTimeLookup {
        @Parameter(0)
        public int mSubId;

        @Parameter(1)
        public LocalDateTime mDateTime;

        @Parameter(2)
        public ExpectedHolder<Matcher<Optional<SubscriptionScheduleEntity>>> mExpected;

        @Parameters(name = "Given subId={0}, dateTime={1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { 999, LocalDateTime.of(2007, 1, 1, 13, 0), expected(is(Optional.empty())) },
                { 1, LocalDateTime.of(2007, 1, 7, 7, 55),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[SUNDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 1, 9, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[MONDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 2, 10, 59),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[TUESDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 3, 10, 59),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 4, 12, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[THURSDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 5, 13, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[FRIDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 6, 0, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[SATURDAY]))) },
            });
        }

        @Test
        public void test() {
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .findNearestAfterDateTime(mSubId, true, mDateTime)));
            assertThat(result, mExpected.value);
        }
    }

    /** Test for {@link SubscriptionScheduler#findNearestBeforeDateTime}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class NearestScheduleBeforeDateTimeLookup extends BaseNearestScheduleDateTimeLookup {
        @Parameter(0)
        public int mSubId;

        @Parameter(1)
        public LocalDateTime mDateTime;

        @Parameter(2)
        public ExpectedHolder<Matcher<Optional<SubscriptionScheduleEntity>>> mExpected;

        @Parameters(name = "Given subId={0}, dateTime={1}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { 999, LocalDateTime.of(2007, 1, 1, 13, 0), expected(is(Optional.empty())) },
                { 1, LocalDateTime.of(2007, 1, 7, 8, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[SUNDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 1, 9, 5),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[MONDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 2, 11, 1),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[TUESDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 3, 11, 4),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 4, 12, 1),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[THURSDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 5, 13, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[FRIDAY]))) },
                { 1, LocalDateTime.of(2007, 1, 6, 0, 0),
                    expected(is(Optional.of(SCHEDULES_BY_DAY_OF_WEEK[SATURDAY]))) },
            });
        }

        @Test
        public void test() {
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .findNearestBeforeDateTime(mSubId, true, mDateTime)));
            assertThat(result, mExpected.value);
        }
    }

    static class BaseNearestScheduleDateTimeLookup extends BaseWithDatabaseAccess {
        static final SubscriptionScheduleEntity[] SCHEDULES_BY_DAY_OF_WEEK =
            new SubscriptionScheduleEntity[] {
                null, // skip 0th index since Sunday starts at 1st index
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
                new SubscriptionScheduleEntity(),
            };

        @Override
        public void setUp() {
            super.setUp();

            SCHEDULES_BY_DAY_OF_WEEK[SUNDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[SUNDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[SUNDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[SUNDAY].setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[SUNDAY], SUNDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[SUNDAY]);

            final var scheduleWithSundaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithSundaySubId1.setSubscriptionId(1);
            scheduleWithSundaySubId1.setSubscriptionEnabled(true);
            scheduleWithSundaySubId1.setEnabled(true);
            scheduleWithSundaySubId1.setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(scheduleWithSundaySubId1, SUNDAY, MONDAY);
            insert(scheduleWithSundaySubId1);

            final var scheduleWithSundaySubId2 = new SubscriptionScheduleEntity();
            scheduleWithSundaySubId2.setSubscriptionId(2);
            scheduleWithSundaySubId2.setSubscriptionEnabled(true);
            scheduleWithSundaySubId2.setEnabled(true);
            scheduleWithSundaySubId2.setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(scheduleWithSundaySubId2, SUNDAY);
            insert(scheduleWithSundaySubId2);

            final var scheduleWithSundaySubId1ButDisabled = new SubscriptionScheduleEntity();
            scheduleWithSundaySubId1ButDisabled.setSubscriptionId(1);
            scheduleWithSundaySubId1ButDisabled.setSubscriptionEnabled(true);
            scheduleWithSundaySubId1ButDisabled.setEnabled(false);
            scheduleWithSundaySubId1ButDisabled.setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(scheduleWithSundaySubId1ButDisabled, SUNDAY);
            insert(scheduleWithSundaySubId1ButDisabled);

            final var scheduleWithSundaySubId1ButSubDisabled = new SubscriptionScheduleEntity();
            scheduleWithSundaySubId1ButSubDisabled.setSubscriptionId(1);
            scheduleWithSundaySubId1ButSubDisabled.setSubscriptionEnabled(false);
            scheduleWithSundaySubId1ButSubDisabled.setEnabled(false);
            scheduleWithSundaySubId1ButSubDisabled.setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(scheduleWithSundaySubId1ButSubDisabled, SUNDAY);
            insert(scheduleWithSundaySubId1ButSubDisabled);

            final var scheduleWithNoDaysOfWeekSubId1 = new SubscriptionScheduleEntity();
            scheduleWithNoDaysOfWeekSubId1.setSubscriptionId(1);
            scheduleWithNoDaysOfWeekSubId1.setSubscriptionEnabled(false);
            scheduleWithNoDaysOfWeekSubId1.setEnabled(true);
            scheduleWithNoDaysOfWeekSubId1.setTime(LocalTime.of(8, 0));
            setScheduleDaysOfWeek(scheduleWithNoDaysOfWeekSubId1);
            insert(scheduleWithNoDaysOfWeekSubId1);

            SCHEDULES_BY_DAY_OF_WEEK[MONDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[MONDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[MONDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[MONDAY].setTime(LocalTime.of(9, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[MONDAY], MONDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[MONDAY]);

            final var scheduleWithMondaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithMondaySubId1.setSubscriptionId(1);
            scheduleWithMondaySubId1.setSubscriptionEnabled(true);
            scheduleWithMondaySubId1.setEnabled(true);
            scheduleWithMondaySubId1.setTime(LocalTime.of(9, 10));
            setScheduleDaysOfWeek(scheduleWithMondaySubId1, MONDAY, TUESDAY);
            insert(scheduleWithMondaySubId1);

            SCHEDULES_BY_DAY_OF_WEEK[TUESDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[TUESDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[TUESDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[TUESDAY].setTime(LocalTime.of(11, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[TUESDAY], TUESDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[TUESDAY]);

            final var scheduleWithTuesdaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithTuesdaySubId1.setSubscriptionId(1);
            scheduleWithTuesdaySubId1.setSubscriptionEnabled(true);
            scheduleWithTuesdaySubId1.setEnabled(true);
            scheduleWithTuesdaySubId1.setTime(LocalTime.of(10, 58));
            setScheduleDaysOfWeek(scheduleWithTuesdaySubId1, TUESDAY, WEDNESDAY);
            insert(scheduleWithTuesdaySubId1);

            SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY].setTime(LocalTime.of(11, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY], WEDNESDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[WEDNESDAY]);

            final var scheduleWithWednesdaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithWednesdaySubId1.setSubscriptionId(1);
            scheduleWithWednesdaySubId1.setSubscriptionEnabled(true);
            scheduleWithWednesdaySubId1.setEnabled(true);
            scheduleWithWednesdaySubId1.setTime(LocalTime.of(11, 05));
            setScheduleDaysOfWeek(scheduleWithWednesdaySubId1, WEDNESDAY, THURSDAY);
            insert(scheduleWithWednesdaySubId1);

            SCHEDULES_BY_DAY_OF_WEEK[THURSDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[THURSDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[THURSDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[THURSDAY].setTime(LocalTime.of(12, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[THURSDAY], THURSDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[THURSDAY]);

            final var scheduleWithThursdaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithThursdaySubId1.setSubscriptionId(1);
            scheduleWithThursdaySubId1.setSubscriptionEnabled(true);
            scheduleWithThursdaySubId1.setEnabled(true);
            scheduleWithThursdaySubId1.setTime(LocalTime.of(12, 05));
            setScheduleDaysOfWeek(scheduleWithThursdaySubId1, THURSDAY, FRIDAY);
            insert(scheduleWithThursdaySubId1);

            SCHEDULES_BY_DAY_OF_WEEK[FRIDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[FRIDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[FRIDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[FRIDAY].setTime(LocalTime.of(13, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[FRIDAY], FRIDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[FRIDAY]);

            final var scheduleWithFridaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithFridaySubId1.setSubscriptionId(1);
            scheduleWithFridaySubId1.setSubscriptionEnabled(true);
            scheduleWithFridaySubId1.setEnabled(true);
            scheduleWithFridaySubId1.setTime(LocalTime.of(13, 05));
            setScheduleDaysOfWeek(scheduleWithFridaySubId1, FRIDAY, SATURDAY);
            insert(scheduleWithFridaySubId1);

            SCHEDULES_BY_DAY_OF_WEEK[SATURDAY].setSubscriptionId(1);
            SCHEDULES_BY_DAY_OF_WEEK[SATURDAY].setSubscriptionEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[SATURDAY].setEnabled(true);
            SCHEDULES_BY_DAY_OF_WEEK[SATURDAY].setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHEDULES_BY_DAY_OF_WEEK[SATURDAY], SATURDAY);
            insert(SCHEDULES_BY_DAY_OF_WEEK[SATURDAY]);

            final var scheduleWithSaturdaySubId1 = new SubscriptionScheduleEntity();
            scheduleWithSaturdaySubId1.setSubscriptionId(1);
            scheduleWithSaturdaySubId1.setSubscriptionEnabled(true);
            scheduleWithSaturdaySubId1.setEnabled(true);
            scheduleWithSaturdaySubId1.setTime(LocalTime.of(0, 1));
            setScheduleDaysOfWeek(scheduleWithSaturdaySubId1, SATURDAY, SUNDAY);
            insert(scheduleWithSaturdaySubId1);
        }
    }

    /**
     * Tests for {@link SubscriptionScheduler#syncAllSubscriptionsEnabledState} and
     * {@link SubscriptionScheduler#syncSubscriptionEnabledState}.
     */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class SubscriptionEnabledStateSyncing extends BaseWithDatabaseAccess {
        private static final SubscriptionScheduleEntity SCHED_START_SUN_12AM_SUB1 =
            new SubscriptionScheduleEntity();
        private static final SubscriptionScheduleEntity SCHED_END_SUN_12AM_SUB1 =
            new SubscriptionScheduleEntity();

        private static final SubscriptionScheduleEntity SCHED_START_SUN_1AM_SUB2 =
            new SubscriptionScheduleEntity();
        private static final SubscriptionScheduleEntity SCHED_END_SUN_12AM_SUB2 =
            new SubscriptionScheduleEntity();

        @Inject
        TelecomManager mTelecomManager;

        @Inject
        TelephonyController mTelephonyController;

        @Inject
        SubscriptionsImpl mSubscriptionsImpl;

        @Override
        public void setUp() {
            super.setUp();

            SCHED_START_SUN_12AM_SUB1.setSubscriptionId(1);
            SCHED_START_SUN_12AM_SUB1.setSubscriptionEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_START_SUN_12AM_SUB1, SUNDAY);
            insert(SCHED_START_SUN_12AM_SUB1);

            SCHED_END_SUN_12AM_SUB1.setSubscriptionId(1);
            SCHED_END_SUN_12AM_SUB1.setSubscriptionEnabled(false);
            SCHED_END_SUN_12AM_SUB1.setEnabled(true);
            SCHED_END_SUN_12AM_SUB1.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_END_SUN_12AM_SUB1, SUNDAY);
            insert(SCHED_END_SUN_12AM_SUB1);

            SCHED_START_SUN_1AM_SUB2.setSubscriptionId(2);
            SCHED_START_SUN_1AM_SUB2.setSubscriptionEnabled(true);
            SCHED_START_SUN_1AM_SUB2.setEnabled(true);
            SCHED_START_SUN_1AM_SUB2.setTime(LocalTime.of(1, 0));
            setScheduleDaysOfWeek(SCHED_START_SUN_1AM_SUB2, SUNDAY);
            insert(SCHED_START_SUN_1AM_SUB2);

            SCHED_END_SUN_12AM_SUB2.setSubscriptionId(2);
            SCHED_END_SUN_12AM_SUB2.setSubscriptionEnabled(false);
            SCHED_END_SUN_12AM_SUB2.setEnabled(true);
            SCHED_END_SUN_12AM_SUB2.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_END_SUN_12AM_SUB2, SUNDAY);
            insert(SCHED_END_SUN_12AM_SUB2);
        }

        @Test
        public void test_syncSubscriptionEnabledState_UndefinedSubscriptionId() {
            final var overrideUserPreference = true;
            final var result = assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.syncSubscriptionEnabledState(1, MONDAY_01_00_55PM,
                            overrideUserPreference)));
            assertThat(result, is(Optional.empty()));
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldTruncateCompareTimeToMinutes() {
            final int subId = SCHED_START_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            assertFutureDone(EXECUTOR.submit(() -> {
                try (final var mock = mockStatic(SubscriptionScheduler.class, CALLS_REAL_METHODS)) {
                    final var overrideUserPreference = true;
                    mSubscriptionScheduler.syncSubscriptionEnabledState(subId, MONDAY_01_00_55PM,
                            overrideUserPreference);

                    mock.verify(() -> getDateTimeBefore(eq(SCHED_START_SUN_12AM_SUB1),
                                mDateTimeCaptor.capture()), times(1));
                    mock.verify(() -> getDateTimeBefore(eq(SCHED_END_SUN_12AM_SUB1),
                                mDateTimeCaptor.capture()), times(1));
                }
            }));

            assertThat(mDateTimeCaptor.getAllValues(), both(hasSize(2))
                    .and(everyItem(is(MONDAY_01_00_55PM.truncatedTo(ChronoUnit.MINUTES)))));
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldSyncSimStateWhenDiffers() {
            final int subId = SCHED_START_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            final var sub = subInfoBuilder.buildSubscriptionInfo();
            setAvailableSubscriptionInfoList(sub);

            final var overrideUserPreference = true;
            final var expectedEnabled = assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionScheduler.syncSubscriptionEnabledState(subId,
                            MONDAY_01_00_55PM, overrideUserPreference)));

            assertThat(expectedEnabled, is(Optional.of(false)));

            verify(mTelephonyController, times(1)).setSimState(eq(sub.getSimSlotIndex()),
                    eq(expectedEnabled.get()), anyBoolean());
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldPostponeDeactivationOnlyWhenInCall() {
            final int subId = SCHED_START_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            shadowOf(mTelecomManager).setIsInCall(true);

            final var overrideUserPreference = true;
            final var expectedEnabled = assertFutureDone(EXECUTOR.submit(() -> {
                try (final var mock = mockStatic(PhoneCallEndObserverService.class)) {
                    final var result = mSubscriptionScheduler.syncSubscriptionEnabledState(subId,
                            MONDAY_01_00_55PM, overrideUserPreference);

                    mock.verify(() -> PhoneCallEndObserverService
                            .syncSubscriptionEnabledState(mApplicationContext, subId,
                                MONDAY_01_00_55PM, overrideUserPreference), times(1));
                    mock.verify(() -> PhoneCallEndObserverService
                            .updateNextWeeklyRepeatScheduleProcessingIter(mApplicationContext,
                                MONDAY_01_00_55PM), times(1));

                    return result;
                }
            }));

            assertThat(expectedEnabled, is(Optional.empty()));
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldSyncAndNotPostponeActivationWhenInCall() {
            final int subId = SCHED_START_SUN_1AM_SUB2.getSubscriptionId();

            final var sub = new Subscription();
            sub.setId(subId);
            sub.setSlotIndex(0);
            sub.setSimState(SimState.DISABLED);

            when(mSubscriptions.getSubscriptionForSubId(subId)).thenAnswer((invocation) ->
                    Optional.of(sub));

            shadowOf(mTelecomManager).setIsInCall(true);

            final var overrideUserPreference = true;
            final var expectedEnabled = assertFutureDone(EXECUTOR.submit(() -> {
                try (final var mock = mockStatic(PhoneCallEndObserverService.class)) {
                    final var result = mSubscriptionScheduler.syncSubscriptionEnabledState(subId,
                            MONDAY_01_00_55PM, overrideUserPreference);

                    mock.verifyNoInteractions();

                    return result;
                }
            }));

            assertThat(expectedEnabled, is(Optional.of(true)));
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldNotSyncWhenAlreadyInState() {
            // Use a subscription ID without schedules to not sync the SIM subscription state
            final int subId = 99;

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            final var overrideUserPreference = true;
            final var result = assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .syncSubscriptionEnabledState(subId, MONDAY_01_00_55PM,
                            overrideUserPreference)));

            assertThat(result, is(Optional.empty()));
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldMaintainKeepDisabledAcrossBootsWhenShouldNotOverrideUserPreference() {
            final var expectedKeepDisabledAcrossBoots = true;

            final var sub = new Subscription();
            sub.setId(SCHED_END_SUN_12AM_SUB1.getSubscriptionId());
            sub.setSlotIndex(0);
            sub.setSimState(SimState.ENABLED);
            sub.keepDisabledAcrossBoots(expectedKeepDisabledAcrossBoots);

            when(mSubscriptions.getSubscriptionForSubId(sub.getId())).thenAnswer((invocation) ->
                    Optional.of(sub));

            final var overrideUserPreference = false;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .syncSubscriptionEnabledState(sub.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference)));

            final var expectedEnabled = false;
            verify(mTelephonyController, times(1)).setSimState(sub.getSlotIndex(), expectedEnabled,
                    expectedKeepDisabledAcrossBoots);
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldOverrideKeepDisabledAcrossBootsWhenShouldOverrideUserPreference() {
            final var expectedKeepDisabledAcrossBoots = false;

            final var sub = new Subscription();
            sub.setId(SCHED_END_SUN_12AM_SUB1.getSubscriptionId());
            sub.setSlotIndex(0);
            sub.setSimState(SimState.ENABLED);
            sub.keepDisabledAcrossBoots(!expectedKeepDisabledAcrossBoots);

            when(mSubscriptions.getSubscriptionForSubId(sub.getId())).thenAnswer((invocation) ->
                    Optional.of(sub));

            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                    .syncSubscriptionEnabledState(sub.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference)));

            final var expectedEnabled = false;
            verify(mTelephonyController, times(1)).setSimState(sub.getSlotIndex(), expectedEnabled,
                    expectedKeepDisabledAcrossBoots);
        }

        @Test
        public void test_syncSubscriptionEnabledState_ShouldSyncUiccSubscriptionStateWhenDiffers() {
            final int subId = SCHED_START_SUN_1AM_SUB2.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> {
                // Note that, there's no way to naturally do the device RIL configuration switch
                // while in the test environment, so we'll manually do it by swapping the
                // Subscriptions implementations in order to trigger the other branch
                when(mSubscriptions.getSubscriptionForSubId(anyInt())).thenAnswer((invocation) ->
                        mSubscriptionsImpl.getSubscriptionForSubId(invocation.getArgument(0)));
                mSubscriptionScheduler.syncSubscriptionEnabledState(subId, MONDAY_01_00_55PM,
                        overrideUserPreference);
            }));

            assertThat(assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptions.getSubscriptionForSubId(subId))).get().getSimState(),
                    is(SimState.ENABLED));
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldNotSyncWhenNoSubscriptions() {
            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM,
                            overrideUserPreference)));
            verify(mSubscriptionScheduler, never()).syncSubscriptionEnabledState(anyInt(), any(),
                    anyBoolean());
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldSyncSubscriptionWithoutDelayWhenOnlyOneAvailable() {
            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            setAvailableSubscriptionInfoList(subInfoBuilder);

            doReturn(Optional.empty()).when(mSubscriptionScheduler)
                .syncSubscriptionEnabledState(anyInt(), any(), anyBoolean());

            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM,
                            overrideUserPreference)),
                    Duration.ofSeconds(2));

            verify(mSubscriptionScheduler, times(1)).syncSubscriptionEnabledState(anyInt(), any(),
                    anyBoolean());
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldSyncSubsequentSubscriptionsWithoutDelayWhenPreviousSyncSkipped() {
            final var sub1 = new Subscription();
            sub1.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.DISABLED);
            sub1.setSlotIndex(0);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_START_SUN_1AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.DISABLED);
            sub2.setSlotIndex(1);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            doReturn(Optional.empty()).when(mSubscriptionScheduler)
                .syncSubscriptionEnabledState(anyInt(), any(), anyBoolean());

            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM,
                            overrideUserPreference)), Duration.ofSeconds(2));

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub1.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub2.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldSyncSubsequentSubscriptionsWithDelay()
            throws ExecutionException, InterruptedException {

            final var sub1 = new Subscription();
            sub1.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.DISABLED);
            sub1.setSlotIndex(0);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_START_SUN_1AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.DISABLED);
            sub2.setSlotIndex(1);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            // Return the result immediately to avoid hanging for too long, since we want to finish
            // each sync request under 2 seconds
            doReturn(Optional.of(false)).when(mSubscriptionScheduler)
                .syncSubscriptionEnabledState(anyInt(), any(), anyBoolean());

            final var overrideUserPreference = true;
            final var future = EXECUTOR.submit(() -> mSubscriptionScheduler
                    .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM, overrideUserPreference));

            // Since the subsequent sync requests should be delayed by exactly 2 seconds, the test
            // should fail when completed earlier
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(5))
                .atLeast(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(50))
                .until(future::isDone);

            future.get();

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub1.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub2.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldCancelSubsequentSubscriptionsSyncWhenInterrupted() {
            final var sub1 = new Subscription();
            sub1.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.DISABLED);
            sub1.setSlotIndex(0);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_START_SUN_1AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.DISABLED);
            sub2.setSlotIndex(1);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            // Return the result immediately to avoid hanging for too long, since we want to finish
            // each sync request under 2 seconds
            doReturn(Optional.of(false)).when(mSubscriptionScheduler)
                .syncSubscriptionEnabledState(anyInt(), any(), anyBoolean());

            final var overrideUserPreference = true;
            final var future = EXECUTOR.submit(() -> mSubscriptionScheduler
                    .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM, overrideUserPreference));

            // Wait for the first sync request to complete
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> verify(mSubscriptionScheduler, times(1))
                        .syncSubscriptionEnabledState(sub1.getId(), MONDAY_01_00_55PM,
                            overrideUserPreference));

            // Interrupt the thread that is currently paused by a 2-second delay
            assertTrue("Expected the task to still run due to delay of 2 seconds for the " +
                    "subsequent sync requests.", future.cancel(true));

            try {
                assertFutureDone(future);
            } catch (CancellationException ignored) { /* @SuppressWarnings("EmptyCatch") */ }
            assertTrue(future.isCancelled());

            verify(mSubscriptionScheduler, never())
                .syncSubscriptionEnabledState(sub2.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);
        }

        @Test
        public void test_syncAllSubscriptionsEnabledState_ShouldSyncSubsequentUiccSubscriptionsWithoutDelay() {
            final var sub1 = new Subscription();
            sub1.setId(SCHED_END_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.ENABLED);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_END_SUN_12AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.ENABLED);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            // Return the result immediately to avoid hanging for too long, since we want to finish
            // each sync request under 2 seconds
            doReturn(Optional.of(false)).when(mSubscriptionScheduler)
                .syncSubscriptionEnabledState(anyInt(), any(), anyBoolean());

            final var overrideUserPreference = true;
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .syncAllSubscriptionsEnabledState(MONDAY_01_00_55PM,
                            overrideUserPreference)),
                    Duration.ofSeconds(2));

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub1.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);

            verify(mSubscriptionScheduler, times(1))
                .syncSubscriptionEnabledState(sub2.getId(), MONDAY_01_00_55PM,
                        overrideUserPreference);
        }
    }

    /** Test for {@link SubscriptionScheduler#updateNextWeeklyRepeatScheduleProcessingIter}. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class UpdateNextWeeklyRepeatScheduleProcessingIter extends BaseWithDatabaseAccess {
        private static final SubscriptionScheduleEntity SCHED_START_SUN_12AM_SUB1 =
            new SubscriptionScheduleEntity();
        private static final SubscriptionScheduleEntity SCHED_END_SUN_12AM_SUB1 =
            new SubscriptionScheduleEntity();

        private static final SubscriptionScheduleEntity SCHED_START_SUN_1AM_SUB2 =
            new SubscriptionScheduleEntity();
        private static final SubscriptionScheduleEntity SCHED_END_SAT_12AM_SUB2 =
            new SubscriptionScheduleEntity();

        @Inject
        AlarmManager mAlarmManager;

        @Inject
        UserManager mUserManager;

        @Override
        public void setUp() {
            super.setUp();

            SCHED_START_SUN_12AM_SUB1.setSubscriptionId(1);
            SCHED_START_SUN_12AM_SUB1.setSubscriptionEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setEnabled(true);
            SCHED_START_SUN_12AM_SUB1.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_START_SUN_12AM_SUB1, SUNDAY);

            SCHED_END_SUN_12AM_SUB1.setSubscriptionId(1);
            SCHED_END_SUN_12AM_SUB1.setSubscriptionEnabled(false);
            SCHED_END_SUN_12AM_SUB1.setEnabled(true);
            SCHED_END_SUN_12AM_SUB1.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_END_SUN_12AM_SUB1, SUNDAY);

            SCHED_START_SUN_1AM_SUB2.setSubscriptionId(2);
            SCHED_START_SUN_1AM_SUB2.setSubscriptionEnabled(true);
            SCHED_START_SUN_1AM_SUB2.setEnabled(true);
            SCHED_START_SUN_1AM_SUB2.setTime(LocalTime.of(1, 0));
            setScheduleDaysOfWeek(SCHED_START_SUN_1AM_SUB2, SUNDAY);

            SCHED_END_SAT_12AM_SUB2.setSubscriptionId(2);
            SCHED_END_SAT_12AM_SUB2.setSubscriptionEnabled(false);
            SCHED_END_SAT_12AM_SUB2.setEnabled(true);
            SCHED_END_SAT_12AM_SUB2.setTime(LocalTime.of(0, 0));
            setScheduleDaysOfWeek(SCHED_END_SAT_12AM_SUB2, SATURDAY);
        }

        @Test
        public void test_ShouldTruncateCompareTimeToMinutes() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            insert(SCHED_END_SUN_12AM_SUB1);

            assertFutureDone(EXECUTOR.submit(() -> {
                try (final var mock = mockStatic(SubscriptionScheduler.class, CALLS_REAL_METHODS)) {
                    mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM);

                    verify(mSubscriptionScheduler, times(1)).findNearestAfterDateTime(eq(subId),
                            eq(false), mDateTimeCaptor.capture());
                    mock.verify(() -> getDateTimeAfter(eq(SCHED_END_SUN_12AM_SUB1),
                                mDateTimeCaptor.capture()), times(1));
                }
            }));

            assertThat(mDateTimeCaptor.getAllValues(), both(hasSize(2))
                    .and(everyItem(is(MONDAY_01_00_55PM.truncatedTo(ChronoUnit.MINUTES)))));
        }

        @Test
        public void test_ShouldNotScheduleNextProcessingIterWhenNoSimSubscriptions() {
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            assertThat(peekNextScheduledAlarm(), is(nullValue()));
        }

        @Test
        public void test_ShouldNotScheduleNextProcessingIterWhenNoEligibleScheduleFound() {
            final var sub = new Subscription();
            sub.setId(SCHED_END_SUN_12AM_SUB1.getSubscriptionId());
            sub.setSlotIndex(0);
            sub.setSimState(SimState.DISABLED);

            final var subList = List.of(sub);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            insert(SCHED_END_SUN_12AM_SUB1);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            assertThat(peekNextScheduledAlarm(), is(nullValue()));
        }

        @Test
        public void test_ShouldScheduleNextWeeklyRepeatScheduleProcessingIterWhenFoundEligibleSchedule() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            insert(SCHED_END_SUN_12AM_SUB1);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));
            assertTrue(scheduledAlarm.isAllowWhileIdle());
            assertThat(scheduledAlarm.getType(), is(AlarmManager.RTC_WAKEUP));
            assertThat(scheduledAlarm.getWindowLengthMs(), is(0L));

            final var triggerAtDateTime = LocalDateTime.ofInstant(Instant
                    .ofEpochMilli(scheduledAlarm.getTriggerAtMs()), ZoneId.systemDefault());
            assertThat(triggerAtDateTime, is(MONDAY_01_00_55PM
                        .plusDays(6)
                        .withHour(SCHED_END_SUN_12AM_SUB1.getTime().getHour())
                        .withMinute(SCHED_END_SUN_12AM_SUB1.getTime().getMinute())
                        .truncatedTo(ChronoUnit.MINUTES)));

            final var pendingIntent = peekPendingIntent();
            assertThat(pendingIntent, is(notNullValue()));
        }

        @Test
        public void test_ShouldScheduleNextProcessingIterUsingNearestDateTimeWhenMultipleSchedulesAvailable_Sub1IsBefore() {
            final var sub1 = new Subscription();
            sub1.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.DISABLED);
            sub1.setSlotIndex(0);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_START_SUN_1AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.DISABLED);
            sub2.setSlotIndex(1);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            insert(SCHED_START_SUN_12AM_SUB1);
            insert(SCHED_START_SUN_1AM_SUB2);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));

            final var triggerAtDateTime = LocalDateTime.ofInstant(Instant
                    .ofEpochMilli(scheduledAlarm.getTriggerAtMs()), ZoneId.systemDefault());
            assertThat(triggerAtDateTime, is(MONDAY_01_00_55PM
                        .plusDays(6)
                        .withHour(SCHED_START_SUN_12AM_SUB1.getTime().getHour())
                        .withMinute(SCHED_START_SUN_12AM_SUB1.getTime().getMinute())
                        .truncatedTo(ChronoUnit.MINUTES)));
        }

        @Test
        public void test_ShouldScheduleNextProcessingIterUsingNearestDateTimeWhenMultipleSchedulesAvailable_Sub2IsBefore() {
            final var sub1 = new Subscription();
            sub1.setId(SCHED_START_SUN_12AM_SUB1.getSubscriptionId());
            sub1.setSimState(SimState.DISABLED);
            sub1.setSlotIndex(0);

            final var sub2 = new Subscription();
            sub2.setId(SCHED_END_SAT_12AM_SUB2.getSubscriptionId());
            sub2.setSimState(SimState.ENABLED);
            sub2.setSlotIndex(1);

            final var subList = List.of(sub1, sub2);
            when(mSubscriptions.iterator()).thenAnswer((invocation) -> subList.iterator());

            insert(SCHED_START_SUN_12AM_SUB1);
            insert(SCHED_END_SAT_12AM_SUB2);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));

            final var triggerAtDateTime = LocalDateTime.ofInstant(Instant
                    .ofEpochMilli(scheduledAlarm.getTriggerAtMs()), ZoneId.systemDefault());
            assertThat(triggerAtDateTime, is(MONDAY_01_00_55PM
                        .plusDays(5)
                        .withHour(SCHED_END_SAT_12AM_SUB2.getTime().getHour())
                        .withMinute(SCHED_END_SAT_12AM_SUB2.getTime().getMinute())
                        .truncatedTo(ChronoUnit.MINUTES)));
        }

        @Test
        public void test_ShouldAttachClearPinCodesAsIntentExtraDataWhenAvailable() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            insert(SCHED_END_SUN_12AM_SUB1);

            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(subId);
            pinEntity.setClearPin("12345");
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM,
                            List.of(pinEntity))));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));

            final var pendingIntent = peekPendingIntent();
            assertThat(pendingIntent, is(notNullValue()));

            final var expectedExtras = new Bundle(1);
            expectedExtras.putString(String.valueOf(subId), pinEntity.getClearPin());
            final var actualExtras = shadowOf(pendingIntent).getSavedIntent().getExtras();
            assertThat(actualExtras, sameBundle(expectedExtras));
        }

        @Test
        public void test_ShouldPreserveExistingClearPinCodesInIntentExtraDataWhenReschedulingWithoutClearPinCodes() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var subInfoBuilder = SubscriptionInfoBuilder.newBuilder();
            subInfoBuilder.setId(subId);
            setAvailableSubscriptionInfoList(subInfoBuilder);

            insert(SCHED_END_SUN_12AM_SUB1);

            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(subId);
            pinEntity.setClearPin("12345");
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM,
                            List.of(pinEntity))));

            final var tuesday_01_00_55pm = MONDAY_01_00_55PM.plusDays(1);
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(tuesday_01_00_55pm, null)));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));

            final var triggerAtDateTime = LocalDateTime.ofInstant(Instant
                    .ofEpochMilli(scheduledAlarm.getTriggerAtMs()), ZoneId.systemDefault());
            assertThat(triggerAtDateTime, is(tuesday_01_00_55pm
                        .plusDays(5)
                        .withHour(SCHED_END_SUN_12AM_SUB1.getTime().getHour())
                        .withMinute(SCHED_END_SUN_12AM_SUB1.getTime().getMinute())
                        .truncatedTo(ChronoUnit.MINUTES)));

            final var pendingIntent = peekPendingIntent();
            assertThat(pendingIntent, is(notNullValue()));

            final var expectedExtras = new Bundle(1);
            expectedExtras.putString(String.valueOf(subId), pinEntity.getClearPin());
            final var actualExtras = shadowOf(pendingIntent).getSavedIntent().getExtras();
            assertThat(actualExtras, sameBundle(expectedExtras));
        }

        @Test
        public void test_ShouldCommunicateCorruptedPinEntitiesToPinStorage() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(subId);
            pinEntity.setCorrupted(true);
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM,
                            List.of(pinEntity))));

            verify(mPinStorage, times(1)).handleBadPinEntity(pinEntity);
        }

        @Test
        public void test_ShouldCommunicateInvalidPinEntitiesToPinStorage() {
            final int subId = SCHED_END_SUN_12AM_SUB1.getSubscriptionId();

            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(subId);
            pinEntity.setInvalid(true);
            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM,
                            List.of(pinEntity))));

            verify(mPinStorage, times(1)).handleBadPinEntity(pinEntity);
        }

        @Test
        public void test_ShouldNotRescheduleNextProcessingIterToOccurInFarFutureWhenNoSimSubscriptionsAvailableAndHavingSchedulesButUserLocked() {
            insert(SCHED_END_SUN_12AM_SUB1);

            shadowOf(mUserManager).setUserUnlocked(false);

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            assertThat(peekNextScheduledAlarm(), is(nullValue()));
        }

        @Test
        public void test_ShouldRescheduleNextProcessingIterToOccurInFarFutureWhenNoSimSubscriptionsAvailableAndHavingSchedulesAndPinEntities() {
            insert(SCHED_END_SUN_12AM_SUB1);

            final var pinEntity = new PinEntity();
            pinEntity.setClearPin("12345");
            assertFutureDone(EXECUTOR.submit(() -> {
                mPinStorage.encrypt(pinEntity);
                mPinStorage.storePin(pinEntity);
            }));

            assertFutureDone(EXECUTOR.submit(() -> mSubscriptionScheduler
                        .updateNextWeeklyRepeatScheduleProcessingIter(MONDAY_01_00_55PM)));

            final var scheduledAlarm = peekNextScheduledAlarm();
            assertThat(scheduledAlarm, is(notNullValue()));
            assertThat(scheduledAlarm.getTriggerAtMs(), is(Long.MAX_VALUE));
        }

        private ScheduledAlarm peekNextScheduledAlarm() {
            return shadowOf(mAlarmManager).peekNextScheduledAlarm();
        }

        private PendingIntent peekPendingIntent() {
            final var i = new Intent(mApplicationContext, AlarmReceiver.class);
            final int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE;
            return PendingIntent.getBroadcast(mApplicationContext, /*requestCode=*/ 0, i, flags);
        }
    }

    /** Test for {@link SubscriptionScheduler#getSubscriptionExpectedEnabledState}. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class SubscriptionExpectedEnabledState extends Base {
        @Parameter(0)
        public Subscription mSubscription;

        @Parameter(1)
        public Optional<LocalDateTime> mStartDateTime;

        @Parameter(2)
        public Optional<LocalDateTime> mEndDateTime;

        @Parameter(3)
        public boolean mOverrideUserPreference;

        @Parameter(4)
        public ExpectedHolder<Matcher<Boolean>> mExpected;

        @Parameters(name = "{0}, startDateTime={1}, endDateTime={2} and overrideUserPreference={3}, {4}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                // SIM is enabled
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastActivatedTime(LocalDateTime.of(2007, 1, 1, 17, 0));
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ false,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastActivatedTime(LocalDateTime.of(2007, 1, 1, 17, 1));
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ false,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastActivatedTime(LocalDateTime.of(2007, 1, 1, 16, 59));
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ false,
                    expected(is(false))
                },
                {
                    new Subscription() {{ setSimState(SimState.ENABLED); }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        keepDisabledAcrossBoots(false);
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        keepDisabledAcrossBoots(true);
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        keepDisabledAcrossBoots(true);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 12, 0));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        keepDisabledAcrossBoots(true);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 15, 0));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                    }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 17, 0));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 2, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 2, 15, 0));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 2, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 17, 0));
                        keepDisabledAcrossBoots(true);
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 2, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.ENABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 17, 0));
                        keepDisabledAcrossBoots(false);
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 2, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },

                // SIM is disabled
                {
                    new Subscription() {{
                        setSimState(SimState.DISABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 13, 0));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ false,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.DISABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 13, 1));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ false,
                    expected(is(false))
                },
                {
                    new Subscription() {{
                        setSimState(SimState.DISABLED);
                        setLastDeactivatedTime(LocalDateTime.of(2007, 1, 1, 12, 59));
                    }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ false,
                    expected(is(true))
                },
                {
                    new Subscription() {{ setSimState(SimState.DISABLED); }},
                    /*startDateTime*/ Optional.empty(),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
                {
                    new Subscription() {{ setSimState(SimState.DISABLED); }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.empty(),
                    /*overrideUserPreference*/ true,
                    expected(is(true))
                },
                {
                    new Subscription() {{ setSimState(SimState.DISABLED); }},
                    /*startDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 13, 0)),
                    /*endDateTime*/ Optional.of(LocalDateTime.of(2007, 1, 1, 17, 0)),
                    /*overrideUserPreference*/ true,
                    expected(is(false))
                },
            });
        }

        @Test
        public void test() {
            assertThat(getSubscriptionExpectedEnabledState(mSubscription, mStartDateTime,
                        mEndDateTime, mOverrideUserPreference), mExpected.value);
        }
    }

    /**
     * Test for {@link SubscriptionScheduler#getDateTimeBefore} and {@link SubscriptionScheduler#getDateTimeAfter}.
     */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ScheduleFirstDateTimeOccurrence extends Base {
        private final SubscriptionScheduleEntity mSched10AM =
            new SubscriptionScheduleEntity();
        {
            mSched10AM.setTime(LocalTime.of(10, 0));
        }

        @Test
        public void test_getDateTimeBefore_EmptyDaysOfWeek() {
            setScheduleDaysOfWeek(mSched10AM);
            assertThat(getDateTimeBefore(mSched10AM, MONDAY_01_00_55PM), is(Optional.empty()));
        }

        @Test
        public void test_getDateTimeBefore_ScheduleOccurringYesterday() {
            setScheduleDaysOfWeek(mSched10AM, SUNDAY);
            assertThat(getDateTimeBefore(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(MONDAY_01_00_55PM
                            .minusDays(1)
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }

        @Test
        public void test_getDateTimeBefore_ScheduleOccurringOnSameDayAndTime() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            mSched10AM.setTime(MONDAY_01_00_55PM.toLocalTime());
            assertThat(getDateTimeBefore(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(LocalDateTime.of(MONDAY_01_00_55PM.toLocalDate(),
                                mSched10AM.getTime()))));
        }

        @Test
        public void test_getDateTimeBefore_ScheduleOccurringOnSameDayWithTimeAhead() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            mSched10AM.setTime(MONDAY_01_00_55PM.toLocalTime().plusHours(1));
            assertThat(getDateTimeBefore(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(MONDAY_01_00_55PM
                            .minusDays(7)
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }

        @Test
        public void test_getDateTimeBefore_ScheduleOccurringOnSameDayWithTimeBehind() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            final var compareTime = MONDAY_01_00_55PM.truncatedTo(ChronoUnit.MINUTES);
            assertThat(getDateTimeBefore(mSched10AM, compareTime),
                    is(Optional.of(LocalDateTime.of(MONDAY_01_00_55PM.toLocalDate(),
                                mSched10AM.getTime()))));
        }

        @Test
        public void test_getDateTimeAfter_EmptyDaysOfWeek() {
            setScheduleDaysOfWeek(mSched10AM);
            assertThat(getDateTimeAfter(mSched10AM, MONDAY_01_00_55PM), is(Optional.empty()));
        }

        @Test
        public void test_getDateTimeAfter_ScheduleOccurringTomorrow() {
            setScheduleDaysOfWeek(mSched10AM, TUESDAY);
            assertThat(getDateTimeAfter(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(MONDAY_01_00_55PM
                            .plusDays(1)
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }

        @Test
        public void test_getDateTimeAfter_ScheduleOccurringOnSaturday() {
            final var now = MONDAY_01_00_55PM.plusDays(5);
            setScheduleDaysOfWeek(mSched10AM, SUNDAY);
            assertThat(getDateTimeAfter(mSched10AM, now),
                    is(Optional.of(now
                            .plusDays(1)
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }

        @Test
        public void test_getDateTimeAfter_ScheduleOccurringOnSameDayAndTime() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            mSched10AM.setTime(MONDAY_01_00_55PM.toLocalTime());
            assertThat(getDateTimeAfter(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(LocalDateTime.of(MONDAY_01_00_55PM.toLocalDate(),
                                mSched10AM.getTime()))));
        }

        @Test
        public void test_getDateTimeAfter_ScheduleOccurringOnSameDayWithTimeAhead() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            mSched10AM.setTime(MONDAY_01_00_55PM.toLocalTime().plusHours(1));
            assertThat(getDateTimeAfter(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(MONDAY_01_00_55PM
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }

        @Test
        public void test_getDateTimeAfter_ScheduleOccurringOnSameDayWithTimeBehind() {
            setScheduleDaysOfWeek(mSched10AM, MONDAY);
            assertThat(getDateTimeAfter(mSched10AM, MONDAY_01_00_55PM),
                    is(Optional.of(MONDAY_01_00_55PM
                            .plusDays(7)
                            .withHour(mSched10AM.getTime().getHour())
                            .withMinute(mSched10AM.getTime().getMinute()))));
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
        @Inject
        DaysOfWeek.Factory mDaysOfWeekFactory;

        void setScheduleDaysOfWeek(final SubscriptionScheduleEntity entity,
                final @DayOfWeek int... daysOfWeek) {

            entity.setDaysOfWeek(mDaysOfWeekFactory.create(daysOfWeek));
        }
    }

    @Config(shadows = {
        ShadowSubscriptionManagerOnSubscriptionsChangedListener.class,
        ShadowSubscriptionManagerHiddenApi.class,
        ShadowTelephonyManagerHiddenApi.class,
    })
    static class BaseWithDatabaseAccess extends Base {
        static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

        @Captor
        ArgumentCaptor<LocalDateTime> mDateTimeCaptor;

        @Inject
        SubscriptionScheduler mSubscriptionScheduler;

        @Inject
        PinStorage mPinStorage;

        @Inject
        SubscriptionManager mSubscriptionManager;

        @Inject
        Subscriptions mSubscriptions;

        @Inject
        AppDatabaseDE mAppDatabaseDE;

        SubscriptionSchedulesDao mSubscriptionSchedulesDao;

        @Override
        public void setUp() {
            super.setUp();

            mSubscriptionSchedulesDao = mAppDatabaseDE.subscriptionSchedulerDao();
        }

        void insert(final SubscriptionScheduleEntity entity) {
            entity.setId(assertFutureDone(EXECUTOR.submit(() ->
                        mSubscriptionSchedulesDao.insert(entity))));
        }

        static <T> T assertFutureDone(final Future<T> future, final Duration atMost) {
            await()
                .dontCatchUncaughtExceptions()
                .pollInSameThread()
                .atMost(atMost)
                .pollInterval(Duration.ofMillis(50))
                .until(future::isDone);
            try {
                return future.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        static <T> T assertFutureDone(final Future<T> future) {
            return assertFutureDone(future, Duration.ofSeconds(5));
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
    }
}
