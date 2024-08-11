/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * GNU GENERAL PUBLIC LICENSE
 * Version 3, 29 June 2007
 *
 * Copyright (C) 2024 Iusico Maxim <iusico.maxim@libero.it>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package com.github.iusmac.sevensim.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Property;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.iusmac.sevensim.R;

import java.util.Locale;

public final class UiUtils {
    public static final Interpolator INTERPOLATOR_FAST_OUT_SLOW_IN =
        new FastOutSlowInInterpolator();

    public static final Property<View, Integer> BACKGROUND_ALPHA =
        new Property<View, Integer>(Integer.class, "background.alpha") {
            @Override
            public Integer get(final View view) {
                Drawable background = view.getBackground();
                if (background instanceof LayerDrawable
                        && ((LayerDrawable) background).getNumberOfLayers() > 0) {
                    background = ((LayerDrawable) background).getDrawable(0);
                }
                return background.getAlpha();
            }

            @Override
            public void set(final View view, final Integer value) {
                setBackgroundAlpha(view, value);
            }
        };

    public static final Property<View, Integer> VIEW_LEFT =
        new Property<View, Integer>(Integer.class, "left") {
            @Override
            public Integer get(final View view) {
                return view.getLeft();
            }

            @Override
            public void set(final View view, final Integer left) {
                view.setLeft(left);
            }
        };

    public static final Property<View, Integer> VIEW_TOP =
        new Property<View, Integer>(Integer.class, "top") {
            @Override
            public Integer get(final View view) {
                return view.getTop();
            }

            @Override
            public void set(final View view, Integer top) {
                view.setTop(top);
            }
        };

    public static final Property<View, Integer> VIEW_BOTTOM =
        new Property<View, Integer>(Integer.class, "bottom") {
            @Override
            public Integer get(final View view) {
                return view.getBottom();
            }

            @Override
            public void set(final View view, Integer bottom) {
                view.setBottom(bottom);
            }
        };

    public static final Property<View, Integer> VIEW_RIGHT =
        new Property<View, Integer>(Integer.class, "right") {
            @Override
            public Integer get(final View view) {
                return view.getRight();
            }

            @Override
            public void set(final View view, Integer right) {
                view.setRight(right);
            }
        };

    /**
     * @param context The {@link Context} to access resources.
     * @param tint The {@link ColorInt} value to apply.
     * @return A tinted {@link Drawable}, cached instance in most cases.
     */
    public static @NonNull Drawable createTintedSimIcon(final @NonNull Context context,
            final @ColorInt int tint) {

        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_sim);
        d = DrawableCompat.wrap(d);
        DrawableCompat.setTint(d.mutate(), tint);
        return d;
    }

    /**
     * <p>Apply marquee effect for a {@link TextView}.
     *
     * <p>Pass 0 as repeat limit to stop the animation.
     *
     * @param outTextView The output {@link TextView}.
     * @param repeatLimit The value for {@link TextView#setMarqueeRepeatLimit(int)}.
     */
    public static void setTextViewMarqueeRepeatLimit(final TextView outTextView,
            final int repeatLimit) {

        outTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        outTextView.setHorizontalFadingEdgeEnabled(true);
        outTextView.setMarqueeRepeatLimit(repeatLimit);
        outTextView.setSelected(repeatLimit != 0);
    }

    /**
     * Sets the alpha of the top layer's drawable (of the background) only, if the background is a
     * layer drawable, to ensure that the other layers (i.e., the selectable item background, and
     * therefore the touch feedback RippleDrawable) are not affected.
     *
     * @param view The affected view.
     * @param value The alpha value (0-255).
     */
    public static void setBackgroundAlpha(final View view, final int value) {
        Drawable background = view.getBackground();
        if (background instanceof LayerDrawable
                && ((LayerDrawable) background).getNumberOfLayers() > 0) {
            background = ((LayerDrawable) background).getDrawable(0);
        }
        background.setAlpha(value);
    }

    /**
     * @param amPmRatio A value between 0 and 1 that is the ratio of the relative size of the am/pm
     * string to the time string.
     * @param includeSeconds Whether or not to include seconds in the time string.
     * @return The format string for 12 hours mode time, not including seconds.
     */
    public static CharSequence get12ModeFormat(float amPmRatio, boolean includeSeconds) {
        return get12ModeFormat(amPmRatio, includeSeconds, true, true);
    }

    /**
     * @param amPmRatio A value between 0 and 1 that is the ratio of the relative size of the am/pm
     * string to the time string.
     * @param includeSeconds Whether or not to include seconds in the time string.
     * @param amPmBolded Whether or not to bold the AM/PM.
     * @param amPmDisplayed  Whether or not to show the AM/PM.
     * @return The Format string for 12 hours mode time, not including seconds.
     */
    public static CharSequence get12ModeFormat(final float amPmRatio, final boolean includeSeconds,
            final boolean amPmBolded, final boolean amPmDisplayed) {

        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                includeSeconds ? "hmsa" : "hma");
        if (amPmRatio <= 0 || amPmDisplayed == false) {
            pattern = pattern.replaceAll("a", "").trim();
        }

        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");
        // Build a spannable so that the am/pm will be formatted
        int amPmPos = pattern.indexOf('a');
        if (amPmPos == -1) {
            return pattern;
        }

        final Spannable sp = new SpannableString(pattern);
        sp.setSpan(new RelativeSizeSpan(amPmRatio), amPmPos, amPmPos + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new StyleSpan(amPmBolded ? Typeface.BOLD : Typeface.NORMAL), amPmPos,
                amPmPos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new TypefaceSpan("sans-serif"), amPmPos, amPmPos + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return sp;
    }

    public static CharSequence get24ModeFormat(boolean includeSeconds) {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(),
                includeSeconds ? "Hms" : "Hm");
    }

    /**
     * Return an animator that animates the bounds of a single view.
     *
     * @param target The view to be morphed.
     * @param from The bounds of the {@code target} before animating.
     * @param to .The bounds of the {@code target} after animating.
     * @return An animator that morphs the {@code target} between the {@code from} bounds and the
     * {@code to} bounds. Note that it is the *content* bounds that matter here, so padding insets
     * contributed by the background are subtracted from the views when computing the {@code target}
     * bounds..
     */
    public static Animator getBoundsAnimator(final View target, final View from, final View to) {
        // Fetch the content insets for the views. Content bounds are what matter, not total bounds.
        final Rect targetInsets = new Rect();
        target.getBackground().getPadding(targetInsets);
        final Rect fromInsets = new Rect();
        from.getBackground().getPadding(fromInsets);
        final Rect toInsets = new Rect();
        to.getBackground().getPadding(toInsets);

        // Before animating, the content bounds of target must match the content bounds of from.
        final int startLeft = from.getLeft() - fromInsets.left + targetInsets.left;
        final int startTop = from.getTop() - fromInsets.top + targetInsets.top;
        final int startRight = from.getRight() - fromInsets.right + targetInsets.right;
        final int startBottom = from.getBottom() - fromInsets.bottom + targetInsets.bottom;

        // After animating, the content bounds of target must match the content bounds of to.
        final int endLeft = to.getLeft() - toInsets.left + targetInsets.left;
        final int endTop = to.getTop() - toInsets.top + targetInsets.top;
        final int endRight = to.getRight() - toInsets.right + targetInsets.right;
        final int endBottom = to.getBottom() - toInsets.bottom + targetInsets.bottom;

        return getBoundsAnimator(target, startLeft, startTop, startRight, startBottom, endLeft,
                endTop, endRight, endBottom);
    }

    /**
     * Return an animator that animates the bounds of a single view.
     */
    public static Animator getBoundsAnimator(final View view, final int fromLeft, final int fromTop,
            final int fromRight, final int fromBottom, final int toLeft, final int toTop,
            final int toRight, final int toBottom) {

        view.setLeft(fromLeft);
        view.setTop(fromTop);
        view.setRight(fromRight);
        view.setBottom(fromBottom);

        return ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofInt(VIEW_LEFT, toLeft),
                PropertyValuesHolder.ofInt(VIEW_TOP, toTop),
                PropertyValuesHolder.ofInt(VIEW_RIGHT, toRight),
                PropertyValuesHolder.ofInt(VIEW_BOTTOM, toBottom));
    }

    /**
     * Get the global position of a {@link ViewHolder} in a {@link ConcatAdapter} using the local
     * position local position of its {@link Adapter}.
     *
     * @return The global position of a {@link ViewHolder} in the given {@link ConcatAdapter},
     * otherwise {@link RecyclerView#NO_POSITION} if the given {@link Adapter} is not part of the
     * {@link ConcatAdapter}.
     */
    public static int getGlobalPosition(final ConcatAdapter concatAdapter,
            final Adapter<? extends ViewHolder> adapter, final int localPosition) {

        int itemsBefore = 0;
        for (final Adapter<? extends ViewHolder> adapterItem : concatAdapter.getAdapters()) {
            if (adapterItem != adapter) {
                itemsBefore += adapterItem.getItemCount();
            } else {
                return itemsBefore + localPosition;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    /**
     * @param context The {@link Context} to access resources.
     * @return Whether the currently the app is displayed in landscape orientation.
     */
    public static boolean isLandscape(final @NonNull Context context) {
        return context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }
}
