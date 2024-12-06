package com.github.iusmac.sevensim.test;

import android.telephony.TelephonyManager.SetSimPowerStateResult;
import android.telephony.PinResult;
import android.telephony.TelephonyManager;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTelephonyManager;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.O_MR1;
import static android.os.Build.VERSION_CODES.S;

@Implements(TelephonyManager.class)
public class ShadowTelephonyManagerHiddenApi extends ShadowTelephonyManager {
    private @SetSimPowerStateResult int mSimPowerStateResult =
        TelephonyManager.SET_SIM_POWER_STATE_SUCCESS;

    private PinResult mPinResult = PinResult.getDefaultFailedResult();
    private int[] mPinReportResult = new int[0];

    @Implementation(minSdk = O_MR1)
    protected void setSimPowerStateForSlot(int slotIndex, int state) {
        // No-op. The callers should control through TelephonyIntents#ACTION_SIM_STATE_CHANGED
        // broadcasts whether this request was successful or failed and timed out if needed
    }

    @Implementation(minSdk = S)
    protected void setSimPowerStateForSlot(int slotIndex, int state, Executor executor,
            Consumer<Integer> callback) {

        executor.execute(() -> callback.accept(mSimPowerStateResult));
    }

    @Implementation(minSdk = S)
    protected PinResult supplyIccLockPin(String pin) {
        return mPinResult;
    }

    @Implementation(minSdk = KITKAT)
    protected int[] supplyPinReportResult(String pin) {
        return mPinReportResult;
    }

    /** Sets the result code to be received by {@link Consumer} callback passed in
     * {@link TelephonyManager#setSimPowerState(int,int,Executor,Consumer)}. */
    public void setSimPowerStateResult(final @SetSimPowerStateResult int resultCode) {
        mSimPowerStateResult = resultCode;
    }

    /** Sets the {@link PinResult} to be returned by {@link TelephonyManager#supplyIccLockPin}. */
    public void setSupplyIccLockPinPinResult(final PinResult pinResult) {
        mPinResult = pinResult;
    }

    /**
     * Sets the {@link PinResult.PinResultType} and the number of PIN attempts remaining to be
     * returned by {@link TelephonyManager#supplyPinReportResult}.
     */
    public void setSupplyPinReportResult(final int[] pinReportResult) {
        mPinReportResult = pinReportResult;
    }
}
