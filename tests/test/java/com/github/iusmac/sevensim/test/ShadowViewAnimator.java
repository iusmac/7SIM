package com.github.iusmac.sevensim.test;

import android.widget.ViewAnimator;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowViewGroup;

// TODO Clean up after https://github.com/robolectric/robolectric/issues/3319 is fixed.
@Implements(ViewAnimator.class)
public class ShadowViewAnimator extends ShadowViewGroup {
}
