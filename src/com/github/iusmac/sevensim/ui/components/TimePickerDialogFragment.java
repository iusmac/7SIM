package com.github.iusmac.sevensim.ui.components;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.time.LocalTime;

/**
 * A {@link DialogFragment} allowing the user to pick a time.
 */
public class TimePickerDialogFragment extends DialogFragment implements OnTimeSetListener {
    public static final String TAG = TimePickerDialogFragment.class.getSimpleName();

    private static final String SAVE_REQUEST_KEY = "TimePickerDialogFragment.requestKey";
    private static final String SAVE_STATE_HOUR = "TimePickerDialogFragment.hour";
    private static final String SAVE_STATE_MINUTE = "TimePickerDialogFragment.minute";
    private static final String SAVE_STATE_IS_24_HOUR = "TimePickerDialogFragment.is24hour";

    /**
     * The default request key string passed in
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)} to identify the result when
     * the user is done filling in the time and pressed the "OK" button.
     */
    public static final String DEFAULT_REQUEST_KEY = "requestKey";

    /** Key holding the time passed in
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)}. */
    public static final String EXTRA_TIME = "time";

    private String mRequestKey;
    private int mHourOfDay = -1, mMinute = -1;
    private boolean mIs24HourFormat;

    public TimePickerDialogFragment() {
    }

    /**
     * @param requestKey The request key string for
     * {@link FragmentResultListener#onFragmentResult(String,Bundle)} to identify the result when
     * the user is done filling in the time and pressed the "OK" button, or {@code null} to use
     * {@link #DEFAULT_REQUEST_KEY}.
     */
    public TimePickerDialogFragment(final @Nullable String requestKey) {
        mRequestKey = requestKey;
    }

    /**
     * Set the hour of day to be used by the picker.
     *
     * @param hour The hour of day from 0 to 23 (inclusive), or -1 to use the hour of day from the
     * current local time.
     */
    public void setHour(final int hour) {
        mHourOfDay = hour < 0 || hour > 23 ? -1 : hour;
    }

    /**
     * Set the minute of hour to bused by the picker.
     *
     * @param minute The minute of hour from 0 to 59 (inclusive), or -1 to use the minute from the
     * current local time.
     */
    public void setMinute(final int minute) {
        mMinute = minute < 0 || minute > 59 ? -1 : minute;
    }

    @Override
    public void onTimeSet(final TimePicker view, final int hourOfDay, final int minute) {
        final String requestKey = mRequestKey == null ? DEFAULT_REQUEST_KEY : mRequestKey;
        final Bundle result = new Bundle(1);
        final String time = LocalTime.of(hourOfDay, minute).toString();
        result.putString(EXTRA_TIME, time);
        getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final LocalTime lt = LocalTime.now();
            if (mHourOfDay < 0) {
                mHourOfDay = lt.getHour();
            }
            if (mMinute < 0) {
                mMinute = lt.getMinute();
            }
            mIs24HourFormat = DateFormat.is24HourFormat(requireContext());
        } else {
            mRequestKey = savedInstanceState.getString(SAVE_REQUEST_KEY);
            mHourOfDay = savedInstanceState.getInt(SAVE_STATE_HOUR);
            mMinute = savedInstanceState.getInt(SAVE_STATE_MINUTE);
            mIs24HourFormat = savedInstanceState.getBoolean(SAVE_STATE_IS_24_HOUR);
        }
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVE_REQUEST_KEY, mRequestKey);
        outState.putInt(SAVE_STATE_HOUR, mHourOfDay);
        outState.putInt(SAVE_STATE_MINUTE, mMinute);
        outState.putBoolean(SAVE_STATE_IS_24_HOUR, mIs24HourFormat);
    }

    @Override
    public @NonNull Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        return new TimePickerDialog(requireContext(), this, mHourOfDay, mMinute, mIs24HourFormat);
    }
}
