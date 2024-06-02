package com.github.iusmac.sevensim.telephony;

import android.telephony.PinResult;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.internal.telephony.PhoneConstants;

/**
 * This class is a wrapper around the {@link PinResult} introduced in Android 11 (R) used to support
 * previous Android versions.
 */
final class PinResultWrapper {
    @IntDef({
        PIN_RESULT_TYPE_SUCCESS,
        PIN_RESULT_TYPE_INCORRECT,
        PIN_RESULT_TYPE_FAILURE,
        PIN_RESULT_TYPE_ABORTED,
    })
    @interface PinResultType {}

    /** See {@link PinResult#PIN_RESULT_TYPE_SUCCESS}. */
    static final int PIN_RESULT_TYPE_SUCCESS = PhoneConstants.PIN_RESULT_SUCCESS;

    /** See {@link PinResult#PIN_RESULT_TYPE_INCORRECT}. */
    static final int PIN_RESULT_TYPE_INCORRECT = PhoneConstants.PIN_PASSWORD_INCORRECT;

    /** See {@link PinResult#PIN_RESULT_TYPE_FAILURE}. */
    static final int PIN_RESULT_TYPE_FAILURE = PhoneConstants.PIN_GENERAL_FAILURE;

    /** See {@link PinResult#PIN_RESULT_TYPE_ABORTED}. */
    static final int PIN_RESULT_TYPE_ABORTED = PhoneConstants.PIN_OPERATION_ABORTED;

    private static PinResultWrapper sFailedResult =
        new PinResultWrapper(PIN_RESULT_TYPE_FAILURE, -1);

    private final @PinResultType int mResult;
    private final int mAttemptsRemaining;

    /** See {@link PinResult(int,int)}. */
    PinResultWrapper(final @PinResultType int result, final int attemptsRemaining) {
        mResult = result;
        mAttemptsRemaining = attemptsRemaining;
    }

    /** Construct from {@link PinResult}. */
    PinResultWrapper(final @NonNull PinResult pinResult) {
        this(pinResult.getResult(), pinResult.getAttemptsRemaining());
    }

    /** See {@link PinResult#getResult()}. */
    @PinResultType int getResult() {
        return mResult;
    }

    /** See {@link PinResult#getAttemptsRemaining()}. */
    int getAttemptsRemaining() {
        return mAttemptsRemaining;
    }

    /** See {@link PinResult#getDefaultFailedResult()}. */
    static PinResultWrapper getDefaultFailedResult() {
        return sFailedResult;
    }

    /** Convert PIN result code to a string. */
    public static String pinResultToString(final @PinResultType int pinResult) {
        switch (pinResult) {
            case PIN_RESULT_TYPE_SUCCESS:
                return "SUCCESS";
            case PIN_RESULT_TYPE_INCORRECT:
                return "INCORRECT";
            case PIN_RESULT_TYPE_FAILURE:
                return "FAILURE";
            case PIN_RESULT_TYPE_ABORTED:
                return "ABORTED";
            default:
                return "UNKNOWN(" + pinResult + ")";
        }
    }

    @Override
    public String toString() {
        return "result: " + pinResultToString(getResult()) + ", attempts remaining: " +
            getAttemptsRemaining();
    }
}
