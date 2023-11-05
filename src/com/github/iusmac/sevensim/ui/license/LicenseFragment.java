package com.github.iusmac.sevensim.ui.license;

import android.os.Bundle;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.github.iusmac.sevensim.R;

public final class LicenseFragment extends PreferenceFragmentCompat {
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.license, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Spanned spanned = HtmlCompat.fromHtml(getString(R.string.license_html),
                HtmlCompat.FROM_HTML_MODE_COMPACT);

        final TextView textView = view.findViewById(R.id.textView);
        textView.setText(spanned);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }
}
