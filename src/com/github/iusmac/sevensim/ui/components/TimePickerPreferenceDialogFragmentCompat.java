package com.github.iusmac.sevensim.ui.components;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.time.LocalTime;

public final class TimePickerPreferenceDialogFragmentCompat
    extends PreferenceDialogFragmentCompat implements OnTimeSetListener {

    private static final String SAVE_STATE_HOUR = "TimePickerPreferenceDialogFragmentCompat.hour";

    private static final String SAVE_STATE_MINUTE =
        "TimePickerPreferenceDialogFragmentCompat.minute";

    private static final String SAVE_STATE_IS_24_HOUR =
        "TimePickerPreferenceDialogFragmentCompat.is24hour";

    private int mHourOfDay, mMinute;
    private boolean mIs24HourFormat;

    @NonNull
    public static TimePickerPreferenceDialogFragmentCompat newInstance(final @NonNull String key) {
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);

        final TimePickerPreferenceDialogFragmentCompat fragment =
            new TimePickerPreferenceDialogFragmentCompat();
        fragment.setArguments(b);
        return fragment;
    }

    private TimePickerPreference getTimePickerPreference() {
        return (TimePickerPreference) getPreference();
    }

    /**
     * Persist when the user is done filling in the time and pressed the "OK" button.
     */
    @Override
    public void onTimeSet(final TimePicker view, final int hourOfDay, final int minute) {
        final TimePickerPreference preference = getTimePickerPreference();
        final String time = LocalTime.of(hourOfDay, minute).toString();
        if (!TextUtils.equals(preference.getTime(), time) && preference.callChangeListener(time)) {
            preference.setTime(time);
        }
    }

    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final CharSequence time = getTimePickerPreference().getTime();
            final LocalTime lt = time != null ? LocalTime.parse(time) : LocalTime.now();
            mHourOfDay = lt.getHour();
            mMinute = lt.getMinute();
            mIs24HourFormat = DateFormat.is24HourFormat(requireContext());
        } else {
            mHourOfDay = savedInstanceState.getInt(SAVE_STATE_HOUR);
            mMinute = savedInstanceState.getInt(SAVE_STATE_MINUTE);
            mIs24HourFormat = savedInstanceState.getBoolean(SAVE_STATE_IS_24_HOUR);
        }
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_HOUR, mHourOfDay);
        outState.putInt(SAVE_STATE_MINUTE, mMinute);
        outState.putBoolean(SAVE_STATE_IS_24_HOUR, mIs24HourFormat);
    }

    @Override
    public @NonNull Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        return new TimePickerDialog(requireContext(), this, mHourOfDay, mMinute, mIs24HourFormat);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        // NO-OP. Already handled by onTimeSet()
    }
}
