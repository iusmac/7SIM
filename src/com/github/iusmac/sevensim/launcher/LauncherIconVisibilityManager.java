package com.github.iusmac.sevensim.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.SevenSimApplication;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.ui.LauncherActivity;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manager of launcher icon's visibility.
 */
@Singleton
public final class LauncherIconVisibilityManager {
    private final boolean mIsSystemApplication;
    private final String mShowAppIconKey;
    private final Intent mIntentService;
    private final ComponentName mComponentName;

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
        mIntentService = new Intent(context, LauncherIconVisibilityChangerService.class);
        mComponentName = new ComponentName(mContext, LauncherActivity.class);
    }

    /**
     * <p>Whether the launcher icon visibility control is supported.
     *
     * <p>Note that, as of Android Q, only system apps are allowed to hide themselves in the launcher's
     * app list.
     *
     * @return Whether the launcher icon can be hidden or not.
     */
    public boolean canHide() {
        return mIsSystemApplication;
    }

    /**
     * <p>Persist the preference and update application icon visibility in the launcher's app list.
     *
     * <p>Note that, launcher icon visibility will be restored immediately if currently invisibile,
     * otherwise this will be delegated to the {@link LauncherIconVisibilityChangerService}.
     *
     * @param visible {@code true} if the application icon should be visible in the launcher app
     * list, {@code false} otherwise.
     */
    public void setVisibility(final boolean visible) {
        mLogger.d("setVisibility(visible=%s).", visible);

        mSharedPreferences.edit().putBoolean(mShowAppIconKey, visible).apply();
        if (visible) {
            updateVisibility();
            mContext.stopServiceAsUser(mIntentService, UserHandle.CURRENT);
        } else {
            mContext.startServiceAsUser(mIntentService, UserHandle.CURRENT);
        }
    }

    /**
     * <p>Align application icon visibility in the launcher's app list with the user's preference.
     *
     * <p>Note that, the launcher icon visibility will be restored regardless of user's preference
     * if {@link #canHide()} returns {@code false}.
     */
    public void updateVisibility() {
        final boolean canHide = canHide();
        final boolean currVisible = isVisible();
        final boolean newVisible = getUserVisibilityPreference().map((userVisibilityPreference) ->
            !canHide ? true : userVisibilityPreference).orElse(true);

        mLogger.d("updateVisibility() : canHide=%s,currVisible=%s,newVisible=%s.", canHide,
                currVisible, newVisible);

        if (currVisible != newVisible) {
            Utils.setComponentEnabledSetting(mContext, mComponentName, newVisible);
        }
    }

    /**
     * Get the user's launcher icon visibility preference.
     *
     * @return An Optional containing the user's preference, if any.
     */
    public Optional<Boolean> getUserVisibilityPreference() {
        if (mSharedPreferences.contains(mShowAppIconKey)) {
            return Optional.of(mSharedPreferences.getBoolean(mShowAppIconKey, true));
        }
        return Optional.empty();
    }

    /**
     * Determine whether the application icon is visible in the launcher's app list.
     *
     * @return {@code true} if the application icon is visible in the launcher app list,
     * {@code false} otherwise.
     */
    public boolean isVisible() {
        return !Utils.isComponentDisabled(mContext, mComponentName);
    }
}
