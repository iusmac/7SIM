package com.github.iusmac.sevensim.telephony;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.SysProp;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@inheritDoc}
 *
 * <p>The responsibility of this class is to provide to devices using the newer Radio Interface
 * Layer (RIL) to disable/re-enable a subscription on a physical (non-eUICC) SIM, all
 * business-related information about available subscriptions found on the device using
 * {@link SubscriptionManager}.
 *
 * <p>A device is considered to be using the newer RIL when the response of
 * {@link TelephonyUtils#canDisableUiccSubscription} is {@code true}.
 */
@Singleton
public final class SubscriptionsImpl extends Subscriptions {
    @Inject
    public SubscriptionsImpl(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory, final AppDatabaseDE appDatabase,
            final SubscriptionManager subscriptionManager,
            final @Named("Telephony/SubState") SysProp subStateSysProp,
            final @Named("Telephony/UsableSubIds") SysProp usableSimSubIdsSysProp) {

        super(context, loggerFactory, appDatabase, subscriptionManager, subStateSysProp,
                usableSimSubIdsSysProp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WorkerThread
    public Iterator<Subscription> iterator() {
        return new SubscriptionList(mSubscriptionManager) {
            /**
             * {@inheritDoc}
             *
             * <p>Look up for the next enabled/disabled non-eUICC subscription.
             */
            @Override
            public boolean hasNext() {
                if (mVisibleSubInfoList != null) {
                    for (int i = mLastIndex; i < mVisibleSubInfoList.size(); i++) {
                        final SubscriptionInfo subInfo;
                        if (!(subInfo = mVisibleSubInfoList.get(i)).isEmbedded()) {
                            mNextElementCandidate = createSubscription(subInfo);
                            mCurrentIndex = i;
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WorkerThread
    protected Subscription createSubscription(final SubscriptionInfo subInfo) {
        final Subscription sub = super.createSubscription(subInfo);

        // Note that, we intentionally don't assign the slot index for the subscription here,
        // because from "real life" testing, it turned out that a disabled subscription will no
        // longer have its associated slot index, instead the subInfo.getSlotIndex() will return -1,
        // while the SIM card is still inserted. So, in the context of this application it's
        // completely pointless to rely on it, and will only lead to bugs. The subscription ID is
        // the only reliable way to identify a SIM subscription on devices using the newer RIL,
        // regardless of its enabled state

        sub.setSimState(TelephonyUtils.simStateInt(subInfo.areUiccApplicationsEnabled()));

        return sub;
    }
}
