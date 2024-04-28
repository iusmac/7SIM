package com.github.iusmac.sevensim.ui.license;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.lifecycle.ViewModel;

import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity;

public final class LicenseActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSubtitle(com.github.iusmac.sevensim.R.string.license_summary);
        if (!getToolbarDecorator().isCollapsingToolbarSupported()) {
            // For better UX (e.g. l10n), apply the marquee effect on the subtitle for
            // non-collapsible Toolbar
            getToolbarDecorator().setSubtitleMarqueeRepeatLimit(1);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    new LicenseFragment()).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public ViewModel onCreateViewModel() {
        return null;
    }
}
