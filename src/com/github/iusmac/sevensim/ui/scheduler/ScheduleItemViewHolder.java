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

package com.github.iusmac.sevensim.ui.scheduler;

import android.animation.Animator;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.ItemAdapter;
import com.github.iusmac.sevensim.ui.components.ItemAnimator;
import com.github.iusmac.sevensim.ui.components.TextTime;

import java.util.Optional;

/**
 * Abstract ViewHolder for schedule items.
 */
public abstract class ScheduleItemViewHolder extends ItemAdapter.ItemViewHolder<ScheduleItemHolder>
        implements ItemAnimator.OnAnimateChangeListener {

    protected static final float CLOCK_ENABLED_ALPHA = 1f;
    protected static final float CLOCK_DISABLED_ALPHA = 0.69f;

    protected static final float ANIM_LONG_DURATION_MULTIPLIER = 2f / 3f;
    protected static final float ANIM_SHORT_DURATION_MULTIPLIER = 1f / 4f;

    protected float mAnnotationsAlpha = CLOCK_ENABLED_ALPHA;

    protected final TextTime mClock;
    protected final CompoundButton mOnOff;
    private final TextView mActionSummary;
    protected final View mArrowContainer;
    protected final ImageView mArrow;

    ScheduleItemViewHolder(final View itemView) {
        super(itemView);

        mClock = itemView.findViewById(R.id.digital_clock);
        mOnOff = itemView.findViewById(R.id.onoff);
        mActionSummary = itemView.findViewById(R.id.action_summary);
        mArrowContainer = itemView.findViewById(R.id.arrow_container);
        mArrow = mArrowContainer.findViewById(R.id.arrow);

        mOnOff.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (checked != getItemHolder().item.getEnabled()) {
                getScheduleItemViewHolderClickHandler()
                    .onEnabledStateChanged(getItemHolder().item, checked);
            }
        });
        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onBindItemView(final ScheduleItemHolder itemHolder) {
        final SubscriptionScheduleEntity schedule = itemHolder.item;

        bindOnOffSwitch(schedule);
        bindClock(schedule);
        bindLabel(schedule);
        bindActionSummary(itemHolder);
    }

    private void bindOnOffSwitch(final SubscriptionScheduleEntity schedule) {
        if (mOnOff.isChecked() != schedule.getEnabled()) {
            mOnOff.setChecked(schedule.getEnabled());
        }
    }

    private void bindClock(final SubscriptionScheduleEntity schedule) {
        mClock.setTime(schedule.getTime().getHour(), schedule.getTime().getMinute());
        mClock.setAlpha(schedule.getEnabled() ? CLOCK_ENABLED_ALPHA : CLOCK_DISABLED_ALPHA);
        final Typeface oldTypeface = schedule.getEnabled() ? mClock.getTypeface() : null;
        mClock.setTypeface(oldTypeface, schedule.getEnabled() ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void bindLabel(final SubscriptionScheduleEntity schedule) {
        final Optional<String> label = Optional.ofNullable(schedule.getLabel());
        itemView.setContentDescription(mClock.getText() + label.map((str) -> " " + str).orElse(""));
    }

    protected void bindActionSummary(final ScheduleItemHolder itemHolder) {
        mActionSummary.setText(getActionSummary(itemHolder.item, itemView.getResources()));
    }

    protected Animator getBoundsAnimator(final View from, final View to, final long duration) {
        final Animator animator = UiUtils
                .getBoundsAnimator(from, from, to)
                .setDuration(duration);
        animator.setInterpolator(UiUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);
        return animator;
    }

    protected void bindAnnotations(final SubscriptionScheduleEntity schedule) {
        mAnnotationsAlpha = schedule.getEnabled() ? CLOCK_ENABLED_ALPHA : CLOCK_DISABLED_ALPHA;
        setChangingViewsAlpha(mAnnotationsAlpha);
    }

    protected ScheduleItemViewHolderClickHandler getScheduleItemViewHolderClickHandler() {
        return getItemHolder().getScheduleItemViewHolderClickHandler();
    }

    protected abstract void setChangingViewsAlpha(float alpha);

    protected abstract String getActionSummary(final SubscriptionScheduleEntity schedule,
            final Resources res);
}
