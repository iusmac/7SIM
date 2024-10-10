package com.github.iusmac.sevensim.test;

import android.telephony.SubscriptionManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSubscriptionManager;

import static android.os.Build.VERSION_CODES.R;

@Implements(SubscriptionManager.class)
public class ShadowSubscriptionManagerHiddenApi extends ShadowSubscriptionManager {
    @Implementation(minSdk = R)
    protected void setUiccApplicationsEnabled(int subscriptionId, boolean enabled) {
        getAvailableSubscriptionInfoList()
            .stream()
            .filter((subInfo) -> subInfo.getSubscriptionId() == subscriptionId)
            .findFirst()
            .ifPresent((subInfo) -> TestUtils.setAreUiccApplicationsEnabled(subInfo, enabled));
    }
}
