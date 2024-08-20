package com.github.iusmac.sevensim.ui.scheduler;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.iusmac.sevensim.DateTimeUtils;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.scheduler.DayOfWeek;
import com.github.iusmac.sevensim.scheduler.DaysOfWeek;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;
import com.github.iusmac.sevensim.scheduler.SubscriptionSchedulerSummaryBuilder;
import com.github.iusmac.sevensim.telephony.PinEntity;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.telephony.Subscriptions;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.hilt.android.qualifiers.ApplicationContext;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SchedulerViewModel extends ViewModel {
    private final Resources mResources;
    private final Handler mHandler;
    private final IntentReceiver mIntentReceiver = new IntentReceiver();

    private final MutableLiveData<CharSequence>
        mMutableNextUpcomingScheduleSummary = new MutableLiveData<>();

    private final MediatorLiveData<Optional<PinEntity>> mMediatorPinEntity =
        new MediatorLiveData<>(Optional.empty());
    private LiveData<Boolean> mObservablePinPresence;
    private final MutableLiveData<Boolean> mMutablePinTaskLock = new MutableLiveData<>(false);
    private LiveData<Optional<PinErrorMessage>> mObseravblePinErrorMessage;

    private final MutableLiveData<Optional<List<SubscriptionScheduleEntity>>> mMutableSchedules =
        new MutableLiveData<>(Optional.empty());

    private final MutableLiveData<SubscriptionScheduleEntity> mMutableScheduleAddedListener =
            new MutableLiveData<>();

    @SuppressLint("StaticFieldLeak")
    private final Context mContext;
    private final Logger mLogger;
    private final DaysOfWeek.Factory mDaysOfWeekFactory;
    private final SubscriptionScheduler mSubscriptionScheduler;
    private final Subscriptions mSubscriptions;
    private final SubscriptionSchedulerSummaryBuilder mSubscriptionSchedulerSummaryBuilder;
    private final PinStorage mPinStorage;
    private final int mSubscriptionId;

    @AssistedInject
    public SchedulerViewModel(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory,
            final DaysOfWeek.Factory daysOfWeekFactory,
            final SubscriptionScheduler subscriptionScheduler,
            final Subscriptions subscriptions,
            final SubscriptionSchedulerSummaryBuilder subscriptionSchedulerSummaryBuilder,
            final PinStorage pinStorage,
            final @Assisted int subscriptionId,
            final @Assisted Looper looper) {

        mContext = context;
        mLogger = loggerFactory.create(getClass().getSimpleName());
        mDaysOfWeekFactory = daysOfWeekFactory;
        mSubscriptionScheduler = subscriptionScheduler;
        mSubscriptions = subscriptions;
        mSubscriptionSchedulerSummaryBuilder = subscriptionSchedulerSummaryBuilder;
        mPinStorage = pinStorage;
        mSubscriptionId = subscriptionId;

        mResources = mContext.getResources();
        mHandler = Handler.createAsync(looper);

        mMediatorPinEntity.addSource(mPinStorage.getObservablePin(mSubscriptionId), (pinEntity) ->
                mMediatorPinEntity.setValue(pinEntity));

        // Fetch data from the database
        mHandler.post(() -> mMutableSchedules.postValue(Optional.of(mSubscriptionScheduler
                    .findAllBySubscriptionId(subscriptionId))));

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        ContextCompat.registerReceiver(mContext, mIntentReceiver, filter,
               ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * Return an observable human-readable string summarizing the next upcoming schedule for this
     * scheduler's SIM subscription.
     */
    LiveData<CharSequence> getNextUpcomingScheduleSummary() {
        return mMutableNextUpcomingScheduleSummary;
    }

    /**
     * Return an observable containing the list of schedules, if any.
     */
    LiveData<Optional<List<SubscriptionScheduleEntity>>> getSchedules() {
        return mMutableSchedules;
    }

    /**
     * Return an observable containing the most recently added schedule, if any.
     */
    LiveData<SubscriptionScheduleEntity> getScheduleAddedListener() {
        return mMutableScheduleAddedListener;
    }

    /**
     * Return an observable containing the PIN presence status.
     */
    LiveData<Boolean> getPinPresence() {
        if (mObservablePinPresence == null) {
            mObservablePinPresence = Transformations.map(mMediatorPinEntity, (pinEntity) ->
                    pinEntity.isPresent());
        }
        return mObservablePinPresence;
    }

    /**
     * Return an observable lock state of the SIM PIN task.
     */
    LiveData<Boolean> getPinTaskLock() {
        return mMutablePinTaskLock;
    }

    /**
     * Return an observable containing a human-readable PIN error message, if any.
     */
    LiveData<Optional<PinErrorMessage>> getPinErrorMessage() {
        if (mObseravblePinErrorMessage == null) {
            mObseravblePinErrorMessage = Transformations.map(mMediatorPinEntity, (pinEntity) ->
                    pinEntity.map((pin) -> {
                        final String title, reason;
                        if (pin.isCorrupted()) {
                            title = mResources.getString(R.string.sim_pin_operation_failed);
                            reason = mResources.getString(
                                    R.string.scheduler_pin_banner_sim_pin_decryption_failed_reason);
                            return new PinErrorMessage(title, reason);
                        } else if (pin.isInvalid()) {
                            title = mResources.getString(R.string.sim_unlock_failed);
                            reason = mResources.getString(
                                    R.string.scheduler_pin_banner_wrong_sim_pin_code_reason);
                            return new PinErrorMessage(title, reason);
                        }
                        return null;
                    }));
        }
        return mObseravblePinErrorMessage;
    }

    /**
     * @param schedule The schedule entity whose enabled state has been changed.
     * @param enabled {@code true} if the scheduler is being enabled, otherwise {@code false}.
     */
    void handleOnEnabledStateChanged(final @NonNull SubscriptionScheduleEntity schedule,
            final boolean enabled) {

        mLogger.d("handleOnEnabledStateChanged(schedule=%s,enabled=%s).", schedule, enabled);

        schedule.setEnabled(enabled);
        persist(schedule);
    }

    /**
     * @param schedule The schedule entity whose SIM subscription enabled state has been changed.
     * @param enabled {@code true} if the SIM subscription is being enabled, otherwise {@code false}.
     */
    void handleOnSubscriptionEnabledStateChanged(final @NonNull SubscriptionScheduleEntity schedule,
            final boolean enabled) {

        mLogger.d("handleOnSubscriptionEnabledStateChanged(schedule=%s,enabled=%s).", schedule,
                enabled);

        schedule.setSubscriptionEnabled(enabled);
        persist(schedule);
    }

    /**
     * @param schedule The corresponding schedule entity.
     * @param dayOfWeek The {@link DayOfWeek} that has been affected.
     * @param enabled Whether the day of week has been enabled or disabled.
     */
    void handleOnDayOfWeekChanged(final SubscriptionScheduleEntity schedule,
            final @DayOfWeek int dayOfWeek, final boolean enabled) {

        final @DayOfWeek List<Integer> daysOfWeekValues = new ArrayList<>(7);
        for (final @DayOfWeek int dayOfWeek_ : schedule.getDaysOfWeek()) {
            if (dayOfWeek == dayOfWeek_) {
                if (enabled) {
                    daysOfWeekValues.add(dayOfWeek_);
                }
            } else if (schedule.getDaysOfWeek().isBitOn(dayOfWeek_)) {
                daysOfWeekValues.add(dayOfWeek_);
            }
        }

        final DaysOfWeek daysOfWeek =
            mDaysOfWeekFactory.create(daysOfWeekValues.stream().toArray(Integer[]::new));

        mLogger.d("handleOnDayOfWeekChanged(schedule=%s,dayOfWeek=%d,enabled=%s) : daysOfWeek=%s.",
                schedule, dayOfWeek, enabled, daysOfWeek);

        schedule.setDaysOfWeek(daysOfWeek);
        persist(schedule);
    }

    /**
     * @param schedule The schedule entity whose time has been changed.
     * @param value The time value in form "H:m", where "H", is the hour of day from 0 to 23
     * (inclusive), and "m", is the minute of hour from 0 to 59 (inclusive).
     */
    void handleOnTimeChanged(final @NonNull SubscriptionScheduleEntity schedule,
            final @NonNull String value) {

        mLogger.d("handleOnTimeChanged(schedule=%s,value=%s).", schedule, value);

        schedule.setTime(DateTimeUtils.parseWallClockTime(value));
        persist(schedule);
    }

    /**
     * Add a new schedule for a picked time. Subscribe to {@link #getScheduleAddedListener()} to
     * know when the schedule has been added.
     *
     * @param value The time value in form "H:m", where "H", is the hour of day from 0 to 23
     * (inclusive), and "m", is the minute of hour from 0 to 59 (inclusive).
     */
    void handleOnTimePicked(final @NonNull String value) {
        mLogger.d("handleOnTimePicked(value=%s).", value);

        final SubscriptionScheduleEntity schedule =
            createSchedule(DateTimeUtils.parseWallClockTime(value));
        persist(schedule);
    }

    /**
     * @param schedule The schedule entity whose label has been changed.
     * @param value The label string value.
     */
    void handleOnLabelChanged(final @NonNull SubscriptionScheduleEntity schedule,
            final @NonNull String value) {

        mLogger.d("handleOnLabelChanged(schedule=%s,value=%s).", schedule, value);

        schedule.setLabel(value.trim().isEmpty() ? null : value);
        persist(schedule);
    }

    /**
     * @param schedule The schedule entity that has been deleted.
     */
    void handleOnDeleted(final @NonNull SubscriptionScheduleEntity schedule) {
        mLogger.d("handleOnDeleted(schedule=%s).", schedule);

        mMutableSchedules.getValue().ifPresent((schedules) -> schedules.remove(schedule));
        // While we're at it, wipe out the added schedule, as it could be the schedule we just
        // removed and we don't want it to appear again
        mMutableScheduleAddedListener.setValue(null);
        mHandler.post(() -> mSubscriptionScheduler.delete(schedule));
        refreshNextUpcomingScheduleSummaryAsync();
    }

    /**
     * @param pin The SIM PIN as string.
     */
    void handleOnPinChanged(final @NonNull String pin) {
        mLogger.d("handleOnPinChanged().");

        mHandler.post(() -> {
            // Acquire lock till asynchronous request completes
            mMutablePinTaskLock.postValue(true);

            final PinEntity pinEntity = mMediatorPinEntity.getValue().orElseGet(() -> {
                final PinEntity p = new PinEntity();
                p.setSubscriptionId(mSubscriptionId);
                return p;
            });
            pinEntity.setInvalid(false);
            pinEntity.setClearPin(pin);
            if (mPinStorage.encrypt(pinEntity)) {
                mPinStorage.storePin(pinEntity);
            } else {
                Utils.makeToast(mContext, mResources.getString(R.string.sim_pin_operation_failed));
                // Unconditionally propagate an empty instance since unencrypted PIN entities cannot
                // be persisted on disk, thus the mediator will remain unchanged
                mMediatorPinEntity.postValue(Optional.empty());
            }

            // In order to supply the SIM subscription PIN codes to the active SIM subscriptions
            // found on the device when processing schedules at the stated time, we need to
            // re-schedule using the list of all decrypted SIM PIN entities
            final List<PinEntity> pinEntities = mPinStorage.getPinEntities();
            pinEntities.forEach((pinEntity1) -> mPinStorage.decrypt(pinEntity1));
            mSubscriptionScheduler.updateNextWeeklyRepeatScheduleProcessingIter(
                    LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(1), pinEntities);

            // Release lock
            mMutablePinTaskLock.postValue(false);
        });
    }

    /**
     * Refresh the human-readable string summarizing the next upcoming schedule for this scheduler's
     * SIM subscription.
     */
    @WorkerThread
    void refreshNextUpcomingScheduleSummary() {
        mLogger.v("refreshNextUpcomingScheduleSummary().");

        final CharSequence summary = mSubscriptions.getSubscriptionForSubId(mSubscriptionId)
            .map((sub) -> mSubscriptionSchedulerSummaryBuilder
                    .buildNextUpcomingSubscriptionScheduleSummary(sub,
                        LocalDateTime.now(ZoneId.systemDefault()))).orElseGet(() ->
                    mResources.getString(R.string.sim_missing));

        mMutableNextUpcomingScheduleSummary.postValue(summary);
    }

    /**
     * Remove the SIM PIN code, if present.
     */
    void removePin() {
        mLogger.d("removePin().");

        mMediatorPinEntity.getValue().ifPresent((pin) -> mHandler.post(() ->
                    mPinStorage.deletePin(pin)));
    }

    /**
     * Return {@code true} if the SIM PIN code has been set, otherwise {@code false}.
     */
    boolean isPinPresent() {
        return mMediatorPinEntity.getValue().isPresent();
    }

    /**
     * Return {@code true} if we need to authenticate the user with their credentials for further
     * crypto operations on the SIM PIN code.
     */
    boolean isAuthenticationRequired() {
        return mPinStorage.isAuthenticationRequired();
    }

    /**
     * Create a valid {@link SubscriptionScheduleEntity} instance representing the given time.
     *
     * @param time Time as seen on a wall clock.
     * @return The schedule entity instance.
     */
    private SubscriptionScheduleEntity createSchedule(final LocalTime time) {
        final SubscriptionScheduleEntity schedule = new SubscriptionScheduleEntity();
        schedule.setSubscriptionId(mSubscriptionId);
        schedule.setDaysOfWeek(mDaysOfWeekFactory.create());
        schedule.setTime(time);
        return schedule;
    }

    /**
     * Add a new or persist the changes for an existing schedule to the database.
     */
    private void persist(final SubscriptionScheduleEntity schedule) {
        if (schedule.getId() > 0L) {
            mHandler.post(() -> mSubscriptionScheduler.update(schedule));
        } else {
            mHandler.post(() -> {
                mSubscriptionScheduler.add(schedule);
                mMutableSchedules.getValue().ifPresent((schedules) -> schedules.add(schedule));
                mMutableScheduleAddedListener.postValue(schedule);
            });
        }
        refreshNextUpcomingScheduleSummaryAsync();
    }

    private void refreshNextUpcomingScheduleSummaryAsync() {
        mHandler.post(this::refreshNextUpcomingScheduleSummary);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mContext.unregisterReceiver(mIntentReceiver);
        mHandler.removeCallbacksAndMessages(null);
    }

    static final class PinErrorMessage {
        final String title;
        final String reason;

        private PinErrorMessage(final String title, final String reason) {
            this.title = title;
            this.reason = reason;
        }
    }

    private final class IntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            mLogger.d("onReceive() : action=" + action);

            switch (action) {
                case Intent.ACTION_LOCALE_CHANGED:
                    // Re-post existing values to trigger the chain of listeners, which will
                    // regenerate locale-sensitive data
                    mMediatorPinEntity.postValue(mMediatorPinEntity.getValue());
                    // Refresh the next upcoming schedule summary locale-sensitive part
                    refreshNextUpcomingScheduleSummaryAsync();
                    break;

                case Intent.ACTION_TIMEZONE_CHANGED:
                case Intent.ACTION_TIME_CHANGED:
                    // Refresh the next upcoming schedule summary time-sensitive part
                    refreshNextUpcomingScheduleSummaryAsync();
            }
        }
    }

    @AssistedFactory
    interface Factory {
        SchedulerViewModel create(int subscriptionId, Looper looper);
    }

    /**
     * Return an instance of the {@link ViewModelProvider}.
     *
     * @param assistedFactory An {@link AssistedFactory} to create the {@link SchedulerViewModel}
     * instance via the {@link AssistedInject} constructor.
     * @param subscriptionId The SIM subscription ID to find the associated schedules for.
     * @param looper The shared (non-main) {@link Looper} instance to perform database requests on.
     */
    static ViewModelProvider.Factory getFactory(final Factory assistedFactory,
            final int subscriptionId, final Looper looper) {

        return new ViewModelProvider.Factory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(final Class<T> modelClass) {
                if (modelClass.isAssignableFrom(SchedulerViewModel.class)) {
                    // The @AssistedFactory requires a 1-1 mapping for the returned type, so
                    // explicitly cast it to satisfy the compiler and ignore the unchecked cast for
                    // now. It's safe till it's done inside this If-block
                    return (T) assistedFactory.create(subscriptionId, looper);
                }
                throw new IllegalArgumentException("Unknown ViewModel class.");
            }
        };
    }
}
