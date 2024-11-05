package com.github.iusmac.sevensim.test

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.View

import androidx.preference.Preference
import androidx.preference.PreferenceGroup.PreferencePositionCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom

import com.github.iusmac.sevensim.ui.components.CollapsingToolbarBaseActivity

import com.github.takahirom.roborazzi.captureRoboImage

import java.time.Duration
import java.util.Locale

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
    appBarExpanded: Boolean = false,
    block: (ActivityScenario<A>) -> Unit,
) = ActivityScenario.launch(A::class.java).use { scenario ->
        scenario.onActivity {
            it.setAppBarExpanded(appBarExpanded)
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
): BoundedDiagnosingMatcher<View, View>(View::class.java) {
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
    }

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
            mView = rv.layoutManager!!.findViewByPosition(pos)
        } else {
            mismatchDescription
                .appendValue(adapter)
                .appendText(" not containing a Preference with key: ")
                .appendValue(key)
        }
        return false // maybe found the Preference-View pair, but still ignore this RecyclerView
    }
}
