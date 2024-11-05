package com.github.iusmac.sevensim.ui.preferences

import android.content.res.Resources
import android.os.Build.VERSION_CODES.Q
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule

import com.github.iusmac.sevensim.R
import com.github.iusmac.sevensim.launcher.LauncherIconVisibilityManager
import com.github.iusmac.sevensim.test.ActivityLauncher
import com.github.iusmac.sevensim.test.LogItemPropertyMatchers.*
import com.github.iusmac.sevensim.test.RoborazziTestBase
import com.github.iusmac.sevensim.test.TestUtils.setIsSystemApplication
import com.github.iusmac.sevensim.test.captureRoboImage
import com.github.iusmac.sevensim.test.withPreferenceKey

import com.github.takahirom.roborazzi.captureRoboImage

import dagger.hilt.android.testing.HiltAndroidTest

import java.time.Duration

import javax.inject.Inject
import javax.inject.Provider

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast

@RunWith(Enclosed::class)
class PreferenceListActivityTest {
    /** Test activity behaviors. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class Activity : Base() {
        @get:Rule
        val rule: ActivityScenarioRule<PreferenceListActivity> =
            ActivityScenarioRule(PreferenceListActivity::class.java)

        @Test
        @Config(minSdk = Q)
        fun `test full view`() {
            onView(isRoot()).captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test full view after activity recreation`() {
            rule.scenario.apply {
                onActivity { assertFalse(it.isFinishing()) }
                recreate()
                onActivity { assertFalse(it.isFinishing()) }
                onView(isRoot()).captureRoboImage()
            }
        }

        @Test
        fun `test fragment added once after activity recreation`() {
            rule.scenario.apply {
                recreate()
                onActivity {
                    assertThat(it.supportFragmentManager.fragments,
                        containsInRelativeOrder(instanceOf(PreferenceListFragment::class.java)))
                }
            }
        }

        @Test
        fun `test activity is finished when back button is pressed`() {
            rule.scenario.onActivity {
                // Simulate click on a button that is different from the back button
                assertFalse(shadowOf(it).clickMenuItem(Resources.ID_NULL))
                assertFalse(it.isFinishing())

                assertTrue(shadowOf(it).clickMenuItem(android.R.id.home))
                // Since this was the only activity in the stack, it should now be finished
                assertTrue(it.isFinishing())
            }
        }

        @Test
        fun `test should throw a What A Terrible Failure for unhandled boolean keys`() {
            val dummyKey = "dummy_key"
            rule.scenario.onActivity { activity ->
                with(activity.preferenceDataStore) {
                    val defValue = true
                    assertThat(getBoolean(dummyKey, defValue), `is`(defValue))
                    putBoolean(dummyKey, true)
                }
            }

            val wtfMatcher = allOf(
                withTag(endsWith(PreferenceListFragment::class.simpleName)),
                withType(Log.ASSERT),
                withMsg(containsString(dummyKey)))
            assertThat(ShadowLog.getLogs(), containsInRelativeOrder(
                both(wtfMatcher).and(withMsg(containsString("getBoolean"))),
                both(wtfMatcher).and(withMsg(containsString("putBoolean")))))
        }
    }

    /** Test "Show app icon" preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ShowAppIconPreference : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be grayed out when cannot hide`() = onActivity {
            onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }

        @Test
        @Config(minSdk = Q)
        fun `test should not be grayed out when can hide`() {
            setIsSystemApplication(mApplicationContext, true, false)
            onActivity {
                onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should remember user choice`() {
            setIsSystemApplication(mApplicationContext, true, false)
            onActivity { scenario ->
                // Turn off switch to hide icon
                onPreference().perform(click())
                // Recreate to simulate freshly opened activity or configuration change
                scenario.recreate()
                onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should remember user choice even if cannot hide anymore`() {
            mLauncherIconVisibilityManagerProvider.get().setVisibility(false)
            onActivity {
                onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        fun `test should toast a restart to apply hint ONLY if turned off`() {
            setIsSystemApplication(mApplicationContext, true, false)
            onActivity {
                // Turn off switch to hide icon
                onPreference().perform(click())

                shadowOf(Looper.getMainLooper()).runOneTask()
                assertThat(ShadowToast.getTextOfLatestToast(),
                    `is`(mApplicationContext.getString(R.string.restart_to_apply)))
                ShadowToast.reset()

                // Turn on switch to show icon
                onPreference().perform(click())

                // Ensure no toasts are scheduled to be shown
                shadowOf(Looper.getMainLooper()).runOneTask()
                assertThat(ShadowToast.getTextOfLatestToast(), `is`(nullValue()))
            }
        }

        private companion object {
            fun onPreference(): ViewInteraction =
                onPreferenceWithKey(R.string.preference_list_show_app_icon_key)
        }
    }
}

/** Shortcut to launch this activity. */
private fun onActivity(
    appBarExpanded: Boolean = false,
    block: (ActivityScenario<PreferenceListActivity>) -> Unit
) = ActivityLauncher<PreferenceListActivity>(appBarExpanded, block)

/** Creates a {@link ViewInteraction} that scrolls to a preference with the given key. */
private fun onPreferenceWithKey(keyResId: Int): ViewInteraction =
    onView(withPreferenceKey(keyResId)).perform(scrollTo())

private val SCROLL_BAR_FADE_DURATION =
    Duration.ofMillis(ViewConfiguration.getScrollBarFadeDuration().toLong())

private val PreferenceListActivity.preferenceDataStore
    get() = supportFragmentManager.fragments
        .first { it is PreferenceListFragment }
        .let { it as PreferenceListFragment }
        .preferenceManager
        .preferenceDataStore!!

open class Base : RoborazziTestBase() {
    @Inject
    internal lateinit var mLauncherIconVisibilityManagerProvider:
        Provider<LauncherIconVisibilityManager>
}
