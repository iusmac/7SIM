package com.github.iusmac.sevensim.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.ComponentActivity;

import com.github.iusmac.sevensim.Logger;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

/**
 * <p>This activity is launched ONLY from the launcher's app list and aims to launch the
 * {@link MainActivity}, then close itself immediately.
 *
 * <p>Note that, this activity shouldn't do anything other than what it was designed for, as it's
 * tied to the launcher icon, which can be hidden via preferences.
 */
@AndroidEntryPoint(ComponentActivity.class)
public final class LauncherActivity extends Hilt_LauncherActivity {
    @Inject
    Logger.Factory mLoggerFactory;

    private Logger mLogger;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            final Intent aIntent = new Intent();
            aIntent.setClass(this, MainActivity.class);
            aIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(aIntent);
        } catch (Exception e) {
            mLogger = mLoggerFactory.create(getClass().getSimpleName());
            mLogger.e("Failed launching activity.", e);
        } finally {
            finish();
        }
    }
}
