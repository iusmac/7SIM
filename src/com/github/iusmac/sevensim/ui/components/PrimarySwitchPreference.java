/*
 * Copyright (C) 2021 The Android Open Source Project
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
 */

package com.github.iusmac.sevensim.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Switch;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.TwoTargetPreference;

import com.github.iusmac.sevensim.R;

/**
 * A custom preference that provides inline switch. It has a mandatory field for title, and optional
 * fields for icon and sub-text.
 */
public final class PrimarySwitchPreference extends TwoTargetPreference {
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch mSwitch;
    private boolean mChecked;
    private boolean mCheckedSet;
    private boolean mEnableSwitch = true;

    public PrimarySwitchPreference(final Context context,
            final AttributeSet attrs, final int defStyleAttr,
            final int defStyleRes) {

        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PrimarySwitchPreference(final Context context,
            final AttributeSet attrs, final int defStyleAttr) {

        super(context, attrs, defStyleAttr);
    }

    public PrimarySwitchPreference(final Context context,
            final AttributeSet attrs) {

        super(context, attrs);
    }

    public PrimarySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected int getSecondTargetResId() {
        return R.layout.preference_widget_primary_switch;
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mSwitch = (Switch) holder.findViewById(R.id.switchWidget);
        if (mSwitch != null) {
            mSwitch.setOnClickListener((view) -> {
                if (mSwitch != null && !mSwitch.isEnabled()) {
                    return;
                }
                final boolean newChecked = !mChecked;
                if (callChangeListener(newChecked)) {
                    setChecked(newChecked);
                    persistBoolean(newChecked);
                }
            });

            // Consume move events to ignore drag actions
            mSwitch.setOnTouchListener((view, event) -> {
                return event.getActionMasked() == MotionEvent.ACTION_MOVE;
            });

            mSwitch.setContentDescription(getTitle());
            mSwitch.setChecked(mChecked);
            mSwitch.setEnabled(mEnableSwitch);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        setSwitchEnabled(enabled);
    }

    @Override
    protected @Nullable Object onGetDefaultValue(final @NonNull TypedArray a,
            final int index) {

        return a.getBoolean(index, false);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = false;
        }
        setChecked(getPersistedBoolean((Boolean) defaultValue));
    }

    public boolean isChecked() {
        return mSwitch != null && mChecked;
    }

    /**
     * Used to validate the state of mChecked and mCheckedSet when testing, without requiring that a
     * ViewHolder be bound to the object.
     */
    @Keep
    @Nullable
    public Boolean getCheckedState() {
        return mCheckedSet ? mChecked : null;
    }

    /**
     * Set the checked status to be {@code checked}.
     *
     * @param checked The new checked status
     */
    public void setChecked(final boolean checked) {
        // Always set checked the first time; don't assume the field's default of false
        final boolean changed = mChecked != checked;
        if (changed || !mCheckedSet) {
            mChecked = checked;
            mCheckedSet = true;
            if (mSwitch != null) {
                mSwitch.setChecked(checked);
            }
        }
    }

    /**
     * Set the enabled state of {@link Switch}.
     *
     * @param enabled The new enabled status.
     */
    public void setSwitchEnabled(final boolean enabled) {
        mEnableSwitch = enabled;
        if (mSwitch != null) {
            mSwitch.setEnabled(enabled);
        }
    }

    public Switch getSwitch() {
        return mSwitch;
    }
}
