package com.github.iusmac.sevensim.test;

import android.os.Bundle;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/** Matches a {@link Bundle} object. */
public final class SameBundleMatcher extends TypeSafeMatcher<Bundle> {
    private final Bundle mExpected;

    public SameBundleMatcher(Bundle expectedBundle) {
        mExpected = expectedBundle;
    }

    @Override
    protected boolean matchesSafely(final Bundle actual) {
        if (mExpected.isParcelled() != actual.isParcelled()) {
            throw new IllegalArgumentException("Cannot compare when only one Bundle is parcelled!");
        }
        return mExpected.kindofEquals(actual);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(mExpected);
    }

    @Override
    protected void describeMismatchSafely(final Bundle actual,
            final Description mismatchDescription) {

        mismatchDescription.appendText("was ").appendValue(actual);
    }

    /**
     * Matches a {@link Bundle} object.
     *
     * @throws IllegalArgumentException When actual and expected Bundle objects don't pass the
     * {@link Bundle#isParcelled} check, otherwise a loose comparison will be performed, which means
     * there can be false negatives.
     */
    public static SameBundleMatcher sameBundle(final Bundle expectedBundle) {
        return new SameBundleMatcher(expectedBundle);
    }
}
