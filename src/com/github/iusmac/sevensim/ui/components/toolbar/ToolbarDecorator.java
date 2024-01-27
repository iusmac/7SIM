package com.github.iusmac.sevensim.ui.components.toolbar;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.ui.UiUtils;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * This class aims to decorate the framework's {@link Toolbar}. For instance, you can apply the
 * marquee effect for the {@link Toolbar}'s title/subtitle, or, if there's support for collapsing
 * toolbar, you can decorate it with a collapsing subtitle, which isn't supported out of the box.
 */
public final class ToolbarDecorator {
    private static final float FADE_MODE_THRESHOLD_FRACTION_RELATIVE = 0.5f;
    private static final Comparator<TextView> VIEW_TOP_COMPARATOR =
        (v1, v2) -> v1.getTop() - v2.getTop();

    private final boolean mIsRtl;
    private AppBarLayout mAppBarLayout;
    private CollapsingToolbarLayout mCollapsingToolbarLayout;
    private AppBarLayout.OnOffsetChangedListener mOffsetChangedListener;
    private Optional<TextView> mToolbarTitleTextView = Optional.empty();
    private Optional<TextView> mToolbarSubtitleTextView = Optional.empty();
    private OptionalInt mTitleMarqueeRepeatLimit = OptionalInt.empty();
    private OptionalInt mSubtitleMarqueeRepeatLimit = OptionalInt.empty();
    private CollapsedSubtitle mCollapsedSubtitle;
    private ExpandedSubtitle mExpandedSubtitle;
    private Optional<View> mDummyView = Optional.empty();

    private final Toolbar mToolbar;

    public ToolbarDecorator(final @NonNull Toolbar toolbar) {
        mToolbar = toolbar;

        mIsRtl = mToolbar.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        for (ViewParent p = mToolbar.getParent(); p != null; p = p.getParent()) {
            if (p instanceof AppBarLayout) {
                mAppBarLayout = (AppBarLayout) p;
            } else if (p instanceof CollapsingToolbarLayout) {
                mCollapsingToolbarLayout = (CollapsingToolbarLayout) p;
            }
        }
    }

    /**
     * @return Whether the framework's {@link Toolbar} widget is wrapped by a
     * {@link CollapsingToolbarLayout} or not.
     */
    public boolean isCollapsingToolbarSupported() {
        return mAppBarLayout != null && mCollapsingToolbarLayout != null;
    }

    /**
     * <p>Set the subtitle for the toolbar.
     *
     * <p>If the {@link Toolbar} is wrapped by a {@link CollapsingToolbarLayout} and the title is
     * enabled, it will decorate the toolbar using a self-created subtitle when applicable,
     * otherwise this method proxies the {@link Toolbar#setSubtitle(CharSequence)}, but also applies
     * the marquee effect, if set via {@link #setSubtitleMarqueeRepeatLimit(int)}.
     *
     * @param subtitle The string to be set as subtitle. Will only update if changed.
     */
    public void setSubtitle(final @Nullable CharSequence subtitle) {
        if (isCollapsingToolbarSupported() && mCollapsingToolbarLayout.isTitleEnabled()) {
            if (!TextUtils.isEmpty(subtitle)) {
                createCollapsingSubtitleIfNeeded();

                final TextView subtitleTextView = (TextView) mCollapsedSubtitle.getCurrentView();
                if (!TextUtils.equals(subtitle, subtitleTextView.getText())) {
                    mCollapsedSubtitle.setText(subtitle);
                    mExpandedSubtitle.setText(subtitle);
                }

                // Listen for vertical scroll in order to collapse/expand the subtitle accordingly
                if (mOffsetChangedListener == null) {
                    mOffsetChangedListener = new OffsetChangedListener();
                }
                mAppBarLayout.addOnOffsetChangedListener(mOffsetChangedListener);
            } else {
                cleanupOffsetChangedListenerAndCollapsingSubtitle();
            }
        } else {
            if (!TextUtils.equals(subtitle, mToolbar.getSubtitle())) {
                // Remove the offset changed listener and collapsing subtitle views if not needed
                cleanupOffsetChangedListenerAndCollapsingSubtitle();

                mToolbar.setSubtitle(subtitle);
                applySubtitleMarqueeRepeatLimitIfNeeded();
            }
        }
    }

    /**
     * <p>Apply the marquee effect for the {@link Toolbar}'s title.
     *
     * <p>The edges are faded by default.
     *
     * <p>Pass 0 as repeat limit to stop the animation.
     *
     * @param repeatLimit The value for {@link TextView#setMarqueeRepeatLimit(int)}.
     */
    public void setTitleMarqueeRepeatLimit(final int repeatLimit) {
        mTitleMarqueeRepeatLimit = OptionalInt.of(repeatLimit);
        applyTitleMarqueeRepeatLimitIfNeeded();
    }

    /**
     * @return An Optional containing the title marquee repeat limit, if any.
     */
    public OptionalInt getTitleMarqueeRepeatLimit() {
        return mTitleMarqueeRepeatLimit;
    }

    /**
     * <p>Apply the marquee effect for the {@link Toolbar}'s subtitle.
     *
     * <p>The edges are faded by default.
     *
     * <p>Pass 0 as repeat limit to stop the animation.
     *
     * @param repeatLimit The value for {@link TextView#setMarqueeRepeatLimit(int)}.
     */
    public void setSubtitleMarqueeRepeatLimit(final int repeatLimit) {
        mSubtitleMarqueeRepeatLimit = OptionalInt.of(repeatLimit);
        applySubtitleMarqueeRepeatLimitIfNeeded();
    }

    /**
     * @return An Optional containing the subtitle marquee repeat limit, if any.
     */
    public OptionalInt getSubtitleMarqueeRepeatLimit() {
        return mSubtitleMarqueeRepeatLimit;
    }

    /**
     * Apply the marquee effect for the {@link Toolbar}'s title if it was set.
     */
    public void applyTitleMarqueeRepeatLimitIfNeeded() {
        mTitleMarqueeRepeatLimit.ifPresent((repeatLimit) ->
                findToolbarTitleTextView().ifPresent((textView) ->
                    UiUtils.setTextViewMarqueeRepeatLimit(textView, repeatLimit)));
    }

    /**
     * Apply the marquee effect for the {@link Toolbar}'s subtitle if it was set.
     */
    public void applySubtitleMarqueeRepeatLimitIfNeeded() {
        mSubtitleMarqueeRepeatLimit.ifPresent((repeatLimit) ->
                findToolbarSubtitleTextView().ifPresent((textView) ->
                    UiUtils.setTextViewMarqueeRepeatLimit(textView, repeatLimit)));
    }

    /**
     * @param mode The mode for {@link View#setImportantForAccessibility(int)}.
     */
    public void setCollapsingSubtitleImportantForAccessibility(final int mode) {
        if (mCollapsedSubtitle != null) {
            mCollapsedSubtitle.setImportantForAccessibility(mode);
        }
        if (mExpandedSubtitle != null) {
            mExpandedSubtitle.setImportantForAccessibility(mode);
        }
    }

    private void cleanupOffsetChangedListenerAndCollapsingSubtitle() {
        if (isCollapsingToolbarSupported()) {
            if (mOffsetChangedListener != null) {
                mAppBarLayout.removeOnOffsetChangedListener(mOffsetChangedListener);
            }
            if (mCollapsedSubtitle != null) {
                mCollapsingToolbarLayout.removeView(mCollapsedSubtitle);
            }
            if (mExpandedSubtitle != null) {
                mCollapsingToolbarLayout.removeView(mExpandedSubtitle);
            }
        }
    }

    private Optional<TextView> findToolbarTitleTextView() {
        if (!mToolbarTitleTextView.filter((tv) -> tv.isAttachedToWindow()).isPresent()) {
            final List<TextView> textViews = findToolbarTextViewsByText(mToolbar.getTitle());
            mToolbarTitleTextView = Optional.ofNullable(textViews.isEmpty() ? null :
                    Collections.min(textViews, VIEW_TOP_COMPARATOR));
        }
        return mToolbarTitleTextView;
    }

    private Optional<TextView> findToolbarSubtitleTextView() {
        if (!mToolbarSubtitleTextView.filter((tv) -> tv.isAttachedToWindow()).isPresent()) {
            final List<TextView> textViews = findToolbarTextViewsByText(mToolbar.getSubtitle());
            mToolbarSubtitleTextView = Optional.ofNullable(textViews.isEmpty() ? null :
                    Collections.max(textViews, VIEW_TOP_COMPARATOR));
        }
        return mToolbarSubtitleTextView;
    }

    private List<TextView> findToolbarTextViewsByText(final @Nullable CharSequence text) {
        final List<TextView> textViews = new ArrayList<>();
        for (int i = 0, z = mToolbar.getChildCount(); i < z; i++) {
            final View child = mToolbar.getChildAt(i);
            if (child instanceof TextView) {
                final TextView textView = (TextView) child;
                if (TextUtils.equals(text, textView.getText())) {
                    textViews.add(textView);
                }
            }
        }
        return textViews;
    }

    private void createCollapsingSubtitleIfNeeded() {
        if (mCollapsedSubtitle == null) {
            mCollapsedSubtitle = new CollapsedSubtitle(mCollapsingToolbarLayout.getContext());
        }
        if (mExpandedSubtitle == null) {
            mExpandedSubtitle = new ExpandedSubtitle(mCollapsingToolbarLayout.getContext());
        }

        // Animate the first view only if the CollapsingToolbarLayout is already rendered, otherwise
        // avoid useless animations on startup/configuration changes
        final boolean shouldAnimateFirstView = mCollapsingToolbarLayout.isLaidOut();

        if (mCollapsedSubtitle.getParent() == null) {
            mCollapsedSubtitle.setAnimateFirstView(shouldAnimateFirstView);
            mCollapsingToolbarLayout.addView(mCollapsedSubtitle, getSubtitleLayoutParams());
        }
        if (mExpandedSubtitle.getParent() == null) {
            // Insert the expanded title at the beginning so it appears to go under the
            // scrim/Toolbar
            final int index = 0;
            mExpandedSubtitle.setAnimateFirstView(shouldAnimateFirstView);
            mCollapsingToolbarLayout.addView(mExpandedSubtitle, index, getSubtitleLayoutParams());
        }
    }

    private CollapsingToolbarLayout.LayoutParams getSubtitleLayoutParams() {
        final CollapsingToolbarLayout.LayoutParams lp = new CollapsingToolbarLayout.LayoutParams(
                CollapsingToolbarLayout.LayoutParams.MATCH_PARENT,
                CollapsingToolbarLayout.LayoutParams.WRAP_CONTENT);
        // Push the subtitle to the bottom of the CollapsingToolbarLayout so it is always below the
        // title
        lp.gravity = Gravity.START | Gravity.BOTTOM;
        return lp;
    }

    /**
     * Find the "dummy view" that {@link CollapsingToolbarLayout} injects into the {@link Toolbar}
     * by its distinct traits. For instance, the "dummy view" is the only {@link Toolbar}'s view
     * that will fill it completely with the aim to create lack of space and force view removal,
     * such as title or subtitle.
     *
     * @see https://github.com/material-components/material-components-android/blob/b80efdd3cb22bd67c512df962f7dc4b4f6466923/lib/java/com/google/android/material/appbar/CollapsingToolbarLayout.java#L109C78-L111C43
     * @see https://github.com/material-components/material-components-android/blob/b597c1e218695b725b6b2cc5dc129dee6f2ee5cf/lib/java/com/google/android/material/appbar/CollapsingToolbarLayout.java#L576
     */
    private void updateToolbarDummyView() {
        if (!mDummyView.filter((v) -> v.isAttachedToWindow()).isPresent()) {
            View view = null;
            for (int i = 0, n = mToolbar.getChildCount(); i < n; i++) {
                final View child = mToolbar.getChildAt(i);
                if (child.getClass() == View.class) {
                    final ViewGroup.LayoutParams lp = child.getLayoutParams();
                    if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                            lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        view = child;
                        break;
                    }
                }
            }
            mDummyView = Optional.ofNullable(view);
        }
    }

    private final class OffsetChangedListener implements AppBarLayout.OnOffsetChangedListener {
        @Override
        public void onOffsetChanged(final AppBarLayout appBarLayout, int verticalOffset) {
            final int expandRange = appBarLayout.getTotalScrollRange();
            final int scrimRange = mCollapsingToolbarLayout.getHeight() -
                mCollapsingToolbarLayout.getScrimVisibleHeightTrigger();
            final float scrollFactor = -verticalOffset / (float) expandRange;
            final float fadeStartFraction = Math.min(1, scrimRange / (float) expandRange);
            final float fadeThresholdFraction = fadeStartFraction + (1 - fadeStartFraction) *
                FADE_MODE_THRESHOLD_FRACTION_RELATIVE;

            // Fade in/out the collapsed subtitle based on vertical offset once threshold is reached
            final float collapsedSubtitleAlpha = Utils.lerp(
                    /*startValue=*/ 0,
                    /*endValue=*/ 1,
                    /*startFraction=*/ fadeThresholdFraction,
                    /*endFraction=*/ 1,
                    scrollFactor);
            mCollapsedSubtitle.setAlpha(collapsedSubtitleAlpha);

            // Anchor the collapsed subtitle at the Toolbar's bottom when scrolling up. This will
            // keep the subtitle always under the title at the same distance
            if (scrollFactor >= fadeThresholdFraction) {
                final int collapseRange = mCollapsingToolbarLayout.getHeight() -
                    appBarLayout.getTotalScrollRange();
                final int anchorOffset = mCollapsedSubtitle.getTop() - (collapseRange -
                        mCollapsedSubtitle.getHeight());
                mCollapsedSubtitle.offsetTopAndBottom(-verticalOffset - anchorOffset);
            }

            // Fade in/out the expanded subtitle based on vertical offset until threshold is reached
            final float expandedSubtitleAlpha = Utils.lerp(
                    /*startValue=*/ 1,
                    /*endValue=*/ 0,
                    /*startFraction=*/ fadeStartFraction,
                    /*endFraction=*/ fadeThresholdFraction,
                    scrollFactor);
            mExpandedSubtitle.setAlpha(expandedSubtitleAlpha);
        }
    }

    private final class CollapsedSubtitle extends Subtitle {
        final int mFrameworkToolbarMarginTop = mToolbar.getTitleMarginTop();
        final int mFrameworkToolbarMarginBottom = mToolbar.getTitleMarginBottom();
        final Runnable mUpdateBoundsRunnable = () -> {
            updateToolbarDummyView();
            // When the "dummy view" is settled up, use its boundaries for the collapsed subtitle
            mDummyView.filter((v) -> v.getBottom() > 0).ifPresent((dummyView) -> {
                final int paddingLeft = dummyView.getLeft() + (mIsRtl ?
                        mToolbar.getTitleMarginEnd() : mToolbar.getTitleMarginStart());
                final int paddingRight = Math.abs(dummyView.getRight() -
                        mCollapsingToolbarLayout.getRight()) + (mIsRtl ?
                        mToolbar.getTitleMarginStart() : mToolbar.getTitleMarginEnd());

                setPadding(paddingLeft, /*top=*/ 0, paddingRight, mFrameworkToolbarMarginTop +
                        mFrameworkToolbarMarginBottom);

                // Since the collapsed title is pushed to the bottom of the CollapsingToolbarLayout,
                // now compute the amount of space on the bottom of the Toolbar's title using
                // subtitle's overall height, which will be used to bound the collapsed title; this
                // effectively places the collapsed title above the subtitle while maintaining the
                // framework's vertical padding values
                final int marginBottom = mFrameworkToolbarMarginTop + getMeasuredHeight() +
                    mFrameworkToolbarMarginBottom;
                if (mToolbar.getTitleMarginBottom() != marginBottom) {
                    mToolbar.setTitleMarginBottom(marginBottom);
                }
            });
        };

        public CollapsedSubtitle(final Context context) {
            super(context);


            setFactory(() -> new SubtitleTextView(context) {
                {
                    setTextAppearance(R.style.TextAppearance_CollapsingToolbarCollapsedSubtitle);
                    setSingleLine();
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();

            // Align the collapsed title with the subtitle. The margins for vertical alignment will
            // be calculated dynamically based on the subtitle's height
            mCollapsingToolbarLayout.setCollapsedTitleGravity(Gravity.START | Gravity.BOTTOM);
            mToolbar.setTitleMarginTop(0);
            mToolbar.setTitleMarginBottom(0);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // Schedule a single update at the end to cover the case when the "dummy view" is
            // already rendered and no more re-measurements will be propagated from the Toolbar
            getHandler().removeCallbacks(mUpdateBoundsRunnable);
            getHandler().postDelayed(mUpdateBoundsRunnable, 0);

            mUpdateBoundsRunnable.run();

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            // Restore vertical alignment to defaults
            mCollapsingToolbarLayout.setCollapsedTitleGravity(Gravity.START |
                    Gravity.CENTER_VERTICAL);
            mToolbar.setTitleMarginTop(mFrameworkToolbarMarginTop);
            mToolbar.setTitleMarginBottom(mFrameworkToolbarMarginBottom);
        }
    }

    private final class ExpandedSubtitle extends Subtitle {
        final int mExpandedTitleMarginBottom =
            mCollapsingToolbarLayout.getExpandedTitleMarginBottom();

        public ExpandedSubtitle(final Context context) {
            super(context);

            final int expandedTitleMarginStart =
                mCollapsingToolbarLayout.getExpandedTitleMarginStart();
            final int expandedTitleMarginEnd = mCollapsingToolbarLayout.getExpandedTitleMarginEnd();
            final int paddingLeft = mIsRtl ? expandedTitleMarginEnd : expandedTitleMarginStart;
            final int paddingRight = mIsRtl ? expandedTitleMarginStart : expandedTitleMarginEnd;
            final int paddingBottom = (int) context.getResources().getDimension(
                    R.dimen.collapsing_toolbar_subtitle_padding_bottom);
            // Align horizontally with the expanded title
            setPadding(paddingLeft, /*top=*/ 0, paddingRight, paddingBottom);

            setFactory(() -> new SubtitleTextView(context) {
                {
                    setTextAppearance(R.style.TextAppearance_CollapsingToolbarExpandedSubtitle);
                    // NOTE: since the subtitle's height is used to calculate the extra space
                    // (margin bottom) under the title, it should be limited to max. 3 lines,
                    // otherwise the title will go under the scrim/Toolbar
                    setMaxLines(3);
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            final int extraMarginBottom = mExpandedTitleMarginBottom + getMeasuredHeight();
            if (mCollapsingToolbarLayout.getExpandedTitleMarginBottom() != extraMarginBottom) {
                // Push the expanded title back as its gravity is set to be at the bottom of the
                // CollapsingToolbarLayout
                mCollapsingToolbarLayout.setExpandedTitleMarginBottom(extraMarginBottom);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            // Remove the extra space reserved before
            if (mCollapsingToolbarLayout.getExpandedTitleMarginBottom() !=
                    mExpandedTitleMarginBottom) {
                mCollapsingToolbarLayout.setExpandedTitleMarginBottom(mExpandedTitleMarginBottom);
            }
        }
    }
}
