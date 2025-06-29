package com.github.iusmac.sevensim;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.TestUtils.ExpectedHolder;
import com.github.iusmac.sevensim.test.TestUtils.GivenHolder;
import com.github.iusmac.sevensim.ui.LauncherActivity;

import java.util.Arrays;
import java.util.Collection;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matcher;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import static com.github.iusmac.sevensim.test.TestUtils.expected;
import static com.github.iusmac.sevensim.test.TestUtils.given;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

import static org.junit.Assert.*;

import static org.robolectric.Shadows.shadowOf;

@RunWith(Enclosed.class)
public class UtilsTest {
    /** Test the {@link Utils#IS_AT_LEAST_R}, {@link Utils#IS_AT_LEAST_S}, etc., constants against
     * different SDK versions. */
    @RunWith(RobolectricTestRunner.class)
    @Config(application = Application.class)
    public static final class IsAtLeastConstants {
        @Test
        @Config(sdk = Build.VERSION_CODES.Q)
        public void test_Q() {
            assertTrue(Utils.IS_OLDER_THAN_S);
            assertFalse(Utils.IS_AT_LEAST_R);
            assertFalse(Utils.IS_AT_LEAST_S);
            assertFalse(Utils.IS_AT_LEAST_T);
            assertFalse(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.R)
        public void test_R() {
            assertTrue(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertFalse(Utils.IS_AT_LEAST_S);
            assertFalse(Utils.IS_AT_LEAST_T);
            assertFalse(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.S)
        public void test_S() {
            assertFalse(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertTrue(Utils.IS_AT_LEAST_S);
            assertFalse(Utils.IS_AT_LEAST_T);
            assertFalse(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.S_V2)
        public void test_S_V2() {
            assertFalse(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertTrue(Utils.IS_AT_LEAST_S);
            assertFalse(Utils.IS_AT_LEAST_T);
            assertFalse(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.TIRAMISU)
        public void test_Tiramisu() {
            assertFalse(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertTrue(Utils.IS_AT_LEAST_S);
            assertTrue(Utils.IS_AT_LEAST_T);
            assertFalse(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        public void test_UpsideDownCake() {
            assertFalse(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertTrue(Utils.IS_AT_LEAST_S);
            assertTrue(Utils.IS_AT_LEAST_T);
            assertTrue(Utils.IS_AT_LEAST_U);
            assertFalse(Utils.IS_AT_LEAST_V);
        }

        @Test
        @Config(sdk = Build.VERSION_CODES.VANILLA_ICE_CREAM)
        public void test_VanillaIceCream() {
            assertFalse(Utils.IS_OLDER_THAN_S);
            assertTrue(Utils.IS_AT_LEAST_R);
            assertTrue(Utils.IS_AT_LEAST_S);
            assertTrue(Utils.IS_AT_LEAST_T);
            assertTrue(Utils.IS_AT_LEAST_U);
            assertTrue(Utils.IS_AT_LEAST_V);
        }
    }

    /** Test text toasting. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class TextToasting extends Base {
        @Test
        public void test_makeToast_FromMainThread() {
            final var text = "Toast message posted on Main thread.";
            Utils.makeToast(mApplicationContext, text);
            // Toasts are posted on the main looper thread, so force run it now before asserting
            shadowOf(Looper.getMainLooper()).runOneTask();
            assertEquals(text, ShadowToast.getTextOfLatestToast());
        }

        @Test
        public void test_makeToast_FromBackgroundThread() throws InterruptedException {
            final var text = "Toast message posted from background thread.";
            final var task = new Thread(() -> Utils.makeToast(mApplicationContext, text));
            task.start();
            task.join();
            shadowOf(Looper.getMainLooper()).runOneTask();
            assertEquals(text, ShadowToast.getTextOfLatestToast());
        }
    }

    /** Test checking and manipulating the component enabled setting. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner.class)
    public static final class ComponentEnabledSetting extends Base {
        private PackageManager mPm;
        private ComponentName mComponent;

        @Override
        public void setUp() {
            super.setUp();

            mPm = mApplicationContext.getPackageManager();
            mComponent = new ComponentName(mApplicationContext, LauncherActivity.class);
        }

        @Test
        public void test_isComponentDisabled() {
            assertFalse(Utils.isComponentDisabled(mApplicationContext, mComponent));
            mPm.setComponentEnabledSetting(mComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            assertTrue(Utils.isComponentDisabled(mApplicationContext, mComponent));
        }

        @Test
        public void test_setComponentEnabledSetting() {
            assertTrue(Utils.setComponentEnabledSetting(mApplicationContext, mComponent, false));
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    mPm.getComponentEnabledSetting(mComponent));

            // Check whether applying the same state again is short-circuited
            assertFalse(Utils.setComponentEnabledSetting(mApplicationContext, mComponent, false));

            assertTrue(Utils.setComponentEnabledSetting(mApplicationContext, mComponent, true));
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    mPm.getComponentEnabledSetting(mComponent));
        }
    }

    /** Test basic linear interpolation. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class BasicLinearInterpolation extends Base {
        @Parameter(0)
        public GivenHolder<float[]> mGiven;

        @Parameter(1)
        public float mFraction;

        @Parameter(2)
        public ExpectedHolder<Matcher<Double>> mExpected;

        @Parameters(name = "{0} as start/end values and a fraction of {1}, {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(new float[] { 12.5f, 14f }), 0/5f, expected(is(12.5d)) },
                { given(new float[] { 12.5f, 14f }), 1/5f, expected(is(closeTo(12.8f, 0.1f))) },
                { given(new float[] { 12.5f, 14f }), 2/5f, expected(is(closeTo(13.1f, 0.1f))) },
                { given(new float[] { 12.5f, 14f }), 3/5f, expected(is(closeTo(13.4f, 0.1f))) },
                { given(new float[] { 12.5f, 14f }), 4/5f, expected(is(closeTo(13.7f, 0.1f))) },
                { given(new float[] { 12.5f, 14f }), 5/5f, expected(is(14d)) },

                { given(new float[] { 14f, 12.5f }), 0/5f, expected(is(14d)) },
                { given(new float[] { 14f, 12.5f }), 0/5f, expected(is(closeTo(14f, 0.1f))) },
                { given(new float[] { 14f, 12.5f }), 1/5f, expected(is(closeTo(13.7f, 0.1f))) },
                { given(new float[] { 14f, 12.5f }), 2/5f, expected(is(closeTo(13.4f, 0.1f))) },
                { given(new float[] { 14f, 12.5f }), 3/5f, expected(is(closeTo(13.1f, 0.1f))) },
                { given(new float[] { 14f, 12.5f }), 4/5f, expected(is(closeTo(12.8f, 0.1f))) },
                { given(new float[] { 14f, 12.5f }), 5/5f, expected(is(12.5d)) },
            });
        }

        @Test
        public void test_lerp() {
            MatcherAssert.assertThat((double) Utils.lerp(mGiven.value[0], mGiven.value[1],
                        mFraction), mExpected.value);
        }
    }

    /** Test the range constrained linear interpolation. */
    @HiltAndroidTest
    @RunWith(ParameterizedRobolectricTestRunner.class)
    public static final class RangeConstrainedLinearInterpolation extends Base {
        @Parameter(0)
        public GivenHolder<float[]> mGiven;

        @Parameter(1)
        public float mFraction;

        @Parameter(2)
        public ExpectedHolder<Matcher<Double>> mExpected;

        @Parameters(name = "{0} as start/end & range start/end values and a fraction of {1}, {2}")
        public static Collection<Object[]> params() {
            return Arrays.asList(new Object[][] {
                { given(new float[] { 0, 1f, 0.4f, 1f }), 0/10f, expected(is(0d)) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 1/10f, expected(is(0d)) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 2/10f, expected(is(0d)) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 3/10f, expected(is(0d)) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 4/10f, expected(is(0d)) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 5/10f, expected(is(closeTo(0.16f, 0.1))) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 6/10f, expected(is(closeTo(0.33f, 0.1))) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 7/10f, expected(is(closeTo(0.49f, 0.1))) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 8/10f, expected(is(closeTo(0.66f, 0.1))) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 9/10f, expected(is(closeTo(0.83f, 0.1))) },
                { given(new float[] { 0, 1f, 0.4f, 1f }), 10/10f, expected(is(1d)) },

                { given(new float[] { 1f, 0, 0.4f, 1f }), 0/10f, expected(is(1d)) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 1/10f, expected(is(1d)) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 2/10f, expected(is(1d)) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 3/10f, expected(is(1d)) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 4/10f, expected(is(1d)) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 5/10f, expected(is(closeTo(0.83f, 0.1))) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 6/10f, expected(is(closeTo(0.66f, 0.1))) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 7/10f, expected(is(closeTo(0.49f, 0.1))) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 8/10f, expected(is(closeTo(0.33f, 0.1))) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 9/10f, expected(is(closeTo(0.16f, 0.1))) },
                { given(new float[] { 1f, 0, 0.4f, 1f }), 10/10f, expected(is(0d)) },
            });
        }

        @Test
        public void test_lerp() {
            final var startValue = mGiven.value[0];
            final var endValue = mGiven.value[1];
            final var rangeStart = mGiven.value[2];
            final var rangeEnd = mGiven.value[3];
            MatcherAssert.assertThat((double) Utils.lerp(startValue, endValue, rangeStart, rangeEnd,
                        mFraction), mExpected.value);
        }
    }

    static class Base extends MockitoHiltAndroidTestBase {
    }
}
