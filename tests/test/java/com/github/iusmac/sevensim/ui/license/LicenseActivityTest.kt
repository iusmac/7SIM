package com.github.iusmac.sevensim.ui.license

import android.content.res.Resources
import android.os.Build.VERSION_CODES.Q

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule

import com.github.iusmac.sevensim.test.RoborazziTestBase

import com.github.takahirom.roborazzi.captureRoboImage

import dagger.hilt.android.testing.HiltAndroidTest

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class LicenseActivityTest : RoborazziTestBase() {
    @get:Rule
    val rule = ActivityScenarioRule(LicenseActivity::class.java)

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
        }
        onView(isRoot()).captureRoboImage()
    }

    @Test
    fun `test fragment added once after activity recreation`() {
        rule.scenario.apply {
            recreate()
            onActivity {
                assertThat(it.supportFragmentManager.fragments,
                    containsInRelativeOrder(instanceOf(LicenseFragment::class.java)))
            }
        }
    }

    @Test
    fun `test activity is finished when back button is pressed`() {
        rule.scenario.apply {
            onActivity {
                // Simulate click on a button that is different from the back button
                assertFalse(shadowOf(it).clickMenuItem(Resources.ID_NULL))
                assertFalse(it.isFinishing())

                assertTrue(shadowOf(it).clickMenuItem(android.R.id.home))
                // Since this was the only activity in the stack, it should now be finished
                assertTrue(it.isFinishing())
            }
        }
    }
}
