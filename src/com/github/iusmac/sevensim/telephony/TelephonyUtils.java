package com.github.iusmac.sevensim.telephony;

import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.github.iusmac.sevensim.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class TelephonyUtils {
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubManager;
    private final boolean mHasUiccSubscriptionToggleCapability;

    @Inject
    public TelephonyUtils(final TelephonyManager telephonyManager,
            final SubscriptionManager subscriptionManager) {

        mTelephonyManager = telephonyManager;
        mSubManager = subscriptionManager;
        mHasUiccSubscriptionToggleCapability = hasUiccSubscriptionToggleCapability();
    }

    /**
     * Return a platform configuration for this device, indicating whether it has capability to
     * disable / re-enable a subscription on a physical (non-eUICC) SIM, or aka non-programmable
     * SIM.
     */
    private boolean hasUiccSubscriptionToggleCapability() {
        // Subscription toggling support was re-added in Android R
        if (Utils.IS_AT_LEAST_R) {
            return mSubManager.canDisablePhysicalSubscription();
        }
        return false;
    }

    /**
     * Check whether this device can disable / re-enable a subscription on a physical (non-eUICC)
     * SIM, or aka non-programmable SIM.
     */
    public boolean canDisableUiccSubscription() {
        return mHasUiccSubscriptionToggleCapability;
    }

    /**
     * <p>The total number of available SIM slots on the device.
     *
     * <p>More specifically, this number represents how many logical modems are currently configured
     * to be active.
     *
     * <p>Possible values are:
     * <ul>
     * <li>0 when none of voice, SMS, data are not supported.</li>
     * <li>1 for Single standby mode (Single SIM functionality).</li>
     * <li>2 for Dual standby mode (Dual SIM functionality).</li>
     * <li>3 for Tri standby mode (Tri SIM functionality).</li>
     * </ul>
     */
    int getActiveSlotCount() {
        if (Utils.IS_AT_LEAST_R) {
            return mTelephonyManager.getActiveModemCount();
        } else {
            return ApiDeprecated.getPhoneCount(mTelephonyManager);
        }
    }

    /**
     * Convert SIM enabled state to its equivalent {@link SimState} code for internal usages.
     */
    static @SimState int simStateInt(final boolean enabled) {
        return enabled ? SimState.ENABLED : SimState.DISABLED;
    }

    /**
     * Get string representing a {@link SimState} code.
     */
    static @NonNull String simStateToString(final @SimState int simState) {
        switch (simState) {
            case SimState.UNKNOWN: return "UNKNOWN";
            case SimState.ENABLED: return "ENABLED";
            case SimState.DISABLED: return "DISABLED";
            default: return "UNKNOWN(" + simState + ")";
        }
    }

    /**
     * Nested class to suppress warnings only for API methods annotated as Deprecated.
     */
    @SuppressWarnings("deprecation")
    private static final class ApiDeprecated {
        private static int getPhoneCount(final TelephonyManager telephony) {
            return telephony.getPhoneCount();
        }
    }
}
