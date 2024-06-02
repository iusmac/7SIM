package com.github.iusmac.sevensim.telephony;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;

import com.android.internal.telephony.ITelephony;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.NotificationManager;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.telephony.Subscriptions.OnSimStatusChangedListener;

import dagger.Lazy;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.List;

/**
 * <p>This class will attempt to unlock all enabled SIM cards currently available in the system
 * using {@link PinEntity}s passed in when constructing this class via {@link Factory#create(List)}.
 *
 * <p>The work will be offloaded onto a separate thread after invoking the {@link #start()} method.
 * It's possible to start slightly before the SIM cards are in {@code PIN_REQUIRED} state; if
 * necessary, we'll wait a maximum of 10 seconds for them to enter into the PIN state.
 */
public final class SimPinFeeder extends Thread {
    private boolean mSimStatusChanged;
    private volatile boolean mReleased;
    private final SparseArrayCompat<PinEntity> mPinEntities;
    private final SparseArrayCompat<SimCard> mSimCardsCache = new SparseArrayCompat<>();

    private final Logger mLogger;
    private final Subscriptions mSubscriptions;
    private final TelephonyManager mTelephonyManager;
    private final Lazy<PinStorage> mPinStorageLazy;
    private final Lazy<NotificationManager> mNotificationManagerLazy;

    @AssistedInject
    SimPinFeeder(final Logger.Factory loggerFactory, final Subscriptions subscriptions,
            final TelephonyUtils telephonyUtils, final TelephonyManager telephonyManager,
            final Lazy<PinStorage> pinStorageLazy,
            final Lazy<NotificationManager> notificationManagerLazy,
            final @Assisted @NonNull List<PinEntity> decryptedPinEntities) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mSubscriptions = subscriptions;
        mTelephonyManager = telephonyManager;
        mPinStorageLazy = pinStorageLazy;
        mNotificationManagerLazy = notificationManagerLazy;

        // Convert to a sparse array for easier mutation and querying by subscription ID
        mPinEntities = new SparseArrayCompat<>(decryptedPinEntities.size());
        for (final PinEntity pinEntity : decryptedPinEntities) {
            if (pinEntity.isCorrupted() || pinEntity.isEncrypted()) {
                throw new AssertionError("Expected decrypted PIN entity, got: " + pinEntity);
            }
            mPinEntities.put(pinEntity.getSubscriptionId(), pinEntity);
        }
    }

    @Override
    public void run() {
        mLogger.d("Started.");

        if (mPinEntities.isEmpty()) {
            mLogger.d("Finishing earlier due to the absence of usable SIM PIN entities.");
            return;
        }

        mLogger.d("mPinEntities: %s", mPinEntities);

        final SimStatusChangedListener simStatusChangedListener = new SimStatusChangedListener();
        mSubscriptions.addOnSimStatusChangedListener(simStatusChangedListener);

        threadLoop:
        while (!mReleased) {
            refreshSimCardCacheList();

            unlockAllSimCardsLoop:
            for (int i = 0, size = mSimCardsCache.size(); !mReleased && i < size &&
                    mPinEntities.size() > 0; i++) {
                final SimCard simCard = mSimCardsCache.valueAt(i);
                PinEntity pinEntity = mPinEntities.get(simCard.getSubId());

                mLogger.v("Processing %s with PIN: %s.", simCard, pinEntity);

                if (pinEntity != null && simCard.isPinRequired()) {
                    // The SIM card is locked and requires the user's SIM PIN to unlock, but the
                    // remaining PIN attempt counter doesn't equal to 3, which means the user is
                    // racing with us and may have already tried to unlock the SIM card and made a
                    // mistake, or this could be another request to unlock all SIM cards that had
                    // previously failed, possibly due to an incorrect SIM PIN the user has provided
                    // to us. In any case, we absolutely want to *STOP* here in order to prevent
                    // things from getting critical, like putting the device into the PUK state
                    if (simCard.getPinAttemptsRemaining() < 3) {
                        mLogger.w("Aborting SIM unlock to avoid blocking SIM PIN for: %s.",
                                simCard);
                        pinEntity = null;
                        mPinEntities.remove(simCard.getSubId());
                        continue unlockAllSimCardsLoop;
                    }

                    retrySupplyPinLoop:
                    for (int retries = 1; retries <= 3; retries++) {
                        final PinResultWrapper result = simCard.supplyPin(pinEntity.getClearPin());

                        mLogger.d("Attempted to unlock %s with %s.", simCard, result);

                        switch (result.getResult()) {
                            case PinResultWrapper.PIN_RESULT_TYPE_SUCCESS:
                                // Persist on disk the PIN that was marked as invalid the last time
                                // it was used, which is now valid
                                if (pinEntity.isInvalid()) {
                                    mPinStorageLazy.get().getPin(pinEntity.getSubscriptionId())
                                        .ifPresent((encryptedPin) -> {
                                            encryptedPin.setInvalid(false);
                                            mPinStorageLazy.get().storePin(encryptedPin);
                                        });
                                }
                                break;

                            case PinResultWrapper.PIN_RESULT_TYPE_INCORRECT:
                                // Detected an incorrect SIM PIN code. Mark it as such and delegate
                                // to the PIN storage to do its job
                                pinEntity.setInvalid(true);
                                mPinStorageLazy.get().handleBadPinEntity(pinEntity);
                                break;

                            default:
                                mLogger.w("Retry attempt %d of 3 failed to unlock SIM card: %s.",
                                        retries, simCard);
                                // No idea what this was and no way to find out. Retrying... :/
                                continue retrySupplyPinLoop;
                        }

                        // PIN entity has been successfully supplied -- dropping it
                        pinEntity = null;
                        mPinEntities.remove(simCard.getSubId());
                        break retrySupplyPinLoop;
                    }

                    // For unknown reasons, the SIM card unlock failed -- at least notify the user
                    if (pinEntity != null) {
                        mLogger.e("Failed to unlock SIM card %s with PIN %s.", simCard, pinEntity);

                        mNotificationManagerLazy.get()
                            .showSimPinOperationFailedNotification(simCard.getSubscription());
                        mPinEntities.remove(simCard.getSubId());
                    }
                }
            }

            // We finish at this point since we've consumed all the usable PIN entities we had, and
            // no more SIM cards can be unlocked
            if (mPinEntities.isEmpty()) {
                break threadLoop;
            }

            if (!mReleased) {
                // Wait to be notified about new SIM state changes or finish on timeout
                synchronized (simStatusChangedListener) {
                    if (!mSimStatusChanged) {
                        try {
                            // Note that for reliability, we want to wait a maximum of 10 seconds
                            // for more SIM card state change events before finishing. Normally,
                            // these events are delivered within a second, but in some edge cases,
                            // such as under high memory pressure, delivery may be delayed even by
                            // 2-3 seconds. This also serves as a "window" to give time to the SIM
                            // cards to enter the PIN state in case we started slightly earlier
                            simStatusChangedListener.wait(10_000L);
                            if (!mSimStatusChanged) {
                                // Timed out. No more events
                                break threadLoop;
                            }
                        } catch (InterruptedException ignored) {
                            if (mReleased) {
                                break threadLoop;
                            }
                        }
                    }
                    mSimStatusChanged = false;
                }
            }
        }

        mSubscriptions.removeOnSimStatusChangedListener(simStatusChangedListener);

        mLogger.d("Finished.");
    }

    /** Gracefully stop unlocking the remaining SIM cards. */
    public void cancel() {
        mLogger.d("cancel().");

        mReleased = true;
        interrupt();
    }

    /** Refresh the list of currently enabled SIM cards in the system. */
    private void refreshSimCardCacheList() {
        mLogger.d("refreshSimCardCacheList().");

        for (final Subscription sub : mSubscriptions) {
            final int subId = sub.getId();

            mLogger.v("refreshSimCardCacheList() : Processing %s.", sub);

            if (sub.isSimEnabled()) {
                if (!mSimCardsCache.containsKey(subId)) {
                    final TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
                    mSimCardsCache.put(subId, new SimCard(sub, tm));
                }
            } else {
                mSimCardsCache.remove(subId);
            }
        }
    }

    /** A helper class representing a SIM card used to facilitate PIN unlocking. */
    private final class SimCard {
        final Subscription mSubscription;
        final TelephonyManager mTelephonyManager;

        /**
         * @param subscription The corresponding subscription instance representing the SIM card.
         * @param telephonyManager The {@link TelephonyManager} pinned to the SIM subscription ID.
         */
        SimCard(final Subscription subscription, final TelephonyManager telephonyManager) {
            mSubscription = subscription;
            mTelephonyManager = telephonyManager;
        }

        /** Return the corresponding subscription ID this SIM card is pinned to. */
        int getSubId() {
            return mSubscription.getId();
        }

        /** Return the corresponding SIM subscription instance. */
        Subscription getSubscription() {
            return mSubscription;
        }

        /**
         * Return the number of attempts at entering the PIN before the SIM will be locked and
         * require a PUK code.
         */
        int getPinAttemptsRemaining() {
            // As per AOSP, need to supply an empty PIN string to retrieve the current remaining PIN
            // attempt number. See https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r41/packages/SystemUI/src/com/android/keyguard/KeyguardSimPinView.java#139
            return supplyPin("").getAttemptsRemaining();
        }

        /**
         * Attempt to unlock the SIM using the provided PIN code and return the result as
         * {@link PinResultWrapper}. Note that, this request is <strong>blocking</strong>.
         *
         * @param pin The SIM PIN code as string.
         */
        PinResultWrapper supplyPin(final String pin) {
            if (Utils.IS_AT_LEAST_S) {
                return new PinResultWrapper(mTelephonyManager.supplyIccLockPin(pin));
            } else {
                int[] result;
                if (Utils.IS_AT_LEAST_R) {
                    result = ApiDeprecated.supplyPinReportResult(mTelephonyManager, pin);
                } else {
                    try {
                        // TelephonyManager behaves weird on pre-Android 11 (R) when attempting to
                        // unlock multiple SIM cards, i.e., only one SIM card is getting unlocked,
                        // even though the TelephonyManager is pinned to a different subId. So,
                        // we'll follow the AOSP way of eluding the TelephonyManager and directly
                        // communicate with the telephony service. See https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r41/packages/SystemUI/src/com/android/keyguard/KeyguardSimPinView.java#260
                        result = ITelephony.Stub.asInterface(ServiceManager
                                .checkService(Context.TELEPHONY_SERVICE))
                            .supplyPinReportResultForSubscriber(mSubscription.getId(), pin);
                    } catch (RemoteException e) {
                        mLogger.e("%s: RemoteException for supplyPinReportResult: %s.", this, e);
                        result = new int[0];
                    }
                }
                if (result.length > 0) {
                    return new PinResultWrapper(result[0], result[1]);
                }
            }
            return PinResultWrapper.getDefaultFailedResult();
        }

        /** Whether the SIM card is locked and requires the user's SIM PIN to unlock. */
        boolean isPinRequired() {
            return mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_PIN_REQUIRED;
        }

        @Override
        public String toString() {
            return "SimCard {"
                + " subscription=" + mSubscription
                + " slotIndex=" + SubscriptionManager.getSlotIndex(mSubscription.getId())
                + " state=" + mTelephonyManager.getSimState()
                + " isPinRequired=" + isPinRequired()
                + " pinAttemptsRemaining=" + (isPinRequired() ? getPinAttemptsRemaining() : -1)
                + " }";
        }
    }

    /**
     * The callback listener that will notify of changes to the {@link TelephonyManager.SimState}
     * for all available SIM cards when waiting on the instance of this object's monitor.
     */
    private final class SimStatusChangedListener implements OnSimStatusChangedListener {
        @Override
        public void onSimStatusChanged(final int slotIndex,
                final @TelephonyManager.SimState int state) {

            mLogger.v("onSimStatusChanged(slotIndex=%d,state=%d).", slotIndex, state);

            synchronized (this) {
                mSimStatusChanged = true;
                notify();
            }
        }
    }

    /**
     * Factory to create {@link SimPinFeeder} instances via the {@link AssistedInject} constructor.
     */
    @AssistedFactory
    public interface Factory {
        /**
         * Create a {@link SimPinFeeder} instance for the given list of unencrypted SIM subscription
         * PIN entities.
         *
         * @param decryptedPinEntities The list containing decrypted SIM subscription PIN entities
         * to be supplied to the enabled SIM cards found on the device that require them.
         * @return An instance of {@link SimPinFeeder} ready to start supplying SIM PIN codes to all
         * SIM cards that needs it after the {@link #start()} method has been invoked.
         */
        SimPinFeeder create(@NonNull List<PinEntity> decryptedPinEntities);
    }

    /**
     * Nested class to suppress warning only for API methods annotated as Deprecated.
     */
    @SuppressWarnings("deprecation")
    private static class ApiDeprecated {
        static int[] supplyPinReportResult(final TelephonyManager telephonyManager, final String pin) {
            return telephonyManager.supplyPinReportResult(pin);
        }
    }
}
