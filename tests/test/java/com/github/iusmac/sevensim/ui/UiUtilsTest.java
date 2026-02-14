package com.github.iusmac.sevensim.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class UiUtilsTest extends MockitoHiltAndroidTestBase {
    @Test
    public void test_BackgroundAlphaProperty_Get_ShouldHandleNotLayerDrawables() {
        final var expectedAlpha = 128;
        final var v = new View(mApplicationContext);
        v.setBackgroundColor(Color.RED);
        v.getBackground().setAlpha(expectedAlpha);

        assertThat(UiUtils.BACKGROUND_ALPHA.get(v), is(expectedAlpha));
    }

    @Test
    public void test_BackgroundAlphaProperty_Get_ShouldHandleNonEmptyLayerDrawable() {
        final var button = new Button(mApplicationContext);
        final var layerDrawable = (LayerDrawable) button.getBackground();

        final var expectedAlpha = 128;
        layerDrawable.getDrawable(0).setAlpha(expectedAlpha);

        assertThat(UiUtils.BACKGROUND_ALPHA.get(button), is(expectedAlpha));
    }

    @Test
    public void test_BackgroundAlphaProperty_Get_ShouldHandleEmptyLayerDrawable() {
        final var button = new Button(mApplicationContext);
        button.setBackground(new LayerDrawable(new Drawable[0]));

        assertThat(((LayerDrawable) button.getBackground()).getNumberOfLayers(), is(0));
        // When LayerDrawable is empty, it will return its parent value that is hardcoded to 0xFF
        assertThat(UiUtils.BACKGROUND_ALPHA.get(button), is(255));
    }

    @Test
    public void test_BackgroundAlphaProperty_Set_ShouldNotAffectOtherDrawablesInLayerDrawable() {
        final var button = new Button(mApplicationContext);
        final var layerDrawable = (LayerDrawable) button.getBackground();

        // Add a second background layer to the button that will only be visible if pressed
        final var stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[] { android.R.attr.state_pressed },
                new ColorDrawable(Color.GREEN));
        layerDrawable.addLayer(stateListDrawable);

        final var expectedAlpha = 128;
        UiUtils.BACKGROUND_ALPHA.set(button, expectedAlpha);

        assertThat(button.getBackground().getAlpha(), is(both(equalTo(expectedAlpha))
                    .and(equalTo(layerDrawable.getDrawable(0).getAlpha()))));
        assertThat(layerDrawable.getDrawable(1).getAlpha(), is(255));
    }

    @Test
    public void test_BackgroundAlphaProperty_Set_ShouldDoNothingWhenEmptyLayerDrawable() {
        final var button = new Button(mApplicationContext);
        button.setBackground(new LayerDrawable(new Drawable[0]));

        UiUtils.BACKGROUND_ALPHA.set(button, 128);

        assertThat(((LayerDrawable) button.getBackground()).getNumberOfLayers(), is(0));
        // When LayerDrawable is empty, it will return its parent value that is hardcoded to 0xFF
        assertThat(button.getBackground().getAlpha(), is(255));
    }

    @Test
    public void test_BackgroundAlphaProperty_Set_ShouldHandleNotLayerDrawables() {
        final var v = new View(mApplicationContext);
        v.setBackgroundColor(Color.RED);

        final var expectedAlpha = 128;
        UiUtils.BACKGROUND_ALPHA.set(v, expectedAlpha);

        assertThat(v.getBackground().getAlpha(), is(expectedAlpha));
    }

    @Test
    public void test_createTintedDrawable() {
        final var expectedTint = Color.GREEN;
        try (final var mock = mockStatic(DrawableCompat.class, CALLS_REAL_METHODS)) {
            mock.when(() -> DrawableCompat.wrap(any(Drawable.class))).thenAnswer((invocation) ->
                    spy((Drawable) invocation.callRealMethod()));

            final var drawable = UiUtils.createTintedDrawable(mApplicationContext,
                    R.drawable.ic_sim, expectedTint);

            verify(drawable, times(1)).mutate();
            verify(drawable, times(1)).setTint(expectedTint);
        }
    }

    @Test
    public void test_setTextViewMarqueeRepeatLimit() {
        final var tv = new TextView(mApplicationContext);
        final var expectedRepeatLimit = 3;
        UiUtils.setTextViewMarqueeRepeatLimit(tv, expectedRepeatLimit);

        assertThat(tv.getEllipsize(), is(TextUtils.TruncateAt.MARQUEE));
        assertTrue(tv.isHorizontalFadingEdgeEnabled());
        assertThat(tv.getMarqueeRepeatLimit(), is(expectedRepeatLimit));
        assertTrue(tv.isSelected());

        UiUtils.setTextViewMarqueeRepeatLimit(tv, 0);
        assertFalse(tv.isSelected());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get12ModeFormat_WithoutAmPm() {
        Locale.setDefault(Locale.US);

        assertThat(UiUtils.get12ModeFormat(0.3f, /*includeSeconds=*/ false, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ false), is("h:mm"));
        assertThat(UiUtils.get12ModeFormat(0.3f, /*includeSeconds=*/ true, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ false), is("h:mm:ss"));
        assertThat(UiUtils.get12ModeFormat(0f, /*includeSeconds=*/ false, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ false), is("h:mm"));
        assertThat(UiUtils.get12ModeFormat(0f, /*includeSeconds=*/ true), is("h:mm:ss"));
        assertThat(UiUtils.get12ModeFormat(-0.3f, /*includeSeconds=*/ false, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ false), is("h:mm"));
        assertThat(UiUtils.get12ModeFormat(-0.3f, /*includeSeconds=*/ true), is("h:mm:ss"));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get12ModeFormat_ShouldReplaceAllSpacesWithHairSpace() {
        Locale.setDefault(Locale.US);

        assertThat(UiUtils.get12ModeFormat(0.3f, /*includeSeconds=*/ false, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ true).toString(), is(both(not(containsString(" ")))
                        .and(containsString("\u200A"))));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get12ModeFormat_WithAmPm() {
        assertThat(UiUtils.get12ModeFormat(0.3f, /*includeSeconds=*/ false, /*amPmBolded=*/ false,
                    /*amPmDisplayed=*/ true).toString(), endsWith("a"));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get12ModeFormat_WithNormalAmPmSpanned() {
        final var amPmRatio = 0.3f;
        final var normalAmPmSpanned = SpannableString.valueOf(UiUtils.get12ModeFormat(amPmRatio,
                    /*includeSeconds=*/ false, /*amPmBolded=*/ false, /*amPmDisplayed=*/ true));

        final var amPmPos = normalAmPmSpanned.toString().indexOf('a');
        final var spans = normalAmPmSpanned.getSpans(amPmPos, amPmPos + 1, Object.class);
        assertThat(spans, arrayContaining(isA(RelativeSizeSpan.class), isA(StyleSpan.class),
                    isA(TypefaceSpan.class)));
        assertThat(((RelativeSizeSpan) spans[0]).getSizeChange(), is(amPmRatio));
        assertThat(((StyleSpan) spans[1]).getStyle(), is(Typeface.NORMAL));
        assertThat(((TypefaceSpan) spans[2]).getTypeface(), is(Typeface.SANS_SERIF));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get12ModeFormat_WithBoldedAmPmSpanned() {
        final var amPmRatio = 0.3f;
        final var boldedAmPmSpanned = SpannableString.valueOf(UiUtils.get12ModeFormat(amPmRatio,
                    /*includeSeconds=*/ true));

        final var amPmPos = boldedAmPmSpanned.toString().indexOf('a');
        final var spans = boldedAmPmSpanned.getSpans(amPmPos, amPmPos + 1, Object.class);
        assertThat(spans, arrayContaining(isA(RelativeSizeSpan.class), isA(StyleSpan.class),
                    isA(TypefaceSpan.class)));
        assertThat(((RelativeSizeSpan) spans[0]).getSizeChange(), is(amPmRatio));
        assertThat(((StyleSpan) spans[1]).getStyle(), is(Typeface.BOLD));
        assertThat(((TypefaceSpan) spans[2]).getTypeface(), is(Typeface.SANS_SERIF));
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.Q)
    public void test_get24ModeFormat() {
        Locale.setDefault(Locale.US);
        assertThat(UiUtils.get24ModeFormat(/*includeSeconds=*/ false), is("HH:mm"));
        assertThat(UiUtils.get24ModeFormat(/*includeSeconds=*/ true), is("HH:mm:ss"));
    }

    @Test
    public void test_getBoundsAnimator_MorphAnimateViewToOtherViewWithoutBackgroundInsets() {
        final var fromView = new View(mApplicationContext);
        final var toView = new View(mApplicationContext);

        fromView.setLeft(0);
        fromView.setTop(0);
        fromView.setRight(90);
        fromView.setBottom(85);

        final int toLeft = fromView.getLeft() + 10;
        final int toTop = fromView.getTop() + 10;
        final int toRight = fromView.getRight() + 10;
        final int toBottom = fromView.getBottom() + 10;
        toView.setLeft(toLeft);
        toView.setTop(toTop);
        toView.setRight(toRight);
        toView.setBottom(toBottom);

        final var animator = UiUtils.getBoundsAnimator(toView, fromView, toView);

        assertThat(toView.getLeft(), is(fromView.getLeft()));
        assertThat(toView.getTop(), is(fromView.getTop()));
        assertThat(toView.getRight(), is(fromView.getRight()));
        assertThat(toView.getBottom(), is(fromView.getBottom()));

        // Start and end animation to assign the "to" values immediately
        animator.start();
        animator.end();

        assertThat(toView.getLeft(), is(toLeft));
        assertThat(toView.getTop(), is(toTop));
        assertThat(toView.getRight(), is(toRight));
        assertThat(toView.getBottom(), is(toBottom));
    }

    @Test
    public void test_getBoundsAnimator_MorphAnimateViewToOtherViewWithBackgroundInsets() {
        final var fromView = new View(mApplicationContext);
        final var fromViewBackground = new VectorDrawable();
        fromViewBackground.setBounds(10, 10, 10, 10);
        fromView.setBackground(fromViewBackground);

        final var toView = new View(mApplicationContext);
        final var toViewBackground = new VectorDrawable();
        toViewBackground.setBounds(10, 10, 10, 10);
        toView.setBackground(fromViewBackground);

        fromView.setLeft(0);
        fromView.setTop(0);
        fromView.setRight(90);
        fromView.setBottom(85);

        final int toLeft = fromView.getLeft() + 10;
        final int toTop = fromView.getTop() + 10;
        final int toRight = fromView.getRight() + 10;
        final int toBottom = fromView.getBottom() + 10;
        toView.setLeft(toLeft);
        toView.setTop(toTop);
        toView.setRight(toRight);
        toView.setBottom(toBottom);

        final var animator = UiUtils.getBoundsAnimator(toView, fromView, toView);

        assertThat(toView.getLeft(), is(fromView.getLeft()));
        assertThat(toView.getTop(), is(fromView.getTop()));
        assertThat(toView.getRight(), is(fromView.getRight()));
        assertThat(toView.getBottom(), is(fromView.getBottom()));

        // Start and end animation to assign the "to" values immediately
        animator.start();
        animator.end();

        assertThat(toView.getLeft(), is(toLeft));
        assertThat(toView.getTop(), is(toTop));
        assertThat(toView.getRight(), is(toRight));
        assertThat(toView.getBottom(), is(toBottom));
    }

    @Test
    public void test_getBoundsAnimator_MorphAnimateViewFromPositionToPosition() {
        final var v = new View(mApplicationContext);

        final int fromLeft = 0;
        final int fromTop = 0;
        final int fromRight = 90;
        final int fromBottom = 85;

        final int toLeft = fromLeft + 10;
        final int toTop = fromTop + 10;
        final int toRight = fromRight + 10;
        final int toBottom = fromBottom + 10;

        final var animator = UiUtils.getBoundsAnimator(v, fromLeft, fromTop, fromRight, fromBottom,
                toLeft, toTop, toRight, toBottom);

        assertThat(v.getLeft(), is(fromLeft));
        assertThat(v.getTop(), is(fromTop));
        assertThat(v.getRight(), is(fromRight));
        assertThat(v.getBottom(), is(fromBottom));

        // Start and end animation to assign the "to" values immediately
        animator.start();
        animator.end();

        assertThat(v.getLeft(), is(toLeft));
        assertThat(v.getTop(), is(toTop));
        assertThat(v.getRight(), is(toRight));
        assertThat(v.getBottom(), is(toBottom));
    }

    @Test
    public void test_getGlobalPosition_ShouldReturnNoPositionWhenEmptyConcatAdapter() {
        final var concatAdapter = new ConcatAdapter();
        final var targetAdapter = buildAdapterWithItemCount(3);
        assertThat(UiUtils.getGlobalPosition(concatAdapter, targetAdapter, 1),
                is(RecyclerView.NO_POSITION));
    }

    @Test
    public void test_getGlobalPosition_ShouldReturnNoPositionWhenNoTargetAdapterFound() {
        final var concatAdapter = new ConcatAdapter(buildAdapterWithItemCount(2));
        final var targetAdapter = buildAdapterWithItemCount(3);
        assertThat(UiUtils.getGlobalPosition(concatAdapter, targetAdapter, 1),
                is(RecyclerView.NO_POSITION));
    }

    @Test
    public void test_getGlobalPosition_ShouldReturnPositionWhenTargetAdapterFound() {
        final var concatAdapter = new ConcatAdapter(buildAdapterWithItemCount(5),
                buildAdapterWithItemCount(3), buildAdapterWithItemCount(7));
        final var targetAdapter = concatAdapter.getAdapters().get(1);
        final var targetLocalPosition = 2;
        assertThat(UiUtils.getGlobalPosition(concatAdapter, targetAdapter, targetLocalPosition),
                // 2nd element in the 2nd adapter + all 5 elements from the preceding adapter is 7
                is(both(equalTo(concatAdapter.getAdapters().get(0).getItemCount() +
                            targetLocalPosition)).and(equalTo(7))));
    }

    @Test
    public void test_isLandscape_WhenPortrait() {
        assertFalse(UiUtils.isLandscape(mApplicationContext));
    }

    @Test
    @Config(qualifiers = "land")
    public void test_isLandscape_WhenLandscape() {
        assertTrue(UiUtils.isLandscape(mApplicationContext));
    }

    @Test
    public void test_isDarkMode_WhenLightMode() {
        assertFalse(UiUtils.isDarkMode(mApplicationContext));
    }

    @Test
    @Config(qualifiers = "night")
    public void test_isDarkMode_WhenDarkMode() {
        assertTrue(UiUtils.isDarkMode(mApplicationContext));
    }

    private static RecyclerView.Adapter<? extends RecyclerView.ViewHolder>
        buildAdapterWithItemCount(final int itemCount) {

        return new RecyclerView.Adapter<>() {
            @Override
            public int getItemCount() {
                return itemCount;
            }

            @Override
            public void onBindViewHolder(ViewHolder holder, int position) {
                throw new UnsupportedOperationException("Unimplemented method onBindViewHolder");
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                throw new UnsupportedOperationException("Unimplemented method onCreateViewHolder");
            }
        };
    }
}
