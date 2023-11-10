package com.github.iusmac.sevensim.ui.preferences;

import android.content.Context;
import android.os.Bundle;

import com.github.iusmac.sevensim.R;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class PreferenceListFragment extends Hilt_PreferenceListFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.preference_list);
    }
}
