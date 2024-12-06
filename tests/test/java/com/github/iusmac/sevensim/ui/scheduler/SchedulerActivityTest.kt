package com.github.iusmac.sevensim.ui.scheduler

import android.app.Application
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.text.InputType
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.TimePicker

import androidx.activity.result.ActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withAlpha
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText

import com.github.iusmac.sevensim.AppDatabaseDE
import com.github.iusmac.sevensim.R
import com.github.iusmac.sevensim.SystemTimeProvider
import com.github.iusmac.sevensim.SystemTimeProviderFakeImpl
import com.github.iusmac.sevensim.scheduler.DayOfWeek
import com.github.iusmac.sevensim.scheduler.DayOfWeek.*
import com.github.iusmac.sevensim.scheduler.DaysOfWeek
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler
import com.github.iusmac.sevensim.telephony.PinEntity
import com.github.iusmac.sevensim.telephony.PinStorage
import com.github.iusmac.sevensim.telephony.Subscription
import com.github.iusmac.sevensim.telephony.Subscriptions
import com.github.iusmac.sevensim.test.ActivityLauncher
import com.github.iusmac.sevensim.test.FakeAndroidKeyStoreProvider
import com.github.iusmac.sevensim.test.LogItemPropertyMatchers.*
import com.github.iusmac.sevensim.test.RoborazziTestBase
import com.github.iusmac.sevensim.test.ShadowRecyclerView
import com.github.iusmac.sevensim.test.ShadowViewAnimator
import com.github.iusmac.sevensim.test.SubscriptionScheduleEntityPropertyMatchers.*
import com.github.iusmac.sevensim.test.TestUtils.invalid
import com.github.iusmac.sevensim.test.TestUtils.set24Hour
import com.github.iusmac.sevensim.test.TestUtils.waitDatabasesUntilIdle
import com.github.iusmac.sevensim.test.TestUtils.waitWorkerThreadLooperUntilIdle
import com.github.iusmac.sevensim.test.actionOnChild
import com.github.iusmac.sevensim.test.captureRoboImage
import com.github.iusmac.sevensim.test.showsTime
import com.github.iusmac.sevensim.test.withPreferenceKey
import com.github.iusmac.sevensim.ui.AuthenticationPromptActivity
import com.github.iusmac.sevensim.ui.components.EditTextDialogFragment
import com.github.iusmac.sevensim.ui.components.ItemAdapter
import com.github.iusmac.sevensim.ui.components.TimePickerDialogFragment

import com.github.takahirom.roborazzi.captureRoboImage

import dagger.hilt.android.testing.HiltAndroidTest

import java.security.KeyStore
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.OptionalInt

import javax.inject.Inject

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.hamcrest.Matcher

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowToast

@RunWith(Enclosed::class)
class SchedulerActivityTest {
    /** Test activity behaviors. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class Activity : Base() {
        @Inject
        lateinit var mSubscriptionManager: SubscriptionManager

        @Inject
        lateinit var mSubscriptions: Subscriptions

        @Test(expected = IllegalArgumentException::class)
        fun `test cannot be initiated without Intent extras`() =
            ActivityLauncher<SchedulerActivity> {}

        @Test(expected = IllegalArgumentException::class)
        fun `test cannot be initiated without Subscription in Intent extras`() =
            onActivity(subscription = null) {}

        @Test
        @Config(minSdk = Q)
        fun `test full view`() = onActivity {
            onView(isRoot()).captureRoboImage()
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+land")
        fun `test full view in landscape`() = onActivity {
            onView(isRoot()).captureRoboImage()
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+night")
        fun `test full view in night mode`() = onActivity {
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
        fun `test fragment added once after activity recreation`() = onActivity { scenario ->
            scenario.apply {
                recreate()
                onActivity {
                    assertThat(it.supportFragmentManager.fragments,
                        containsInRelativeOrder(instanceOf(SchedulerFragment::class.java)))
                }
            }
        }

        @Test
        fun `test should throw What A Terrible Failure for unhandled fragment results`() {
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    it.fragment.onFragmentResult("dummy", Bundle())
                }
                assertThat(ShadowLog.getLogs(), containsInRelativeOrder(allOf(
                    withTag(endsWith(SchedulerFragment::class.java.simpleName)),
                    withType(Log.ASSERT),
                    withMsg(stringContainsInOrder("Unhandled fragment result", "dummy")))))
            }
        }

        @Test
        fun `test should debounce subscription change events when sent in bursts`() = onActivity {
            // For the very first subscription change event debouncing is not applied
            assertThat(shadowOf(activityWorkerLooper).nextScheduledTaskTime, `is`(Duration.ZERO))

            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(null)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1))
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(null)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1))
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(null)

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
            SchedulerViewModel.getFactory(null, 1, null).create(ViewModel::class.java)
        }
    }

    /** Test both collapsing and non-collapsing (framework) toolbars. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    @Config(shadows = [ShadowViewAnimator::class])
    class Toolbar : Base() {
        @Inject
        lateinit var mSubscriptionManager: SubscriptionManager

        @Test
        @Config(minSdk = S, qualifiers = "+land +h270dp")
        fun `test expanded toolbar is not overlapped by FABs in landscape on extra-small screen`() {
            onActivity(Subscription().apply {
                setSimName("SIM with very long name")
            }) { scenario ->
                scenario.onActivity {
                    it.setSubtitle("This text should be split into two lines")
                }
                onView(collapsingToolbarMatcher()).captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = S)
        fun `test should set important for accessbility flag for collapsing toolbar subtitle`() {
            onActivity { scenario ->
                scenario.onActivity {
                    with(it.toolbarDecorator) {
                        assertThat(getCollapsingSubtitleImportantForAccessibility(),
                            `is`(OptionalInt.of(View.IMPORTANT_FOR_AUTOFILL_YES)))
                    }
                }
            }
        }

        @Test
        @Config(minSdk = S)
        fun `test should not set marquee repeat limit for collapsing toolbar`() {
            onActivity { scenario ->
                scenario.onActivity {
                    with(it.toolbarDecorator) {
                        assertThat(getSubtitleMarqueeRepeatLimit(), `is`(OptionalInt.empty()))
                    }
                }
            }
        }

        @Test
        @Config(maxSdk = Build.VERSION_CODES.R)
        fun `test should set infinite subtitle marquee repeat limit for non-collapsing toolbar`() {
            onActivity { scenario ->
                scenario.onActivity {
                    with(it.toolbarDecorator) {
                        assertThat(getSubtitleMarqueeRepeatLimit(), `is`(OptionalInt.of(-1)))
                    }
                }
            }
        }

        @Test
        fun `test should refresh SIM name when the corresponding subscription changed`() {
            var subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
            }.buildSubscriptionInfo()
            onActivity(Subscription().apply {
                id = subInfo.subscriptionId
                simName = subInfo.displayName.toString()
            }) { scenario ->
                with(scenario) {
                    onActivity {
                        assertThat(it.title, `is`(subInfo.displayName))

                        // Make the SIM card that this schedule is representing, appear in the
                        // system
                        val origName = subInfo.displayName
                        subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                            setId(subInfo.subscriptionId)
                            setDisplayName("SIM 1 updated")
                        }.buildSubscriptionInfo()
                        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

                        // Process the update event and ensure the UI is up-to-date
                        with(shadowOf(Looper.getMainLooper())) {
                            idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)
                            waitActivityWorkerThreadUntilIdle()
                            idle()
                        }

                        assertThat(it.title, both(not(origName)).and(`is`(subInfo.displayName)))
                    }
                }
            }
        }

        @Test
        fun `test should refresh SIM name ONLY when the corresponding subscription changed`() {
            onActivity { scenario ->
                val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                    setId(2)
                    setDisplayName("SIM 2")
                }.buildSubscriptionInfo()
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

                // Process the update event and ensure the UI is up-to-date
                with(shadowOf(Looper.getMainLooper())) {
                    idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)
                    waitActivityWorkerThreadUntilIdle()
                    idle()
                }

                scenario.onActivity {
                    assertThat(it.title, `is`(both(not(nullValue())).and(not(subInfo.displayName))))
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should refresh next upcoming schedule summary on subscriptions changed`() {
            onActivity {
                // Ensure ViewModel finished updating UI before capturing the initial state
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onToolbar().captureRoboImage()

                // Make the SIM card that this schedule is representing, appear in the system
                shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(
                    SubscriptionInfoBuilder.newBuilder().apply {
                        setId(1)
                        setDisplayName("SIM 1")
                    }.buildSubscriptionInfo()
                )

                // Process the update event and ensure the UI is up-to-date
                with(shadowOf(Looper.getMainLooper())) {
                    idleFor(SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION)
                    waitActivityWorkerThreadUntilIdle()
                    idle()
                }
                onToolbar().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should regenerate locale-sensitive data`() = onActivity {
            // Switch from English to Italian locale (activity will be recreated)
            RuntimeEnvironment.setQualifiers("+it")

            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onToolbar().captureRoboImage()
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+w420dp") // NOTE: width is to make room for text on pre-S
        fun `test should regenerate time-sensitive data in SIM entries`() {
            val subInfo = SubscriptionInfoBuilder.newBuilder().apply {
                setId(1)
                setDisplayName("SIM 1")
            }.buildSubscriptionInfo()
            shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo)

            // Insert a schedule for the SIM card in database to be displayed in the subtitle
            addScheduleSync { schedule ->
                with(schedule) {
                    subscriptionId = subInfo.subscriptionId
                    subscriptionEnabled = false
                    enabled = true
                    daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(TUESDAY))
                    time = LocalTime.of(18, 30)
                }
            }

            mSystemTimeProvider.mutate().setNow(MONDAY_11_50_55PM)

            onActivity(Subscription().apply {
                id = subInfo.subscriptionId
                simName = subInfo.displayName.toString()
            }) {
                // Ensure ViewModel finished updating UI before capturing the initial state
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onToolbar().captureRoboImage()

                // Simulate timezone changed by 1 hour forward to reach Tuesday
                mSystemTimeProvider.mutate().setNow(MONDAY_11_50_55PM.plusHours(1))
                mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()

                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onToolbar().captureRoboImage()

                // Simulate toggling the "Use 24-hour format" option in built-in Settings app
                set24Hour(mApplicationContext, true)
                mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
                shadowOf(Looper.getMainLooper()).idle()

                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onToolbar().captureRoboImage()
            }
        }

        private companion object {
            fun collapsingToolbarMatcher() =
                withId(com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar)

            fun framewokToolbarMatcher() =
                both(withId(com.android.settingslib.collapsingtoolbar.R.id.action_bar))
                    // Ensure we don't get confused by CollapsingToolbarLayout which is a wrapper
                    // for the framework Toolbar
                    .and(not(isDescendantOfA(collapsingToolbarMatcher())))

            fun onToolbar(): ViewInteraction =
                onView(anyOf(framewokToolbarMatcher(), collapsingToolbarMatcher()))
        }
    }

    /** Test SIM PIN error banner preference. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class PinErrorBannerPreference : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should not be shown when no PIN errors detected`() = onActivity {
            addPinEntityToStorageSync {}
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key)).check(doesNotExist())
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be shown when PIN entity is corrupted`() = onActivity {
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key)).check(doesNotExist())

            addPinEntityToStorageSync { pinEntity ->
                pinEntity.setCorrupted(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key))
                .check(matches(isDisplayed()))
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be shown when PIN entity is invalid`() = onActivity {
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key)).check(doesNotExist())

            addPinEntityToStorageSync { pinEntity ->
                pinEntity.setInvalid(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key))
                .check(matches(isDisplayed()))
        }

        @Test
        @Config(minSdk = Q)
        fun `test should disappear when PIN re-inserted`() = onActivity { scenario ->
            val pinEntity = addPinEntityToStorageSync { pinEntity ->
                pinEntity.setCorrupted(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key))
                .check(matches(isDisplayed()))

            scenario.onActivity {
                it.fragment.handleOnPinChanged(pinEntity.clearPin)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key)).check(doesNotExist())
        }

        @Test
        @Config(minSdk = Q)
        fun `test should disappear when PIN removed`() = onActivity { scenario ->
            addPinEntityToStorageSync { pinEntity ->
                pinEntity.setInvalid(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key))
                .check(matches(isDisplayed()))

            scenario.onActivity {
                it.viewModel!!.removePin()
            }
            waitActivityWorkerThreadUntilIdle()

            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onView(withPreferenceKey(R.string.scheduler_pin_error_key)).check(doesNotExist())
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be disabled while performing PIN tasks`() = onActivity { scenario ->
            val pinEntity = addPinEntityToStorageSync { pinEntity ->
                pinEntity.setInvalid(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onBanner().check(matches(isEnabled()))

            // Simulate PIN being re-entered to init a PIN task but prevent it from running
            shadowOf(activityWorkerLooper).pause()
            scenario.onActivity {
                it.fragment.handleOnPinChanged(pinEntity.clearPin)
            }
            onBanner().check(matches(not(isEnabled())))
        }

        @Test
        @Config(minSdk = Q)
        fun `test should open prompt dialog on positive button click`() = onActivity { scenario ->
            addPinEntityToStorageSync { pinEntity ->
                pinEntity.setCorrupted(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertThat(it.editTextDialogFragment, `is`(nullValue()))
            }
            onBanner().perform(positiveButtonClick())
            scenario.onActivity {
                assertThat(it.editTextDialogFragment, `is`(not(nullValue())))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should regenerate locale-sensitive data`() = onActivity {
            addPinEntityToStorageSync { pinEntity ->
                pinEntity.setCorrupted(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            // Switch from English to Italian locale (activity will be recreated)
            RuntimeEnvironment.setQualifiers("+it")
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()

            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onBanner().captureRoboImage(idleFor = SCROLL_BAR_FADE_DURATION)
        }

        private companion object {
            fun positiveButtonClick() = actionOnChild(withId(
                com.android.settingslib.widget.preference.banner.R.id.banner_positive_btn), click())

            fun onBanner(): ViewInteraction =
                onViewPrescrolled(withPreferenceKey(R.string.scheduler_pin_error_key))
        }
    }

    /** Test empty view placeholder visibility. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class EmptyViewPreference : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be shown when no schedules`() = onActivity {
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onEmptyView().check(matches(isDisplayed()))
        }

        @Test
        @Config(minSdk = Q)
        fun `test should not be shown when have at least one schedule`() {
            addScheduleSync { }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                onEmptyView().check(doesNotExist())
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should disappear after first schedule created`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            onEmptyView().check(matches(isDisplayed()))

            scenario.onActivity {
                it.viewModel!!.scheduleAddedListener.mutate().value =
                    SubscriptionScheduleEntity().apply {
                        subscriptionId = 1
                        daysOfWeek = mDaysOfWeekFactory.create()
                        time = LocalTime.MIDNIGHT
                    }
            }
            onEmptyView().check(doesNotExist())
        }

        @Test
        @Config(minSdk = Q)
        fun `test should appear ONLY after last schedule is removed`() {
            addScheduleSync { }
            addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                onEmptyView().check(doesNotExist())

                scenario.onActivity {
                    // Since the logic will be triggered on adapter events, we can perform the
                    // removal directly instead of searching for the "Delete" button among views
                    assertThat(it.fragment.mItemAdapter.removeItemAt(0), `is`(not(nullValue())))
                }
                onEmptyView().check(doesNotExist())

                scenario.onActivity {
                    assertThat(it.fragment.mItemAdapter.removeItemAt(0), `is`(not(nullValue())))
                }
                onEmptyView().check(matches(isDisplayed()))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should start creating new schedule when clicked`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertThat(it.timePickerDialogFragment, `is`(nullValue()))
            }
            onEmptyView().perform(click())
            scenario.onActivity {
                assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))
            }
        }

        private companion object {
            fun onEmptyView(): ViewInteraction =
                onView(withPreferenceKey(R.string.scheduler_empty_view_key))
        }
    }

    /** Test PIN floating action button and PIN popup menu. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class PinFab : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be in state related to PIN unset`() = onActivity {
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onPinFab().captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be in state related to PIN set`() = onActivity {
            addPinEntityToStorageSync { }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            onPinFab().captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be disabled while performing PIN tasks`() = onActivity { scenario ->
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            // Simulate PIN being re-entered to init a PIN task but prevent it from running
            shadowOf(activityWorkerLooper).pause()
            scenario.onActivity {
                it.fragment.handleOnPinChanged("5678")
            }
            onPinFab().captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test should start PIN prompt dialog on click when unset`() = onActivity { scenario ->
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertThat(it.editTextDialogFragment, `is`(nullValue()))
            }
            onPinFab().perform(click())
            scenario.onActivity {
                assertThat(it.editTextDialogFragment, `is`(not(nullValue())))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should launch popup menu on click when PIN was set`() = onActivity { scenario ->
            addPinEntityToStorageSync { }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertFalse(shadowOf(it.fragment.mPinPopupMenu).isShowing)
            }
            onPinFab().perform(click())
            scenario.onActivity {
                assertTrue(shadowOf(it.fragment.mPinPopupMenu).isShowing)
            }
            onView(isRoot()).inRoot(isPlatformPopup()).captureRoboImage()
        }

        @Test
        @Config(minSdk = Q)
        fun `test should dismiss popup menu in onDestroy`() = onActivity { scenario ->
            addPinEntityToStorageSync { }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            onPinFab().perform(click())
            scenario.apply {
                lateinit var shadowPinPopupMenu: ShadowPopupMenu
                onActivity {
                    shadowPinPopupMenu = shadowOf(it.fragment.mPinPopupMenu)
                }
                assertTrue(shadowPinPopupMenu.isShowing)
                moveToState(Lifecycle.State.DESTROYED)
                assertFalse(shadowPinPopupMenu.isShowing)
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should restore popup menu when activity recreated`() = onActivity { scenario ->
            addPinEntityToStorageSync { }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            onPinFab().perform(click())
            scenario.apply {
                onActivity {
                    assertTrue(shadowOf(it.fragment.mPinPopupMenu).isShowing)
                }
                recreate()
                onActivity {
                    assertTrue(shadowOf(it.fragment.mPinPopupMenu).isShowing)
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should not restore popup menu if dismissed when activity recreated`() {
            onActivity { scenario ->
                addPinEntityToStorageSync { }
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onPinFab().perform(click())
                scenario.apply {
                    onActivity {
                        assertTrue(shadowOf(it.fragment.mPinPopupMenu).isShowing)
                        it.fragment.mPinPopupMenu.dismiss()
                    }
                    recreate()
                    onActivity {
                        assertFalse(shadowOf(it.fragment.mPinPopupMenu).isShowing)
                    }
                }
            }
        }

        @Test
        fun `test should throw What A Terrible Failure for unhandled popup menu options`() {
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    assertFalse(shadowOf(it.fragment.mPinPopupMenu).onMenuItemClickListener
                        .onMenuItemClick(RoboMenuItem(Resources.ID_NULL)))
                }
                assertThat(ShadowLog.getLogs(), containsInRelativeOrder(allOf(
                    withTag(endsWith(SchedulerFragment::class.java.simpleName)),
                    withType(Log.ASSERT),
                    withMsg(startsWith("Unhandled menu option")))))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should remove PIN entity from PinStorage when remove clicked in popup menu`() {
            onActivity { scenario ->
                addPinEntityToStorageSync { }
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    assertTrue(it.viewModel!!.isPinPresent)
                }

                onPinFab().perform(click())
                onView(both(withText(R.string.scheduler_pin_menu_delete_title))
                    .and(withId(android.R.id.title))).perform(click())
                // Wait for the PIN removal request to finish
                waitActivityWorkerThreadUntilIdle()

                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                scenario.onActivity {
                    assertFalse(it.viewModel!!.isPinPresent)
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should start PIN prompt dialog when edit clicked in popup menu`() {
            onActivity { scenario ->
                addPinEntityToStorageSync { }
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.editTextDialogFragment, `is`(nullValue()))
                }
                onPinFab().perform(click())
                onView(both(withText(R.string.scheduler_pin_menu_edit_title))
                    .and(withId(android.R.id.title))).perform(click())
                scenario.onActivity {
                    assertThat(it.editTextDialogFragment, `is`(not(nullValue())))
                }
            }
        }

        private companion object {
            fun onPinFab(): ViewInteraction = onView(withId(R.id.fab_pin))
        }
    }

    /** Test PIN prompt dialog. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class PinPromptDialog : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test full view`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.showPinPromptDialog()
            }
            onView(isRoot()).inRoot(isDialog()).captureRoboImage()
        }

        @Test
        fun `test input field is configured for entering PIN code`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.showPinPromptDialog()
            }
            onEditText().apply {
                // Verify it's empty at the beginning
                check(matches(withText("")))

                // Verify whether only numbers are allowed & the input is transformed to password
                check({ view, _ ->
                    with(view as TextView) {
                        assertThat(inputType, `is`(
                            InputType.TYPE_CLASS_NUMBER.or(
                                InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
                    }
                })

                // Verify maximum allowed string length is 8 chars as per UICC specs
                perform(replaceText("123456789"))
                check(matches(withText("12345678")))
            }
        }

        @Test
        fun `test should successfully propagate PIN when pressed OK`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.showPinPromptDialog()
            }

            // Prevent the ViewModel from immediately starting processing the PIN request, so we can
            // capture the lock state that will be held while performing PIN tasks
            shadowOf(activityWorkerLooper).pause()
            scenario.onActivity {
                assertFalse(it.viewModel!!.pinTaskLock.value!!)
            }
            onEditText().perform(replaceText("1234"), pressImeActionButton())
            scenario.onActivity {
                assertTrue(it.viewModel!!.pinTaskLock.value!!)
            }
        }

        private companion object {
            fun onEditText(): ViewInteraction = onView(withId(android.R.id.edit)).inRoot(isDialog())
        }
    }

    /** Test handling of the new PIN string supplied after prompt dialog is dismissed. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class PinMutations : Base() {
        @Inject
        lateinit var mKeyStore: KeyStore

        @Inject
        lateinit var mSubscriptionScheduler: SubscriptionScheduler

        @Test
        fun `test should discard when supplied an invalid PIN`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.handleOnPinChanged("")
                assertFalse(it.viewModel!!.isPinPresent)
            }
            // Wait for the PIN request to finish
            waitActivityWorkerThreadUntilIdle()
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            scenario.onActivity {
                assertFalse(it.viewModel!!.isPinPresent)
            }
        }

        @Test
        fun `test should toast message when supplied an invalid PIN`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.handleOnPinChanged("")
            }
            shadowOf(Looper.getMainLooper()).runOneTask()
            assertThat(ShadowToast.getTextOfLatestToast(),
                `is`(mApplicationContext.getString(R.string.scheduler_pin_invalid_hint)))
        }

        @Test
        fun `test should successfully handle PIN when authentication not required`() {
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.handleOnPinChanged("1234")
                    assertFalse(it.viewModel!!.isPinPresent)
                }
                // Wait for the PIN request to finish
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                scenario.onActivity {
                    assertTrue(it.viewModel!!.isPinPresent)
                }
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.handleOnPinChanged("1234")
                    assertFalse(it.viewModel!!.isPinPresent)
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                // Wait for the PIN request to finish
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()
                scenario.onActivity {
                    assertTrue(it.viewModel!!.isPinPresent)
                }
            }
        }

        @Test
        fun `test should hold lock while performing async request`() = onActivity { scenario ->
            // Prevent the ViewModel from immediately starting processing the PIN request, so we can
            // capture the lock state that will be held while performing PIN tasks
            shadowOf(activityWorkerLooper).pause()
            scenario.onActivity {
                assertFalse(it.viewModel!!.pinTaskLock.value!!)
                it.fragment.handleOnPinChanged("1234")
                assertTrue(it.viewModel!!.pinTaskLock.value!!)

                // Resume paused PIN request and wait for it to finish
                shadowOf(activityWorkerLooper).unPause()
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                assertFalse(it.viewModel!!.pinTaskLock.value!!)
            }
        }

        @Test
        fun `test should unset invalid flag`() = onActivity { scenario ->
            var pinEntity = addPinEntityToStorageSync { pinEntity ->
                pinEntity.setInvalid(true)
            }
            // Wait for Room to populate observable PIN LiveData
            waitDatabasesUntilIdle()
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                it.fragment.handleOnPinChanged(pinEntity.clearPin)
            }
            // Wait for the PIN request to finish
            waitActivityWorkerThreadUntilIdle()
            pinEntity = runBlocking {
                val job = async(Dispatchers.Default) {
                    mPinStorage.getPin(pinEntity.subscriptionId).get()
                }
                withTimeout(5.seconds) { job.await() }
            }
            assertThat(pinEntity, `is`(not(invalid())))
        }

        @Test
        fun `test should toast message when PIN encryption failed`() = onActivity { scenario ->
            // Simulate AndroidKeyStore fails to generate a secret key
            (mKeyStore.provider as FakeAndroidKeyStoreProvider).setAESKeyGenerationAlwaysFails(true)
            scenario.onActivity {
                it.fragment.handleOnPinChanged("1234")
            }
            // Wait for the PIN request to finish
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(ShadowToast.getTextOfLatestToast(),
                `is`(mApplicationContext.getString(R.string.sim_pin_operation_failed)))
        }

        @Test
        fun `test should update next weekly repeat schedule processing iteration on scheduler`() {
            mSystemTimeProvider.mutate().setNow(MONDAY_11_50_55PM)
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.handleOnPinChanged("1234")
                }
                // Wait for the PIN request to finish
                waitActivityWorkerThreadUntilIdle()

                val timeCaptor = ArgumentCaptor.forClass(LocalDateTime::class.java)
                verify(mSubscriptionScheduler, times(1))
                    .updateNextWeeklyRepeatScheduleProcessingIter(timeCaptor.capture(), anyList())
                assertThat(timeCaptor.value, `is`(MONDAY_11_50_55PM.plusMinutes(1)))
            }
        }
    }

    /** Test the "Add" floating action button. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class AddFab : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test full view`() = onActivity {
            onAddFab().captureRoboImage()
        }

        @Test
        fun `test should start time picker dialog when clicked`() = onActivity { scenario ->
            scenario.onActivity {
                assertThat(it.timePickerDialogFragment, `is`(nullValue()))
            }
            onAddFab().perform(click())
            scenario.onActivity {
                assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))
            }
        }

        @Test
        fun `test should configure time picker to display current wall clock time`() = onActivity {
	    val now = LocalDateTime.of(2007, 1, 1, 13, 0)
            mSystemTimeProvider.mutate().setNow(now)
            onAddFab().perform(click())
            onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog())
                .check(showsTime(now.toLocalTime()))
        }

        @Test
        fun `test should clear currently selected schedule when clicked`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.mSelectedSchedule = SubscriptionScheduleEntity()
            }
            onAddFab().perform(click())
            scenario.onActivity {
                assertThat(it.fragment.mSelectedSchedule, `is`(nullValue()))
            }
        }

        private companion object {
            fun onAddFab(): ViewInteraction = onView(withId(R.id.fab_add))
        }
    }

    /** Test the time picker dialog. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class TimePickerDialog : Base() {
        @Test
        fun `test should display the desired wall clock time`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.showTimePicker(PICKED_TIME)
            }
            onTimePicker().check(showsTime(PICKED_TIME))
        }

        @Test
        fun `test should dismiss old instance if already exists`() = onActivity { scenario ->
            scenario.onActivity {
                assertThat(it.timePickerDialogFragment, `is`(nullValue()))
                val pickers = it.supportFragmentManager.run {
                    it.fragment.showTimePicker(PICKED_TIME)
                    executePendingTransactions()
                    assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))

                    it.fragment.showTimePicker(PICKED_TIME)
                    executePendingTransactions()
                    assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))

                    fragments.filterIsInstance<TimePickerDialogFragment>()
                }
                assertThat(pickers, hasSize(1))
            }
        }

        @Test
        fun `test should successfully create schedule with picked time`() = onActivity { scenario ->
            scenario.onActivity {
                it.fragment.showTimePicker(PICKED_TIME)
                assertThat(it.viewModel!!.scheduleAddedListener.value, `is`(nullValue()))
            }

            onTimePicker().perform(actionOnChild(withId(android.R.id.button1), click()))
            // Wait for the add request to finish
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertThat(it.viewModel!!.scheduleAddedListener.value, `is`(withTime(PICKED_TIME)))
            }
        }

        private companion object {
            val PICKED_TIME = LocalTime.of(1, 0)

            fun onTimePicker(): ViewInteraction =
                onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog())
        }
    }

    /** Test handling of the picked time after the time picker dialog is dismissed. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class TimeMutations : Base() {
        @Test
        fun `test should successfully create schedule with PIN when authentication not required`() {
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    it.fragment.handleOnTimePicked(PICKED_TIME.toString())
                    assertThat(it.viewModel!!.scheduleAddedListener.value, `is`(nullValue()))
                }
                // Wait for the add request to finish
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.viewModel!!.scheduleAddedListener.value,
                        `is`(withTime(PICKED_TIME)))
                }
            }
        }

        @Test
        fun `test should successfully create schedule without PIN when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.handleOnTimePicked(PICKED_TIME.toString())
                    assertThat(it.viewModel!!.scheduleAddedListener.value, `is`(nullValue()))
                }
                // Wait for the add request to finish
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.viewModel!!.scheduleAddedListener.value,
                        `is`(withTime(PICKED_TIME)))
                }
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    assertThat(it.viewModel!!.scheduleAddedListener.value, `is`(nullValue()))
                    it.fragment.handleOnTimePicked(PICKED_TIME.toString())
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime())
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                // Wait for the add request to finish
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.viewModel!!.scheduleAddedListener.value,
                        `is`(withTime(PICKED_TIME)))
                }
            }
        }

        @Test
        fun `test should update time in the selected schedule`() {
            // Insert a schedule for the SIM card in database to further select it
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnTimePicked(PICKED_TIME.toString())
                        assertThat(mSelectedSchedule.time,
                            both(not(schedule.time)).and(`is`(PICKED_TIME)))
                    }
                }
            }
        }

        @Test
        fun `test should successfully update time in the selected schedule after activity recreated`() {
            // Insert a schedule for the SIM card in database to further select it
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                    }
                }
                scenario.recreate()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        handleOnTimePicked(PICKED_TIME.toString())
                        assertThat(mSelectedSchedule.time,
                            both(not(schedule.time)).and(`is`(PICKED_TIME)))
                    }
                }
            }
        }

        @Test
        fun `test should rearrange schedules in the list when sorting criteria changed`() {
            // Insert two schedules for the SIM card in database that occur at the same time
            addScheduleSync { }
            val selectedSchedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(selectedSchedule.id)
                        // The selected schedule was inserted last, so it will be first in the list
                        assertThat(mItemAdapter.getPosition(selectedSchedule.id), `is`(0))
                        handleOnTimePicked(selectedSchedule.time.plusHours(1).toString())
                        // Since the selected schedule is set to occur one hour later, it will be
                        // positioned lower in the list, as per ascending order
                        assertThat(mItemAdapter.getPosition(selectedSchedule.id), `is`(1))
                    }
                }
                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should reflect updated time in the selected schedule view`() {
            val selectedScheduleId = addScheduleSync { }.id
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onView(withId(R.id.digital_clock)).captureRoboImage()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(selectedScheduleId)
                        handleOnTimePicked(PICKED_TIME.toString())
                    }
                }
                onView(withId(R.id.digital_clock)).captureRoboImage()

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        private companion object {
            val PICKED_TIME = LocalTime.of(1, 0)
        }
    }

    /** Test the schedule view in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ScheduleView : Base() {
        private lateinit var mSchedule: SubscriptionScheduleEntity

        override fun setUp() {
            super.setUp()

            mSchedule = addScheduleSync { }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should expand when clicked on empty space`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                assertFalse(it.fragment.isExpanded(mSchedule.id))
                doActionOnScheduleView(clickBottomLeftCorner())
                assertTrue(it.fragment.isExpanded(mSchedule.id))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test full view collapsed`() = onActivity {
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+land")
        fun `test full view collapsed in landscape`() = onActivity {
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+land")
        fun `test full view expanded in landscape`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(clickBottomLeftCorner())
            scenario.onActivity {
                assertTrue(it.fragment.isExpanded(mSchedule.id))
            }
            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+night")
        fun `test full view collapsed in night mode`() = onActivity {
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q)
        fun `test full view expanded`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(clickBottomLeftCorner())
            scenario.onActivity {
                assertTrue(it.fragment.isExpanded(mSchedule.id))
            }
            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q, qualifiers = "+night")
        fun `test full view expanded in night mode`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            doActionOnScheduleView(clickBottomLeftCorner())
            scenario.onActivity {
                assertTrue(it.fragment.isExpanded(mSchedule.id))
            }
            doActionOnScheduleView(captureRoboImage())
        }

        @Test
        @Config(minSdk = Q)
        fun `test should collapse when clicked`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.fragment) {
                    expand(mSchedule.id)
                    assertTrue(isExpanded(mSchedule.id))
                }
            }
            doActionOnScheduleView(clickBottomLeftCorner())
            scenario.onActivity {
                assertFalse(it.fragment.isExpanded(mSchedule.id))
            }
        }

        @Test
        fun `test should re-expand when activity recreated`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.fragment) {
                    expand(mSchedule.id)
                    assertTrue(isExpanded(mSchedule.id))
                }
            }
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity {
                assertTrue(it.fragment.isExpanded(mSchedule.id))
            }
        }

        @Test
        fun `test should not notify listeners when already expanded`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.fragment) {
                    expand(mSchedule.id)
                    assertTrue(isExpanded(mSchedule.id))

                    val itemHolder = mItemAdapter.findItemById(mSchedule.id)
                    itemHolder.addOnItemChangedListener(object : ItemAdapter.OnItemChangedListener {
                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>) {
                            fail()
                        }

                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>, payload: Any?) {
                            fail()
                        }
                    })
                    expand(mSchedule.id)
                    assertTrue(isExpanded(mSchedule.id))
                }
            }
        }

        @Test
        fun `test should not notify listeners when already collapsed`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.fragment) {
                    assertFalse(isExpanded(mSchedule.id))

                    val itemHolder = mItemAdapter.findItemById(mSchedule.id)
                    itemHolder.addOnItemChangedListener(object : ItemAdapter.OnItemChangedListener {
                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>) {
                            fail()
                        }

                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>, payload: Any?) {
                            fail()
                        }
                    })
                    collapse(mSchedule.id)
                    assertFalse(isExpanded(mSchedule.id))
                }
            }
        }

        private companion object {
            /** Action to perform a precise click on the bottom left corner of the schedule view. */
            fun clickBottomLeftCorner(): ViewAction = actionWithAssertions(
                GeneralClickAction(
                    Tap.SINGLE,
                    GeneralLocation.BOTTOM_LEFT,
                    Press.PINPOINT,
                    InputDevice.SOURCE_UNKNOWN,
                    MotionEvent.BUTTON_PRIMARY))

            fun doActionOnScheduleView(action: ViewAction) {
                onView(isAssignableFrom(RecyclerView::class.java))
                    .perform(actionOnItemAtPosition<ScheduleItemViewHolder>(0, action))
            }
        }
    }

    /** Test the schedule's clock view in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ClockView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should display the schedule time`() {
            addScheduleSync { }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onClockView().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should change visiual effects after disabling schedule`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
                schedule.enabled = true
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onClockView().check(matches(withAlpha(ScheduleItemViewHolder.CLOCK_ENABLED_ALPHA)))
                onClockView().check { view, _ ->
                    with(view as TextView) {
                        assertThat(typeface.style, `is`(Typeface.BOLD))
                    }
                }
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnEnabledStateChanged(false)
                    }
                }
                onClockView().check(matches(withAlpha(ScheduleItemViewHolder.CLOCK_DISABLED_ALPHA)))
                onClockView().check { view, _ ->
                    with(view as TextView) {
                        assertThat(typeface, `is`(nullValue()))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should start time picker dialog when clicked`() {
            addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.timePickerDialogFragment, `is`(nullValue()))
                }
                onClockView().perform(click())
                scenario.onActivity {
                    assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))
                }
            }
        }

        @Test
        fun `test should start time picker dialog on click when schedule view already expanded`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                    assertThat(it.timePickerDialogFragment, `is`(nullValue()))
                }
                onClockView().perform(click())
                scenario.onActivity {
                    assertThat(it.timePickerDialogFragment, `is`(not(nullValue())))
                }
            }
        }

        @Test
        fun `test should configure time picker to display the scheduled time`() {
            val schedule = addScheduleSync { }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onClockView().perform(click())
                onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog())
                    .check(showsTime(schedule.time))
            }
        }

        @Test
        fun `test should set currently selected schedule when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(nullValue()))
                }
                onClockView().perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(withId(schedule.id)))
                }
            }
        }

        @Test
        fun `test should expand schedule view when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        val initialExpanded = false
                        assertThat(isExpanded(schedule.id), `is`(initialExpanded))
                        onClockView().perform(click())
                        assertThat(isExpanded(schedule.id), `is`(not(initialExpanded)))
                    }
                }
            }
        }

        private companion object {
            fun onClockView(): ViewInteraction = onView(
                both(withId(R.id.digital_clock))
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test the schedule's read-only & editable label views in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class EditLabelView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be effectively invisible when no label provided`() {
            val schedule = addScheduleSync { }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                assertThat(schedule.label, `is`(nullValue()))
                onEditLabel().check(matches(withEffectiveVisibility(Visibility.GONE)))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be effectively visible when non-empty label provided`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                assertThat(schedule.label, `is`(not(nullValue())))
                onEditLabel().check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should become effectively invisible after label removed`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onEditLabel().check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnLabelChanged("")
                    }
                }
                onEditLabel().check(matches(withEffectiveVisibility(Visibility.GONE)))

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display hint when no label provided and schedule is expanded`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                assertThat(schedule.label, `is`(nullValue()))
                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onEditLabel().check(matches(withHint(R.string.scheduler_name_hint)))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should expand schedule when clicked`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertFalse(it.fragment.isExpanded(schedule.id))
                }
                onEditLabel().perform(click())
                scenario.onActivity {
                    assertTrue(it.fragment.isExpanded(schedule.id))
                }
            }
        }

        @Test
        fun `test should set currently selected schedule when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        expand(schedule.id)
                    }
                }
                onEditLabel().perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(withId(schedule.id)))
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should start edit label dialog on click when schedule expanded`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                    assertThat(it.editTextDialogFragment, `is`(nullValue()))
                }
                onEditLabel().perform(click())
                scenario.onActivity {
                    assertThat(it.editTextDialogFragment, `is`(not(nullValue())))
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should change visiual effects after disabling schedule`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
                schedule.enabled = true
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onEditLabel().check(matches(withAlpha(ScheduleItemViewHolder.CLOCK_ENABLED_ALPHA)))
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnEnabledStateChanged(false)
                    }
                }
                onEditLabel().check(matches(withAlpha(ScheduleItemViewHolder.CLOCK_DISABLED_ALPHA)))

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should be limited to single line with ellipsize when schedule collapsed`() {
            addScheduleSync { schedule ->
                schedule.label = "This text should not be split into two lines when schedule collapsed"
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onEditLabel().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should support multiline when schedule expanded`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "This text should be split into two lines when schedule expanded"
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onEditLabel().captureRoboImage()
            }
        }

        private companion object {
            fun onEditLabel(): ViewInteraction = onView(
                both(anyOf(withId(R.id.label), withId(R.id.edit_label)))
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test the edit label prompt dialog. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class EditLabelPromptDialog: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test full view`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "This text should not be split into two lines"
            }
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.onEditLabelClicked(schedule)
                }
                onView(isRoot()).inRoot(isDialog()).captureRoboImage()
            }
        }

        @Test
        fun `test should successfully propagate label text when pressed OK`() {
            val label = "Example label"
            val schedule = addScheduleSync { schedule ->
                schedule.label = label
            }
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.onEditLabelClicked(schedule)
                }
                val appendLabel = " with appended text"
                onEditText().perform(typeText(appendLabel), pressImeActionButton())
                assertThat(schedule.label, `is`(label + appendLabel))

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        private companion object {
            fun onEditText(): ViewInteraction = onView(withId(android.R.id.edit)).inRoot(isDialog())
        }
    }

    /** Test handling of the new schedule label after the edit label prompt dialog is dismissed. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class EditLabelMutations : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should reflect updated label in the selected schedule view`() {
            val schedule = addScheduleSync { schedule ->
                schedule.label = "Example label"
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onEditLabel().captureRoboImage()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnLabelChanged("Example updated label")
                    }
                }
                onEditLabel().captureRoboImage()

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        private companion object {
            fun onEditLabel(): ViewInteraction = onView(
                both(anyOf(withId(R.id.label), withId(R.id.edit_label)))
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test the schedule's on/off toggle switch view. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class OnOffSwitchView: Base() {
        @Test
        fun `test should set currently selected schedule when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        expand(schedule.id)
                    }
                }
                onSwitchView().perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(withId(schedule.id)))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should propagate enabled state only when different`() {
            addScheduleSync { schedule ->
                schedule.enabled = true
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    // When the switch view is initialized during its item view binding, it could
                    // trigger the corresponding click handler API, that will select the schedule to
                    // further apply the *same* change, which is not the wanted behavior
                    assertThat(it.fragment.mSelectedSchedule, `is`(nullValue()))
                }
            }
        }

        private companion object {
            fun onSwitchView(): ViewInteraction = onView(both(withId(R.id.onoff))
                .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test handling of the new schedule enabled state after the on/off switch is clicked. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class OnOffSwitchMutations : Base() {
        @Test
        fun `test should successfully update state for schedule without PIN`() {
            val schedule = addScheduleSync { schedule ->
                schedule.enabled = false
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        val newEnabled = !schedule.enabled
                        handleOnEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.enabled, `is`(newEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should successfully update state for schedule with PIN when authentication not required`() {
            val schedule = addScheduleSync { schedule ->
                schedule.enabled = false
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        val newEnabled = !schedule.enabled
                        handleOnEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.enabled, `is`(newEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            val schedule = addScheduleSync { schedule ->
                schedule.enabled = true
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                val newEnabled = !schedule.enabled
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.enabled, `is`(not(newEnabled)))
                    }
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime())
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule.enabled, `is`(newEnabled))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }
    }

    /** Test the schedule's action summary view & popup menu in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ActionSummaryView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should launch popup menu when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                onView(isRoot()).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should expand schedule when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertFalse(isExpanded(schedule.id))
                        onActionView().perform(click())
                        assertTrue(isExpanded(schedule.id))
                    }
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the action name in short form when schedule collapsed`() {
            addScheduleSync { schedule ->
                schedule.subscriptionEnabled = true
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onActionView().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the actual subscription enabled state in the popup menu`() {
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = false
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                onView(isRoot()).inRoot(isPlatformPopup()).captureRoboImage()
            }
        }

        @Test
        fun `test should dismiss popup menu when schedule collapsed`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                scenario.onActivity {
                    with(it.fragment) {
                        val viewHolder = listView.findViewHolderForLayoutPosition(0)
                        val shadowPopup = with(viewHolder as ExpandedScheduleViewHolder) {
                            shadowOf(mActionPopupMenu)
                        }
                        assertTrue(shadowPopup.isShowing())
                        collapse(schedule.id)
                        shadowOf(Looper.getMainLooper()).idle()
                        assertFalse(shadowPopup.isShowing())
                    }
                }
            }
        }

        @Test
        fun `test should restore popup menu when activity recreated`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                onView(isRoot()).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
                scenario.recreate()
                shadowOf(Looper.getMainLooper()).idle()
                onView(isRoot()).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            }
        }

        @Test
        fun `test should not restore popup menu if dismissed when activity recreated`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                with(scenario) {
                    onActivity {
                        val viewHolder = it.fragment.listView.findViewHolderForLayoutPosition(0)
                        with(viewHolder as ExpandedScheduleViewHolder) {
                            assertTrue(shadowOf(mActionPopupMenu).isShowing())
                            mActionPopupMenu.dismiss()
                        }
                    }
                    recreate()
                }
                shadowOf(Looper.getMainLooper()).idle()

                // After the activity has been recreated, only the schedule expansion option should
                // survive, while the popup menu should not
                scenario.onActivity {
                    assertTrue(it.fragment.isExpanded(schedule.id))
                    val viewHolder = it.fragment.listView.findViewHolderForLayoutPosition(0)
                    with(viewHolder as ExpandedScheduleViewHolder) {
                        assertFalse(shadowOf(mActionPopupMenu).isShowing())
                    }
                }
            }
        }

        @Test
        fun `test should propagate subscription enabled state only when different`() {
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = true
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onActionView().perform(click())
                onView(both(withText(R.string.scheduler_action_activate_sim_long_text))
                    .and(withId(android.R.id.title))).perform(click())

                scenario.onActivity {
                    // When the switch view is initialized during its item view binding, it could
                    // trigger the corresponding click handler API, that will select the schedule to
                    // further apply the *same* change, which is not the wanted behavior
                    assertThat(it.fragment.mSelectedSchedule, `is`(nullValue()))
                }
            }
        }

        @Test
        fun `test should successfully propagate subscription state when selected in popup menu`() {
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = true
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }

                // Verify different action
                onActionView().perform(click())
                onView(both(withText(R.string.scheduler_action_deactivate_sim_long_text))
                    .and(withId(android.R.id.title))).perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(withSubscriptionEnabled(false)))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test(expected = IllegalArgumentException::class)
        fun `test should abort on unhandled popup menu actions`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        expand(schedule.id)
                        shadowOf(Looper.getMainLooper()).idle()
                        val viewHolder = listView.findViewHolderForLayoutPosition(0)
                        with(viewHolder as ExpandedScheduleViewHolder) {
                            shadowOf(mActionPopupMenu).onMenuItemClickListener
                                .onMenuItemClick(RoboMenuItem(Resources.ID_NULL))
                        }
                    }
                }
            }
        }

        private companion object {
            fun onActionView(): ViewInteraction = onView(both(withId(R.id.action_summary))
                .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test handling of the new subscription enabled state after the popup menu is dismissed. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class SubscriptionEnabledStateMutations : Base() {
        @Test
        fun `test should successfully update state for schedule without PIN`() {
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = false
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        val newEnabled = !schedule.subscriptionEnabled
                        handleOnSubscriptionEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.subscriptionEnabled, `is`(newEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should successfully update state for schedule with PIN when authentication not required`() {
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = false
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        val newEnabled = !schedule.subscriptionEnabled
                        handleOnSubscriptionEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.subscriptionEnabled, `is`(newEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            val schedule = addScheduleSync { schedule ->
                schedule.subscriptionEnabled = true
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                val newEnabled = !schedule.subscriptionEnabled
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnSubscriptionEnabledStateChanged(newEnabled)
                        assertThat(mSelectedSchedule.subscriptionEnabled, `is`(not(newEnabled)))
                    }
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime())
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule.subscriptionEnabled, `is`(newEnabled))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should reflect updated state in the selected schedule view when collapsed`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                onActionView().captureRoboImage()
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnSubscriptionEnabledStateChanged(!schedule.subscriptionEnabled)
                    }
                }
                onActionView().captureRoboImage()

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should reflect updated state in the selected schedule view when expanded`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        expand(schedule.id)
                        onActionView().captureRoboImage()
                        handleOnSubscriptionEnabledStateChanged(!schedule.subscriptionEnabled)
                    }
                }
                onActionView().captureRoboImage()

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        private companion object {
            fun onActionView(): ViewInteraction = onView(both(withId(R.id.action_summary))
                .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test the schedule's days of week view in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class DaysOfWeekView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should expand schedule when clicked`() {
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(SUNDAY))
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    assertFalse(it.fragment.isExpanded(schedule.id))
                }
                onDayOfWeek().perform(click())
                scenario.onActivity {
                    assertTrue(it.fragment.isExpanded(schedule.id))
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the text in long form only when a single day is selected`() {
            addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(SUNDAY))
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onDayOfWeek().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the text in short form only when at least two days selected`() {
            addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(SUNDAY, WEDNESDAY, FRIDAY)
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onDayOfWeek().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the appropriate text when all days of week selected`() {
            addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
                    THURSDAY, FRIDAY, SATURDAY)
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onDayOfWeek().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the appropriate text when no days of week selected`() {
            addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create()
            }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                onDayOfWeek().captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should toggle state when clicked`() {
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(MONDAY, FRIDAY)
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                // Capture initial state
                onDayOfWeek().captureRoboImage()
                repeat(7) { index ->
                    onDayOfWeek(index + 1).perform(click())
                    onDayOfWeek().captureRoboImage()
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should set currently selected schedule when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        expand(schedule.id)
                    }
                }
                onDayOfWeek(MONDAY).perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule, `is`(withId(schedule.id)))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should successfully propagate selected day when clicked`() {
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(SUNDAY, TUESDAY)
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }

                onDayOfWeek(MONDAY).perform(click())
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule.daysOfWeek,
                        `is`(mDaysOfWeekFactory.create(SUNDAY, MONDAY, TUESDAY)))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        private companion object {
            /** Matches a single day of week button or the parent container view. */
            fun onDayOfWeek(@DayOfWeek dayOfWeek: Int = 0): ViewInteraction {
                val targetMatcher: Matcher<View>
                if (dayOfWeek > 0) {
                    val dayOfWeekMatcher = when (dayOfWeek) {
                        1 -> R.id.day_button_0
                        2 -> R.id.day_button_1
                        3 -> R.id.day_button_2
                        4 -> R.id.day_button_3
                        5 -> R.id.day_button_4
                        6 -> R.id.day_button_5
                        7 -> R.id.day_button_6
                        else -> 0
                    }
                    targetMatcher = allOf(withParent(withId(R.id.repeat_days)),
                        withId(dayOfWeekMatcher))
                } else {
                    targetMatcher = anyOf(withId(R.id.days_of_week), withId(R.id.repeat_days))
                }
                return onView(both(targetMatcher)
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
            }
        }
    }

    /** Test handling of the selected days of week after the corresponding button was clicked. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class DaysOfWeekMutations : Base() {
        @Test
        fun `test should successfully update the selected day for schedule without PIN`() {
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create()
            }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        val wantedDay = MONDAY
                        val wantedEnabled = true
                        assertThat(mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                            `is`(not(wantedEnabled)))
                        handleOnDayOfWeekChanged(wantedDay, wantedEnabled)
                        assertThat(mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                            `is`(wantedEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should successfully update the selected day for schedule with PIN when authentication not required`() {
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create()
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                val wantedDay = MONDAY
                val wantedEnabled = true
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        assertThat(mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                            `is`(not(wantedEnabled)))
                        handleOnDayOfWeekChanged(wantedDay, wantedEnabled)
                        assertThat(mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                            `is`(wantedEnabled))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            val schedule = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create()
            }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                val wantedDay = MONDAY
                val wantedEnabled = true
                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnDayOfWeekChanged(wantedDay, wantedEnabled)
                        assertThat(mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                            `is`(not(wantedEnabled)))
                    }
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime())
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                scenario.onActivity {
                    assertThat(it.fragment.mSelectedSchedule.daysOfWeek.isBitOn(wantedDay),
                        `is`(wantedEnabled))
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule update request,
                // that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }
    }

    /** Test the schedule's "Delete" button view & confirmation container view. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class DeleteView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should be replaced with confirm view when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                // Capture initial state
                onDeleteView(isContainer = true).captureRoboImage()
                onDeleteView().perform(click())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onDeleteView(isContainer = true).captureRoboImage()
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should replace the confim view when cancel clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onCancelView()
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onDeleteView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    .perform(click())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onCancelView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    .perform(click())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onDeleteView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should successfully propagate schedule removal when OK clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onOkView()
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onDeleteView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    .perform(click())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(not(nullValue())))
                        assertThat(mExpandedScheduleId, `is`(schedule.id))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                    }
                }
                onOkView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    .perform(click())
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(nullValue()))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        assertThat(mExpandedScheduleId, `is`(not(schedule.id)))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule removal
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should restore confirm view when activity recreated`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.expand(schedule.id)
                }
                onDeleteView()
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    .perform(click())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.delete_confirm_container))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                scenario.recreate()
                shadowOf(Looper.getMainLooper()).idle()
                onDeleteView()
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.delete_confirm_container))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            }
        }

        private companion object {
            /** Matches the "Delete" button view or its parent view container. */
            fun onDeleteView(isContainer: Boolean = false): ViewInteraction =
                onView(both(withId(if (isContainer) R.id.delete_container else R.id.delete))
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))

            fun onOkView(): ViewInteraction = onView(allOf(withId(R.id.ok),
                    isDescendantOfA(withId(R.id.delete_confirm_container))))

            fun onCancelView(): ViewInteraction = onView(allOf(withId(R.id.cancel),
                    isDescendantOfA(withId(R.id.delete_confirm_container))))
        }
    }

    /** Test handling of the removal of the selected schedule after the "OK" button was clicked. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class DeleteHandling : Base() {
        @Test
        fun `test should successfully delete schedule without PIN`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(not(nullValue())))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        setSelectedSchedule(schedule.id)
                        handleOnScheduleDeleted()
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(nullValue()))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule removal
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should successfully delete schedule with PIN when authentication not required`() {
            val schedule = addScheduleSync { }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(not(nullValue())))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                        setSelectedSchedule(schedule.id)
                        handleOnScheduleDeleted()
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(nullValue()))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule removal
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }

        @Test
        fun `test should start authentication prompt activity when authentication is required`() {
            shadowOf(mKeyguardManager).setIsDeviceSecure(true)
            val schedule = addScheduleSync { }
            addPinEntityToStorageSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                // Wait for Room to populate observable PIN LiveData
                waitDatabasesUntilIdle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnScheduleDeleted()
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(not(nullValue())))
                        assertThat(mSelectedSchedule, `is`(not(nullValue())))
                    }
                }
                with(shadowOf(mApplicationContext as Application).nextStartedActivityForResult) {
                    assertThat(this, `is`(not(nullValue())))
                    assertThat(intent.component, `is`(ComponentName(mApplicationContext,
                        AuthenticationPromptActivity::class.java)))
                    // Assume authentication succeeded
                    PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime())
                    scenario.onActivity {
                        it.dispatchOkResult(requestCode, intent)
                    }
                }
                scenario.onActivity {
                    with(it.fragment) {
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(nullValue()))
                        assertThat(mSelectedSchedule, `is`(nullValue()))
                    }
                }

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule removal
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }
    }

    /** Test the schedule's arrow view in expanded/collapsed states. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ArrowView: Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should toggle schedule expansion state when clicked`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertFalse(isExpanded(schedule.id))
                        onArrowView().perform(click())
                        assertTrue(isExpanded(schedule.id))
                        onArrowView().perform(click())
                        assertFalse(isExpanded(schedule.id))
                    }
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should display the schedule expansion state`() {
            addScheduleSync { }
            onActivity {
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Capture initial state
                onArrowView().captureRoboImage()
                onArrowView().perform(click())
                // Capture expanded state
                onArrowView().captureRoboImage()
                onArrowView().perform(click())
                // Capture collapsed state
                onArrowView().captureRoboImage()
            }
        }

        private companion object {
            fun onArrowView(): ViewInteraction =
                onView(both(withId(R.id.arrow_container))
                    .and(isDescendantOfA(isAssignableFrom(RecyclerView::class.java))))
        }
    }

    /** Test sorting of the schedule list by the {@link ItemAdapter}. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class Sorting : Base() {
        @Test
        fun `test should sort in ascending order by the days of the week first`() {
            val scheduleIdAtTue = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(TUESDAY))
            }.id
            val scheduleIdAtMon = addScheduleSync { schedule ->
                schedule.daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(MONDAY))
            }.id
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment.mItemAdapter) {
                        assertThat(arrayOf(
                            getPosition(scheduleIdAtMon),
                            getPosition(scheduleIdAtTue)
                        ), `is`(arrayContaining(0, 1)))
                    }
                }
            }
        }

        @Test
        fun `test should sort in ascending order by the time after the days of the week`() {
            val scheduleIdAtMonNoon = addScheduleSync { schedule ->
                with(schedule) {
                    daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(MONDAY))
                    time = LocalTime.NOON
                }
            }.id
            val scheduleIdAtMonMidnight = addScheduleSync { schedule ->
                with(schedule) {
                    daysOfWeek = mDaysOfWeekFactory.create(*arrayOf(MONDAY))
                    time = LocalTime.MIDNIGHT
                }
            }.id
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment.mItemAdapter) {
                        assertThat(arrayOf(
                            getPosition(scheduleIdAtMonMidnight),
                            getPosition(scheduleIdAtMonNoon)
                        ), `is`(arrayContaining(0, 1)))
                    }
                }
            }
        }

        @Test
        fun `test should sort in descending order by ID after days of week and time`() {
            val scheduleId1 = addScheduleSync { }.id
            val scheduleId2 = addScheduleSync { }.id
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    with(it.fragment.mItemAdapter) {
                        assertThat(scheduleId2, `is`(greaterThan(scheduleId1)))
                        assertThat(arrayOf(
                            getPosition(scheduleId2),
                            getPosition(scheduleId1)
                        ), `is`(arrayContaining(0, 1)))
                    }
                }
            }
        }
    }

    /** Test the authentication result handling after the AuthenticationPromptActivity is dismissed. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class AuthenticationHandling: Base() {
        @Test
        fun `test should throw What A Terrible Failure for succeeded unhandled actions`() {
            onActivity { scenario ->
                scenario.onActivity {
                    it.fragment.onAuthResult(ActivityResult(android.app.Activity.RESULT_OK,
                        Intent("dummy")))
                }
                assertThat(ShadowLog.getLogs(), containsLogEntryWithUnhandledAction("dummy"))
            }
        }

        @Test
        fun `test should refresh schedule when was unsuccessful`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    it.fragment.setSelectedSchedule(schedule.id)
                    var refreshCalled = 0
                    val itemHolder = it.fragment.mItemAdapter.findItemById(schedule.id)
                    itemHolder.addOnItemChangedListener(object : ItemAdapter.OnItemChangedListener {
                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>) {
                            // Should not use this API, as it triggers changing animations on rebind
                            fail()
                        }

                        override fun onItemChanged(holder: ItemAdapter.ItemHolder<*>, payload: Any?) {
                            refreshCalled++
                        }
                    })
                    it.fragment.onAuthResult(ActivityResult(android.app.Activity.RESULT_CANCELED,
                        Intent("dummy")))
                    assertThat(refreshCalled, `is`(1))
                }
                // Also ensure we never fall through after handling an unsuccessful action
                assertThat(ShadowLog.getLogs(), not(containsLogEntryWithUnhandledAction("dummy")))
            }
        }

        @Test
        fun `test should no-op when was unsuccessful`() {
            onActivity { scenario ->
                scenario.onActivity {
                    with(it.fragment) {
                        // Assume the user is not modifying a schedule but is adding a PIN that
                        // also requires authentication
                        setSelectedSchedule(null)
                        // The test should just pass when continuing without a selected schedule
                        onAuthResult(ActivityResult(android.app.Activity.RESULT_CANCELED,
                            Intent("dummy")))
                    }
                }
                // Also ensure we never fall through after handling an unsuccessful action
                assertThat(ShadowLog.getLogs(), not(containsLogEntryWithUnhandledAction("dummy")))
            }
        }

        private companion object {
            fun containsLogEntryWithUnhandledAction(action: String = ""):
                Matcher<Iterable<ShadowLog.LogItem>> = containsInRelativeOrder(allOf(
                    withTag(endsWith(SchedulerFragment::class.java.simpleName)),
                    withType(Log.ASSERT),
                    withMsg(stringContainsInOrder("unhandled action", action))))
        }
    }

    /** Test the auto-scrolling to a schedule feature. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class AutoScrollingToSchedule : Base() {
        private val mSchedules = arrayOfNulls<SubscriptionScheduleEntity>(10)

        override fun setUp() {
            super.setUp()

            repeat(10) { i ->
                mSchedules[i] = addScheduleSync { schedule ->
                    schedule.time = LocalTime.NOON.plusMinutes(i.toLong())
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should auto-scroll to the newly added schedule`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.viewModel!!) {
                    handleOnTimePicked(mSchedules.last()!!.time.plusMinutes(1).toString())
                    waitActivityWorkerThreadUntilIdle()
                    shadowOf(Looper.getMainLooper()).idle()
                    onView(`is`(it.fragment.findScheduleViewByPosition(mSchedules.lastIndex + 1)))
                        .check(isFullyDisplayed())
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should auto-scroll to where the schedule is moving`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            // Set the last schedule time to match the first schedule time to trigger item
            // re-arrangement
            scenario.onActivity {
                with(it.fragment) {
                    setSelectedSchedule(mSchedules.last()!!.id)
                    val scheduleView = it.fragment.findScheduleViewByPosition(mSchedules.lastIndex)
                    onView(`is`(scheduleView)).check(matches(not(isDisplayed())))
                    handleOnTimePicked(mSchedules.first()!!.time.toString())
                    waitActivityWorkerThreadUntilIdle()
                    shadowOf(Looper.getMainLooper()).idle()
                    onView(`is`(scheduleView)).check(isFullyDisplayed())
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should auto-scroll to the expanded schedule`() = onActivity { scenario ->
            // Ensure ViewModel finished updating UI
            waitActivityWorkerThreadUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity {
                with(it.fragment) {
                    onView(`is`(findScheduleViewByPosition(mSchedules.lastIndex)))
                        .check(matches(not(isDisplayed())))
                    expand(mSchedules.last()!!.id)
                    shadowOf(Looper.getMainLooper()).idle()
                    onView(`is`(findScheduleViewByPosition(mSchedules.lastIndex)))
                        .check(isFullyDisplayed())
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should auto-scroll to the last expanded schedule after activity recreated`() {
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        onView(`is`(findScheduleViewByPosition(mSchedules.lastIndex)))
                            .check(matches(not(isDisplayed())))
                        expand(mSchedules.last()!!.id)
                        shadowOf(Looper.getMainLooper()).idle()
                        onView(`is`(it.fragment.findScheduleViewByPosition(mSchedules.lastIndex)))
                            .check(isFullyDisplayed())
                    }
                }
                with(scenario) {
                    recreate()
                    shadowOf(Looper.getMainLooper()).idle()
                    onActivity {
                        onView(`is`(it.fragment.findScheduleViewByPosition(mSchedules.lastIndex)))
                            .check(isFullyDisplayed())
                    }
                }
            }
        }

        @Test
        @Config(minSdk = Q)
        fun `test should auto-scroll to the last expanded schedule instead of the newly added schedule after activity recreated`() {
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    // Add the 11th schedule
                    with(it.viewModel!!) {
                        handleOnTimePicked(mSchedules.last()!!.time.plusMinutes(1).toString())
                        waitActivityWorkerThreadUntilIdle()
                        shadowOf(Looper.getMainLooper()).idle()
                        onView(`is`(it.fragment.findScheduleViewByPosition(mSchedules.lastIndex + 1)))
                            .check(isFullyDisplayed())
                    }
                    // Expand the 5th schedule instead
                    with(it.fragment) {
                        onView(`is`(findScheduleViewByPosition(4)))
                            .check(matches(not(isDisplayed())))
                        expand(mSchedules[4]!!.id)
                        shadowOf(Looper.getMainLooper()).idle()
                        onView(`is`(it.fragment.findScheduleViewByPosition(4)))
                            .check(isFullyDisplayed())
                    }
                }
                // Verify auto-scrolled to the 5th schedule after activity recreated
                with(scenario) {
                    recreate()
                    shadowOf(Looper.getMainLooper()).idle()
                    onActivity {
                        onView(`is`(it.fragment.findScheduleViewByPosition(4)))
                            .check(isFullyDisplayed())
                    }
                }
            }
        }

        private companion object {
            fun isFullyDisplayed(): ViewAssertion = matches(isDisplayingAtLeast(100))
        }
    }

    /** Test internal IntentReceiver. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class IntentReceiver : Base() {
        @Test
        fun `test should notify about unhandled intents`() = onActivity {
            val receiver = findIntentReceiver()!!.broadcastReceiver
            receiver.onReceive(mApplicationContext, Intent("dummy"))
            assertThat(ShadowLog.getLogs(), containsLogEntryWithUnhandledAction("dummy"))
        }

        @Test
        fun `test receiver should be unregistered ONLY when activity is finished`() {
            onActivity { scenario ->
                assertThat(findIntentReceiver(), `is`(not(nullValue())))
                scenario.moveToState(Lifecycle.State.STARTED) // <- will invoke Activity.onPause()
                assertThat(findIntentReceiver(), `is`(not(nullValue())))
            }
            assertThat(findIntentReceiver(), `is`(nullValue()))
        }

        @Test
        fun `test should handle all declared intent filter actions`() = onActivity {
            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            mApplicationContext.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(ShadowLog.getLogs(), `is`(not(containsLogEntryWithUnhandledAction())))
        }

        private fun findIntentReceiver(): ShadowApplication.Wrapper? =
            shadowOf(mApplicationContext as Application).registeredReceivers.find { wrapper ->
                wrapper.broadcastReceiver::class == SchedulerViewModel.IntentReceiver::class
            }

        private companion object {
            fun containsLogEntryWithUnhandledAction(action: String = ""):
                Matcher<Iterable<ShadowLog.LogItem>> = containsInRelativeOrder(allOf(
                    withTag(endsWith(SchedulerViewModel::class.java.simpleName)),
                    withType(Log.ERROR),
                    withMsg(stringContainsInOrder("Unhandled action", action))))
        }
    }

    /** Test management of the schedule list. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class ScheduleListManagement : Base() {
        @Test
        @Config(minSdk = Q)
        fun `test should collapse prior schedule before expanding a new one`() {
            val schedule1 = addScheduleSync { }
            val schedule2 = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertFalse(isExpanded(schedule1.id))
                        expand(schedule1.id)
                        assertTrue(isExpanded(schedule1.id))

                        assertFalse(isExpanded(schedule2.id))
                        expand(schedule2.id)
                        assertTrue(isExpanded(schedule2.id))
                    }
                }
            }
        }

        @Test
        fun `test should defer list update when currently animating`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                // Pause the choreographer before starting the expansion animation
                ShadowChoreographer.setPaused(true)
                scenario.onActivity {
                    with(it.fragment) {
                        val animator = listView.itemAnimator!!
                        assertFalse(animator.isRunning())
                        expand(schedule.id)
                        // Run one frame to start the animation
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
                        assertTrue(animator.isRunning())
                    }

                    // Add another schedule
                    it.viewModel!!.handleOnTimePicked(LocalTime.NOON.toString())
                    waitActivityWorkerThreadUntilIdle()
                    shadowOf(Looper.getMainLooper()).idle()

                    with(it.fragment) {
                        assertThat(mItemAdapter.itemCount, `is`(1))
                        assertTrue(listView.itemAnimator!!.isRunning())

                        // Resume the choreographer and wait for another 249ms to reach 250ms
                        // (RecyclerView's default animation change duration), in order to finish
                        // the expansion animation and run all deferred tasks simultaneously
                        ShadowChoreographer.setPaused(false)
                        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(249))
                        assertFalse(listView.itemAnimator!!.isRunning())
                        assertThat(mItemAdapter.itemCount, `is`(2))
                    }
                }
            }
        }

        @Test
        @Config(shadows = [ShadowRecyclerView::class])
        fun `test should defer list update when computing layout`() {
            addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        assertFalse(listView.isComputingLayout())
                        (shadowOf(listView) as ShadowRecyclerView).setIsComputingLayout(true)
                        assertTrue(listView.isComputingLayout())
                    }

                    // Add another schedule
                    it.viewModel!!.handleOnTimePicked(LocalTime.NOON.toString())
                    waitActivityWorkerThreadUntilIdle()
                    shadowOf(Looper.getMainLooper()).runOneTask() // <- runs LiveData observer only

                    with(it.fragment) {
                        assertThat(mItemAdapter.itemCount, `is`(1))
                        // Reset the RecyclerView to use the real isComputingLayout implementation
                        // (it should not compute the layout at this stage), and run all deferred
                        // tasks simultaneously
                        (shadowOf(listView) as ShadowRecyclerView).setIsComputingLayout(null)
                        assertFalse(listView.isComputingLayout())
                        shadowOf(Looper.getMainLooper()).idle()
                        assertThat(mItemAdapter.itemCount, `is`(2))
                    }
                }
            }
        }

        @Test
        fun `test should reserve space at the bottom ONLY when in portrait mode`() {
            onActivity { scenario ->
                val fabHeight = mApplicationContext.resources
                    .getDimensionPixelSize(R.dimen.fab_height)
                scenario.onActivity {
                    assertThat(it.fragment.listView.paddingBottom, `is`(fabHeight))
                }
                RuntimeEnvironment.setQualifiers("+land")
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                scenario.onActivity {
                    assertThat(it.fragment.listView.paddingBottom, `is`(not(fabHeight)))
                }
            }
        }
    }

    /** Test for crashes or anomalies in different configurations. */
    @HiltAndroidTest
    @RunWith(RobolectricTestRunner::class)
    class Anomalies : Base() {
        @Test
        fun `test should be successfully recreated after removing a schedule`() {
            val schedule = addScheduleSync { }
            onActivity { scenario ->
                // Ensure ViewModel finished updating UI
                waitActivityWorkerThreadUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()

                scenario.onActivity {
                    with(it.fragment) {
                        setSelectedSchedule(schedule.id)
                        handleOnScheduleDeleted()
                        assertThat(mItemAdapter.findItemById(schedule.id), `is`(nullValue()))
                    }
                }
                scenario.recreate()

                // Wait for the ViewModel to complete before exiting the test, otherwise the
                // database will be closed too early while there's an async schedule removal
                // request, that still interacts with it
                waitActivityWorkerThreadUntilIdle()
            }
        }
    }
}

private val MONDAY_11_50_55PM = LocalDateTime.of(2007, 1, 1, 23, 50, 55)

/** Shortcut to launch this activity. */
private fun onActivity(
    subscription: Subscription? = Subscription().apply {
        id = 1
        simName = "SIM 1"
    },
    appBarExpanded: Boolean? = null,
    block: (ActivityScenario<SchedulerActivity>) -> Unit
) = ActivityLauncher<SchedulerActivity>(Intent().apply {
        putExtra(SchedulerActivity.EXTRA_SUBSCRIPTION, subscription)
    }, appBarExpanded, block)

private val SUBSCRIPTIONS_CHANGED_DEBOUNCE_DURATION = Duration.ofMillis(300)

/** Creates a {@link ViewInteraction} that pre-scrolls to a matching view. */
private fun onViewPrescrolled(matcher: Matcher<View>): ViewInteraction =
    onView(matcher).perform(scrollTo())

private val SCROLL_BAR_FADE_DURATION =
    Duration.ofMillis(ViewConfiguration.getScrollBarFadeDuration().toLong())

private val activityWorkerLooper
    get() = SchedulerActivity.sHandler?.looper

private fun waitActivityWorkerThreadUntilIdle() =
    checkNotNull(activityWorkerLooper) {
        "Access activity's worker only in ActivityScenario.launch(...) { } context."
    }.run {
        waitWorkerThreadLooperUntilIdle(this)
    }

private fun <T> LiveData<T>.mutate() = this as MutableLiveData<T>

private fun SystemTimeProvider.mutate() = this as SystemTimeProviderFakeImpl

private val SchedulerActivity.fragment
    get() = supportFragmentManager.fragments
        .first { it is SchedulerFragment }
        .let { it as SchedulerFragment }

private fun SchedulerFragment.isExpanded(scheduleId: Long) =
    mItemAdapter.findItemById(scheduleId).isExpanded()

private fun SchedulerFragment.setSelectedSchedule(id: Long?) {
    mSelectedSchedule = id.takeIf { it != null }?.let {
        // NOTE: we *must* reference the item from the adapter, so that the changes are reflected
        mItemAdapter.findItemById(it).item
    }
}

private fun SchedulerFragment.findScheduleViewByPosition(position: Int): View? =
    listView.findViewHolderForLayoutPosition(position)?.itemView

private fun SchedulerFragment.expand(scheduleId: Long) {
    mItemAdapter.findItemById(scheduleId).expand()
}

private fun SchedulerFragment.collapse(scheduleId: Long) {
    mItemAdapter.findItemById(scheduleId).collapse()
}

private val SchedulerActivity.editTextDialogFragment
    get() = supportFragmentManager
        .findFragmentByTag(EditTextDialogFragment.TAG) as? EditTextDialogFragment

private val SchedulerActivity.timePickerDialogFragment
    get() = supportFragmentManager
        .findFragmentByTag(TimePickerDialogFragment.TAG) as? TimePickerDialogFragment

private fun SchedulerActivity.dispatchOkResult(requestCode: Int, data: Intent) =
    activityResultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, data)

open class Base : RoborazziTestBase() {
    @Inject
    lateinit var mPinStorage: PinStorage

    @Inject
    lateinit var mSystemTimeProvider: SystemTimeProvider

    @Inject
    lateinit var mKeyguardManager: KeyguardManager

    @Inject
    lateinit var mDaysOfWeekFactory: DaysOfWeek.Factory

    @Inject
    lateinit var mAppDatabaseDE: AppDatabaseDE

    /** Synchronously add a PinEntity to the PinStorage. */
    internal fun addPinEntityToStorageSync(beforeEncrypt: (PinEntity) -> Unit): PinEntity {
        return runBlocking {
            val job = async(Dispatchers.Default) {
                val pinEntity = PinEntity().apply {
                    subscriptionId = 1
                    setClearPin("1234")
                }
                beforeEncrypt(pinEntity)
                mPinStorage.encrypt(pinEntity)
                mPinStorage.storePin(pinEntity)
                return@async pinEntity
            }
            withTimeout(5.seconds) { job.await() }
        }
    }

    /** Synchronously add a SubscriptionScheduleEntity to the database */
    internal fun addScheduleSync(beforeInsert: (SubscriptionScheduleEntity) -> Unit) = runBlocking {
        val schedule = SubscriptionScheduleEntity().apply {
            subscriptionId = 1
            time = LocalTime.NOON
            daysOfWeek = mDaysOfWeekFactory.create()
        }
        beforeInsert(schedule)
        val insertJob = async(Dispatchers.Default) {
            mAppDatabaseDE.subscriptionSchedulerDao().insert(schedule)
        }
        schedule.id = withTimeout(5.seconds) { insertJob.await() }
        return@runBlocking schedule
    }

    @After
    fun resetPinStorage() {
        PinStorage.setLastKeystoreAuthTimestamp(0)
    }
}
