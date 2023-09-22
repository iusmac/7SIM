package com.github.iusmac.sevensim.telephony;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

abstract class SubscriptionList implements Iterator<Subscription> {
    protected int mLastIndex, mCurrentIndex;
    protected Subscription mNextElementCandidate;

    /**
     * The visible list of {@link SubscriptionInfo}s including disabled ones, or {@code null} if no
     * SIM cards in the device.
     */
    protected final List<SubscriptionInfo> mVisibleSubInfoList;

    protected SubscriptionList(final SubscriptionManager subscriptionManager) {
        mVisibleSubInfoList = subscriptionManager.getSelectableSubscriptionInfoList();
    }

    /**
     * For performance effectiveness, this method has dual functionality in this iterator
     * implementation, as it also prepares the next value for the {@link #next()} during lookup.
     */
    @Override
    public abstract boolean hasNext();

    @Override
    public Subscription next() {
        // In case iterating using only next() without explicitly calling hasNext()
        if (mNextElementCandidate == null && !hasNext()) {
            throw new NoSuchElementException();
        }
        mLastIndex = mCurrentIndex + 1;
        final Subscription sub = mNextElementCandidate;
        mNextElementCandidate = null;
        return sub;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
