/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.github.iusmac.sevensim.ui.components;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextView;

import com.github.iusmac.sevensim.ui.UiUtils;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Based on {@link android.widget.TextClock}, this widget displays a constant time of day using
 * format specifiers. {@link android.widget.TextClock} doesn't support a non-ticking clock.
 */
@SuppressLint("AppCompatCustomView")
public class TextTime extends TextView {
    /** UTC does not have DST rules and will not alter the hour and minute. */
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final CharSequence DEFAULT_FORMAT_12_HOUR = "h:mm a";
    private static final CharSequence DEFAULT_FORMAT_24_HOUR = "H:mm";

    // Format the time relative to UTC to ensure hour and minute are not adjusted for DST.
    private final Calendar mTime = Calendar.getInstance(UTC);
    private CharSequence mFormat12;
    private CharSequence mFormat24;
    private CharSequence mFormat;

    private boolean mAttached;

    private final ContentObserver mFormatChangeObserver =
            new ContentObserver(new Handler(Looper.myLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            chooseFormat();
            updateTime();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            chooseFormat();
            updateTime();
        }
    };

    @SuppressWarnings("UnusedDeclaration")
    public TextTime(final Context context) {
        this(context, null);
    }

    @SuppressWarnings("UnusedDeclaration")
    public TextTime(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextTime(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        setFormat12Hour(UiUtils.get12ModeFormat(0.3f /* amPmRatio */, false));
        setFormat24Hour(UiUtils.get24ModeFormat(false));

        chooseFormat();
    }

    @SuppressWarnings("UnusedDeclaration")
    public CharSequence getFormat12Hour() {
        return mFormat12;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setFormat12Hour(final CharSequence format) {
        mFormat12 = format;

        chooseFormat();
        updateTime();
    }

    @SuppressWarnings("UnusedDeclaration")
    public CharSequence getFormat24Hour() {
        return mFormat24;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setFormat24Hour(final CharSequence format) {
        mFormat24 = format;

        chooseFormat();
        updateTime();
    }

    private void chooseFormat() {
        final boolean format24Requested = DateFormat.is24HourFormat(getContext());
        if (format24Requested) {
            mFormat = mFormat24 == null ? DEFAULT_FORMAT_24_HOUR : mFormat24;
        } else {
            mFormat = mFormat12 == null ? DEFAULT_FORMAT_12_HOUR : mFormat12;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            registerObserver();
            updateTime();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            unregisterObserver();
            mAttached = false;
        }
    }

    private void registerObserver() {
        final ContentResolver resolver = getContext().getContentResolver();
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24),
                true, mFormatChangeObserver);
    }

    private void unregisterObserver() {
        final ContentResolver resolver = getContext().getContentResolver();
        resolver.unregisterContentObserver(mFormatChangeObserver);
    }

    public void setTime(final int hour, final int minute) {
        mTime.set(Calendar.HOUR_OF_DAY, hour);
        mTime.set(Calendar.MINUTE, minute);
        updateTime();
    }

    private void updateTime() {
        final CharSequence text = DateFormat.format(mFormat, mTime);
        setText(text);
        // Strip away the spans from text so talkback is not confused
        setContentDescription(text.toString());
    }
}
