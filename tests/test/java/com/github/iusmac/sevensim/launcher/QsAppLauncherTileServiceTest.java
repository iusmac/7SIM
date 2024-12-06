package com.github.iusmac.sevensim.launcher;

import android.app.ActivityThread;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.service.quicksettings.Tile;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowPendingIntentHiddenApi;
import com.github.iusmac.sevensim.test.ShadowTileServiceUnimplementedApi;
import com.github.iusmac.sevensim.ui.MainActivity;

import dagger.hilt.android.testing.HiltAndroidTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.robolectric.Robolectric.buildService;
import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class QsAppLauncherTileServiceTest extends MockitoHiltAndroidTestBase {
    private final ServiceController<QsAppLauncherTileService> mController =
        buildService(QsAppLauncherTileService.class);

    private QsAppLauncherTileService mService;
    private Tile mTile;

    @Override
    public void setUp() {
        super.setUp();

        mService = mController.create().get();
        mTile = mService.getQsTile();

        mTile.setSubtitle(null);
        mTile.setContentDescription(null);
    }

    @Test
    public void test_onTileAdded_ShouldUpdateTileString() {
        final CharSequence oldSubtitle = mTile.getSubtitle();
        final CharSequence oldContentDescription = mTile.getContentDescription();

        mService.onTileAdded();
        assertThat(oldSubtitle, is(not(equalTo(mTile.getSubtitle()))));
        assertThat(oldContentDescription, is(not(equalTo(mTile.getContentDescription()))));
    }

    @Test
    public void test_onStartListening_ShouldUpdateTileString() {
        final CharSequence oldSubtitle = mTile.getSubtitle();
        final CharSequence oldContentDescription = mTile.getContentDescription();

        mService.onStartListening();
        assertThat(oldSubtitle, is(not(equalTo(mTile.getSubtitle()))));
        assertThat(oldContentDescription, is(not(equalTo(mTile.getContentDescription()))));
    }

    @Test
    @Config(shadows = {ShadowTileServiceUnimplementedApi.class, ShadowPendingIntentHiddenApi.class})
    public void test_onClick_ShouldStartMainActivity() throws PendingIntent.CanceledException {
        mService.onClick();
        final var pi = Shadow.<ShadowTileServiceUnimplementedApi>extract(mService).pendingIntent;
        pi.send();
        assertMainActivityStarted();

        final var shadowPendingIntent = shadowOf(pi);
        assertThat(shadowPendingIntent.getFlags(), is(PendingIntent.FLAG_IMMUTABLE));
        assertThat(shadowPendingIntent.getOptions(), is(nullValue()));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_onClick_ShouldStartMainActivity_BeforeU() {
        mService.onClick();
        assertMainActivityStarted();
    }

    @Test
    public void test_updateTileString_ShouldUpdateOnceWhenUnchanged() {
        final var serviceMock = spy(Shadow.newInstanceOf(QsAppLauncherTileService.class));
        final var mockTile = spy(Shadow.newInstanceOf(Tile.class));

        when(serviceMock.getQsTile()).thenReturn(mockTile);

        // Attach context to access resources
        serviceMock.attach(mApplicationContext,
                (ActivityThread) RuntimeEnvironment.getActivityThread(),
                QsAppLauncherTileService.class.getSimpleName(), new Binder(), (Application)
                mApplicationContext, null);

        serviceMock.updateTileStrings();
        verify(mockTile, times(1)).updateTile();

        serviceMock.updateTileStrings();
        verify(mockTile, times(1)).updateTile();
    }

    private void assertMainActivityStarted() {
        final Intent i = shadowOf((Application) mApplicationContext).getNextStartedActivity();
        assertThat(i.getComponent(), is(new ComponentName(mService, MainActivity.class)));
        assertThat(i.getFlags(), is(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }
}
