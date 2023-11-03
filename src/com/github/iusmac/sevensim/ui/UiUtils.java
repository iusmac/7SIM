package com.github.iusmac.sevensim.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

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
}
