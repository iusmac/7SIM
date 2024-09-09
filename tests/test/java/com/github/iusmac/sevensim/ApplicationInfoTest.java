package com.github.iusmac.sevensim;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class ApplicationInfoTest extends MockitoHiltAndroidTestBase {
    @Inject
    ApplicationInfo mApplicationInfo;

    @Test
    public void test_hasAospPlatformSignature() {
        TestUtils.useAospPlatformSignature(mApplicationContext, false);
        assertFalse(mApplicationInfo.hasAospPlatformSignature());

        TestUtils.useAospPlatformSignature(mApplicationContext, true);
        assertTrue(mApplicationInfo.hasAospPlatformSignature());

        // Test case to verify AOSP signature detection of non-existent package
        removePackage();
        assertFalse(mApplicationInfo.hasAospPlatformSignature());
    }

    @Test
    public void test_getPackageVersionName() {
        assertThat(mApplicationInfo.getPackageVersionName(),
                is(not(both(emptyString()).and(blankString()))));

        // Test case to verify package version name of non-existent package
        removePackage();
        assertTrue(mApplicationInfo.getPackageVersionName().isEmpty());
    }

    @Test
    public void test_isSystemApplication() {
        assertFalse(mApplicationInfo.isSystemApplication());

        TestUtils.setIsSystemApplication(mApplicationContext, true, /*isUpdatedSystemApp=*/ false);
        assertTrue(mApplicationInfo.isSystemApplication());

        TestUtils.setIsSystemApplication(mApplicationContext, true, true);
        assertTrue(mApplicationInfo.isSystemApplication());

        // Test case to verify whether the app is considered as a system app of non-existent package
        removePackage();
        assertFalse(mApplicationInfo.isSystemApplication());
    }

    @Test
    public void test_getAppBatterySettingsActivityIntent() {
        final Intent i = mApplicationInfo.getAppBatterySettingsActivityIntent();
        assertThat(i.getData(), is(Uri.parse("package:" + mApplicationContext.getPackageName())));
        assertThat(i.getExtras().size(), is(1));
        assertTrue(i.getBooleanExtra("request_ignore_background_restriction", false));
        assertThat(i.getFlags(), is(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.S)
    public void test_getAppBatterySettingsActivityIntent_SinceS() {
        assertThat(mApplicationInfo.getAppBatterySettingsActivityIntent().getAction(),
                is(Settings.ACTION_VIEW_ADVANCED_POWER_USAGE_DETAIL));
    }

    @Test
    @Config(maxSdk = Build.VERSION_CODES.R)
    public void test_getAppBatterySettingsActivityIntent_BeforeS() {
        assertThat(mApplicationInfo.getAppBatterySettingsActivityIntent().getAction(),
                is("android.settings.APP_BATTERY_SETTINGS"));
    }

    /**
     * Remove this app so that {@link PackageManager.NameNotFoundException} is thrown when making
     * calls to {@link PackageManager}.
     */
    private void removePackage() {
        shadowOf(mApplicationContext.getPackageManager())
            .removePackage(mApplicationContext.getPackageName());
    }
}
