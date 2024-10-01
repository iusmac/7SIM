package com.github.iusmac.sevensim.test;

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContextImpl;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.O;

@Implements(className = ShadowContextImpl.CLASS_NAME)
public class ShadowContextHiddenApi extends ShadowContextImpl {
    @Implementation(minSdk = O)
    protected ComponentName startForegroundServiceAsUser(Intent service, UserHandle user) {
        return startForegroundService(service);
    }

    @Implementation(minSdk = KITKAT)
    protected ComponentName startServiceAsUser(Intent service, UserHandle user) {
        return startService(service);
    }

    @Implementation(minSdk = KITKAT)
    protected boolean stopServiceAsUser(Intent service, UserHandle user) {
        return stopService(service);
    }
}
