package com.github.iusmac.sevensim.ui.components;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.View;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarDelegate;

import com.github.iusmac.sevensim.ui.components.toolbar.ToolbarDecorator;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * <p>A base Activity that has a collapsing toolbar layout is used for the activities intending to
 * enable the collapsing toolbar function.
 *
 * <p>The activity also allows to decorate the framework's {@link Toolbar}. For instance, you can
 * apply the marquee effect for the {@link Toolbar}'s title/subtitle, or, if there's support for
 * collapsing toolbar, you can decorate it with a collapsing subtitle, which isn't supported out of
 * the box.
 */
public abstract class CollapsingToolbarBaseActivity extends FragmentActivity {
    private static final int SCRIM_ANIMATION_DURATION = 250;

    private CollapsingToolbarDelegate mToolbardelegate;
    private ToolbarDecorator mToolbarDecorator;
    private ViewModel mViewModel;

    @Override
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        mViewModel = onCreateViewModel();

        super.onCreate(savedInstanceState);

        final View view = getToolbarDelegate().onCreateView(getLayoutInflater(), null);
        super.setContentView(view);

        final ToolbarDecorator toolbarDecorator = getToolbarDecorator();
        if (toolbarDecorator.isCollapsingToolbarSupported()) {
            // Override the default AOSP's value of 50ms, which is too short and makes the scrim
            // flicker on <60Hz displays
            getCollapsingToolbarLayout().setScrimAnimationDuration(SCRIM_ANIMATION_DURATION);
            // Enforce fade in/out and translate collapse effect for the title so that it's
            // consistent with the subtitle that doesn't support scaling, which may be selected if
            // using non-AOSP sources
            getCollapsingToolbarLayout()
                .setTitleCollapseMode(CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_FADE);
            // Enforce the header content scrim background color so it's always different from the
            // content view background as we display a subtitle text that may fuse visually with
            // other text
            getCollapsingToolbarLayout()
                .setContentScrimResource(com.android.settingslib.widget.theme.R.color.settingslib_colorSurfaceHeader);
        } else {
            // For better UX (e.g. l10n), apply the marquee effect on the title for non-collapsing
            // Toolbar
            if (!toolbarDecorator.getTitleMarqueeRepeatLimit().isPresent()) {
                toolbarDecorator.setTitleMarqueeRepeatLimit(1);
            }
        }
    }

    /**
     * Called when the activity is starting, but before propagating to the parent's
     * {@link #onCreate(Bundle)}.
     *
     * @return The {@link ViewModel} instance, if any.
     */
    public abstract @Nullable ViewModel onCreateViewModel();

    /**
     * @return The {@link ViewModel} instance created via {@link #onCreateViewModel()}.
     */
    public @Nullable ViewModel getViewModel() {
        return mViewModel;
    }

    @Override
    public void setTitle(final @Nullable CharSequence title) {
        getToolbarDelegate().setTitle(title);
        getToolbarDecorator().applyTitleMarqueeRepeatLimitIfNeeded();
    }

    @Override
    public void setTitle(final @StringRes int titleId) {
        setTitle(getText(titleId));
    }

    public void setSubtitle(final @Nullable CharSequence subtitle) {
        getToolbarDecorator().setSubtitle(subtitle);
    }

    public void setSubtitle(final @StringRes int subtitleId) {
        setSubtitle(getText(subtitleId));
    }

    @Override
    public boolean onNavigateUp() {
        if (!super.onNavigateUp()) {
            finishAfterTransition();
        }
        return true;
    }

    /**
     * Return an instance of collapsing toolbar.
     */
    @Nullable
    public CollapsingToolbarLayout getCollapsingToolbarLayout() {
        return getToolbarDelegate().getCollapsingToolbarLayout();
    }

    /**
     * Return an instance of app bar.
     */
    @Nullable
    public AppBarLayout getAppBarLayout() {
        return getToolbarDelegate().getAppBarLayout();
    }

    /**
     * Return an instance of {@link Toolbar} decorator.
     */
    @NonNull
    public ToolbarDecorator getToolbarDecorator() {
        if (mToolbarDecorator == null) {
            mToolbarDecorator = new ToolbarDecorator(getToolbarDelegate().getToolbar());
        }
        return mToolbarDecorator;
    }

    private CollapsingToolbarDelegate getToolbarDelegate() {
        if (mToolbardelegate == null) {
            mToolbardelegate = new CollapsingToolbarDelegate(new DelegateCallback());
        }
        return mToolbardelegate;
    }

    private class DelegateCallback implements CollapsingToolbarDelegate.HostCallback {
        @Nullable
        @Override
        public ActionBar setActionBar(final Toolbar toolbar) {
            CollapsingToolbarBaseActivity.super.setActionBar(toolbar);
            return CollapsingToolbarBaseActivity.super.getActionBar();
        }

        @Override
        public void setOuterTitle(final CharSequence title) {
            CollapsingToolbarBaseActivity.super.setTitle(title);
        }
    }
}
