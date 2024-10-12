package com.github.iusmac.sevensim.test;

import androidx.annotation.CallSuper;

import com.github.iusmac.sevensim.telephony.Subscription;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/** A collection of {@link org.hamcrest.Matcher}s for a {@link Subscription} with properties. */
public final class SubscriptionPropertyMatchers {
    /** Matches a {@link Subscription} with ID. */
    public static TypeSafeMatcher<Subscription> withSubId(final int expectedSubId) {
        return new WithSubIdMatcher(expectedSubId);
    }
}

final class WithSubIdMatcher extends SubscriptionPropertyMatcher {
    private final int mExpectedSubId;

    WithSubIdMatcher(int expectedId) {
        mExpectedSubId = expectedId;
    }

    @Override
    protected boolean matchesSafely(final Subscription subscription) {
        return subscription.getId() == mExpectedSubId;
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with ID ").appendValue(mExpectedSubId);
    }

    @Override
    protected void describeMismatchSafely(final Subscription subscription,
            final Description mismatchDescription) {

        super.describeMismatchSafely(subscription, mismatchDescription);
        mismatchDescription.appendText("with ID ").appendValue(subscription.getId());
    }
}

abstract class SubscriptionPropertyMatcher extends TypeSafeMatcher<Subscription> {
    @CallSuper
    @Override
    public void describeTo(Description description) {
        description.appendText("a Subscription ");
    }

    @CallSuper
    @Override
    protected void describeMismatchSafely(final Subscription subscription,
            final Description mismatchDescription) {

        mismatchDescription.appendText("was a Subscription ");
    }
}
