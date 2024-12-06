package com.github.iusmac.sevensim.launcher;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowContextHiddenApi;
import com.github.iusmac.sevensim.test.TestUtils;
import com.github.iusmac.sevensim.ui.LauncherActivity;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowContextHiddenApi.class)
public final class LauncherIconVisibilityManagerTest extends MockitoHiltAndroidTestBase {
    @Inject
    Provider<LauncherIconVisibilityManager> mLauncherIconVisibilityManagerProvider;

    @Inject
    SharedPreferences mSharedPrefs;

    private PackageManager mPm;
    private ComponentName mComponentName;
    private String mShowAppIconKey;
    private ShadowApplication mShadowApplication;
    private Intent mIntentService;

    @Override
    public void setUp() {
        super.setUp();

        mPm = mApplicationContext.getPackageManager();
        mComponentName = new ComponentName(mApplicationContext, LauncherActivity.class);
        mShowAppIconKey = mApplicationContext.getString(R.string.preference_list_show_app_icon_key);
        mShadowApplication = shadowOf((Application) mApplicationContext);
        mIntentService = new Intent(mApplicationContext,
                LauncherIconVisibilityChangerService.class);
    }

    @Test
    public void test_canHide_WhenIsNotASystemApp() {
        assertFalse(mLauncherIconVisibilityManagerProvider.get().canHide());
    }

    @Test
    public void test_canHide_WhenIsASystemApp() {
        TestUtils.setIsSystemApplication(mApplicationContext, true, false);
        assertTrue(mLauncherIconVisibilityManagerProvider.get().canHide());
    }

    @Test
    public void test_canHide_WhenIsAnUpdatedSystemApp() {
        TestUtils.setIsSystemApplication(mApplicationContext, true, true);
        assertTrue(mLauncherIconVisibilityManagerProvider.get().canHide());
    }

    @Test
    public void test_setVisibility_ShouldAlwaysPersistState() {
        var expectedVisibility = true;
        mLauncherIconVisibilityManagerProvider.get().setVisibility(expectedVisibility);
        assertTrue(mSharedPrefs.getBoolean(mShowAppIconKey, !expectedVisibility));

        expectedVisibility = false;
        mLauncherIconVisibilityManagerProvider.get().setVisibility(expectedVisibility);
        assertFalse(mSharedPrefs.getBoolean(mShowAppIconKey, !expectedVisibility));
    }

    @Test
    public void test_setVisibility_ShouldDelegateToVisibilityChangerServiceWhenHiding() {
        mLauncherIconVisibilityManagerProvider.get().setVisibility(false);
        final Intent i = mShadowApplication.getNextStartedService();
        assertThat(i.getComponent(), is(mIntentService.getComponent()));
    }

    @Test
    public void test_setVisibility_ShouldStopVisibilityChangerServiceWhenShowing() {
        mLauncherIconVisibilityManagerProvider.get().setVisibility(false);
        mLauncherIconVisibilityManagerProvider.get().setVisibility(true);

        final Intent i = mShadowApplication.getNextStoppedService();
        assertThat(i.getComponent(), is(mIntentService.getComponent()));
    }

    @Test
    public void test_updateVisibility_ShouldBeVisibleWhenNoUserPreference() {
        mLauncherIconVisibilityManagerProvider.get().updateVisibility();

        assertThat(mPm.getComponentEnabledSetting(mComponentName),
                is(not(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)));
    }

    @Test
    public void test_updateVisibility_ShouldBeVisibleAsPerUserPreferenceButCannotHide() {
        mLauncherIconVisibilityManagerProvider.get().setVisibility(false);
        mLauncherIconVisibilityManagerProvider.get().updateVisibility();

        assertThat(mPm.getComponentEnabledSetting(mComponentName),
                is(not(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)));
    }

    @Test
    public void test_updateVisibility_ShouldBeInvisibleAsPerUserPreferenceWhenCanHide() {
        TestUtils.setIsSystemApplication(mApplicationContext, true, true);
        mLauncherIconVisibilityManagerProvider.get().setVisibility(false);

        mLauncherIconVisibilityManagerProvider.get().updateVisibility();

        assertThat(mPm.getComponentEnabledSetting(mComponentName),
                is(PackageManager.COMPONENT_ENABLED_STATE_DISABLED));
    }

    @Test
    public void test_getUserPreference_ContainingEmptyWhenNoUserPreference() {
        assertThat(mLauncherIconVisibilityManagerProvider.get().getUserVisibilityPreference(),
                is(Optional.empty()));
    }

    @Test
    public void test_getUserPreference_ContainingUserPreference() {
        final var expectedUserPreference = false;
        mLauncherIconVisibilityManagerProvider.get().setVisibility(expectedUserPreference);

        assertThat(mLauncherIconVisibilityManagerProvider.get().getUserVisibilityPreference(),
                is(Optional.of(expectedUserPreference)));
    }

    @Test
    public void test_isVisible() {
        assertTrue(mLauncherIconVisibilityManagerProvider.get().isVisible());

        mPm.setComponentEnabledSetting(mComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        assertFalse(mLauncherIconVisibilityManagerProvider.get().isVisible());
    }
}
