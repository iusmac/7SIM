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

import android.os.Bundle;

import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.ui.components.ItemAdapter;

public class ScheduleItemHolder extends ItemAdapter.ItemHolder<SubscriptionScheduleEntity> {
    private static final String SAVED_EXPANDED_KEY = "expanded";
    private static final String SAVED_ACTION_POPUP_MENU_VISIBLE = "actionPopupMenuVisible";
    private static final String SAVED_DELETE_CONFIRM_VISIBILE = "deleteConfirmVisibile";

    private boolean mExpanded;
    private boolean mActionPopupMenuVisible;
    private boolean mDeleteConfirmVisibile;

    private final ScheduleItemViewHolderClickHandler mScheduleItemViewHolderClickHandler;

    ScheduleItemHolder(final SubscriptionScheduleEntity scheduleEntity,
            final ScheduleItemViewHolderClickHandler scheduleItemViewHolderClickHandler) {

        super(scheduleEntity, scheduleEntity.getId());

        mScheduleItemViewHolderClickHandler = scheduleItemViewHolderClickHandler;
    }

    @Override
    public int getItemViewType() {
        return isExpanded() ? ExpandedScheduleViewHolder.VIEW_TYPE :
            CollapsedScheduleViewHolder.VIEW_TYPE;
    }

    ScheduleItemViewHolderClickHandler getScheduleItemViewHolderClickHandler() {
        return mScheduleItemViewHolderClickHandler;
    }

    void expand() {
        if (!isExpanded()) {
            mExpanded = true;
            notifyItemChanged();
        }
    }

    void collapse() {
        if (isExpanded()) {
            mExpanded = false;
            notifyItemChanged();
        }
    }

    boolean isExpanded() {
        return mExpanded;
    }

    void saveActionPopupMenuVisiblityState(final boolean visible) {
        mActionPopupMenuVisible = visible;
    }

    boolean isActionPopupMenuVisible() {
        return mActionPopupMenuVisible;
    }

    void saveDeleteConfirmVisibilityState(final boolean visible) {
        mDeleteConfirmVisibile = visible;
    }

    boolean isDeleteConfirmVisibile() {
        return mDeleteConfirmVisibile;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);

        bundle.putBoolean(SAVED_EXPANDED_KEY, mExpanded);
        bundle.putBoolean(SAVED_ACTION_POPUP_MENU_VISIBLE, mActionPopupMenuVisible);
        bundle.putBoolean(SAVED_DELETE_CONFIRM_VISIBILE, mDeleteConfirmVisibile);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);

        mExpanded = bundle.getBoolean(SAVED_EXPANDED_KEY);
        mActionPopupMenuVisible = bundle.getBoolean(SAVED_ACTION_POPUP_MENU_VISIBLE);
        mDeleteConfirmVisibile = bundle.getBoolean(SAVED_DELETE_CONFIRM_VISIBILE);
    }
}
