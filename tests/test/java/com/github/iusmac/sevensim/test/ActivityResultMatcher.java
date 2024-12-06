package com.github.iusmac.sevensim.test;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import static androidx.activity.result.ActivityResult.resultCodeToString;

/** Matches an {@link ActivityResult} object with the same {@link ActivityResult#getResultCode}. */
public final class ActivityResultMatcher extends TypeSafeMatcher<ActivityResult> {
    private final int mExpected;

    public ActivityResultMatcher(int expectedResultCode) {
        mExpected = expectedResultCode;
    }

    @Override
    protected boolean matchesSafely(final ActivityResult actual) {
        return mExpected == actual.getResultCode();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("an ActivityResult with ")
            .appendText(resultCodeToString(mExpected));
    }

    @Override
    protected void describeMismatchSafely(final ActivityResult actual,
            final Description mismatchDescription) {

        mismatchDescription.appendText("was an ActivityResult with ")
            .appendText(resultCodeToString(actual.getResultCode()));
    }

    /** Matches a successful {@link ActivityResult} object. */
    public static ActivityResultMatcher isOk() {
        return new ActivityResultMatcher(Activity.RESULT_OK);
    }

    /** Matches a canceled {@link ActivityResult} object. */
    public static ActivityResultMatcher isCanceled() {
        return new ActivityResultMatcher(Activity.RESULT_CANCELED);
    }
}
