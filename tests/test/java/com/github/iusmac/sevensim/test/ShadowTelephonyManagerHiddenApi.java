package com.github.iusmac.sevensim.test;

import android.telephony.TelephonyManager.SetSimPowerStateResult;
import android.telephony.TelephonyManager;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTelephonyManager;

import static android.os.Build.VERSION_CODES.S;

@Implements(TelephonyManager.class)
public class ShadowTelephonyManagerHiddenApi extends ShadowTelephonyManager {
    private @SetSimPowerStateResult int mSimPowerStateResult =
        TelephonyManager.SET_SIM_POWER_STATE_SUCCESS;

    @Implementation(minSdk = S)
    protected void setSimPowerStateForSlot(int slotIndex, int state, Executor executor,
            Consumer<Integer> callback) {

        executor.execute(() -> callback.accept(mSimPowerStateResult));
    }
}
