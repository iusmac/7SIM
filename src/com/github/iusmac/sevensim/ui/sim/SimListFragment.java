package com.github.iusmac.sevensim.ui.sim;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.collection.SparseArrayCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.widget.BannerMessagePreference;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.SevenSimApplication;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.PrimarySwitchPreference;
import com.github.iusmac.sevensim.ui.scheduler.SchedulerActivity;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class SimListFragment extends Hilt_SimListFragment {
    @Inject
    SevenSimApplication mApp;

    @Inject
    SharedPreferences mSharedPrefs;

    private SimListViewModel mViewModel;

    private PreferenceCategory mSimPreferenceCategory;
    private Preference mNoSimPreference;
    private SparseArrayCompat<PrimarySwitchPreference> mSimPreferences = new SparseArrayCompat<>();

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        mViewModel = new ViewModelProvider(requireActivity()).get(SimListViewModel.class);

        addPreferencesFromResource(R.xml.sim_preferences);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupDisclaimerBanner();
        setupSimList();
        setupUpdatesPref();
        setupVersionPref();
    }

    private void setupDisclaimerBanner() {
        final BannerMessagePreference disclaimerBanner =
            findPreference(getString(R.string.sim_list_disclaimer_banner_key));

        disclaimerBanner.setVisible(mApp.hasAospPlatformSignature() &&
                mSharedPrefs.getBoolean(disclaimerBanner.getKey(), true));
        disclaimerBanner
            .setAttentionLevel(BannerMessagePreference.AttentionLevel.MEDIUM)
            .setPositiveButtonText(
                    com.android.settingslib.widget.R.string.accessibility_banner_message_dismiss)
            .setPositiveButtonOnClickListener((view) -> {
                disclaimerBanner.setVisible(false);
                mSharedPrefs.edit().putBoolean(disclaimerBanner.getKey(), false).apply();
            });
    }

    private void setupSimList() {
        mSimPreferenceCategory = findPreference(getString(R.string.sim_list_key));

        mNoSimPreference = mSimPreferenceCategory
            .findPreference(getString(R.string.sim_list_no_sim_key));

        mViewModel.getSimEntries().observe(getViewLifecycleOwner(), (entries) ->
                updateSimPreferenceList(entries));
    }

    private void setupUpdatesPref() {
        final Preference updatesPref = findPreference(getString(R.string.sim_list_updates_key));
        // Note that the preference *must* be grayed out if the application has been signed with ROM
        // maintainer's private keys (aka dev-keys signature). Hopefully, this will prevent users
        // from "blindly" installing the official update signed with public AOSP platform signature
        updatesPref.setEnabled(mApp.hasAospPlatformSignature());
    }

    private void setupVersionPref() {
        final Preference versionPref = findPreference(getString(R.string.sim_list_version_key));
        versionPref.setSummary(mApp.getPackageVersionName());
    }

    private void updateSimPreferenceList(
            final SparseArrayCompat<SimListViewModel.SimEntry> simEntries) {

        final SparseArrayCompat<PrimarySwitchPreference> existingSimPreferences = mSimPreferences;
        mSimPreferences = new SparseArrayCompat<>(simEntries.size());

        final Context context = requireContext();

        for (int i = 0, size = simEntries.size(); i < size; i++) {
            final int simEntryId = simEntries.keyAt(i);
            final SimListViewModel.SimEntry simEntry = simEntries.valueAt(i);
            final Subscription sub = simEntry.getSubscription();

            PrimarySwitchPreference pref;
            if ((pref = existingSimPreferences.get(simEntryId)) == null) {
                pref = new PrimarySwitchPreference(context);
                pref.setIconSize(PrimarySwitchPreference.ICON_SIZE_MEDIUM);
                // Since we're not persisting the preference, override the default value (which is
                // false = unchecked) to toggle the switch widget when first added
                pref.setDefaultValue(sub.isSimEnabled());
                pref.setOnPreferenceChangeListener((changedPref, value) -> {
                    // Lock the switch till next update cycle
                    ((PrimarySwitchPreference) changedPref).setSwitchEnabled(false);

                    final boolean enabled = (Boolean) value;
                    mViewModel.handleOnSimEnabledStateChanged(simEntryId, enabled);
                    return false; // don't persist
                });
                mSimPreferenceCategory.addPreference(pref);
            } else {
                existingSimPreferences.remove(simEntryId);
                pref.setSwitchEnabled(true);
            }
            pref.setOrder(i);
            pref.setIcon(UiUtils.createTintedSimIcon(context, sub.getIconTint()));
            pref.setTitle(sub.getSimName());
            pref.setSummary(simEntry.getNextUpcomingScheduleSummary());
            pref.setChecked(sub.isSimEnabled());
            pref.setOnPreferenceClickListener((clickedPref) -> {
                final Intent intent = new Intent(context, SchedulerActivity.class);
                intent.putExtra(SchedulerActivity.EXTRA_SUBSCRIPTION, sub);
                startActivity(intent);
                return true;
            });
            mSimPreferences.put(simEntryId, pref);
        }

        for (int i = 0, size = existingSimPreferences.size(); i < size; i++) {
            mSimPreferenceCategory.removePreference(existingSimPreferences.valueAt(i));
        }

        // Show a placeholder message if no SIM cards
        mNoSimPreference.setVisible(simEntries.isEmpty());
    }
}
