package com.github.iusmac.sevensim.ui.sim

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.ViewConfiguration

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule

import com.github.iusmac.sevensim.AppDatabaseDE
import com.github.iusmac.sevensim.R
import com.github.iusmac.sevensim.SystemTimeProvider
import com.github.iusmac.sevensim.SystemTimeProviderFakeImpl
import com.github.iusmac.sevensim.scheduler.DayOfWeek.*
import com.github.iusmac.sevensim.scheduler.DaysOfWeek
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity
import com.github.iusmac.sevensim.telephony.SimState
import com.github.iusmac.sevensim.telephony.Subscription
import com.github.iusmac.sevensim.telephony.Subscriptions
import com.github.iusmac.sevensim.test.ActivityLauncher
import com.github.iusmac.sevensim.test.LogItemPropertyMatchers.*
import com.github.iusmac.sevensim.test.RoborazziTestBase
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerHiddenApi
import com.github.iusmac.sevensim.test.ShadowTelephonyManagerHiddenApi
import com.github.iusmac.sevensim.test.SubscriptionPropertyMatchers.withSubId
import com.github.iusmac.sevensim.test.TestUtils.set24Hour
import com.github.iusmac.sevensim.test.TestUtils.setAreUiccApplicationsEnabled
import com.github.iusmac.sevensim.test.TestUtils.useAospPlatformSignature
import com.github.iusmac.sevensim.test.TestUtils.waitWorkerThreadLooperUntilIdle
import com.github.iusmac.sevensim.test.actionOnChild
import com.github.iusmac.sevensim.test.captureRoboImage
import com.github.iusmac.sevensim.test.withPreferenceKey
// Note: for a proper screenshot test of the SimListActivity, we shadow it with the
// LauncherActivity, which proxies it through the MainActivity
import com.github.iusmac.sevensim.ui.LauncherActivity as SimListActivity
import com.github.iusmac.sevensim.ui.license.LicenseActivity
import com.github.iusmac.sevensim.ui.preferences.PreferenceListActivity
import com.github.iusmac.sevensim.ui.scheduler.SchedulerActivity

import com.github.takahirom.roborazzi.captureRoboImage

import dagger.hilt.android.testing.HiltAndroidTest

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional

import javax.inject.Inject
import javax.inject.Provider

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder
import org.robolectric.shadows.ShadowSystemClock

@RunWith(Enclosed::class)
class SimListActivityTest {
    /** Test activity behaviors. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class Activity : Base() {
        @Inject
        lateinit var mSubscriptionManager: SubscriptionManager

        @Inject
        lateinit var mSubscriptions: Subscriptions

        @Test
        @Config(minSdk = Q)
        fun `test full view`() = onActivity(appBarExpanded = true) {
            onView(isRoot()).captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test full view after activity recreation`() {
            onActivity(appBarExpanded = true) { scenario ->
                scenario.apply {
                    onActivity { assertFalse(it.isFinishing()) }
                    recreate()
                    onActivity { assertFalse(it.isFinishing()) }
                    onView(isRoot()).captureRoboImage()
                }
            }
        }

        @Test
        @Config(minSdk = S)
        fun `test full view with app bar collapsed`() = onActivity(appBarExpanded = false) {
            onView(isRoot()).captureRoboImage()
        }

        @Test
        fun `test should launch preference list activity from options menu`() {
            onActivity { scenario ->
                scenario.onActivity {
                    openActionBarOverflowOrOptionsMenu(it)
                    onView(withText(R.string.preference_list_title)).perform(click())
                }
            }
            assertThat(shadowOf(mApplicationContext as Application).nextStartedActivity.component,
                `is`(Intent(mApplicationContext, PreferenceListActivity::class.java).component))
        }

        @Test
        fun `test fragment added once after activity recreation`() = onActivity { scenario ->
            scenario.apply {
                recreate()
                onActivity {
                    assertThat(it.supportFragmentManager.fragments,
                        containsInRelativeOrder(instanceOf(SimListFragment::class.java)))
                }
            }
        }

        @Test
        fun `test should debounce subscription change events when sent in bursts`() = onActivity {
            // For the very first subscription change event debouncing is not applied
            assertThat(shadowOf(activityWorkerLooper).nextScheduledTaskTime, `is`(Duration.ZERO))

            val emptySubInfoList = kotlin.emptyArray<SubscriptionInfo>()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(*emptySubInfoList)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1))
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(*emptySubInfoList)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1))
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(*emptySubInfoList)

            val debounceDelayDuration = shadowOf(activityWorkerLooper).nextScheduledTaskTime
                .minus(Duration.ofMillis(SystemClock.uptimeMillis()))
            assertThat(debounceDelayDuration, `is`(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION))

            // Run only one subscription change event. This is expected to be the last event
            shadowOf(activityWorkerLooper).runToNextTask()
            assertThat(shadowOf(activityWorkerLooper).nextScheduledTaskTime, `is`(Duration.ZERO))
        }

        @Test
        fun `test should stop listening for subscriptions change events when paused`() {
            onActivity { scenario ->
                with(scenario) {
                    onActivity {
                        assertTrue(mSubscriptions.hasOnSubscriptionsChangedListener(it))
                    }
                    moveToState(Lifecycle.State.STARTED) // <- will invoke Activity.onPause()
                    onActivity {
                        assertFalse(mSubscriptions.hasOnSubscriptionsChangedListener(it))
                    }
                }
            }
        }

        @Test(expected = IllegalArgumentException::class)
        fun `test should abort ViewModel creation on wrong ViewModel instance`() {
            SimListViewModel.getFactory(null, null).create(ViewModel::class.java)
        }
    }

    /** Test the "Background usage required" banner. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class BackgroundRestrictedBanner : Base() {
        @Inject
        lateinit var mActivityManager: ActivityManager

        @Test
        @Config(minSdk = Q)
        fun `test full view when background usage restricted`() {
            shadowOf(mActivityManager).setBackgroundRestricted(true)
            onActivity {
                onBanner().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be shown after activity resumed and background usage restricted`() {
            onActivity { scenario ->
                onView(withPreferenceKey(R.string.sim_list_background_restricted_banner_key))
                    .check(doesNotExist())

                shadowOf(mActivityManager).setBackgroundRestricted(true)

                // Trigger Activity.onResume() to sync the banner visibility
                with(scenario) {
                    moveToState(Lifecycle.State.STARTED)
                    moveToState(Lifecycle.State.RESUMED)
                }
                onView(withPreferenceKey(R.string.sim_list_background_restricted_banner_key))
                    .check(matches(isDisplayed()))
            }
        }

        @Test
        @Config(maxSdk = Build.VERSION_CODES.R)
        fun `test should launch app battery settings activity when positive button is clicked on pre-S`() {
            shadowOf(mActivityManager).setBackgroundRestricted(true)
            onActivity {
                onBanner().perform(positiveButtonClick())
                val nextActivity = shadowOf(mApplicationContext as Application).nextStartedActivity
                assertThat(nextActivity.action, `is`("android.settings.APP_BATTERY_SETTINGS"))
            }
        }

        @Test
        @Config(minSdk = Build.VERSION_CODES.S)
        fun `test should launch app battery settings activity when positive button is clicked on S+`() {
            shadowOf(mActivityManager).setBackgroundRestricted(true)
            onActivity {
                onBanner().perform(positiveButtonClick())
                val nextActivity = shadowOf(mApplicationContext as Application).nextStartedActivity
                assertThat(nextActivity.action,
                    `is`(Settings.ACTION_VIEW_ADVANCED_POWER_USAGE_DETAIL))
            }
        }

        private companion object {
            fun positiveButtonClick() = actionOnChild(withId(
                com.android.settingslib.widget.preference.banner.R.id.banner_positive_btn), click())

            fun onBanner(): ViewInteraction =
                onViewPrescrolled(
                    withPreferenceKey(R.string.sim_list_background_restricted_banner_key))
        }
    }

    /** Test the disclaimer banner. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class DisclaimerBanner : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test full view when app signed with AOSP platform signature and not yet dismissed`() {
            useAospPlatformSignature(mApplicationContext, true)
            onActivity {
                onBanner().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should not be shown when app not signed with AOSP platform signature`() {
            onActivity {
                onView(withPreferenceKey(R.string.sim_list_disclaimer_banner_key))
                    .check(doesNotExist())
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be permanently dismissed after positive button is clicked`() {
            useAospPlatformSignature(mApplicationContext, true)
            onActivity { scenario ->
                onBanner().perform(positiveButtonClick())
                onView(withPreferenceKey(R.string.sim_list_disclaimer_banner_key))
                    .check(doesNotExist())

                scenario.recreate()

                onView(withPreferenceKey(R.string.sim_list_disclaimer_banner_key))
                    .check(doesNotExist())
            }
        }

        private companion object {
            fun positiveButtonClick() = actionOnChild(withId(
                com.android.settingslib.widget.preference.banner.R.id.banner_positive_btn), click())

            fun onBanner(): ViewInteraction =
                onViewPrescrolled(withPreferenceKey(R.string.sim_list_disclaimer_banner_key))
        }
    }

    /** Test the SIM entries in the SIM section. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class SimEntries : Base() {
        @Inject
        lateinit var mSubscriptionManager: SubscriptionManager

        @Inject
        lateinit var mTelephonyManager: TelephonyManager

        @Inject
        lateinit var mSubscriptionsProvider: Provider<Subscriptions>

        @Inject
        lateinit var mAppDatabaseDE: AppDatabaseDE

        @Inject
        lateinit var mDaysOfWeekFactory: DaysOfWeek.Factory

        @Inject
        lateinit var mSystemTimeProvider: SystemTimeProvider

        @Test
        @Config(minSdk = Q)
        fun `test should regenerate locale-sensitive data in SIM entries`() = onActivity {
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(1)
                    setDisplayName("SIM 1")
                    setIconTint(Color.BLUE)
                }.buildSubscriptionInfo()
            )

            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            // Switch from English to Italian locale (activity will be recreated)
            RuntimeEnvironment.setQualifiers("+it")

            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }

        @Test
        @Config(minSdk = Q)
        fun `test should regenerate time-sensitive data in SIM entries`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
                setIconTint(Color.BLUE)
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            // Insert a schedule for the SIM card in database to be displayed in the summary
            runBlocking {
                val insertJob = async(Dispatchers.Default) {
                    val schedule = SubscriptionScheduleEntity().apply {
                        setSubscriptionId(subInfo.subscriptionId)
                        setSubscriptionEnabled(false)
                        setEnabled(true)
                        setDaysOfWeek(mDaysOfWeekFactory.create(*arrayOf(TUESDAY)))
                        setTime(LocalTime.of(18, 30))
                    }
                    mAppDatabaseDE.subscriptionSchedulerDao().insert(schedule)
                }
                withTimeout(5.seconds) { insertJob.await() }
            }

            mSystemTimeProvider.mutate().setNow(MONDAY_11_50_55PM)

            onActivity {
                // Ensure ViewModel finished updating UI before capturing the initial state
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

                // Simulate timezone changed by 1 hour forward to reach Tuesday
                mSystemTimeProvider.mutate().setNow(MONDAY_11_50_55PM.plusHours(1))
                mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()

                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

                // Simulate toggling the "Use 24-hour format" option in built-in Settings app
                set24Hour(mApplicationContext, true)
                mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()

                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should show No SIM cards inserted entry ONLY when appropriate`() = onActivity {
            // Ensure ViewModel finished updating UI before capturing the initial state
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

            // Simulate SIM card insertion
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(1)
                    setDisplayName("SIM 1")
                    setIconTint(Color.BLUE)
                }.buildSubscriptionInfo()
            )
            shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)

            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

            // Simulate SIM card ejection
            shadowOf(mSubscriptionManager)
                .setAvailableSubscriptionInfos(*kotlin.emptyArray<SubscriptionInfo>())
            shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)

            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }

        @Test
        @Config(minSdk = Q)
        fun `test should contain all available SIM cards`() {
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(1)
                    setSimSlotIndex(0)
                    setDisplayName("SIM 1")
                    setIconTint(Color.BLUE)
                }.buildSubscriptionInfo(),
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(2)
                    setSimSlotIndex(1)
                    setDisplayName("SIM 2")
                    setIconTint(Color.DKGRAY)
                }.buildSubscriptionInfo(),
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(3)
                    setSimSlotIndex(2)
                    setDisplayName("SIM 3")
                    setIconTint(Color.MAGENTA)
                }.buildSubscriptionInfo()
            )
            shadowOf(mTelephonyManager).apply {
                setActiveModemCount(3)
                setPhoneCount(3)
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
                onSimEntryAt(1).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
                onSimEntryAt(2).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        fun `test should launch scheduler activity on click`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
                setIconTint(Color.BLUE)
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).perform(click())

                val nextActivity = shadowOf(mApplicationContext as Application).nextStartedActivity
                assertThat(nextActivity.component,
                    `is`(ComponentName(mApplicationContext, SchedulerActivity::class.java)))
                assertThat(nextActivity.getParcelableExtra(SchedulerActivity.EXTRA_SUBSCRIPTION,
                    Subscription::class.java), `is`(withSubId(subInfo.subscriptionId)))
            }
        }

        @Test
        @Config(minSdk = Q, shadows = [
            ShadowSubscriptionManagerHiddenApi::class,
            ShadowTelephonyManagerHiddenApi::class,
        ])
        fun `test should disable toggle switch on click`() {
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                SubscriptionInfoBuilder.newBuilder().apply {
                    setId(1)
                    setDisplayName("SIM 1")
                    setIconTint(Color.BLUE)
                }.buildSubscriptionInfo()
            )

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).perform(switchClick())
                    .captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async SIM state change
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q, shadows = [
            ShadowSubscriptionManagerHiddenApi::class,
            ShadowTelephonyManagerHiddenApi::class,
        ])
        fun `test should re-enable toggle switch on next update cycle`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
                setIconTint(Color.BLUE)
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).perform(switchClick())
                    .captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

                // Wait for the ViewModel to process the async SIM state change request
                waitActivityWorkerThreadUntilIdle()

                // Simulate a subscription change event is fired due to the subscription state alter
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)
                shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)

                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should refresh all SIM entry data on SIM subscription update event`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
                setIconTint(Color.BLUE)
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)

                // Simulate SIM subscription updated from built-in Settings app
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                    SubscriptionInfoBuilder.newBuilder().apply {
                        setId(subInfo.subscriptionId)
                        setDisplayName("SIM (updated) 1")
                        setIconTint(Color.RED)
                    }.buildSubscriptionInfo()
                )
                shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)

                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onSimEntryAt(0).captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        @Config(shadows = [
            ShadowTelephonyManagerHiddenApi::class,
        ])
        fun `test should request SIM state change on toggle switch click (legacy RIL)`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Disable SIM card
                onSimEntryAt(0).perform(switchClick())

                // Wait for the ViewModel to process the async SIM state change request
                waitActivityWorkerThreadUntilIdle()

                // Assume SIM subscription disappeared from the system after being disabled
                shadowOf(mSubscriptionManager)
                    .setAvailableSubscriptionInfos(*kotlin.emptyArray<SubscriptionInfo>())

                var sub = runBlocking {
                    val job = async(Dispatchers.Default) {
                        mSubscriptionsProvider.get().getSubscriptionForSubId(subInfo.subscriptionId)
                    }
                    withTimeout(5.seconds) { job.await() }
                }
                assertThat(sub, `is`(not(Optional.empty())))
                with(sub.get()) {
                    assertThat(getSimState(), `is`(SimState.DISABLED))
                    assertThat(getKeepDisabledAcrossBoots(), `is`(true))
                }

                // Ensure ViewModel finished updating UI
                shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Enable SIM card
                onSimEntryAt(0).perform(switchClick())

                // Wait for the ViewModel to process the async SIM state change request
                waitActivityWorkerThreadUntilIdle()

                // Assume SIM subscription appeared in the system after being enabled
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

                sub = runBlocking {
                    val job = async(Dispatchers.Default) {
                        mSubscriptionsProvider.get().getSubscriptionForSubId(subInfo.subscriptionId)
                    }
                    withTimeout(5.seconds) { job.await() }
                }
                assertThat(sub, `is`(not(Optional.empty())))
                with(sub.get()) {
                    assertThat(getSimState(), `is`(SimState.ENABLED))
                    assertThat(getKeepDisabledAcrossBoots(), `is`(false))
                }
            }
        }

        @Test
        @Config(shadows = [
            ShadowTelephonyManagerHiddenApi::class,
            ShadowSubscriptionManagerHiddenApi::class,
        ])
        fun `test should request SIM state change on toggle switch click (non-legacy RIL)`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
            }.buildSubscriptionInfo().also {
                setAreUiccApplicationsEnabled(it, true)
            }
            with(shadowOf(mSubscriptionManager) as ShadowSubscriptionManagerHiddenApi) {
                setAvailableSubscriptionInfos(subInfo)
                setCanDisablePhysicalSubscription(true)
            }

            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Disable SIM card
                onSimEntryAt(0).perform(switchClick())

                // Wait for the ViewModel to process the async SIM state change request
                waitActivityWorkerThreadUntilIdle()

                var sub = runBlocking {
                    val job = async(Dispatchers.Default) {
                        mSubscriptionsProvider.get().getSubscriptionForSubId(subInfo.subscriptionId)
                    }
                    withTimeout(5.seconds) { job.await() }
                }
                assertThat(sub, `is`(not(Optional.empty())))
                assertThat(sub.get().getSimState(), `is`(SimState.DISABLED))

                // Simulate a subscription change event is fired due to the subscription state alter
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)
                shadowOf(Looper.getMainLooper()).idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)

                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Enable SIM card
                onSimEntryAt(0).perform(switchClick())

                // Wait for the ViewModel to process the async SIM state change request
                waitActivityWorkerThreadUntilIdle()

                sub = runBlocking {
                    val job = async(Dispatchers.Default) {
                        mSubscriptionsProvider.get().getSubscriptionForSubId(subInfo.subscriptionId)
                    }
                    withTimeout(5.seconds) { job.await() }
                }
                assertThat(sub, `is`(not(Optional.empty())))
                assertThat(sub.get().getSimState(), `is`(SimState.ENABLED))
            }
        }

        private companion object {
            val MONDAY_11_50_55PM = LocalDateTime.of(2007, 1, 1, 23, 50, 55)

            fun switchClick() = actionOnChild(withId(R.id.switchWidget), click())

            fun onSimEntryAt(index: Int): ViewInteraction =
                onViewPrescrolled(withPreferenceKey(R.string.sim_list_key)
                    // Skip the SIM PreferenceCategory and stride to the next SIM entry by index
                    .strideSiblings(index + 1))
        }
    }

    /** Test "Updates" preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class UpdatesPreference : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be grayed out when app signed with non-AOSP platform key`() = onActivity {
            onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }

        @Test
        @Config(minSdk = Q)
        fun `test should not be grayed out when app signed with AOSP platform key`() {
            useAospPlatformSignature(mApplicationContext, true)
            onActivity {
                onPreference().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
            }
        }

        @Test
        fun `test should open Releases URL in browser when pressed`() {
            useAospPlatformSignature(mApplicationContext, true)
            onActivity {
                onPreference().perform(click())
                assertThat(shadowOf(mApplicationContext as Application).nextStartedActivity.action,
                    `is`(Intent.ACTION_VIEW))
            }
        }

        private companion object {
            fun onPreference(): ViewInteraction = onPreferenceWithKey(R.string.sim_list_updates_key)
        }
    }

    /** Test "Build version" preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class BuildVersionPreference : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be grayed out and contain package version name string`() = onActivity {
            onPreferenceWithKey(R.string.sim_list_version_key)
                .captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }
    }

    /** Test "Help & feedback" preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class HelpAndFeedbackPreference : Base() {
        @Test
        fun `test should open Help & Feedback URL in browser when pressed`() = onActivity {
            onPreferenceWithKey(R.string.sim_list_help_key).perform(click())
            assertThat(shadowOf(mApplicationContext as Application).nextStartedActivity.action,
            `is`(Intent.ACTION_VIEW))
        }
    }

    /** Test "Open source licenses" preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class LicensePreference : Base() {
        @Test
        fun `test should launch license activity when pressed`() = onActivity {
            onPreferenceWithKey(R.string.sim_list_license_key).perform(click())
            assertThat(shadowOf(mApplicationContext as Application).nextStartedActivity.component,
                `is`(Intent(mApplicationContext, LicenseActivity::class.java).component))
        }
    }

    /** Test internal IntentReceiver. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class IntentReceiver : Base() {
        @get:Rule
        val rule = ActivityScenarioRule(SimListActivity::class.java)

        @Test
        fun `test should notify about unhandled intents`() {
            val receiver = findIntentReceiver()!!.broadcastReceiver
            receiver.onReceive(mApplicationContext, Intent("dummy"))
            assertThat(ShadowLog.getLogs(), containsLogEntryWithUnhandledAction("dummy"))
        }

        @Test
        fun `test receiver should be unregistered when activity paused`() {
            assertThat(findIntentReceiver(), `is`(not(nullValue())))
            rule.scenario.moveToState(Lifecycle.State.STARTED) // <- will invoke Activity.onPause()
            assertThat(findIntentReceiver(), `is`(nullValue()))
        }

        @Test
        fun `test should handle all declared intent filter actions`() {
            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(ShadowLog.getLogs(), `is`(not(containsLogEntryWithUnhandledAction())))
        }

        private fun findIntentReceiver(): ShadowApplication.Wrapper? =
            shadowOf(mApplicationContext as Application).registeredReceivers.find { wrapper ->
                wrapper.broadcastReceiver::class ==
                    com.github.iusmac.sevensim.ui.sim.SimListActivity.IntentReceiver::class
            }

        private companion object {
            fun containsLogEntryWithUnhandledAction(action: String = ""):
                Matcher<Iterable<ShadowLog.LogItem>> = containsInRelativeOrder(allOf(
                    withTag(endsWith(SimListActivity::class.java.simpleName)),
                    withType(Log.ERROR),
                    withMsg(stringContainsInOrder("Unhandled action", action))))
        }
    }
}

/** Shortcut to launch this activity. */
private fun onActivity(
    appBarExpanded: Boolean = false,
    block: (ActivityScenario<SimListActivity>) -> Unit
) = ActivityLauncher<SimListActivity>(appBarExpanded, block)

private val SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION = Duration.ofMillis(300)

/** Creates a {@link ViewInteraction} that pre-scrolls to a matching view. */
private fun onViewPrescrolled(matcher: Matcher<View>): ViewInteraction =
    onView(matcher).perform(scrollTo())

/** Creates a {@link ViewInteraction} that pre-scrolls to a preference matching the given key. */
private fun onPreferenceWithKey(keyResId: Int): ViewInteraction =
    onViewPrescrolled(withPreferenceKey(keyResId))

private val SCROLL_BAR_FADE_DURATION =
    Duration.ofMillis(ViewConfiguration.getScrollBarFadeDuration().toLong())

private val activityWorkerLooper
    get() = com.github.iusmac.sevensim.ui.sim.SimListActivity.sHandler?.looper

private fun waitActivityWorkerThreadUntilIdle() =
    checkNotNull(activityWorkerLooper) {
        "Access activity's worker only in ActivityScenario.launch(...) { } context."
    }.run {
        waitWorkerThreadLooperUntilIdle(this)
    }

private fun SystemTimeProvider.mutate(): SystemTimeProviderFakeImpl =
    this as SystemTimeProviderFakeImpl

open class Base : RoborazziTestBase() {
    override fun setUp() {
        super.setUp()

        val packageInfo = shadowOf(mApplicationContext.packageManager)
            .getInternalMutablePackageInfo(mApplicationContext.packageName)
        // To avoid regenerating screenshots every time due to dynamically generated version
        // name string, we provide a static one instead
        packageInfo.versionName = "0.0.0"
    }
}
