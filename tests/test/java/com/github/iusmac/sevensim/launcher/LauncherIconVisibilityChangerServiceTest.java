package com.github.iusmac.sevensim.launcher;

import android.app.Service;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.robolectric.Robolectric.buildService;
import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class LauncherIconVisibilityChangerServiceTest extends MockitoHiltAndroidTestBase {
    private final ServiceController<LauncherIconVisibilityChangerService> mController =
        buildService(LauncherIconVisibilityChangerService.class);

    @Override
    public void setUp() {
        super.setUp();

        final var service = mController.create().get();
        service.mLauncherIconVisibilityManager = spy(service.mLauncherIconVisibilityManager);
    }

    @Test
    public void test_onTaskRemoved_ShouldUpdateVisibilityAndStopSelf() {
        final LauncherIconVisibilityChangerService service = mController.get();
        service.onTaskRemoved(mController.getIntent());

        verify(service.mLauncherIconVisibilityManager, times(1)).updateVisibility();
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void test_onStartCommand_ShouldReturnsSticky() {
        assertThat(mController.get().onStartCommand(mController.getIntent(), 0, 1),
                is(Service.START_STICKY));
    }

    @Test
    public void test_onBind_NullBinder() {
        assertThat(mController.get().onBind(mController.getIntent()), is(nullValue()));
    }
}
