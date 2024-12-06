package com.github.iusmac.sevensim.scheduler;

import android.content.res.Resources;

import androidx.core.text.HtmlCompat;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.telephony.SimState;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.mockito.BDDMockito.willAnswer;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class SubscriptionSchedulerSummaryBuilderTest extends MockitoHiltAndroidTestBase {
    private static final LocalDateTime NOW = LocalDateTime.of(2007, 1, 1, 13, 0);

    private final Subscription mSubscription = new Subscription();
    {
        mSubscription.setId(1);
    }

    private final SubscriptionScheduleEntity mNearestSchedule = new SubscriptionScheduleEntity();
    {
        mNearestSchedule.setTime(NOW.toLocalTime().plusHours(1));
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Inject
    SubscriptionScheduler mSubscriptionScheduler;

    @Inject
    DaysOfWeek.Factory mDaysOfWeekFactory;

    @Inject
    SubscriptionSchedulerSummaryBuilder mSubscriptionSchedulerSummaryBuilder;

    private Resources mResources;

    @Override
    public void setUp() {
        super.setUp();

        mResources = mApplicationContext.getResources();

        mNearestSchedule.setDaysOfWeek(mDaysOfWeekFactory
                .create(new int[] { NOW.get(WeekFields.SUNDAY_START.dayOfWeek()) }));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithNoSchedules() {
        assertThat(buildNextUpcomingSubscriptionScheduleSummary(),
                is(mResources.getString(R.string.scheduler_no_schedule_summary)));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithNoNearestScheduleAndSimDisabled() {
        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return 1;
        }).given(mSubscriptionScheduler).getCountBySubscriptionId(mSubscription.getId());

        assertThat(buildNextUpcomingSubscriptionScheduleSummary(),
                is(mResources.getText(R.string.scheduler_start_time_none_summary)));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithNoNearestScheduleShouldDisableAndSimEnabled() {
        mSubscription.setSimState(SimState.ENABLED);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return 1;
        }).given(mSubscriptionScheduler).getCountBySubscriptionId(mSubscription.getId());

        assertThat(buildNextUpcomingSubscriptionScheduleSummary(),
                is(mResources.getText(R.string.scheduler_end_time_none_summary)));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithNearestScheduleShouldDisableAndSimEnabled() {
        mSubscription.setSimState(SimState.ENABLED);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_end_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("today, 2")).and(endsWith("PM"))));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithNearestScheduleShouldEnabledAndSimEnabled() {
        mSubscription.setSimState(SimState.ENABLED);
        mNearestSchedule.setSubscriptionEnabled(true);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_start_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("today, 2")).and(endsWith("PM"))));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithSimAlreadyEnabledAndNearestScheduleShouldEnable() {
        mSubscription.setSimState(SimState.ENABLED);
        mSubscription.setLastActivatedTime(NOW);
        mNearestSchedule.setSubscriptionEnabled(true);
        mNearestSchedule.setTime(NOW.toLocalTime());

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_start_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("Mon, Jan 8, 1")).and(endsWith("PM"))));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_WithSimAlreadyDisabledAndNearestScheduleShouldDisable() {
        mSubscription.setSimState(SimState.DISABLED);
        mSubscription.setLastDeactivatedTime(NOW);
        mNearestSchedule.setTime(NOW.toLocalTime());

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_end_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("Mon, Jan 8, 1")).and(endsWith("PM"))));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_MultipleTimes() {
        mSubscription.setSimState(SimState.ENABLED);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        buildNextUpcomingSubscriptionScheduleSummary();

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_end_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("today, 2")).and(endsWith("PM"))));
    }

    @Test
    public void buildNextUpcomingSubscriptionScheduleSummary_LocaleChange() {
        mSubscription.setSimState(SimState.ENABLED);

        willAnswer((invocation) -> {
            invocation.callRealMethod();
            return Optional.of(mNearestSchedule);
        }).given(mSubscriptionScheduler).findNearestAfterDateTime(mSubscription.getId(),
            !mSubscription.isSimEnabled(), NOW);

        buildNextUpcomingSubscriptionScheduleSummary();

        Locale.setDefault(Locale.ITALY);

        final var summary = HtmlCompat.fromHtml(mResources.getString(
                    R.string.scheduler_end_time_custom_summary, ""), 0).toString();
        assertThat(buildNextUpcomingSubscriptionScheduleSummary().toString(),
                is(both(startsWith(summary)).and(containsString("oggi, 2")).and(endsWith("PM"))));
    }

    private CharSequence buildNextUpcomingSubscriptionScheduleSummary() {
        final var future = EXECUTOR.submit(() -> mSubscriptionSchedulerSummaryBuilder
                .buildNextUpcomingSubscriptionScheduleSummary(mSubscription, NOW));

        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(3))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);

        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
