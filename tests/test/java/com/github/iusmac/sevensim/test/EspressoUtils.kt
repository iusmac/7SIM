package com.github.iusmac.sevensim.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import android.widget.TimePicker

import androidx.preference.Preference
import androidx.preference.PreferenceGroup.PreferencePositionCallback
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation

import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity

import com.github.takahirom.roborazzi.captureRoboImage

import java.time.Duration
import java.time.LocalTime
import java.util.Locale

import junit.framework.AssertionFailedError

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*

import org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks

/**
 * Shortcut to control the AppBarLayout expansion with no animations for any Activity that is a
 * descendant of {@link CollapsingToolbarBaseActivity}.
 */
fun Activity.setAppBarExpanded(expanded: Boolean) =
    (this as? CollapsingToolbarBaseActivity)?.getAppBarLayout()?.apply {
        setExpanded(expanded, /*animate=*/ false)
    }

/** Shortcut to launch an activity scenario. */
inline fun <reified A : Activity> ActivityLauncher(
    appBarExpanded: Boolean? = null,
    block: (ActivityScenario<A>) -> Unit,
) = ActivityLauncher<A>(Intent(), appBarExpanded, block)

/** Shortcut to launch an activity scenario. */
inline fun <reified A : Activity> ActivityLauncher(
    startActivityIntent: Intent = Intent(),
    appBarExpanded: Boolean? = null,
    block: (ActivityScenario<A>) -> Unit,
) = ActivityScenario.launch<A>(Intent(startActivityIntent).apply {
        setClass(getInstrumentation().getTargetContext(), A::class.java)
    }).use { scenario ->
        scenario.onActivity {
            appBarExpanded?.run {
                it.setAppBarExpanded(this)
            }
        }
        block(scenario)
    }

/**
 * Capture an image of the desired view with Roborazzi after playing all delayed UI tasks.
 *
 * @param idleFor The amount of time to idle before capture. NOTE: even if you pass Duration.ZERO,
 * *ALL* delayed UI tasks will still be executed with this call. Use this parameter to compute the
 * interpolated keyframes based on time of an animation.
 */
fun ViewInteraction.captureRoboImage(
    idleFor: Duration,
) = perform(ImageCaptureViewAction(idleFor))

/** Like {@link ViewInteraction#captureRoboImage(Duration)}, but as a view action. */
fun captureRoboImage(idleFor: Duration = Duration.ZERO): ViewAction =
    ImageCaptureViewAction(idleFor)

private class ImageCaptureViewAction(
    val idleFor: Duration,
): ViewAction {
    override fun getConstraints(): Matcher<View> = any(View::class.java)

    override fun getDescription(): String = String.format(Locale.ROOT,
        "capture view to image with Roborazzi")

    override fun perform(uiController: UiController, view: View) {
        runUiThreadTasksIncludingDelayedTasks()

        if (!idleFor.isZero) {
            uiController.loopMainThreadForAtLeast(idleFor.toMillis())
            // Make a full screen redraw starting from the view root to update animations
            view.viewRootImpl.view.updateDisplayListIfDirty()
        }

        view.captureRoboImage()
    }
}

/**
 * Returns a view matcher that matches a view representing a {@link Preference}.
 *
 * @param key The string key to match with the {@link Preference#getKey}.
 */
fun withPreferenceKey(key: String): WithPreferenceKeyMatcher = WithPreferenceKeyMatcher(key)

/**
 * Returns a view matcher that matches a view representing a {@link Preference}.
 *
 * @param resId The resource ID of the string to match with the {@link Preference#getKey}.
 */
fun withPreferenceKey(resId: Int): WithPreferenceKeyMatcher = WithPreferenceKeyMatcher(resId)

class WithPreferenceKeyMatcher private constructor(
    val mKeyResId: Int,
    val mKey: String?,
    val mStride: Int = 0,
): BoundedDiagnosingMatcher<View, View>(View::class.java), StrideableViewMatcher {
    private var mContext: Context? = null
    private var mView: View? = null

    constructor(keyResId: Int) : this(keyResId, null)
    constructor(key: String) : this(Resources.ID_NULL, key)

    override fun describeMoreTo(description: Description) {
        description.appendText("representing a Preference with ")
        if (mKey == null) {
            if (mContext == null) {
                description.appendText("ID: ").appendValue(mKeyResId)
            } else {
                description.appendText("key: ").appendValue(mContext!!.getString(mKeyResId))
            }
        } else {
            description.appendText("key: ").appendValue(mKey)
        }
        if (mStride != 0) {
            description.appendText(" and with stride for sibling traversal of ").appendValue(mStride)
        }
    }

    override fun strideSiblings(stride: Int): StrideableViewMatcher =
        WithPreferenceKeyMatcher(mKeyResId, mKey, stride)

    override protected fun matchesSafely(
        view: View,
        mismatchDescription: Description
    ): Boolean {
        mContext = view.context
        if (mView != null) {
            return (mView == view).also { if (it) mView = null }
        }
        if (!isAssignableFrom(RecyclerView::class.java).matches(view)) {
            mismatchDescription
                .appendText("Not assignable from ")
                .appendValue(RecyclerView::class.java)
            return false
        }
        val rv = view as RecyclerView
        val adapter = when (rv.adapter) {
            is PreferencePositionCallback -> rv.adapter
            is ConcatAdapter -> (rv.adapter as ConcatAdapter).adapters.find {
                return@find (it is PreferencePositionCallback).also { isPreferenceAdapter ->
                    if (!isPreferenceAdapter) {
                        mismatchDescription
                            .appendValue(it)
                            .appendText(" must implement ")
                            .appendValue(PreferencePositionCallback::class.java)
                            .appendText("\n")
                    }
                }
            }.also {
                if (it == null) {
                    mismatchDescription
                        .appendText("ConcatAdapter not containing adapters implementing ")
                        .appendValue(PreferencePositionCallback::class.java)
                    return false
                }
            }
            null -> {
                mismatchDescription
                    .appendValue(RecyclerView.Adapter::class.java)
                    .appendText(" was set to null")
                return false
            }
            else -> {
                mismatchDescription
                    .appendValue(rv.adapter)
                    .appendText(" must implement ")
                    .appendValue(PreferencePositionCallback::class.java)
                return false
            }
        }
        val key = mKey ?: mContext!!.getString(mKeyResId)
        val pos = (adapter as PreferencePositionCallback).getPreferenceAdapterPosition(key)
        if (pos != RecyclerView.NO_POSITION) {
            mView = rv.layoutManager!!.findViewByPosition(pos + mStride)
        } else {
            mismatchDescription
                .appendValue(adapter)
                .appendText(" not containing a Preference with key: ")
                .appendValue(key)
        }
        return false // maybe found the Preference-View pair, but still ignore this RecyclerView
    }
}

/**
 * A matcher for views that allows specifying a stride to control the direction and steps when
 * matching sibling views.
 */
interface StrideableViewMatcher : Matcher<View> {
    /**
     * @param stride The stride value to use for sibling traversal. Positive values indicate forward
     * traversal, and negative values indicate backward traversal.
     * @return A new instance of this matcher configured with the specified stride.
     */
    fun strideSiblings(stride: Int): StrideableViewMatcher
}

/** Returns an action that performs action on a single child view satisfying the given matcher. */
fun actionOnChild(
    childMatcher: Matcher<View>,
    viewAction: ViewAction,
) = ActionOnChildViewAction(childMatcher, viewAction)

class ActionOnChildViewAction(
    val childMatcher: Matcher<View>,
    val viewAction: ViewAction,
): ViewAction {
    override fun getConstraints(): Matcher<View> = any(View::class.java)

    override fun getDescription(): String = String.format(Locale.ROOT,
        "%s on child matching: %s", viewAction.description, childMatcher)

    override fun perform(uiController: UiController, parent: View) {
        val childs = arrayListOf<View>()
        for (view in TreeIterables.breadthFirstViewTraversal(parent)) {
            if (childMatcher.matches(view)) {
                childs += view
            }
        }
        try {
            if (childs.isEmpty()) {
                throw RuntimeException(
                    String.format("No child view found matching: %s", childMatcher))
            }
            if (childs.size > 1) {
                val ambiguousViewError = StringBuilder()
                ambiguousViewError.append(
                    String.format("Found more than one sub-view matching: %s\n", childMatcher))
                childs.forEach {
                    ambiguousViewError.append("$it\n")
                }
                throw RuntimeException(ambiguousViewError.toString())
            }
            viewAction.perform(uiController, childs[0])
        } catch (e: RuntimeException) {
            throw PerformException.Builder()
                .withActionDescription(this.description)
                .withViewDescription(HumanReadables.describe(parent))
                .withCause(e)
                .build()
        }
    }
}

/**
 * Returns a {@link ViewAssertion} that asserts that the view is a {@link TimePicker} showing the
 * given wall clock time.
 */
fun showsTime(wantedTime: LocalTime): ViewAssertion = ShowsTimeAssertion(wantedTime)

private class ShowsTimeAssertion(val wantedTime: LocalTime): ViewAssertion {
    override fun check(view: View, noViewException: NoMatchingViewException?) {
        if (noViewException != null) {
            throw noViewException
        }
        assertThat(view, isAssignableFrom(TimePicker::class.java))
        val showingTime = (view as TimePicker).let {
            LocalTime.of(it.hour, it.minute)
        }
        assertThat(showingTime, `is`(wantedTime))
    }
}
