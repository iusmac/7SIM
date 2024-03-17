package com.github.iusmac.sevensim.telephony;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.telecom.ConnectionService;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.github.iusmac.sevensim.Utils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public final class TelephonyUtils {
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubManager;
    private final Provider<TelecomManager> mTelecomManagerProvider;
    private final Provider<AudioManager> mAudioManagerProvider;
    private final boolean mHasUiccSubscriptionToggleCapability;

    @Inject
    public TelephonyUtils(final TelephonyManager telephonyManager,
            final SubscriptionManager subscriptionManager,
            final Provider<TelecomManager> telecomManagerProvider,
            final Provider<AudioManager> audioManagerProvider,
            final @Named("Telephony/UiccSubscriptionToggleCapabilityDisabledSetting")
                boolean isUiccSubscriptionToggleCapabilityDisabled) {

        mTelephonyManager = telephonyManager;
        mSubManager = subscriptionManager;
        mTelecomManagerProvider = telecomManagerProvider;
        mAudioManagerProvider = audioManagerProvider;
        mHasUiccSubscriptionToggleCapability =
            hasUiccSubscriptionToggleCapability(isUiccSubscriptionToggleCapabilityDisabled);
    }

    /**
     * Return a platform configuration for this device, indicating whether it has capability to
     * disable / re-enable a subscription on a physical (non-eUICC) SIM, or aka non-programmable
     * SIM.
     *
     * @param isUiccSubscriptionToggleCapabilityDisabled Whether to override device's ability to
     * disable / re-enable a subscription on a pSIM, even if availability is configured by the
     * platform.
     */
    private boolean hasUiccSubscriptionToggleCapability(
            final boolean isUiccSubscriptionToggleCapabilityDisabled) {

        if (isUiccSubscriptionToggleCapabilityDisabled) {
            return false;
        }

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
     * <p>Whether there's an ongoing phone call in progress.
     *
     * <p>Note that, this method relies on {@link TelecomManager#isInCall()} to supply an aggregate
     * "in call" state for the entire device, but it may still resolve to {@code true} if any app is
     * using voice communication; this is because {@link TelecomManager} doesn't handle the case
     * where some apps don't implement {@link ConnectionService}.
     */
    public boolean isInCall() {
        try {
            return mTelecomManagerProvider.get().isInCall() || hasVoiceCall();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    /**
     * Whether any app is using voice communication.
     */
    private boolean hasVoiceCall() {
        try {
            final int audioMode = mAudioManagerProvider.get().getMode();
            return audioMode == AudioManager.MODE_IN_CALL
                || audioMode == AudioManager.MODE_IN_COMMUNICATION;
        } catch (Exception ignored) {
            return false;
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
     * @param context The context to access content resolver.
     * @return {@code true} if the "Airplane mode" is enabled, otherwise {@code false}.
     */
    static boolean isAirplaneModeOn(final Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
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
