package com.github.iusmac.sevensim.ui.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.widget.MainSwitchPreference;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;
import com.github.iusmac.sevensim.ui.components.TimePickerPreference;
import com.github.iusmac.sevensim.ui.components.TimePickerPreferenceDialogFragmentCompat;

import dagger.hilt.android.AndroidEntryPoint;

import java.util.Set;

import javax.inject.Inject;

import static com.github.iusmac.sevensim.ui.scheduler.SchedulerViewModel.TimeType;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class SchedulerFragment extends Hilt_SchedulerFragment {
    @Inject
    Logger.Factory mLoggerFactory;

    private Logger mLogger;
    private String mPrefEnabledKey;
    private String mPrefDaysOfWeekKey;
    private String mPrefStartTimeKey;
    private String mPrefEndTimeKey;
    private String mPrefPinKey;
    private SchedulerViewModel mViewModel;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());
        mPrefEnabledKey = getString(R.string.scheduler_enabled_key);
        mPrefDaysOfWeekKey = getString(R.string.scheduler_days_of_week_key);
        mPrefStartTimeKey = getString(R.string.scheduler_start_time_key);
        mPrefEndTimeKey = getString(R.string.scheduler_end_time_key);
        mPrefPinKey = getString(R.string.scheduler_pin_key);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        mViewModel = ((SchedulerActivity) requireActivity()).getViewModel();

        // Hijack default key-value shared preferences before the preferences are actually added
        getPreferenceManager().setPreferenceDataStore(new PreferenceDataStoreCustom());

        addPreferencesFromResource(R.xml.scheduler_preferences);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupMainSwitchPref();
        setupDaysOfWeekPref();
        setupTimePref(TimeType.START_TIME);
        setupTimePref(TimeType.END_TIME);
        setupPinPref();
    }

    private void setupMainSwitchPref() {
        final MainSwitchPreference mainSwitchPref = findPreference(mPrefEnabledKey);

        mViewModel.getSchedulerEnabledState().observe(getViewLifecycleOwner(), (isEnabled) ->
            mainSwitchPref.setChecked(isEnabled));
    }

    private void setupDaysOfWeekPref() {
        final MultiSelectListPreference daysOfWeekPref = findPreference(mPrefDaysOfWeekKey);

        mViewModel.getDaysOfWeekValues().observe(getViewLifecycleOwner(), (values) ->
                daysOfWeekPref.setValues(values));

        mViewModel.getAllDaysOfWeekEntryMap().observe(getViewLifecycleOwner(), (pair) -> {
            daysOfWeekPref.setEntries(pair.getKey());
            daysOfWeekPref.setEntryValues(pair.getValue());
        });

        mViewModel.getDaysOfWeekSummary().observe(getViewLifecycleOwner(), (summary) ->
                daysOfWeekPref.setSummary(summary));

        mViewModel.getSchedulerWeeklyRepeatCycleState()
            .observe(getViewLifecycleOwner(), (isRepeating) ->
                    daysOfWeekPref.notifyDependencyChange(/*disableDependents=*/ !isRepeating));
    }

    private void setupTimePref(final TimeType which) {
        final boolean isStartTime = which == TimeType.START_TIME;
        final TimePickerPreference timePref = findPreference(isStartTime ? mPrefStartTimeKey :
                mPrefEndTimeKey);
        final LiveData<CharSequence> timeSummary = isStartTime ? mViewModel.getStartTimeSummary() :
            mViewModel.getEndTimeSummary();
        mViewModel.getTime(which).observe(getViewLifecycleOwner(), (time) ->
                    timePref.setTime(time.toString()));
        timeSummary.observe(getViewLifecycleOwner(), (summary) -> timePref.setSummary(summary));
    }

    private void setupPinPref() {
        final EditTextPreference pinPref = findPreference(mPrefPinKey);

        pinPref.setOnBindEditTextListener((editText) -> {
            // Clear input junk from the previous usage
            editText.setText("");

            // Allow only numbers in the input field
            editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD);

            // Limit the maximum PIN length as per UICC specs
            editText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(TelephonyUtils.PIN_MAX_PIN_LENGTH)
            });
        });

        mViewModel.getPinPresenceSummary().observe(getViewLifecycleOwner(), (summary) ->
                pinPref.setSummary(summary));

        mViewModel.getPinTaskLock().observe(getViewLifecycleOwner(), (isPinTaskLockHeld) ->
                pinPref.setEnabled(!isPinTaskLockHeld));
    }

    private void handleOnPinChanged(final String pin) {
        // Proceed only if PIN string meets the UICC specs
        if (!TelephonyUtils.isValidPin(pin)) {
            Utils.makeToast(requireContext(), getString(R.string.scheduler_pin_invalid_hint));
            return;
        }

        mViewModel.handleOnPinChanged(pin);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(final @NonNull Preference preference) {
        if (!(preference instanceof TimePickerPreference)) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }

        final FragmentManager manager = getParentFragmentManager();
        if (manager.isDestroyed()) {
            return;
        }

        // Check if the dialog is already showing
        if (manager.findFragmentByTag(TimePickerPreference.TAG) != null) {
            return;
        }

        final DialogFragment f = TimePickerPreferenceDialogFragmentCompat
            .newInstance(preference.getKey());
        // TODO: b/181793702 Sticking to the deprecated until the official migration to
        // androidx.fragment.app.FragmentResultListener API
        f.setTargetFragment(this, /*requestCode=*/ 0);
        f.show(manager, TimePickerPreference.TAG);
    }

    /**
     * This is a custom instance of {@link PreferenceDataStore} used to hijack the default
     * persistence phase on disk via {@link SharedPreferences} when preferences change or require an
     * initial value.
     */
    private final class PreferenceDataStoreCustom extends PreferenceDataStore {
        @Override
        public void putBoolean(final String key, final boolean value) {
            if (key.equals(mPrefEnabledKey)) {
                mViewModel.handleOnEnabledStateChanged(value);
            } else {
                mLogger.wtf("putBoolean() : unhandled key = " + key);
            }
        }

        @Override
        public boolean getBoolean(final String key, final boolean defVal) {
            if (key.equals(mPrefEnabledKey)) {
                return mViewModel.getSchedulerEnabledState().getValue();
            }
            mLogger.wtf("getBoolean() : unhandled key = " + key);
            return defVal;
        }

        @Override
        public void putStringSet(final String key, final Set<String> values) {
            if (key.equals(mPrefDaysOfWeekKey)) {
                mViewModel.handleOnDaysOfWeekChanged(values);
            } else {
                mLogger.wtf("putStringSet() : unhandled key = " + key);
            }
        }

        @Override
        public Set<String> getStringSet(final String key, final Set<String> defValues) {
            if (key.equals(mPrefDaysOfWeekKey)) {
                return mViewModel.getDaysOfWeekValues().getValue();
            }
            mLogger.wtf("getStringSet() : unhandled key = " + key);
            return defValues;
        }

        @Override
        public void putString(final String key, final String value) {
            final boolean isStartTime = key.equals(mPrefStartTimeKey);
            if (isStartTime || key.equals(mPrefEndTimeKey)) {
                mViewModel.handleOnTimeChanged(isStartTime ? TimeType.START_TIME :
                        TimeType.END_TIME, value);
            } else if (key.equals(mPrefPinKey)) {
                handleOnPinChanged(value);
            } else {
                mLogger.wtf("putString() : unhandled key = " + key);
            }
        }

        @Override
        public String getString(final String key, final String defValue) {
            final boolean isStartTime = key.equals(mPrefStartTimeKey);
            if (isStartTime || key.equals(mPrefEndTimeKey)) {
                return mViewModel.getTime(isStartTime ? TimeType.START_TIME :
                        TimeType.END_TIME).getValue().toString();
            }
            if (key.equals(mPrefPinKey)) {
                return null;
            }
            mLogger.wtf("getString() : unhandled key = " + key);
            return defValue;
        }

        @Override
        public int getInt(final String key, final int defValue) {
            mLogger.wtf("getInt() : unhandled key = " + key);
            return defValue;
        }

        @Override
        public long getLong(final String key, final long defValue) {
            mLogger.wtf("getLong() : unhandled key = " + key);
            return defValue;
        }

        @Override
        public float getFloat(final String key, final float defValue) {
            mLogger.wtf("getFloat() : unhandled key = " + key);
            return defValue;
        }
    }
}
