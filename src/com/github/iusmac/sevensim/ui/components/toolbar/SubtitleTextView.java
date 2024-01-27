package com.github.iusmac.sevensim.ui.components.toolbar;

import android.content.Context;
import android.text.TextUtils;

import androidx.appcompat.widget.AppCompatTextView;

abstract class SubtitleTextView extends AppCompatTextView {
    public SubtitleTextView(final Context context) {
        super(context);

        setIncludeFontPadding(false);
        setEllipsize(TextUtils.TruncateAt.END);
    }
}
