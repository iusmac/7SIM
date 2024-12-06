package com.github.iusmac.sevensim.telephony;

import android.app.Application;
import android.telephony.PinResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import static com.github.iusmac.sevensim.telephony.PinResultWrapper.PIN_RESULT_TYPE_SUCCESS;
import static com.github.iusmac.sevensim.telephony.PinResultWrapper.PIN_RESULT_TYPE_INCORRECT;
import static com.github.iusmac.sevensim.telephony.PinResultWrapper.PIN_RESULT_TYPE_FAILURE;
import static com.github.iusmac.sevensim.telephony.PinResultWrapper.PIN_RESULT_TYPE_ABORTED;
import static com.github.iusmac.sevensim.telephony.PinResultWrapper.pinResultToString;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public final class PinResultWrapperTest {
    @Test
    public void test_ConstructingUsingPinResultTypeAndAttemptsRemaining() {
        final int expectedResultType = PIN_RESULT_TYPE_ABORTED;
        final int expectedAttemptsRemaining = 2;
        final var pinResultWrapper = new PinResultWrapper(expectedResultType,
                expectedAttemptsRemaining);
        assertThat(pinResultWrapper.getResult(), is(expectedResultType));
        assertThat(pinResultWrapper.getAttemptsRemaining(), is(expectedAttemptsRemaining));
    }

    @Test
    public void test_ConstructingFromPinResultInstance() {
        final int expectedResultType = PIN_RESULT_TYPE_SUCCESS;
        final int expectedAttemptsRemaining = 3;
        final var pinResult = new PinResult(expectedResultType, expectedAttemptsRemaining);
        final var pinResultWrapper = new PinResultWrapper(pinResult);
        assertThat(pinResultWrapper.getResult(), is(expectedResultType));
        assertThat(pinResultWrapper.getAttemptsRemaining(), is(expectedAttemptsRemaining));
    }

    @Test
    public void test_getDefaultFailedResult() {
        final var pinResultWrapperFailed = PinResultWrapper.getDefaultFailedResult();
        assertThat(pinResultWrapperFailed.getResult(), is(PIN_RESULT_TYPE_FAILURE));
        assertThat(pinResultWrapperFailed.getAttemptsRemaining(), is(-1));
    }

    @Test
    public void test_pinResultToString() {
        assertThat(pinResultToString(PIN_RESULT_TYPE_SUCCESS), is("SUCCESS"));
        assertThat(pinResultToString(PIN_RESULT_TYPE_INCORRECT), is("INCORRECT"));
        assertThat(pinResultToString(PIN_RESULT_TYPE_FAILURE), is("FAILURE"));
        assertThat(pinResultToString(PIN_RESULT_TYPE_ABORTED), is("ABORTED"));
        assertThat(pinResultToString(-1), is("UNKNOWN(-1)"));
    }
}
