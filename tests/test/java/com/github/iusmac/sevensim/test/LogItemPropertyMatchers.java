package com.github.iusmac.sevensim.test;

import androidx.annotation.CallSuper;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import org.robolectric.shadows.ShadowLog.LogItem;

/** A collection of {@link org.hamcrest.Matcher}s for a {@link LogItem} with properties. */
public final class LogItemPropertyMatchers {
    /** Matches a {@link LogItem#type} property. */
    public static TypeSafeMatcher<LogItem> withType(final int expectedType) {
        return new WithTypeMatcher(expectedType);
    }

    /** Matches a {@link LogItem#tag} property. */
    public static TypeSafeMatcher<LogItem> withTag(final Matcher<String> expectedTag) {
        return new WithTagMatcher(expectedTag);
    }

    /** Matches a {@link LogItem#msg} property. */
    public static TypeSafeMatcher<LogItem> withMsg(final Matcher<String> expectedMsg) {
        return new WithMsgMatcher(expectedMsg);
    }

    /** Matches a {@link LogItem#throwable} property. */
    public static TypeSafeMatcher<LogItem> withThrowable(final Throwable expectedThrowable) {
        return new WithThrowableMatcher(expectedThrowable);
    }
}

final class WithTypeMatcher extends LogItemPropertyMatcher {
    private final int mExpectedType;

    WithTypeMatcher(int expectedType) {
        mExpectedType = expectedType;
    }

    @Override
    protected boolean matchesSafely(final LogItem logItem) {
        return logItem.type == mExpectedType;
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with type property ").appendValue(mExpectedType);
    }

    @Override
    protected void describeMismatchSafely(final LogItem logItem,
            final Description mismatchDescription) {

        super.describeMismatchSafely(logItem, mismatchDescription);
        mismatchDescription.appendText("with type property ").appendValue(logItem.type);
    }
}

final class WithTagMatcher extends LogItemPropertyMatcher {
    private final Matcher<String> mExpectedTag;

    WithTagMatcher(Matcher<String> expectedTag) {
        mExpectedTag = expectedTag;
    }

    @Override
    protected boolean matchesSafely(final LogItem logItem) {
        return mExpectedTag.matches(logItem.tag);
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with tag property ").appendValue(mExpectedTag);
    }

    @Override
    protected void describeMismatchSafely(final LogItem logItem,
            final Description mismatchDescription) {

        super.describeMismatchSafely(logItem, mismatchDescription);
        mismatchDescription.appendText("with tag property ").appendValue(logItem.tag);
    }
}

final class WithMsgMatcher extends LogItemPropertyMatcher {
    private final Matcher<String> mExpectedMsg;

    WithMsgMatcher(Matcher<String> expectedMsg) {
        mExpectedMsg = expectedMsg;
    }

    @Override
    protected boolean matchesSafely(final LogItem logItem) {
        return mExpectedMsg.matches(logItem.msg);
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with msg property ").appendDescriptionOf(mExpectedMsg);
    }

    @Override
    protected void describeMismatchSafely(final LogItem logItem,
            final Description mismatchDescription) {

        super.describeMismatchSafely(logItem, mismatchDescription);
        mismatchDescription.appendText("with msg property ").appendValue(logItem.msg);
    }
}

final class WithThrowableMatcher extends LogItemPropertyMatcher {
    private final Throwable mExpectedThrowable;

    WithThrowableMatcher(Throwable expectedThrowable) {
        mExpectedThrowable = expectedThrowable;
    }

    @Override
    protected boolean matchesSafely(final LogItem logItem) {
        return mExpectedThrowable.equals(logItem.throwable);
    }

    @Override
    public void describeTo(Description description) {
        super.describeTo(description);
        description.appendText("with throwable property ").appendValue(mExpectedThrowable);
    }

    @Override
    protected void describeMismatchSafely(final LogItem logItem,
            final Description mismatchDescription) {

        super.describeMismatchSafely(logItem, mismatchDescription);
        mismatchDescription.appendText("with throwable property ").appendValue(logItem.throwable);
    }
}

abstract class LogItemPropertyMatcher extends TypeSafeMatcher<LogItem> {
    @CallSuper
    @Override
    public void describeTo(Description description) {
        description.appendText("a LogItem ");
    }

    @CallSuper
    @Override
    protected void describeMismatchSafely(final LogItem logItem,
            final Description mismatchDescription) {

        mismatchDescription.appendText("was a LogItem ");
    }
}
