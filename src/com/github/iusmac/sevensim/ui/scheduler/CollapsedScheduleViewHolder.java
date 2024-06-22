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
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.EllipsizeLayout;
import com.github.iusmac.sevensim.ui.components.ItemAdapter;

/**
 * A ViewHolder containing views for a schedule item in collapsed stated.
 */
public final class CollapsedScheduleViewHolder extends ScheduleItemViewHolder {
    static final int VIEW_TYPE = R.layout.schedule_card_collapsed;

    private final TextView mLabel;
    private final EllipsizeLayout mEllipsizeLayout;
    private final TextView mDaysOfWeek;
    private final View mHairline;

    private CollapsedScheduleViewHolder(final View itemView) {
        super(itemView);

        mLabel = itemView.findViewById(R.id.label);
        mEllipsizeLayout = itemView.findViewById(R.id.ellipse_layout);
        mDaysOfWeek = mEllipsizeLayout.findViewById(R.id.days_of_week);
        mHairline = itemView.findViewById(R.id.hairline);

        // Expand handler
        itemView.setOnClickListener((v) -> getItemHolder().expand());
        mArrowContainer.setOnClickListener((v) -> getItemHolder().expand());

        // Edit time handler
        mClock.setOnClickListener((v) -> {
            getScheduleItemViewHolderClickHandler().onClockClicked(getItemHolder().item);
            getItemHolder().expand();
        });
    }

    @Override
    protected void onBindItemView(final ScheduleItemHolder itemHolder) {
        super.onBindItemView(itemHolder);

        final SubscriptionScheduleEntity schedule = itemHolder.item;
        final Context context = itemView.getContext();
        bindReadOnlyLabel(context, schedule);
        bindRepeatText(schedule);
        bindAnnotations(schedule);
    }

    private void bindReadOnlyLabel(final Context context,
            final SubscriptionScheduleEntity schedule) {

        final String label = schedule.getLabel();
        if (label != null && label.length() != 0) {
            mLabel.setText(label);
            mLabel.setVisibility(View.VISIBLE);
            mLabel.setContentDescription(context.getString(R.string.scheduler_name_description)
                    + " " + label);
        } else {
            mLabel.setVisibility(View.GONE);
        }
    }

    private void bindRepeatText(final SubscriptionScheduleEntity schedule) {
        final boolean useLongNames = schedule.getDaysOfWeek().getCount() == 1;
        final String daysOfWeek;
        if (schedule.getDaysOfWeek().isFullWeek()) {
            daysOfWeek = itemView.getContext().getString(R.string.scheduler_days_of_week_all);
        } else if (!schedule.getDaysOfWeek().isRepeating()) {
            daysOfWeek = itemView.getContext().getString(R.string.scheduler_days_of_week_none);
        } else {
            daysOfWeek = schedule.getDaysOfWeek().toString(useLongNames);
        }
        mDaysOfWeek.setText(daysOfWeek);
    }

    @Override
    protected String getActionSummary(final SubscriptionScheduleEntity schedule,
            final Resources res) {

        return res.getString(schedule.getSubscriptionEnabled() ?
                    R.string.scheduler_action_activate_sim_short_text :
                    R.string.scheduler_action_deactivate_sim_short_text);
    }

    @Override
    public Animator onAnimateChange(final ViewHolder oldHolder, final ViewHolder newHolder,
            final long duration) {

        if (!(oldHolder instanceof ScheduleItemViewHolder)
                || !(newHolder instanceof ScheduleItemViewHolder)) {
            return null;
        }

        final boolean isCollapsing = this == newHolder;
        setChangingViewsAlpha(isCollapsing ? 0f : mAnnotationsAlpha);

        final Animator changeAnimatorSet = isCollapsing
                ? createCollapsingAnimator((ScheduleItemViewHolder) oldHolder, duration)
                : createExpandingAnimator((ScheduleItemViewHolder) newHolder, duration);
        changeAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                setChangingViewsAlpha(mAnnotationsAlpha);
                mArrow.jumpDrawablesToCurrentState();
            }
        });
        return changeAnimatorSet;
    }

    private Animator createExpandingAnimator(final ScheduleItemViewHolder newHolder,
            final long duration) {

        final AnimatorSet alphaAnimatorSet = new AnimatorSet();
        alphaAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mLabel, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(mEllipsizeLayout, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(mHairline, View.ALPHA, 0f));
        alphaAnimatorSet.setDuration((long) (duration * ANIM_SHORT_DURATION_MULTIPLIER));

        final Animator boundsAnimator = getBoundsAnimator(itemView, newHolder.itemView, duration);
        final Animator switchAnimator = getBoundsAnimator(mOnOff, newHolder.mOnOff, duration);
        final Animator clockAnimator = getBoundsAnimator(mClock, newHolder.mClock, duration);
        final Animator arrowAnimator = getBoundsAnimator(mArrowContainer, newHolder.mArrowContainer,
                duration);
        final Animator arrowAnimation = ObjectAnimator.ofFloat(mArrow, View.ROTATION, 0f,
                180f).setDuration(duration);
        arrowAnimation.setInterpolator(UiUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnimatorSet, boundsAnimator, switchAnimator, clockAnimator,
                arrowAnimator, arrowAnimation);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mClock.setVisibility(View.INVISIBLE);
                mOnOff.setVisibility(View.INVISIBLE);
                mArrow.setRotation(0f);
            }
        });
        return animatorSet;
    }

    private Animator createCollapsingAnimator(final ScheduleItemViewHolder oldHolder,
            final long duration) {

        mClock.setVisibility(View.INVISIBLE);
        mOnOff.setVisibility(View.INVISIBLE);
        mArrow.setVisibility(View.INVISIBLE);

        final AnimatorSet alphaAnimatorSet = new AnimatorSet();
        alphaAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mLabel, View.ALPHA, mAnnotationsAlpha),
                ObjectAnimator.ofFloat(mEllipsizeLayout, View.ALPHA, mAnnotationsAlpha),
                ObjectAnimator.ofFloat(mHairline, View.ALPHA, 1f));

        final View newView = itemView;
        final Animator boundsAnimator = UiUtils.getBoundsAnimator(newView, oldHolder.itemView,
                newView).setDuration(duration);
        boundsAnimator.setInterpolator(UiUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final Animator arrowAnimator = getBoundsAnimator(oldHolder.mArrowContainer, mArrowContainer,
                duration);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(alphaAnimatorSet, boundsAnimator, arrowAnimator);
        return animatorSet;
    }

    @Override
    protected void setChangingViewsAlpha(float alpha) {
        mLabel.setAlpha(alpha);
        mEllipsizeLayout.setAlpha(alpha);
    }

    static ItemAdapter.ItemViewHolder.Factory getItemViewHolderFactory(final Context context) {
        return new ItemAdapter.ItemViewHolder.Factory() {
            @Override
            public ItemAdapter.ItemViewHolder<?> createViewHolder(final ViewGroup parent,
                    final int viewType) {

                final LayoutInflater inflater = LayoutInflater.from(context);
                final View itemView = inflater.inflate(viewType, parent, false);
                return new CollapsedScheduleViewHolder(itemView);
            }
        };
    }
}
