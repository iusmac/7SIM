package com.github.iusmac.sevensim.ui.components;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

import com.github.iusmac.sevensim.R;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * A {@link DialogPreference} that shows a {@link TimePickerDialog} in the dialog.
 */
@SuppressLint("RestrictedApi")
public final class TimePickerPreference extends DialogPreference {
    public static final String TAG = "TimePickerPreference";

    private LocalTime mTime;
    private String mSummary;

    public TimePickerPreference(final @NonNull Context context, final @Nullable AttributeSet attrs,
            final int defStyleAttr, final int defStyleRes) {

        super(context, attrs, defStyleAttr, defStyleRes);

        // Retrieve the Preference summary attribute since it's private in the Preference class
        final TypedArray a = context.obtainStyledAttributes(attrs,
                androidx.preference.R.styleable.Preference, defStyleAttr, defStyleRes);

        mSummary = TypedArrayUtils.getString(a, androidx.preference.R.styleable.Preference_summary,
                androidx.preference.R.styleable.Preference_android_summary);

        a.recycle();
    }

    public TimePickerPreference(final @NonNull Context context, final @Nullable AttributeSet attrs,
            final int defStyleAttr) {

        this(context, attrs, defStyleAttr, 0);
    }

    public TimePickerPreference(final @NonNull Context context, final @Nullable AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                    androidx.preference.R.attr.dialogPreferenceStyle,
                    android.R.attr.dialogPreferenceStyle));
    }

    public TimePickerPreference(final @NonNull Context context) {
        this(context, null);
    }

    /**
     * Save the time to the current data storage.
     *
     * @param time The time value in form "H:m', where "H, is the hour of day from 0 to 23
     * (inclusive), and "m", is the minute of hour from 0 to 59 (inclusive).
     * @throws DateTimeParseException If the time string cannot be parsed.
     */
    public void setTime(final @NonNull String time) {
        if (!TextUtils.equals(getTime(), time)) {

            final boolean wasBlocking = shouldDisableDependents();

            mTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("H:m"));

            persistString(time);

            final boolean isBlocking = shouldDisableDependents();
            if (isBlocking != wasBlocking) {
                notifyDependencyChange(isBlocking);
            }

            notifyChanged();
        }
    }

    /**
     * @return The time, such as "10:15" or {@code null} if the time is unset.
     */
    @Nullable
    public CharSequence getTime() {
        return mTime != null ? mTime.toString() : null;
    }

    @Override
    public void setSummary(final @Nullable CharSequence summary) {
        super.setSummary(summary);

        mSummary = summary == null ? null : summary.toString();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public CharSequence getSummary() {
        if (getSummaryProvider() != null) {
            return getSummaryProvider().provideSummary(this);
        }
        final CharSequence summary = super.getSummary();
        if (mSummary == null) {
            return summary;
        }
        final CharSequence time = getTime() == null ? "" : getTime();
        String formattedString = String.format(mSummary, time);
        if (TextUtils.equals(formattedString, summary)) {
            return summary;
        }
        Log.w(TAG, "Setting a summary with a String formatting marker is no " +
                "longer supported. You should use a SummaryProvider instead.");
        return formattedString;
    }

    @Override
    protected Object onGetDefaultValue(final @NonNull TypedArray a, final int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(final @Nullable Object defaultValue) {
        final String time = getPersistedString((String) defaultValue);
        if (time != null) {
            setTime(time);
        }
    }

    @Override
    public boolean shouldDisableDependents() {
        return mTime == null || super.shouldDisableDependents();
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.mTime = (String) getTime();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(final @Nullable Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setTime(myState.mTime);
    }

    static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
            new Parcelable.Creator<SavedState>() {
                @Override
                public SavedState createFromParcel(final Parcel in) {
                    return new SavedState(in);
                }

                @Override
                public SavedState[] newArray(final int size) {
                    return new SavedState[size];
                }
            };

        String mTime;

        SavedState(final Parcel source) {
            super(source);
            mTime = source.readString();
        }

        SavedState(final Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mTime);
        }
    }

    /**
     * A simple {@link Preference.SummaryProvider} implementation. If no value has been set, the
     * summary displayed will be 'Not set', otherwise the summary displayed will be the entry set
     * for this preference.
     */
    public static final class SimpleSummaryProvider implements SummaryProvider<TimePickerPreference> {
        private static SimpleSummaryProvider sSimpleSummaryProvider;

        private SimpleSummaryProvider() {}

        /**
         * @return a singleton instance of this {@link Preference.SummaryProvider} implementation.
         */
        public @NonNull static SimpleSummaryProvider getInstance() {
            if (sSimpleSummaryProvider == null) {
                sSimpleSummaryProvider = new SimpleSummaryProvider();
            }
            return sSimpleSummaryProvider;
        }

        @Override
        public @Nullable CharSequence provideSummary(final @NonNull TimePickerPreference preference) {
            final CharSequence time = preference.getTime();
            return time != null ? time :
                preference.getContext().getString(R.string.preference_not_set);
        }
    }
}
