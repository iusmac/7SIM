package com.github.iusmac.sevensim.ui.license;

import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.widget.LayoutPreference;

import com.github.iusmac.sevensim.R;

public final class LicenseFragment extends PreferenceFragmentCompat {
    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final LayoutPreference pref = findPreference(getString(R.string.license_key));
        final Spanned spanned = HtmlCompat.fromHtml(getString(R.string.license_html),
                HtmlCompat.FROM_HTML_MODE_COMPACT);

        final TextView textView = pref.findViewById(R.id.textView);
        textView.setText(spanned);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.license_preferences);
    }
}
