package com.github.iusmac.sevensim.ui.scheduler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.github.iusmac.sevensim.ui.AuthenticationPromptActivity;
import com.github.iusmac.sevensim.ui.components.TimePickerPreference;
import com.github.iusmac.sevensim.ui.components.TimePickerPreferenceDialogFragmentCompat;

import dagger.hilt.android.AndroidEntryPoint;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import static com.github.iusmac.sevensim.ui.scheduler.SchedulerViewModel.TimeType;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class SchedulerFragment extends Hilt_SchedulerFragment {
    private static final String ACTION_AUTH_HANDLE_ON_PIN_CHANGED =
        "action_auth_handle_on_pin_changed";

    private static final String ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED =
        "action_auth_handle_on_enabled_state_changed";

    private static final String ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED =
        "action_auth_handle_on_days_of_week_changed";

    private static final String ACTION_AUTH_HANDLE_ON_TIME_CHANGED =
        "action_auth_handle_on_time_changed";

    private static final String EXTRA_PIN = "pin";
    private static final String EXTRA_ENABLED = "enabled";
    private static final String EXTRA_DAYS_OF_WEEK = "days_of_week";
    private static final String EXTRA_TIME_TYPE = "time_type";
    private static final String EXTRA_TIME = "time";

    @Inject
    Logger.Factory mLoggerFactory;

    private Logger mLogger;
    private String mPrefEnabledKey;
    private String mPrefDaysOfWeekKey;
    private String mPrefStartTimeKey;
    private String mPrefEndTimeKey;
    private String mPrefPinKey;
    private SchedulerViewModel mViewModel;
    private final ActivityResultLauncher<Intent> mAuthenticationPromptLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                this::onAuthResult);

    private MainSwitchPreference mMainSwitchPref;

    private void onAuthResult(final ActivityResult result) {
        mLogger.d("onAuthResult(result=%s).", result);

        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        final Intent data = result.getData();
        final String action = data.getAction() != null ? data.getAction() : "";
        switch (action) {
            case ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED:
                mViewModel.handleOnEnabledStateChanged(data.getBooleanExtra(EXTRA_ENABLED, false));
                break;

            case ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED:
                final Set<String> values = Arrays.stream(data
                        .getStringArrayExtra(EXTRA_DAYS_OF_WEEK)).collect(Collectors.toSet());
                mViewModel.handleOnDaysOfWeekChanged(values);
                break;

            case ACTION_AUTH_HANDLE_ON_TIME_CHANGED:
                final TimeType which = TimeType.valueOf(data.getStringExtra(EXTRA_TIME_TYPE));
                mViewModel.handleOnTimeChanged(which, data.getStringExtra(EXTRA_TIME));
                break;

            case ACTION_AUTH_HANDLE_ON_PIN_CHANGED:
                mViewModel.handleOnPinChanged(data.getStringExtra(EXTRA_PIN));
                break;

            default: mLogger.wtf("onAuthResult(result=%s) : unhandled action: %s.", result, action);
        }
    }

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
        mMainSwitchPref = findPreference(mPrefEnabledKey);

        mViewModel.getSchedulerEnabledState().observe(getViewLifecycleOwner(), (isEnabled) ->
            mMainSwitchPref.setChecked(isEnabled));
    }

    @SuppressWarnings("unchecked")
    private void setupDaysOfWeekPref() {
        final MultiSelectListPreference daysOfWeekPref = findPreference(mPrefDaysOfWeekKey);

        daysOfWeekPref.setOnPreferenceChangeListener((pref, value) -> {
            final Set<String> values = (Set<String>) value;
            if (!values.isEmpty() && mViewModel.getSchedulerEnabledState().getValue()
                    && mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
                final Bundle payload = new Bundle(1);
                payload.putStringArray(EXTRA_DAYS_OF_WEEK,
                        values.toArray(new String[values.size()]));
                authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_DAYS_OF_WEEK_CHANGED, payload);
                return false;
            }
            return true;
        });

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
        timePref.setOnPreferenceChangeListener((pref, value) -> {
            if (mViewModel.getSchedulerEnabledState().getValue()
                    && !mViewModel.getDaysOfWeekValues().getValue().isEmpty()
                    && mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
                final Bundle payload = new Bundle(2);
                payload.putString(EXTRA_TIME_TYPE, which.toString());
                payload.putString(EXTRA_TIME, (String) value);
                authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_TIME_CHANGED, payload);
                return false;
            }
            return true;
        });
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

        // Authenticate the user again to unlock the hardware-backed KeyStore for further crypto
        // operations on the provided SIM PIN code
        if (mViewModel.isAuthenticationRequired()) {
            final Bundle payload = new Bundle(1);
            payload.putString(EXTRA_PIN, pin);
            authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_PIN_CHANGED, payload);
        } else {
            mViewModel.handleOnPinChanged(pin);
        }
    }

    /**
     * @param action The action to run after authenticating the user.
     * @param extras The Bundle holding payload data.
     */
    private void authenticateAndRunAction(final String action, final Bundle payload) {
        final Intent i = new Intent(requireContext(), AuthenticationPromptActivity.class);
        i.setAction(action);
        i.putExtras(payload);
        mAuthenticationPromptLauncher.launch(i);
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
                if (value && !mViewModel.getDaysOfWeekValues().getValue().isEmpty()
                        && mViewModel.isPinPresent() && mViewModel.isAuthenticationRequired()) {
                    final Bundle payload = new Bundle(1);
                    payload.putBoolean(EXTRA_ENABLED, value);
                    authenticateAndRunAction(ACTION_AUTH_HANDLE_ON_ENABLED_STATE_CHANGED, payload);
                    mMainSwitchPref.updateStatus(!value); // revert toggle state
                } else {
                    mViewModel.handleOnEnabledStateChanged(value);
                }
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
