package com.github.iusmac.sevensim.test;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;

import java.time.LocalTime;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * A collection of {@link org.hamcrest.Matcher}s for a {@link SubscriptionScheduleEntity} with
 * properties.
 */
public final class SubscriptionScheduleEntityPropertyMatchers {
    /** Matches a {@link SubscriptionScheduleEntity} with ID. */
    public static TypeSafeMatcher<SubscriptionScheduleEntity> withId(final long expectedId) {
        return new WithIdMatcher(expectedId);
    }

    /** Matches a {@link SubscriptionScheduleEntity} with ID. */
    public static TypeSafeMatcher<SubscriptionScheduleEntity> withSubscriptionEnabled(
            final boolean enabled) {
        return new WithSubscriptionEnabledMatcher(enabled);
    }

    /** Matches a {@link SubscriptionScheduleEntity} with time. */
    public static TypeSafeMatcher<SubscriptionScheduleEntity> withTime(final LocalTime time) {
        return new WithTimeMatcher(time);
    }
}

final class WithIdMatcher extends SubscriptionScheduleEntityMatcher {
    private final long mExpectedId;

    WithIdMatcher(final long expectedId) {
        mExpectedId = expectedId;
    }

    @Override
    protected boolean matchesSafely(final SubscriptionScheduleEntity schedule) {
        return schedule.getId() == mExpectedId;
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with ID ").appendValue(mExpectedId);
    }

    @Override
    protected void describeMismatchSafely(final SubscriptionScheduleEntity schedule,
            final Description mismatchDescription) {

        super.describeMismatchSafely(schedule, mismatchDescription);
        mismatchDescription.appendText("with ID ").appendValue(schedule.getId());
    }
}

final class WithSubscriptionEnabledMatcher extends SubscriptionScheduleEntityMatcher {
    private final boolean mExpectedEnabled;

    WithSubscriptionEnabledMatcher(final boolean enabled) {
        mExpectedEnabled = enabled;
    }

    @Override
    protected boolean matchesSafely(final SubscriptionScheduleEntity schedule) {
        return schedule.getSubscriptionEnabled() == mExpectedEnabled;
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with subscription enabled ").appendValue(mExpectedEnabled);
    }

    @Override
    protected void describeMismatchSafely(final SubscriptionScheduleEntity schedule,
            final Description mismatchDescription) {

        super.describeMismatchSafely(schedule, mismatchDescription);
        mismatchDescription.appendText("with subscription enabled ")
            .appendValue(schedule.getSubscriptionEnabled());
    }
}

final class WithTimeMatcher extends SubscriptionScheduleEntityMatcher {
    private final LocalTime mExpectedTime;

    WithTimeMatcher(final LocalTime time) {
        mExpectedTime = time;
    }

    @Override
    protected boolean matchesSafely(final SubscriptionScheduleEntity schedule) {
        return schedule.getTime().equals(mExpectedTime);
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with time ").appendValue(mExpectedTime);
    }

    @Override
    protected void describeMismatchSafely(final SubscriptionScheduleEntity schedule,
            final Description mismatchDescription) {

        super.describeMismatchSafely(schedule, mismatchDescription);
        mismatchDescription.appendText("with time ").appendValue(schedule.getTime());
    }
}

abstract class SubscriptionScheduleEntityMatcher extends TypeSafeMatcher<SubscriptionScheduleEntity> {
    @CallSuper
    @Override
    public void describeTo(Description description) {
        description.appendText("a SubscriptionScheduleEntity ");
    }

    @CallSuper
    @Override
    protected void describeMismatchSafely(final SubscriptionScheduleEntity schedule,
            final Description mismatchDescription) {

        mismatchDescription.appendText("was a SubscriptionScheduleEntity ");
    }
}
