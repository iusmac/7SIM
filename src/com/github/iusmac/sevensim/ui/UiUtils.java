package com.github.iusmac.sevensim.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.github.iusmac.sevensim.R;

public final class UiUtils {
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
}
