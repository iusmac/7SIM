package com.github.iusmac.sevensim.ui.preferences;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.launcher.LauncherIconVisibilityManager;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint(PreferenceFragmentCompat.class)
public final class PreferenceListFragment extends Hilt_PreferenceListFragment {
    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    LauncherIconVisibilityManager mLauncherIconVisibilityManager;

    private Logger mLogger;
    private String mPrefShowAppIconKey;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());
        mPrefShowAppIconKey = getString(R.string.preference_list_show_app_icon_key);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        // Hijack default key-value shared preferences before the preferences are actually added
        getPreferenceManager().setPreferenceDataStore(new PreferenceDataStoreImpl());
        addPreferencesFromResource(R.xml.preference_list);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupShowAppIconPref();
    }

    private void setupShowAppIconPref() {
         final Preference showAppIconPref = findPreference(mPrefShowAppIconKey);
         showAppIconPref.setEnabled(mLauncherIconVisibilityManager.isSupported());
    }

    /**
     * This is an implementation class of {@link PreferenceDataStore} used to hijack the default
     * persistence phase on disk via {@link SharedPreferences} when preferences change or require an
     * initial value.
     */
    private final class PreferenceDataStoreImpl extends PreferenceDataStore {
        @Override
        public void putBoolean(final String key, final boolean value) {
            if (key.equals(mPrefShowAppIconKey)) {
                mLauncherIconVisibilityManager.setVisibility(value);
            } else {
                mLogger.wtf("putBoolean() : unhandled key = " + key);
            }
        }

        @Override
        public boolean getBoolean(final String key, final boolean defVal) {
            if (key.equals(mPrefShowAppIconKey)) {
                return mLauncherIconVisibilityManager.isVisible();
            } else {
                mLogger.wtf("getBoolean() : unhandled key = " + key);
            }
            return defVal;
        }
    }
}
