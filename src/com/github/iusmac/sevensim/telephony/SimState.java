package com.github.iusmac.sevensim.telephony;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
    SimState.UNKNOWN,
    SimState.ENABLED,
    SimState.DISABLED
})
public @interface SimState {
    /**
     * This state means that we don't know if the SIM card is enabled or disabled.
     */
    int UNKNOWN = 0;

    /**
     * This state means that the SIM card is enabled and fully functional.
     */
    int ENABLED = 1;

    /**
     * <p>This state means that the SIM card is currently disabled.
     *
     * <p>In the context of the application, this state can be achieved within two distinct ways:
     * - by disabling the SIM subscription, if using the newer Radio Interface Layer (RIL).
     * - by powering down the modem for the corresponding SIM card slot, if using the legacy Radio
     *   Interface Layer (RIL).

     * <p>A device is considered to be using the newer RIL when the response of
     * {@link TelephonyUtils#canDisableUiccSubscription} is {@code true}.
     */
    int DISABLED = 2;
}
