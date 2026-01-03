package com.github.iusmac.sevensim.ui.components;

import android.annotation.DrawableRes;
import android.app.ActionBar;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarDelegate;
import com.android.settingslib.collapsingtoolbar.EdgeToEdgeUtils;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.github.iusmac.sevensim.ui.components.toolbar.ToolbarDecorator;
import com.github.iusmac.sevensim.R;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.Optional;

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
    private CollapsingToolbarDelegate mToolbardelegate;
    private ToolbarDecorator mToolbarDecorator;
    private ViewModel mViewModel;
    private Optional<View> mTrailingButtons = Optional.empty();

    @Override
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        mViewModel = onCreateViewModel();

        EdgeToEdgeUtils.enable(this);
        super.onCreate(savedInstanceState);

        getToolbarDelegate().registerToolbarCollapseBehavior(this);

        final boolean isExpressiveTheme = SettingsThemeHelper.isExpressiveTheme(this);
        if (isExpressiveTheme) {
            setTheme(R.style.Theme_SubSettingsBase_Expressive_Custom);
        }

        final View view = getToolbarDelegate().onCreateView(getLayoutInflater(), null);
        super.setContentView(view);

        final ToolbarDecorator toolbarDecorator = getToolbarDecorator();
        if (toolbarDecorator.isCollapsingToolbarSupported()) {
            final int scrimAnimationDuration = getResources().getInteger(isExpressiveTheme ?
                    R.integer.collapsingtoolbar_scrim_anim_duration_expressive
                    : R.integer.collapsingtoolbar_scrim_anim_duration);
            getCollapsingToolbarLayout().setScrimAnimationDuration(scrimAnimationDuration);
            // Enforce fade in/out and translate collapse effect for the title so that it's
            // consistent with the subtitle that doesn't support scaling, which may be selected if
            // using non-AOSP sources
            getCollapsingToolbarLayout()
                .setTitleCollapseMode(CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_FADE);
            // Enforce the header content scrim background color so it's always different from the
            // content view background as we display a subtitle text that may fuse visually with
            // other text
            if (!isExpressiveTheme) {
                getCollapsingToolbarLayout()
                    .setContentScrimResource(com.android.settingslib.widget.theme.R.color.settingslib_colorSurfaceHeader);
            }
            // Override the default AOSP's collapsed state of the AppBarLayout to be expanded upon
            // first launch when expressive theme is enabled
            if (isExpressiveTheme && savedInstanceState == null) {
                getAppBarLayout().setExpanded(true);
            }
            if (isExpressiveTheme) {
                mTrailingButtons = Optional.<View>ofNullable(getToolbarDelegate().getToolbar()
                        // Use action button view to find the trailing buttons parent
                        .findViewById(com.android.settingslib.collapsingtoolbar.R.id.action_button))
                    .map((v) -> (View) v.getParent());
                // Hide the trailing buttons view by default when expressive theme is enabled to
                // avoid the blank space at the end of the Toolbar added in XML, which we can't
                // edit. This will allow for the Toolbar's title & subtitle to fully expand
                setTrailingButtonsEnabled(false);
            }
            // Our use case requires the CollapsingToolbar to be permanently lifted above the
            // scrollable content (safe to disable; less animations, better performance)
            getAppBarLayout().setLiftable(false);
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
     * Return the {@link ViewModel} instance created via {@link #onCreateViewModel()}.
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

    /**
     * Show/Hide the primary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setPrimaryButtonEnabled(final boolean enabled) {
        getToolbarDelegate().setPrimaryButtonEnabled(enabled);
    }

    /** Set the icon to the primary button */
    public void setPrimaryButtonIcon(final @DrawableRes int drawableRes) {
        getToolbarDelegate().setPrimaryButtonIcon(this, drawableRes);
    }

    /** Set the OnClick listener to the primary button. */
    public void setPrimaryButtonOnClickListener(final @Nullable View.OnClickListener listener) {
        getToolbarDelegate().setPrimaryButtonOnClickListener(listener);
    }

    /** Set the content description to the primary button */
    public void setPrimaryButtonContentDescription(final @Nullable CharSequence contentDescription) {
        getToolbarDelegate().setPrimaryButtonContentDescription(contentDescription);
    }

    /**
     * Show/Hide the secondary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setSecondaryButtonEnabled(final boolean enabled) {
        getToolbarDelegate().setSecondaryButtonEnabled(enabled);
    }

    /** Set the icon to the secondary button */
    public void setSecondaryButtonIcon(final @DrawableRes int drawableRes) {
        getToolbarDelegate().setSecondaryButtonIcon(this, drawableRes);
    }

    /** Set the OnClick listener to the secondary button */
    public void setSecondaryButtonOnClickListener(final @Nullable View.OnClickListener listener) {
        getToolbarDelegate().setSecondaryButtonOnClickListener(listener);
    }

    /** Set the content description to the secondary button */
    public void setSecondaryButtonContentDescription(final @Nullable CharSequence contentDescription) {
        getToolbarDelegate().setSecondaryButtonContentDescription(contentDescription);
    }

    /**
     * Show/Hide the action button on the Toolbar.
     *
     * NOTE: the action button is available only in expressive theme since Android 16 (Baklava).
     *
     * @param enabled {@code true} to show the button, otherwise it's hidden.
     */
    public void setActionButtonEnabled(final boolean enabled) {
        getToolbarDelegate().setActionButtonEnabled(enabled);
    }

    /**
     * Enable/Disable the action button on the Toolbar (being clickable or not).
     * @param clickable true to enable the button, otherwise it's disabled.
     */
    public void setActionButtonClickable(final boolean clickable) {
        getToolbarDelegate().setActionButtonClickable(clickable);
    }

    /** Set the icon to the action button */
    public void setActionButtonIcon(final @DrawableRes int drawableRes) {
        getToolbarDelegate().setActionButtonIcon(this, drawableRes);
    }

    /** Set the text to the action button */
    public void setActionButtonText(final @Nullable CharSequence text) {
        getToolbarDelegate().setActionButtonText(text);
    }

    /** Set the OnClick listener to the action button */
    public void setActionButtonListener(final @Nullable View.OnClickListener listener) {
        getToolbarDelegate().setActionButtonOnClickListener(listener);
    }

    /** Set the content description to the action button */
    public void setActionButtonContentDescription(final @Nullable CharSequence contentDescription) {
        getToolbarDelegate().setActionButtonContentDescription(contentDescription);
    }

    /**
     * Show/Hide the Toolbar's trailing buttons (action/primary/secondary) view.
     *
     * @param enabled {@code true} to show the trailing buttons view, otherwise it's hidden.
     */
    public void setTrailingButtonsEnabled(final boolean enabled) {
        mTrailingButtons.ifPresent((v) -> v.setVisibility(enabled ? View.VISIBLE : View.GONE));
    }

    @Override
    public boolean onNavigateUp() {
        if (!super.onNavigateUp()) {
            finishAfterTransition();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
            mToolbardelegate = new CollapsingToolbarDelegate(new DelegateCallback(),
                    /*useCollapsingToolbar=*/ true);
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
