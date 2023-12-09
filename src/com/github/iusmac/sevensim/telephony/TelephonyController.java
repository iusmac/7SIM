package com.github.iusmac.sevensim.telephony;

import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>The responsibility of this class is to provide to devices using the legacy Radio Interface
 * Layer (RIL) the ability to control the enable state of individual physical (non-eUICC) SIM cards.
 *
 * <p>The enabled state of a SIM card is controlled by leveraging the public system APIs to manage
 * the power-up state of a SIM card slot.
 *
 * <p>Note that, as per {@link TelephonyManager#setSimPowerStateForSlot(int,int)}, the SIM card
 * power-up state is NOT persistent across boots. On reboot, SIM will power up normally.
 *
 * <p>A device is considered to be using the legacy RIL when the response of
 * {@link TelephonyUtils#canDisableUiccSubscription()} is {@code false}.
 *
 * <p>This class is <strong>thread-safe</strong>.
 *
 * @see SubscriptionsImplLegacy
 */
@Singleton
public final class TelephonyController {
    /**
     * Timeout used to wait for the response after performing SIM power state change request via
     * {@link #setSimPowerStateForSlot}.
     */
    private static final int SET_SIM_POWER_STATE_REQUEST_TIMEOUT_MILLIS = 3_000;

    /**
     * <p>Custom SIM power state response codes used to handle uncovered edge cases when performing
     * modem requests.
     *
     * <p>Note, the values **must** be less than zero to not collide with the system ones.
     */
    private static final int SET_SIM_POWER_STATE_SIM_ABSENT = -1;
    private static final int SET_SIM_POWER_STATE_MODEM_TIMEOUT = -2;

    /** For request metadata fields. */
    private static final String KEY_SUBSCRIPTION = "subscription";
    private static final String KEY_LAST_ACTIVATED_TIME = "last_activated_time";
    private static final String KEY_LAST_DEACTIVATED_TIME = "last_deactivated_time";

    /** The globally accessible request metadata used when performing SIM power state mutations. */
    @GuardedBy("this")
    private final Bundle mRequestMetadata = new Bundle(3);

    @GuardedBy("this")
    private SimStatusChangedListener mSimStatusChangedListener;

    private final Context mContext;
    private final Logger mLogger;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionsImplLegacy mSubscriptions;

    @Inject
    public TelephonyController(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory,
            final TelephonyManager telephonyManager,
            final SubscriptionsImplLegacy subscriptions) {

        mContext = context;
        mLogger = loggerFactory.create(getClass().getSimpleName());
        mTelephonyManager = telephonyManager;
        mSubscriptions = subscriptions;
    }

    /**
     * <p>Disable or re-enable a SIM card.
     *
     * <p>In order to be notified when the setting for the SIM card is applied, the callers must
     * monitor the {@link Subscriptions}.
     *
     * @param slotIndex The slot index to identify a SIM card whose state is being changed.
     * @param enabled {@code true} if SIM card should be enabled, {@code false} otherwise.
     */
    @WorkerThread
    public void setSimState(final int slotIndex, final boolean enabled) {
        final String logPrefix = String.format(Locale.getDefault(), "setSimState(slotIndex=%d," +
                "enabled=%s) : ", slotIndex, enabled);

        // From testing, it turned out that SIM power state change request ignores Airplane mode,
        // so we can allow disabling but not enabling request
        if (enabled && TelephonyUtils.isAirplaneModeOn(mContext)) {
            mLogger.w(logPrefix + "Aborting due to Airplane mode.");

            Utils.makeToast(mContext, mContext.getString(R.string.airplane_mode_enabled));

            mSubscriptions.notifyAllListeners();
            return;
        }

        synchronized (this) {
            mLogger.d(logPrefix + "In sync block.");

            final Subscription sub =
                mSubscriptions.getSubscriptionForSimSlotIndex(slotIndex).orElse(null);

            if (sub == null) {
                mLogger.e(logPrefix + "Aborting due to missing subscription.");
                mSubscriptions.notifyAllListeners();
                return;
            }

            // Globally save metadata needed when handling SIM power change request termination
            mRequestMetadata.putParcelable(KEY_SUBSCRIPTION, sub);

            // Keep track of SIM state whenever it's mutated. This will be persisted in a volatile
            // memory, so that we can further restore all relevant data. This because when powering
            // down the SIM is the same as removing it, which means the SIM will completely
            // disappear from the system and we won't be able to grab the subscription data from
            // SubscriptionManager anymore, even though the SIM is still present in the slot
            sub.setSimState(TelephonyUtils.simStateInt(enabled));

            mRequestMetadata.putString(KEY_LAST_ACTIVATED_TIME,
                    sub.getLastActivatedTime().toString());
            mRequestMetadata.putString(KEY_LAST_DEACTIVATED_TIME,
                    sub.getLastDeactivatedTime().toString());

            sub.setLastActivatedTime(enabled ? LocalDateTime.now() : LocalDateTime.MIN);
            sub.setLastDeactivatedTime(!enabled ? LocalDateTime.now() : LocalDateTime.MIN);

            // Before making any request, persist the subscription associated with the SIM whose
            // power state we're going to change, to immediately reflect the changes on the callers
            // side, since this request is generally successful and pretty fast. In case the SIM
            // power change request fails, everything will be reverted to the actual state and the
            // callers will be notified again
            mSubscriptions.persistSubscription(sub);

            // Start to listen to the state mutations of all available SIM cards on versions prior
            // to Android 12 (API 31). On newer versions we have an executor+callback API to handle
            // the response
            if (Utils.IS_OLDER_THAN_S) {
                mSimStatusChangedListener = new SimStatusChangedListener();
                mSubscriptions.addOnSimStatusChangedListener(mSimStatusChangedListener);
            }

            setSimPowerStateForSlot(slotIndex, simStateInt(enabled));

            try {
                wait(SET_SIM_POWER_STATE_REQUEST_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                mLogger.w(logPrefix + "Acquire wait interrupted.");
            }

            if (Utils.IS_OLDER_THAN_S) {
                mSubscriptions.removeOnSimStatusChangedListener(mSimStatusChangedListener);
                mSimStatusChangedListener = null;
            }

            if (!mRequestMetadata.isEmpty()) { // Timed out
                final int resCode;
                if (enabled) {
                    // When trying to enable SIM, but the response from modem timeouts, then we
                    // know there's no SIM card in the slot. This is an implicit edge case that
                    // needs to be handled manually, because by Android telephony design, a powered
                    // up modem won't respond if there's no SIM card
                    resCode = SET_SIM_POWER_STATE_SIM_ABSENT;
                } else {
                    // When trying to disable SIM, but the response from modem timeouts, then most
                    // likely the device modem does not support TelephonyManager#setSimPowerStateForSlot
                    // call. So far, this can occur on non-QCOM SoCs
                    resCode = SET_SIM_POWER_STATE_MODEM_TIMEOUT;
                }
                handleOnSetSimPowerStateForSlotFinished(resCode);
            }
        }
    }

    /**
     * Set SIM card power state.
     *
     * @param slotIndex The slot index to change SIM power state for.
     * @param state One of the following SIM states:
     * {@link TelephonyManager#CARD_POWER_UP}
     * {@link TelephonyManager#CARD_POWER_DOWN}
     */
    @GuardedBy("this")
    private void setSimPowerStateForSlot(final int slotIndex, final int state) {
        if (Utils.IS_AT_LEAST_S) {
            final Consumer<Integer> callback = (resCode) -> {
                synchronized (TelephonyController.this) {
                    handleOnSetSimPowerStateForSlotFinished(resCode);
                    TelephonyController.this.notifyAll();
                }
            };
            mTelephonyManager.setSimPowerStateForSlot(slotIndex, state, (runnable) ->
                    runnable.run(), callback);
        } else {
            ApiDeprecated.setSimPowerStateForSlot(mTelephonyManager, slotIndex, state);
        }
    }

    /**
     * @param resCode The response code from the modem.
     */
    @GuardedBy("this")
    private void handleOnSetSimPowerStateForSlotFinished(final int resCode) {
        final String logPrefix = String.format(Locale.getDefault(),
                "handleOnSetSimPowerStateForSlotFinished(resCode=%d) : requestMetadata=%s",
                resCode, mRequestMetadata);

        boolean shouldNotifyAllListeners = false;
        boolean requestFailed = false;
        if (Utils.IS_AT_LEAST_S) {
            switch (resCode) {
                case TelephonyManager.SET_SIM_POWER_STATE_SUCCESS: // 0
                    break;

                case TelephonyManager.SET_SIM_POWER_STATE_ALREADY_IN_STATE: // 1
                    // Although sources classify this response state as an error, but from our
                    // point of view we got what we needed. At this point treat the request as
                    // successful. However, to ensure reliability and adhere to the application
                    // design, we must notify listeners. This is necessary because there's no
                    // guarantee that the Android telephony will do that for this particular state
                    shouldNotifyAllListeners = true;
                    break;

                case TelephonyManager.SET_SIM_POWER_STATE_MODEM_ERROR: // 2
                case TelephonyManager.SET_SIM_POWER_STATE_SIM_ERROR: // 3
                case TelephonyManager.SET_SIM_POWER_STATE_NOT_SUPPORTED: // 4
                    requestFailed = true;
                    break;

                default:
                    mLogger.e(logPrefix + ". Unexpected resCode.");
            }
        } else {
            switch (resCode) {
                case TelephonyManager.SIM_STATE_PRESENT: // 11
                case TelephonyManager.SIM_STATE_ABSENT: // 1
                case SET_SIM_POWER_STATE_SIM_ABSENT:
                    break;

                case TelephonyManager.SIM_STATE_CARD_IO_ERROR: // 8
                case TelephonyManager.SIM_STATE_CARD_RESTRICTED: // 9
                case SET_SIM_POWER_STATE_MODEM_TIMEOUT:
                    requestFailed = true;
                    break;
            }
        }

        final Subscription sub = Utils.getParcelable(mRequestMetadata, KEY_SUBSCRIPTION,
                Subscription.class);

        final boolean expectedEnabled = sub.isSimEnabled();

        // Revert everything back as we know the SIM power state change request failed
        if (requestFailed) {
            Utils.makeToast(mContext, mContext.getString(R.string.sim_state_change_request_failed,
                        resCode));

            sub.setSimState(TelephonyUtils.simStateInt(!expectedEnabled));
            sub.setLastActivatedTime(LocalDateTime.parse(mRequestMetadata.getString(
                            KEY_LAST_ACTIVATED_TIME)));
            sub.setLastDeactivatedTime(LocalDateTime.parse(mRequestMetadata.getString(
                            KEY_LAST_DEACTIVATED_TIME)));

            mSubscriptions.persistSubscription(sub);

            final int revertState = simStateInt(!expectedEnabled);

            // When trying to change SIM state, but the request fails, then we must revert the SIM
            // power state to its actual state to avoid side effects on some devices, like when the
            // SIM is operational but Android telephony layer (e.g SubscriptionManager) believes
            // it's not. This behavior was discovered by manually testing the app on real devices
            // and it's likely to occur on non-QCOM SoCs
            if (Utils.IS_AT_LEAST_S) {
                mTelephonyManager.setSimPowerStateForSlot(sub.getSlotIndex(), revertState,
                        (runnable) -> runnable.run(), (x) -> {});
            } else {
                setSimPowerStateForSlot(sub.getSlotIndex(), revertState);
            }
        }

        // If the user pulled out the SIM card while attempting to enable it, there's no guarantee
        // that the Android Radio Interface Layer (RIL) will trigger subscriptions changed event.
        // To ensure reliability and adhere to the application design, we should notify listeners,
        // so they can stay tuned to actual state. At most this will notify listeners twice
        shouldNotifyAllListeners |= expectedEnabled;

        // If SIM state change request fails, then the Android Radio Interface Layer (RIL) won't
        // trigger subscriptions changed event. To adhere to the application design, it's up to us
        // to explicitly notify listeners, so they can stay tuned to actual state
        shouldNotifyAllListeners |= requestFailed;

        mLogger.d(logPrefix + ", requestFailed=%s,shouldNotifyAllListeners=%s", requestFailed,
                shouldNotifyAllListeners);

        if (shouldNotifyAllListeners) {
            mSubscriptions.notifyAllListeners();
        }

        // Nullify all the metadata as we no longer need them + this will also signal that the
        // response has been successfully handled within the timeout interval
        mRequestMetadata.clear();
    }

    /**
     * Convert SIM enabled state to its equivalent state code used in
     * {@link TelephonyManager#setSimPowerStateForSlot(int,int)}.
     */
    private int simStateInt(final boolean enabled) {
        return enabled ? TelephonyManager.CARD_POWER_UP : TelephonyManager.CARD_POWER_DOWN;
    }

    /**
     * <p>The callback listener used on versions prior to Android 12 (API 31) that will notify us of
     * changes to the {@link TelephonyManager.SimState} for all available SIM cards. This is needed
     * to determine success or failure of {@link #setSimPowerStateForSlot(int,int)} request.
     *
     * <p>The listener will react to various carrier config changes, but here we're only interested in
     * some SIM states emitted by {@link TelephonyManager#ACTION_SIM_CARD_STATE_CHANGED} action.
     *
     * <p>The listener will be unregistered after a timeout.
     */
    private class SimStatusChangedListener implements Subscriptions.OnSimStatusChangedListener {
        @Override
        public void onSimStatusChanged(final int slotIndex,
                final @TelephonyManager.SimState int state) {

            mLogger.v("onSimStatusChanged(slotIndex=%d,state=%d).", slotIndex, state);

            // Filter out all irrelevant carrier config changes and keep only those emitted after
            // the disable / re-enable action
            switch (state) {
                case TelephonyManager.SIM_STATE_PRESENT: // 11
                case TelephonyManager.SIM_STATE_ABSENT: // 1
                case TelephonyManager.SIM_STATE_CARD_IO_ERROR: // 8
                case TelephonyManager.SIM_STATE_CARD_RESTRICTED: // 9
                    break;
                default: return;
            }

            final Subscription sub = Utils.getParcelable(mRequestMetadata, KEY_SUBSCRIPTION,
                    Subscription.class);

            if (sub.getSlotIndex() != slotIndex) {
                // Since we're listening to state mutations of all available SIM cards, we can
                // hypothetically receive a concurrent update for a different SIM card than the one
                // whose SIM power state we expect to change
                return;
            }

            synchronized (TelephonyController.this) {
                handleOnSetSimPowerStateForSlotFinished(state);
                TelephonyController.this.notifyAll();
            }
        }
    }

    /**
     * Nested class to suppress warning only for API methods annotated as Deprecated.
     */
    @SuppressWarnings("deprecation")
    private static class ApiDeprecated {
        private static void setSimPowerStateForSlot(final TelephonyManager telephony,
                final int slotIndex, final int state) {

            telephony.setSimPowerStateForSlot(slotIndex, state);
        }
    }
}
