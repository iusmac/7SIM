package com.github.iusmac.sevensim.test;

import androidx.recyclerview.widget.RecyclerView;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowViewGroup;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

import static org.robolectric.util.reflector.Reflector.reflector;

@Implements(RecyclerView.class)
public class ShadowRecyclerView extends ShadowViewGroup {
    @RealObject RecyclerView realObject;

    private volatile Boolean mIsComputingLayout;

    @Implementation
    protected boolean isComputingLayout() {
        return mIsComputingLayout != null ? mIsComputingLayout :
            reflector(ReflectorRecyclerView.class, realObject).isComputingLayout();
    }

    /**
     * Sets the response of {@link RecyclerView#isComputingLayout}. Pass {@code null} to use real
     * implementation instead.
     */
    public void setIsComputingLayout(final Boolean isComputingLayout) {
        mIsComputingLayout = isComputingLayout;
    }

    @ForType(RecyclerView.class)
    interface ReflectorRecyclerView {
        @Direct
        boolean isComputingLayout();
    }
}
