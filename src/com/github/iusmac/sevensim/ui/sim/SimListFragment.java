package com.github.iusmac.sevensim.ui.sim;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.collection.SparseArrayCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.PrimarySwitchPreference;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class SimListFragment extends Hilt_SimListFragment {
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

        setupSimList();
    }

    private void setupSimList() {
        mSimPreferenceCategory = findPreference(getString(R.string.sim_list_key));

        mNoSimPreference = mSimPreferenceCategory
            .findPreference(getString(R.string.sim_list_no_sim_key));

        mViewModel.getSimEntries().observe(getViewLifecycleOwner(), (entries) ->
                updateSimPreferenceList(entries));
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
            pref.setChecked(sub.isSimEnabled());
            mSimPreferences.put(simEntryId, pref);
        }

        for (int i = 0, size = existingSimPreferences.size(); i < size; i++) {
            mSimPreferenceCategory.removePreference(existingSimPreferences.valueAt(i));
        }

        // Show a placeholder message if no SIM cards
        mNoSimPreference.setVisible(simEntries.isEmpty());
    }
}
