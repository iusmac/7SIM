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
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.scheduler.DayOfWeek;
import com.github.iusmac.sevensim.scheduler.DaysOfWeek;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.ui.UiUtils;
import com.github.iusmac.sevensim.ui.components.ItemAdapter;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Iterator;

/**
 * A ViewHolder containing views for schedule item in expanded state.
 */
public final class ExpandedScheduleViewHolder extends ScheduleItemViewHolder {
    static final int VIEW_TYPE = R.layout.schedule_card_expanded;

    private final TextView mEditLabel;
    private final ConstraintLayout mRepeatDays;
    private final LinearLayout mActionSummaryContainer;
    private final View mDeleteContainer;
    private final TextView mDelete;
    private final View mDeleteConfirmContainer;

    private final CompoundButton[] mDayButtons;
    private final PopupMenu mActionPopupMenu;

    @AssistedInject
    ExpandedScheduleViewHolder(final DaysOfWeek.Factory daysOfWeekFactory,
            final @Assisted View itemView) {

        super(itemView);

        mEditLabel = itemView.findViewById(R.id.edit_label);
        mRepeatDays = itemView.findViewById(R.id.repeat_days);
        mActionSummaryContainer = itemView.findViewById(R.id.action_summary_container);
        mDeleteContainer = itemView.findViewById(R.id.delete_container);
        mDeleteConfirmContainer = mDeleteContainer.findViewById(R.id.delete_confirm_container);
        mDelete = mDeleteContainer.findViewById(R.id.delete);

        // Build a button for each day
        mDayButtons = new CompoundButton[] {
                itemView.findViewById(R.id.day_button_0),
                itemView.findViewById(R.id.day_button_1),
                itemView.findViewById(R.id.day_button_2),
                itemView.findViewById(R.id.day_button_3),
                itemView.findViewById(R.id.day_button_4),
                itemView.findViewById(R.id.day_button_5),
                itemView.findViewById(R.id.day_button_6),
        };
        final DaysOfWeek daysOfWeek = daysOfWeekFactory.create();
        final Iterator<Integer> it = daysOfWeek.iterator();
        for (int i = 0; it.hasNext(); i++) {
            final CompoundButton dayButton = mDayButtons[i];
            final @DayOfWeek int dayOfWeek = it.next();
            dayButton.setText(DaysOfWeek.getNarrowDisplayName(dayOfWeek));
            dayButton.setContentDescription(daysOfWeek.getDisplayName(dayOfWeek,
                        /*useLongName*/ true));

            // Button handler
            dayButton.setOnClickListener((v) -> {
                final boolean isChecked = dayButton.isChecked();
                getScheduleItemViewHolderClickHandler().onDayOfWeekChanged(getItemHolder().item,
                        dayOfWeek, isChecked);
            });
        }

        // Collapse handler
        itemView.setOnClickListener((v) -> getItemHolder().collapse());
        mArrowContainer.setOnClickListener((v) -> getItemHolder().collapse());

        // Edit label handler
        mEditLabel.setOnClickListener((v) -> getScheduleItemViewHolderClickHandler()
                .onEditLabelClicked(getItemHolder().item));

        // Edit time handler
        mClock.setOnClickListener((v) -> getScheduleItemViewHolderClickHandler()
                .onClockClicked(getItemHolder().item));

        // Action summary handler
        mActionPopupMenu = new PopupMenu(itemView.getContext(), mActionSummaryContainer,
                    Gravity.NO_GRAVITY, /*popupStyleAttr=*/ 0,
                    R.style.PopupMenuDefaultAnimationStyle);
        mActionPopupMenu.inflate(R.menu.schedule_card_action_options);
        mActionPopupMenu.setOnMenuItemClickListener((menuItem) -> {
            if (menuItem.getGroupId() == R.id.scheduler_sim_actions_group) {
                final boolean newEnabled = menuItem.getItemId() ==
                    R.id.scheduler_action_activate_sim;
                if (getItemHolder().item.getSubscriptionEnabled() != newEnabled) {
                    getScheduleItemViewHolderClickHandler()
                        .onSubscriptionEnabledStateChanged(getItemHolder().item, newEnabled);
                }
            } else {
                throw new IllegalArgumentException("Unhandled menu action: " + menuItem);
            }
            return true;
        });
        mActionPopupMenu.setOnDismissListener((p) -> {
            final ScheduleItemHolder itemHolder = getItemHolder();
            if (itemHolder != null) {
                itemHolder.saveActionPopupMenuVisiblityState(false);
            }
        });
        mActionSummaryContainer.setOnClickListener((v) -> {
            getItemHolder().saveActionPopupMenuVisiblityState(true);
            mActionPopupMenu.show();
        });

        // Delete schedule handler
        final TextView deleteConfirmOk = mDeleteConfirmContainer.findViewById(R.id.ok);
        final TextView deleteConfirmCancel = mDeleteConfirmContainer.findViewById(R.id.cancel);
        mDelete.setOnClickListener((v) -> setDeleteConfirmVisibility(true));
        deleteConfirmOk.setOnClickListener((v) -> getScheduleItemViewHolderClickHandler()
                .onDeleteClicked(getItemHolder().item));
        deleteConfirmCancel.setOnClickListener((v) -> setDeleteConfirmVisibility(false));
    }

    @Override
    protected void onBindItemView(final ScheduleItemHolder itemHolder) {
        super.onBindItemView(itemHolder);

        final SubscriptionScheduleEntity schedule = itemHolder.item;
        bindEditLabel(itemView.getContext(), schedule);
        bindDaysOfWeekButtons(schedule);
        bindAnnotations(schedule);

        // If this view is bound without coming from a CollapsedScheduleViewHolder (e.g., when
        // calling expand() before this schedule was visible in it's collapsed state), the animation
        // listeners won't do the showing and therefore lead to unwanted half-visible state
        mClock.setVisibility(View.VISIBLE);
        mOnOff.setVisibility(View.VISIBLE);
        mArrow.setVisibility(View.VISIBLE);
        setDeleteConfirmVisibility(itemHolder.isDeleteConfirmVisibile());
        mRepeatDays.setAlpha(1f);
        mActionSummaryContainer.setAlpha(1f);
        mDeleteContainer.setAlpha(1f);
    }

    @Override
    protected void bindActionSummary(final ScheduleItemHolder itemHolder) {
        super.bindActionSummary(itemHolder);

        mActionPopupMenu.getMenu().findItem(itemHolder.item.getSubscriptionEnabled() ?
                R.id.scheduler_action_activate_sim :
                R.id.scheduler_action_deactivate_sim).setChecked(true);

        if (itemHolder.isActionPopupMenuVisible()) {
            // Ensure the PopupMenu is only shown after the anchor view has been laid out
            mActionSummaryContainer.post(() -> mActionPopupMenu.show());
        }
    }

    private void bindDaysOfWeekButtons(final SubscriptionScheduleEntity schedule) {
        final DaysOfWeek daysOfWeek = schedule.getDaysOfWeek();
        final Iterator<Integer> it = daysOfWeek.iterator();
        for (int i = 0; it.hasNext(); i++) {
            final CompoundButton dayButton = mDayButtons[i];
            final @DayOfWeek int dayOfWeek = it.next();
            dayButton.setChecked(daysOfWeek.isBitOn(dayOfWeek));
        }
    }

    private void bindEditLabel(final Context context, final SubscriptionScheduleEntity schedule) {
        final String label = schedule.getLabel();
        mEditLabel.setText(label);
        mEditLabel.setContentDescription(label != null && label.length() > 0
                ? context.getString(R.string.scheduler_name_description) + " " + label
                : context.getString(R.string.scheduler_name_hint));
    }

    @Override
    protected String getActionSummary(final SubscriptionScheduleEntity schedule,
            final Resources res) {

        return res.getString(schedule.getSubscriptionEnabled() ?
                    R.string.scheduler_action_activate_sim_long_text :
                    R.string.scheduler_action_deactivate_sim_long_text);
    }

    @Override
    public Animator onAnimateChange(final ViewHolder oldHolder, final ViewHolder newHolder,
            final long duration) {

        if (!(oldHolder instanceof ScheduleItemViewHolder)
                || !(newHolder instanceof ScheduleItemViewHolder)) {
            return null;
        }

        final boolean isExpanding = this == newHolder;
        UiUtils.setBackgroundAlpha(itemView, isExpanding ? 0 : 255);
        setChangingViewsAlpha(isExpanding ? 0f : mAnnotationsAlpha);

        final Animator changeAnimatorSet = isExpanding
                ? createExpandingAnimator((ScheduleItemViewHolder) oldHolder, duration)
                : createCollapsingAnimator((ScheduleItemViewHolder) newHolder, duration);
        changeAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                UiUtils.setBackgroundAlpha(itemView, 255);
                setChangingViewsAlpha(mAnnotationsAlpha);
                mArrow.jumpDrawablesToCurrentState();
                mClock.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
                mOnOff.setVisibility(isExpanding ? View.VISIBLE : View.INVISIBLE);
            }
        });
        return changeAnimatorSet;
    }

    private Animator createCollapsingAnimator(final ScheduleItemViewHolder newHolder,
            final long duration) {

        final AnimatorSet backgroundAnimatorSet = new AnimatorSet();
        backgroundAnimatorSet.playTogether(
                ObjectAnimator.ofPropertyValuesHolder(itemView,
                    PropertyValuesHolder.ofInt(UiUtils.BACKGROUND_ALPHA, 255, 0)),
                ObjectAnimator.ofPropertyValuesHolder(newHolder.itemView,
                    PropertyValuesHolder.ofInt(UiUtils.BACKGROUND_ALPHA, 0, 255)));
        backgroundAnimatorSet.setDuration(duration);

        final Animator boundsAnimator = getBoundsAnimator(itemView, newHolder.itemView, duration);
        final Animator switchAnimator = getBoundsAnimator(mOnOff, newHolder.mOnOff, duration);
        final Animator clockAnimator = getBoundsAnimator(mClock, newHolder.mClock, duration);

        final long shortDuration = (long) (duration * ANIM_SHORT_DURATION_MULTIPLIER);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(mEditLabel, View.ALPHA,
                0f).setDuration(shortDuration);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(mRepeatDays, View.ALPHA,
                0f).setDuration(shortDuration);
        final Animator actionSummaryContainerAnimation = ObjectAnimator
            .ofFloat(mActionSummaryContainer, View.ALPHA, 0f).setDuration(shortDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(mDeleteContainer, View.ALPHA,
                0f).setDuration(shortDuration);
        final Animator arrowAnimation = ObjectAnimator.ofFloat(mArrow, View.ROTATION, 180f,
                0f).setDuration(duration);
        arrowAnimation.setInterpolator(UiUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimatorSet, boundsAnimator, editLabelAnimation,
                clockAnimator, switchAnimator, repeatDaysAnimation, actionSummaryContainerAnimation,
                deleteAnimation, arrowAnimation);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                newHolder.mClock.setVisibility(View.VISIBLE);
                newHolder.mOnOff.setVisibility(View.VISIBLE);
                newHolder.mArrow.setVisibility(View.VISIBLE);
                mArrow.setRotation(0f);
                // Since the item is now completely invisible, simplify flip the delete confirm
                // visibility flag, so that the view is hidden when this item rebinds
                getItemHolder().saveDeleteConfirmVisibilityState(false);
            }
        });
        return animatorSet;
    }

    private Animator createExpandingAnimator(final ScheduleItemViewHolder oldHolder,
            final long duration) {

        mClock.setVisibility(View.INVISIBLE);
        mOnOff.setVisibility(View.INVISIBLE);
        mArrow.setVisibility(View.INVISIBLE);
        mRepeatDays.setAlpha(0f);
        mActionSummaryContainer.setAlpha(0f);
        mDeleteContainer.setAlpha(0f);
        setChangingViewsAlpha(0f);

        final AnimatorSet swapBackgroundsAnimatorSet = new AnimatorSet();
        swapBackgroundsAnimatorSet.playTogether(
                ObjectAnimator.ofPropertyValuesHolder(oldHolder.itemView,
                    PropertyValuesHolder.ofInt(UiUtils.BACKGROUND_ALPHA, 255, 0)),
                ObjectAnimator.ofPropertyValuesHolder(itemView,
                    PropertyValuesHolder.ofInt(UiUtils.BACKGROUND_ALPHA, 0, 255)));
        swapBackgroundsAnimatorSet.setDuration(duration);

        final View newView = itemView;
        final Animator boundsAnimator = UiUtils.getBoundsAnimator(newView, oldHolder.itemView,
                newView);
        boundsAnimator.setDuration(duration);
        boundsAnimator.setInterpolator(UiUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final long longDuration = (long) (duration * ANIM_LONG_DURATION_MULTIPLIER);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(mRepeatDays, View.ALPHA,
                1f).setDuration(longDuration);
        final Animator actionSummaryContainerAnimation = ObjectAnimator
            .ofFloat(mActionSummaryContainer, View.ALPHA, 1f).setDuration(longDuration);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(mEditLabel, View.ALPHA,
                mAnnotationsAlpha).setDuration(longDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(mDeleteContainer, View.ALPHA,
                1f).setDuration(longDuration);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(swapBackgroundsAnimatorSet, boundsAnimator, repeatDaysAnimation,
                actionSummaryContainerAnimation, editLabelAnimation, deleteAnimation);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mArrow.setVisibility(View.VISIBLE);
                mArrow.setRotation(180f);
            }
        });
        return animatorSet;
    }

    private void setDeleteConfirmVisibility(final boolean visible) {
        mDelete.setVisibility(visible ? View.GONE : View.VISIBLE);
        mDeleteConfirmContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        getItemHolder().saveDeleteConfirmVisibilityState(visible);
    }

    @Override
    protected void onRecycleItemView() {
        super.onRecycleItemView();

        mActionPopupMenu.dismiss();
    }

    @Override
    protected void setChangingViewsAlpha(float alpha) {
        mEditLabel.setAlpha(alpha);
    }

    @AssistedFactory
    interface Factory {
        ExpandedScheduleViewHolder create(View itemView);
    }

    static ItemAdapter.ItemViewHolder.Factory getItemViewHolderFactory(final Context context,
            final Factory assistedFactory) {

        return new ItemAdapter.ItemViewHolder.Factory() {
            @Override
            public ItemAdapter.ItemViewHolder<?> createViewHolder(final ViewGroup parent,
                    final int viewType) {

                final LayoutInflater inflater = LayoutInflater.from(context);
                final View itemView = inflater.inflate(viewType, parent, false);
                return assistedFactory.create(itemView);
            }
        };
    }
}
