package com.github.iusmac.sevensim.ui.components.toolbar;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextSwitcher;

abstract class Subtitle extends TextSwitcher {
    public Subtitle(final Context context) {
        super(context);

        setMeasureAllChildren(false);

        final Animation animIn = AnimationUtils.loadAnimation(context,
                android.R.anim.slide_in_left);
        final Animation animOut = AnimationUtils.loadAnimation(context,
                android.R.anim.slide_out_right);
        animIn.setInterpolator(context, android.R.anim.overshoot_interpolator);
        animOut.setInterpolator(context, android.R.anim.anticipate_overshoot_interpolator);
        setInAnimation(animIn);
        setOutAnimation(animOut);
    }

    @Override
    public void setText(final CharSequence text) {
        super.setText(text);
        if (!TextUtils.isEmpty(text)) {
            announceForAccessibility(text);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(final AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.setFocusable(true);
    }
}
