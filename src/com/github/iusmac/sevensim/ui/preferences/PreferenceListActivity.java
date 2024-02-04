package com.github.iusmac.sevensim.ui.preferences;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.lifecycle.ViewModel;

import com.android.settingslib.widget.R;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint(CollapsingToolbarBaseActivity.class)
public final class PreferenceListActivity extends Hilt_PreferenceListActivity {
    @Inject
    Logger.Factory mLoggerFactory;

    private Logger mLogger;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate().");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.content_frame,
                    new PreferenceListFragment()).commit();
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
        // Make Dagger instantiate @Inject fields prior to the ViewModel creation
        inject();

        return null;
    }
}
