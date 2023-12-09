package com.github.iusmac.sevensim.telephony;

import android.telephony.SubscriptionManager;

import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.Logger;

import java.time.LocalDateTime;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>The responsibility of this class is to provide to devices using the newer Radio Interface
 * Layer (RIL) the ability to control the subscription state on individual physical (non-eUICC) SIM
 * cards.
 *
 * <p>The enabled state of a SIM card is controlled by leveraging the public system APIs to manage
 * the subscriptions.
 *
 * <p>Note that, as per {@link SubscriptionManager#setUiccApplicationsEnabled(int,boolean)}, the
 * state will be remembered on the subscription and will persist across boots.
 *
 * <p>A device is considered to be using the newer RIL when the response of
 * {@link TelephonyUtils#canDisableUiccSubscription} is {@code true}.
 *
 * <p>This class is <strong>thread-safe</strong>.
 *
 * @see SubscriptionsImpl
 */
@Singleton
public final class SubscriptionController {
    private final Logger mLogger;
    private final SubscriptionManager mSubManager;
    private final SubscriptionsImpl mSubscriptions;

    @Inject
    public SubscriptionController(final Logger.Factory loggerFactory,
            final SubscriptionManager subscriptionManager,
            final SubscriptionsImpl subscriptions) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mSubManager = subscriptionManager;
        mSubscriptions = subscriptions;
    }

    /**
     * <p>Disable or re-enable a subscription.
     *
     * <p>In order to be notified when the setting for the subscription is applied, the callers must
     * monitor the {@link Subscriptions}.
     *
     * @param subId The subscription ID whose state is being changed.
     * @param enabled {@code true} if the subscription should be enabled, otherwise {@code false}.
     */
    @WorkerThread
    public void setUiccApplicationsEnabled(final int subId, final boolean enabled) {
        final String logPrefix = String.format(Locale.getDefault(), "setUiccApplicationsEnabled(" +
                "subId=%d,enabled=%s)", subId, enabled);

        mLogger.d(logPrefix);

        final Subscription sub = mSubscriptions.getSubscriptionForSubId(subId).orElse(null);

        if (sub == null) {
            mLogger.e(logPrefix + " Aborting due to missing subscription.");
            mSubscriptions.notifyAllListeners();
            return;
        }

        sub.setSimState(TelephonyUtils.simStateInt(enabled));
        sub.setLastActivatedTime(enabled ? LocalDateTime.now() : LocalDateTime.MIN);
        sub.setLastDeactivatedTime(!enabled ? LocalDateTime.now() : LocalDateTime.MIN);
        mSubscriptions.persistSubscription(sub);

        mSubManager.setUiccApplicationsEnabled(subId, enabled);
    }
}
