package com.github.iusmac.sevensim.ui.sim;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.scheduler.SubscriptionSchedulerSummaryBuilder;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.SubscriptionController;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.TelephonyController;

import dagger.Lazy;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.time.LocalDateTime;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;

public final class SimListViewModel extends ViewModel {
    private final MutableLiveData<SparseArrayCompat<SimEntry>>
        mMutableSimEntries = new MutableLiveData<>();

    private final Logger mLogger;
    private final Subscriptions mSubscriptions;
    private final Lazy<SubscriptionController> mSubscriptionControllerLazy;
    private final Lazy<TelephonyController> mTelephonyControllerLazy;
    private final SubscriptionSchedulerSummaryBuilder mSubscriptionSchedulerSummaryBuilder;

    private final Handler mHandler;

    @AssistedInject
    public SimListViewModel(final Logger.Factory loggerFactory,
            final Subscriptions subscriptions,
            final Lazy<SubscriptionController> subscriptionControllerLazy,
            final Lazy<TelephonyController> telephonyControllerLazy,
            final SubscriptionSchedulerSummaryBuilder subscriptionSchedulerSummaryBuilder,
            final @Assisted Looper looper) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mSubscriptions = subscriptions;
        mSubscriptionControllerLazy = subscriptionControllerLazy;
        mTelephonyControllerLazy = telephonyControllerLazy;
        mSubscriptionSchedulerSummaryBuilder = subscriptionSchedulerSummaryBuilder;

        mHandler = Handler.createAsync(looper);
    }

    LiveData<SparseArrayCompat<SimEntry>> getSimEntries() {
        return mMutableSimEntries;
    }

    /**
     * Refresh the live SIM entry list.
     */
    @WorkerThread
    void refreshSimEntries() {
        final SparseArrayCompat<SimEntry> simEntries = new SparseArrayCompat<>();
        final LocalDateTime now = LocalDateTime.now();
        for (Subscription sub : mSubscriptions) {
            mLogger.v("refreshSimEntries() : %s.", sub);

            // Note that, in order to uniquely identify the SIM entry, we use the slot index if
            // the subscription has one, otherwise we fallback to the subscription ID
            final int id = sub.getSlotIndex() == INVALID_SIM_SLOT_INDEX ? sub.getId() :
                sub.getSlotIndex();

            final CharSequence nextUpcomingScheduleSummary = mSubscriptionSchedulerSummaryBuilder
                .buildNextUpcomingSubscriptionScheduleSummary(sub, now);

            simEntries.put(id, new SimEntry(sub, nextUpcomingScheduleSummary));
        }
        mMutableSimEntries.postValue(simEntries);
    }

    /**
     * @param simEntryId The SIM entry ID whose enabled state has been changed.
     * @param enabled {@code true} if SIM card should be enabled, {@code false} otherwise.
     */
    void handleOnSimEnabledStateChanged(final int simEntryId, final boolean enabled) {
        mLogger.d("handleOnSimStateChanged(simEntryId=%d,enabled=%s).", simEntryId, enabled);

        final SimEntry simEntry = mMutableSimEntries.getValue().get(simEntryId);
        final Subscription sub = simEntry.getSubscription();

        mHandler.post(() -> {
            if (sub.getSlotIndex() == INVALID_SIM_SLOT_INDEX) {
                mSubscriptionControllerLazy.get().setUiccApplicationsEnabled(sub.getId(), enabled);
            } else {
                mTelephonyControllerLazy.get().setSimState(sub.getSlotIndex(), enabled,
                        /*keepDisabledAcrossBoots=*/ !enabled);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mHandler.removeCallbacksAndMessages(null);
    }

    static final class SimEntry {
        private final Subscription subscription;
        private final CharSequence nextUpcomingScheduleSummary;

        private SimEntry(final Subscription subscription,
                final CharSequence nextUpcomingScheduleSummary) {

            this.subscription = subscription;
            this.nextUpcomingScheduleSummary = nextUpcomingScheduleSummary;
        }

        Subscription getSubscription() {
            return subscription;
        }

        CharSequence getNextUpcomingScheduleSummary() {
            return nextUpcomingScheduleSummary;
        }
    }

    @AssistedFactory
    interface Factory {
        SimListViewModel create(Looper looper);
    }

    /**
     * @param assistedFactory An {@link AssistedFactory} to create the {@link SimListViewModel}
     * instance via the {@link AssistedInject} constructor.
     * @param looper The shared (non-main) {@link Looper} instance to perform database requests on.
     * @return An instance of the {@link ViewModelProvider}.
     */
    static ViewModelProvider.Factory getFactory(final Factory assistedFactory,
            final Looper looper) {

        return new ViewModelProvider.Factory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(final Class<T> modelClass) {
                if (modelClass.isAssignableFrom(SimListViewModel.class)) {
                    // The @AssistedFactory requires a 1-1 mapping for the returned type, so
                    // explicitly cast it to satisfy the compiler and ignore the unchecked cast for
                    // now. It's safe till it's done inside this If-block
                    return (T) assistedFactory.create(looper);
                }
                throw new IllegalArgumentException("Unknown ViewModel class.");
            }
        };
    }
}
