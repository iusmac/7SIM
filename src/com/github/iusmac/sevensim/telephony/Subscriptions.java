package com.github.iusmac.sevensim.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.android.internal.telephony.PhoneConstants;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.Utils;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import javax.inject.Named;

/**
 * <p>Basic implementation used to provide all business-related information about available
 * subscriptions found on the device using {@link SubscriptionManager}.
 *
 * <p>The majority of the information about subscriptions is obtained from {@link SubscriptionInfo}
 * DTOs provided by the {@link SubscriptionManager}. Some information is produced internally by the
 * application business-logic. All subscription information is packed in {@link Subscription} data
 * transfer objects (DTOs).
 *
 * <p>The client can obtain all relevant subscription data as {@link Subscription} DTOs directly by
 * iterating over this class, which provides an {@link Iterable} interface, or by calling
 * appropriate public APIs.
 *
 * @see SubscriptionsImpl
 * @see SubscriptionsImplLegacy
 */
public abstract class Subscriptions implements Iterable<Subscription> {
    /**
     * The list of interested clients that are notified of changes to the subscriptions data.
     */
    private final CopyOnWriteArrayList<OnSubscriptionsChangedListener>
        mOnSubscriptionsChangedListeners = new CopyOnWriteArrayList<>();

    /**
     * The list of interested clients that are notified of state changes to all available SIM cards.
     */
    private final CopyOnWriteArrayList<OnSimStatusChangedListener>
        mOnSimStatusChangedListeners = new CopyOnWriteArrayList<>();

    /**
     * The listener of the {@link SubscriptionManager} that will notify us of any changes to
     * {@link SubscriptionInfo} records.
     */
    private final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionManagerListener;

    /** The receiver that will notify us of various carrier config changes. */
    private final BroadcastReceiver mCarrierConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            mLogger.v("onReceive() : intent=" + intent);

            switch (intent.getAction()) {
                case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED:
                case TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED:
                    final int slotIndex = intent.getIntExtra(PhoneConstants.SLOT_KEY, -1);
                    final int state = intent.getIntExtra(TelephonyManager.EXTRA_SIM_STATE,
                            TelephonyManager.SIM_STATE_UNKNOWN);
                    dispatchOnSimStatusChanged(slotIndex, state);
            }
        }
    };

    /**
     * Atomic flag indicating whether the listener of the {@link SubscriptionManager} has been
     * registered or not.
     */
    private final AtomicBoolean mSubscriptionManagerListenerRegistered = new AtomicBoolean();

    /**
     * Atomic flag indicating whether the listener of various carrier config changes has been
     * registered or not.
     */
    private final AtomicBoolean mCarrierConfigChangedReceiverRegistered = new AtomicBoolean();

    private final Context mContext;
    protected final Logger mLogger;
    protected final SubscriptionManager mSubscriptionManager;
    private final SysProp mSubscriptionStateSysProp;
    private final SysProp mUsableSubIdsSysProp;
    protected final SubscriptionsDao mSubscriptionsDao;

    public Subscriptions(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory, final AppDatabaseDE appDatabase,
            final SubscriptionManager subscriptionManager,
            final @Named("Telephony/SubState") SysProp subStateSysProp,
            final @Named("Telephony/UsableSubIds") SysProp usableSimSubIdsSysProp) {

        mContext = context;
        mLogger = loggerFactory.create(getClass().getSimpleName());
        mSubscriptionManager = subscriptionManager;
        mSubscriptionStateSysProp = subStateSysProp;
        mUsableSubIdsSysProp = usableSimSubIdsSysProp;
        mSubscriptionsDao = appDatabase.subscriptionsDao();

        mSubscriptionManagerListener =
            new SubscriptionManager.OnSubscriptionsChangedListener(mContext.getMainLooper()) {
                @Override
                public void onSubscriptionsChanged() {
                    dispatchOnSubscriptionInfoRecordsChanged();
                }
            };
    }

    /**
     * Return an iterator allowing iteration over all available subscriptions found on the device
     * using {@link SubscriptionManager}.
     */
    @Override
    public abstract Iterator<Subscription> iterator();

    /**
     * Get a subscription satisfying the custom predicate.
     *
     * @param predicate The predicate to test.
     * @return An Optional containing the {@link Subscription}, if any.
     */
    @CallSuper
    public Optional<Subscription> getSubscription(
            final @NonNull Predicate<Subscription> predicate) {

        for (Subscription sub : this) {
            if (predicate.test(sub)) {
                return Optional.of(sub);
            }
        }
        return Optional.empty();
    }

    /**
     * Get subscription by subscription ID.
     *
     * @param subId The corresponding subscription ID.
     * @return An Optional containing the {@link Subscription}, if any.
     */
    @CallSuper
    public Optional<Subscription> getSubscriptionForSubId(final int subId) {
        return getSubscription((sub) -> sub.getId() == subId);
    }

    /**
     * Create a {@link Subscription} DTO with all business-related information based on a
     * {@link SubscriptionInfo} DTO produced by the {@link SubscriptionManager}.
     *
     * @param subInfo The {@link SubscriptionInfo} to extract data from.
     * @return An instance of {@link Subscription} with all business-related information.
     */
    @CallSuper
    @WorkerThread
    protected Subscription createSubscription(final @NonNull SubscriptionInfo subInfo) {
        final Subscription subscription = new Subscription();
        subscription.setId(subInfo.getSubscriptionId());
        subscription.setIconTint(subInfo.getIconTint());
        Optional.ofNullable(subInfo.getDisplayName()).ifPresent((name) ->
                subscription.setSimName(name.toString()));

        mSubscriptionsDao.findBySubscriptionId(subInfo.getSubscriptionId()).ifPresent((sub) -> {
            subscription.setLastActivatedTime(sub.getLastActivatedTime());
            subscription.setLastDeactivatedTime(sub.getLastDeactivatedTime());
            subscription.keepDisabledAcrossBoots(sub.getKeepDisabledAcrossBoots());
        });

        return subscription;
    }

    /**
     * Persist a snapshot of the {@link Subscription} on disk.
     *
     * @param sub An instance of {@link Subscription} to persist.
     */
    @CallSuper
    @WorkerThread
    protected void persistSubscription(final Subscription sub) {
        mLogger.v("persistSubscription(sub=%s).", sub);

        // Persist the SIM subscription enabled state in volatile memory to be able to detect
        // alterations from outside
        persistSubscriptionState(sub.getId(), sub.getSimState());

        mSubscriptionsDao.insertOrUpdate(sub);
    }

    /**
     * <p>Add a callback to be invoked when subscription information mutates.
     *
     * <p>More specifically, the callback will be triggered when {@link Subscription} information
     * will change. The events that may produce a new {@link Subscription} are:
     * <ul>
     * <li>{@link SubscriptionInfo} records change.</li>
     * <li>internal subscription-related information produced by business-logic.</li>
     * </ul>
     *
     * <p>The listener method will also be triggered once initially when calling this function.
     *
     * <p>To remove, use {@link #removeOnSubscriptionsChangedListener(OnSubscriptionsChangedListener)}.
     *
     * @param listener The listener to add.
     */
    public void addOnSubscriptionsChangedListener(
            final @NonNull OnSubscriptionsChangedListener listener) {

        mLogger.v("addOnSubscriptionsChangedListener().");

        if (listener == null) {
            return;
        }

        if (!mOnSubscriptionsChangedListeners.contains(listener)) {
            mOnSubscriptionsChangedListeners.add(listener);

            if (!mSubscriptionManagerListenerRegistered.getAndSet(true)) {
                registerSubscriptionManagerListener();
            } else {
                // Follow SubscriptionManager behavior on initial triggering once per client
                listener.onSubscriptionsChanged();
            }
        }
    }

    /**
     * Remove a previously added callback used to be invoked when subscription information mutates.
     *
     * @param listener The listener to remove.
     */
    public void removeOnSubscriptionsChangedListener(
            final @NonNull OnSubscriptionsChangedListener listener) {

        mLogger.v("removeOnSubscriptionsChangedListener().");

        if (listener == null) {
            return;
        }

        mOnSubscriptionsChangedListeners.remove(listener);

        // Stop listening for the SubscriptionManager if the last listener unsubscribed
        if (mOnSubscriptionsChangedListeners.isEmpty()) {
            unregisterSubscriptionManagerListener();
        }
    }

    /**
     * <p>Sync the internal state of all SIM subscriptions.
     *
     * <p>In this method, we're mainly interested in the SIM subscription state divergences from the
     * expected one. For instance, the user may alterate the SIM state from outside via the "Use
     * SIM" toggle switch on the SIM details page in the built-in Settings app.
     *
     * <p>Here, we also expect to capture the SIM subscription addition/removal on SIM card
     * insertion/ejection.
     *
     * <p>If the SIM subscriptions are altered within the app, this method should short-circuit the
     * relative logic.
     *
     * @param dateTime The date-time of when the change occurred.
     */
    @WorkerThread
    public void syncSubscriptions(final LocalDateTime dateTime) {
        final List<String> removedSubIds = new ArrayList<>(getPersistedUsableSubIds());
        final List<String> usableSubIds = new ArrayList<>();

        // Process SIM subscriptions available on the device
        for (final Subscription sub : this) {
            final @SimState int currentSubState = sub.getSimState();
            final @SimState int expectedSubState = getPersistedSubscriptionState(sub.getId());
            final String subId = String.valueOf(sub.getId());
            final boolean existsInUsableList = removedSubIds.remove(subId);

            mLogger.d("syncSubscriptions(dateTime=%s) : %s,currentSubState=%s," +
                    "expectedSubState=%s,existsInUsableList=%s.", dateTime, sub,
                    TelephonyUtils.simStateToString(currentSubState),
                    TelephonyUtils.simStateToString(expectedSubState), existsInUsableList);

            if (currentSubState != expectedSubState) {
                if (existsInUsableList) {
                    // Update the last activated/deactivated times on SIM subscription state
                    // alterations from outside. This is because we expect the user preference to
                    // take precedence over schedules. For instance, if the SIM subscription is
                    // expected to be disabled, but the user enabled it manually, then we want to
                    // maintain the state within the allowed period
                    sub.setLastActivatedTime(sub.isSimEnabled() ? dateTime : LocalDateTime.MIN);
                    sub.setLastDeactivatedTime(!sub.isSimEnabled() ? dateTime : LocalDateTime.MIN);
                    persistSubscription(sub);
                } else {
                    // Persist the actual SIM subscription enabled state in volatile memory to be
                    // able to detect alterations from outside
                    persistSubscriptionState(sub.getId(), sub.getSimState());
                }
            }

            usableSubIds.add(subId);
        }

        // Persist the updated the list of usable SIM subscription IDs in volatile memory
        persistUsableSubIds(usableSubIds);

        // Process the list of SIM subscription IDs that doesn't exist anymore in the system
        for (final String subId : removedSubIds) {
            try {
                final int subIdInt = Integer.parseInt(subId);
                final Optional<Subscription> sub = mSubscriptionsDao.findBySubscriptionId(subIdInt);

                sub.ifPresent((sub1) -> {
                    mLogger.d("syncSubscriptions(dateTime=%s) : %s.", dateTime, sub1);

                    // Reset the last activated/deactivated times on SIM subscription removal. This
                    // is because we expect the schedules to take precedence over user preference
                    // when re-inserted
                    sub1.setLastActivatedTime(LocalDateTime.MIN);
                    sub1.setLastDeactivatedTime(LocalDateTime.MIN);
                    sub1.setSimState(SimState.UNKNOWN);
                    persistSubscription(sub1);
                });

                if (!sub.isPresent()) {
                    // Reset the SIM subscription enabled state in volatile memory to be able to
                    // detect alterations from outside when re-inserted
                    persistSubscriptionState(subIdInt, SimState.UNKNOWN);
                }
            } catch (NumberFormatException e) {
                mLogger.e("syncSubscriptions(dateTime=%s) : Invalid subscription ID: %s.",
                        dateTime, subId);
            }
        }
    }

    /**
     * <p>Start listening for the {@link SubscriptionManager} that will notify us of any changes to
     * {@link SubscriptionInfo} records used then to create {@link Subscription} DTOs for
     * application business-logic.
     *
     * <p>To unregister, use {@link #unregisterSubscriptionManagerListener()}.
     */
    private void registerSubscriptionManagerListener() {
        mLogger.v("registerSubscriptionManagerListener().");

        if (Utils.IS_AT_LEAST_S) {
            mSubscriptionManager.addOnSubscriptionsChangedListener(
                    mSubscriptionManagerListener.getHandlerExecutor(),
                    mSubscriptionManagerListener);
        } else {
            ApiDeprecated.addOnSubscriptionsChangedListener(mSubscriptionManager,
                    mSubscriptionManagerListener);
        }
    }

    /**
     * Unregister the listener for the {@link SubscriptionManager} registered before.
     */
    private void unregisterSubscriptionManagerListener() {
        mLogger.v("unregisterSubscriptionManagerListener().");

        mSubscriptionManager.removeOnSubscriptionsChangedListener(mSubscriptionManagerListener);

        mSubscriptionManagerListenerRegistered.set(false);
    }

    /**
     * Dispatch the {@link SubscriptionInfo} records changed event triggered by
     * {@link SubscriptionManager.OnSubscriptionsChangedListener}.
     */
    private void dispatchOnSubscriptionInfoRecordsChanged() {
        mLogger.v("dispatchOnSubscriptionInfoRecordsChanged().");

        notifyAllListeners();
    }

    /**
     * <p>Notify interested clients subscribed using
     * {@link #addOnSubscriptionsChangedListener(OnSubscriptionsChangedListener)} about changes to
     * subscription information.
     *
     * <p>The client can obtain all relevant subscription data as {@link Subscription} DTOs directly
     * by iterating over this class, which provides an {@link Iterable} interface, or by calling
     * appropriate public APIs.
     */
    protected void notifyAllListeners() {
        mLogger.v("notifyAllListeners().");

        // Note, because of the use of CopyOnWriteArrayList, we *must* use an iterator to perform
        // the subscription data change dispatching. The iterator is a safe guard against listeners
        // that could mutate the list by calling the various add/remove methods. This prevents the
        // array from being modified while we iterate it
        for (OnSubscriptionsChangedListener listener : mOnSubscriptionsChangedListeners) {
            if (listener != null) {
                listener.onSubscriptionsChanged();
            }
        }
    }

    /**
     * Dispatch the event when {@link TelephonyManager.SimState} for a particular SIM mutates.
     *
     * @param slotIndex The corresponding SIM slot index whose state has been changed.
     * @param state The SIM state code.
     */
    private void dispatchOnSimStatusChanged(final int slotIndex,
            final @TelephonyManager.SimState int state) {

        mLogger.v("dispatchOnSimStatusChanged(slotIndex=%d,state=%d).", slotIndex, state);

        // Note, because of the use of CopyOnWriteArrayList, we *must* use an iterator to perform
        // the subscriptions data change dispatching. The iterator is a safe guard against
        // listeners that could mutate the list by calling the various add/remove methods. This
        // prevents the array from being modified while we iterate it
        for (OnSimStatusChangedListener listener : mOnSimStatusChangedListeners) {
            if (listener != null) {
                listener.onSimStatusChanged(slotIndex, state);
            }
        }
    }

    /**
     * <p>Add a callback to be invoked when {@link TelephonyManager.SimState} for a particular SIM
     * mutates.
     *
     * <p>To remove, use {@link #removeOnSimStatusChangedListener}.
     *
     * @param listener The listener to add.
     */
    public void addOnSimStatusChangedListener(final OnSimStatusChangedListener listener) {
        mLogger.v("addOnSimStatusChangedListener().");

        if (listener == null) {
            return;
        }

        if (!mOnSimStatusChangedListeners.contains(listener)) {
            mOnSimStatusChangedListeners.add(listener);

            if (!mCarrierConfigChangedReceiverRegistered.getAndSet(true)) {
                registerCarrierConfigChangedReceiver();
            }
        }
    }

    /**
     * Remove a previously added callback used to be invoked when {@link TelephonyManager.SimState}
     * for a particular SIM change.
     *
     * @param listener The listener to remove.
     */
    public void removeOnSimStatusChangedListener(final OnSimStatusChangedListener listener) {
        mLogger.v("removeOnSimStatusChangedListener().");

        if (listener == null) {
            return;
        }

        mOnSimStatusChangedListeners.remove(listener);

        // Stop the receiver if the last listener unsubscribed
        if (mOnSimStatusChangedListeners.isEmpty()) {
            unregisterCarrierConfigChangedReceiver();
        }
    }

    /**
     * Start the receiver that will notify us of various carrier config changes.
     */
    private void registerCarrierConfigChangedReceiver() {
        mLogger.v("registerCarrierConfigChangedReceiver().");

        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        ContextCompat.registerReceiver(mContext, mCarrierConfigChangedReceiver, filter,
                ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * Stop the receiver registered before used to track various  carrier config changes.
     */
    private void unregisterCarrierConfigChangedReceiver() {
        mLogger.v("unregisterCarrierConfigChangedReceiver().");

        mContext.unregisterReceiver(mCarrierConfigChangedReceiver);

        mCarrierConfigChangedReceiverRegistered.set(false);
    }

    /**
     * Get the SIM subscription state previously persisted in volatile memory.
     *
     * @param subId The corresponding SIM subscription ID.
     * @return The SIM subscription state or {@link SimState.UNKNOWN}.
     */
    private @SimState int getPersistedSubscriptionState(final int subId) {
        return mSubscriptionStateSysProp.get(Optional.empty(), subId).map((value) -> {
            try {
                final int state = Integer.parseInt(value);
                switch (state) {
                    case SimState.ENABLED:
                    case SimState.DISABLED:
                    case SimState.UNKNOWN:
                        return state;
                }
            } catch (NumberFormatException e) {}

            mLogger.e("getPersistedSubscriptionState(subId=%d) : Invalid subscription state: %s.",
                    subId, value);

            return null;
        }).orElse(SimState.UNKNOWN);
    }

    /**
     * Persist the SIM subscription state in volatile memory.
     *
     * @param subId The corresponding SIM subscription ID.
     * @param state One of {@link SimState}s.
     */
    private void persistSubscriptionState(final int subId, final @SimState int state) {
        mLogger.d("persistSubscriptionState(subId=%d,state=%s).", subId,
                TelephonyUtils.simStateToString(state));

        mSubscriptionStateSysProp.set(Optional.of(Integer.toString(state)), subId);
    }

    /**
     * Get the list of SIM subscription IDs previously persisted in volatile memory.
     *
     * @return An immutable list of usable SIM subscription IDs.
     */
    private List<String> getPersistedUsableSubIds() {
        final String[] subIds = mUsableSubIdsSysProp.get(Optional.empty()).map((str) ->
                TextUtils.split(str, ",")).orElseGet(() -> new String[]{});
        return Arrays.asList(subIds);
    }

    /**
     * Persist the comma-separated list of usable SIM subscriptions IDs in volatile memory.
     *
     * @param usableSubIds The list of usable SIM subscription IDs.
     */
    private void persistUsableSubIds(final List<String> usableSubIds) {
        mUsableSubIdsSysProp.set(Optional.ofNullable(usableSubIds.isEmpty() ? null :
                    String.join(",", usableSubIds)));
    }

    /**
     * <p>The functional interface through which interested clients are notified of data changes to
     * subscription information. The onSubscriptionsChanged method will also be triggered once
     * initially when calling this function.
     *
     * <p>The client can obtain all relevant subscription data as {@link Subscription} DTOs directly
     * by iterating over this class, which provides an {@link Iterable} interface, or by calling
     * appropriate public APIs.
     */
    @FunctionalInterface
    public interface OnSubscriptionsChangedListener {
        public void onSubscriptionsChanged();
    }

    /**
     * The functional interface through which interested clients are notified of
     * {@link TelephonyManager.SimState} changes to a particular SIM card.
     */
    @FunctionalInterface
    public interface OnSimStatusChangedListener {
        public void onSimStatusChanged(int slotIndex, @TelephonyManager.SimState int state);
    }

    /**
     * Nested class to suppress warnings only for API methods annotated as Deprecated.
     */
    @SuppressWarnings("deprecation")
    private static final class ApiDeprecated {
        static void addOnSubscriptionsChangedListener(final SubscriptionManager subManager,
                final SubscriptionManager.OnSubscriptionsChangedListener listener) {

            subManager.addOnSubscriptionsChangedListener(listener);
        }
    }
}
