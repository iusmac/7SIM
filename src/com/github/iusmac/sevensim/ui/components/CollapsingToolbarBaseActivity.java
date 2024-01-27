package com.github.iusmac.sevensim.ui.components;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.View;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;

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
public class CollapsingToolbarBaseActivity extends FragmentActivity {
    private static final int SCRIM_ANIMATION_DURATION = 250;

    private CollapsingToolbarDelegate mToolbardelegate;
    private ToolbarDecorator mToolbarDecorator;

    @Override
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View view = getToolbarDelegate().onCreateView(getLayoutInflater(), null);
        super.setContentView(view);

        final ToolbarDecorator toolbarDecorator = getToolbarDecorator();
        if (toolbarDecorator.isCollapsingToolbarSupported()) {
            // Override the default AOSP's value of 50ms, which is too short and makes the scrim
            // flicker on <60Hz displays
            getCollapsingToolbarLayout().setScrimAnimationDuration(SCRIM_ANIMATION_DURATION);
        } else {
            // For better UX (e.g. l10n), apply the marquee effect on the title for non-collapsing
            // Toolbar
            if (!toolbarDecorator.getTitleMarqueeRepeatLimit().isPresent()) {
                toolbarDecorator.setTitleMarqueeRepeatLimit(1);
            }
        }
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
