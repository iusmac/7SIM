package com.github.iusmac.sevensim.ui;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.util.OptionalInt;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.robolectric.Robolectric.buildActivity;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class MainActivityTest extends MockitoHiltAndroidTestBase {
    private final ActivityController<MainActivity> mController = buildActivity(MainActivity.class);

    @Test
    @Config(minSdk = S)
    public void test_onCreate_ShouldNotSetSubtitleMarqueeRepeatLimitForCollapsingToolbar() {
        final var toolbarDecorator = mController.create().get().getToolbarDecorator();
        assertThat(toolbarDecorator.getSubtitleMarqueeRepeatLimit(), is(OptionalInt.empty()));
    }

    @Test
    @Config(maxSdk = R)
    public void test_onCreate_ShouldSetSubtitleMarqueeRepeatLimitForNonCollapsingToolbar() {
        final var toolbarDecorator = mController.create().get().getToolbarDecorator();
        assertThat(toolbarDecorator.getSubtitleMarqueeRepeatLimit(), is(OptionalInt.of(1)));
    }
}
