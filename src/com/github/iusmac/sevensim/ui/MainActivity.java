package com.github.iusmac.sevensim.ui;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.ui.sim.SimListActivity;

import android.os.Bundle;

/**
 * This activity is the front-door of the application.
 */
public class MainActivity extends SimListActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(false);

        setSubtitle(R.string.app_description);
        if (!getToolbarDecorator().isCollapsingToolbarSupported()) {
            // For better UX (e.g. l10n), apply the marquee effect on the subtitle for
            // non-collapsible Toolbar
            getToolbarDecorator().setSubtitleMarqueeRepeatLimit(1);
        }
    }
}
