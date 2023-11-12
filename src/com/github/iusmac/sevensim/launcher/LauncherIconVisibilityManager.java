package com.github.iusmac.sevensim.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.SevenSimApplication;
import com.github.iusmac.sevensim.Utils;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manager of launcher icon's visibility.
 */
@Singleton
public final class LauncherIconVisibilityManager {
    private final boolean mIsSystemApplication;
    private final String mShowAppIconKey;

    private final Context mContext;
    private final Logger mLogger;
    private final SharedPreferences mSharedPreferences;

    @Inject
    LauncherIconVisibilityManager(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory, final SevenSimApplication app,
            final SharedPreferences sharedPrefs) {

        mContext = context;
        mLogger = loggerFactory.create(getClass().getSimpleName());
        mSharedPreferences = sharedPrefs;

        mIsSystemApplication = app.isSystemApplication();
        mShowAppIconKey = context.getString(R.string.preference_list_show_app_icon_key);
    }

    /**
     * <p>Whether the launcher icon visibility control is supported.
     *
     * <p>Note that, as of Android Q, only system apps are allowed to hide themselves in the
     * launcher's app list.
     */
    public boolean isSupported() {
        return mIsSystemApplication;
    }

    /**
     * Persist the preference and update application icon visibility in the launcher's app list.
     *
     * @param visible {@code true} if the application icon should be visible in the launcher app
     * list, {@code false} otherwise.
     */
    public void setVisibility(final boolean visible) {
        mSharedPreferences.edit().putBoolean(mShowAppIconKey, visible).apply();
        updateVisibility();
    }

    /**
     * <p>Align application icon visibility in the launcher's app list with the user's preference.
     *
     * <p>Note that, the launcher icon visibility will be restored regardless of user's preference
     * if {@link #isSupported()} returns {@code false}.
     */
    public void updateVisibility() {
        final boolean visible = isVisible();

        mLogger.d("updateVisibility() : visible=%s.", visible);

        Utils.setComponentEnabledSetting(mContext, new ComponentName(mContext,
                    LauncherActivity.class), visible);
    }

    /**
     * Determine whether the application icon is visible in the launcher's app list by checking
     * user's preference, but note that the latter may be ignored if {@link #isSupported()} returns
     * {@code false}.
     *
     * @return {@code true} if the application icon is visible in the launcher app list,
     * {@code false} otherwise.
     */
    public boolean isVisible() {
        if (mSharedPreferences.contains(mShowAppIconKey)) {
            if (!isSupported()) {
                return true;
            }
            return mSharedPreferences.getBoolean(mShowAppIconKey, true);
        }
        return true;
    }
}
