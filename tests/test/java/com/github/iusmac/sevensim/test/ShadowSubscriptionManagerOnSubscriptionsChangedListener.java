package com.github.iusmac.sevensim.test;

import android.telephony.SubscriptionManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(SubscriptionManager.OnSubscriptionsChangedListener.class)
public class ShadowSubscriptionManagerOnSubscriptionsChangedListener {
    // TODO Clean up after Robolectric will upstream its android-all-robolectric.jar package in
    // Maven Repository to Android 15 sources to include the Android 14-QPR2 changes
    @Implementation
    public void __constructor__() {
        // NOTE: this empty constructor shadows the old implementation existed until Android 14-QPR2
        // that creates a Handler using the caller's Looper. Since there's a check whether the
        // Looper.prepare() has been called, due this our tests fail because we're using the
        // ThreadPoolExecutor to perform async requests instead. This can't be reproduced in
        // production code after the Android 14-QPR2 update, where it's no longer necessary to
        // instantiate a Handler inside of the OnSubscriptionsChangedListener, and everyone should
        // migrate (what we've already done) to the
        // SubscriptionManager#addOnSubscriptionsChangedListener API allowing to pass in a custom
        // Executor
    }
}
