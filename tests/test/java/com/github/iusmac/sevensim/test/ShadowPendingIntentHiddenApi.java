package com.github.iusmac.sevensim.test;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPendingIntent;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

@Implements(PendingIntent.class)
public class ShadowPendingIntentHiddenApi extends ShadowPendingIntent {
    @Implementation(minSdk = JELLY_BEAN_MR1)
    protected static PendingIntent getActivityAsUser(Context context, int requestCode,
            Intent intent, int flags, Bundle options, UserHandle user) {
        return getActivity(context, requestCode, intent, flags, options);
    }
}
