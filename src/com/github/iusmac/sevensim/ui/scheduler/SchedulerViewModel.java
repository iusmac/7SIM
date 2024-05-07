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
import androidx.collection.ArraySet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.iusmac.sevensim.DateTimeUtils;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.R;
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
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SchedulerViewModel extends ViewModel {
    enum TimeType {
        START_TIME(LocalTime.of(8, 0)), END_TIME(LocalTime.of(19, 0));

        private LocalTime mTime;

        TimeType(final LocalTime time) {
            mTime = time;
        }

        LocalTime getDefaultLocalTime() {
            return mTime;
        }
    }

    private final Resources mResources;
    private final Handler mHandler;
    private final IntentReceiver mIntentReceiver = new IntentReceiver();

    private final MutableLiveData<SubscriptionScheduleEntity> mMutableStartSchedule;
    private final MutableLiveData<SubscriptionScheduleEntity> mMutableEndSchedule;

    private final MutableLiveData<CharSequence>
        mMutableNextUpcomingScheduleSummary = new MutableLiveData<>();

    private MediatorLiveData<Boolean> mMediatorSchedulerEnabledState;
    private MutableLiveData<Boolean> mMutableSchedulerRepeatCycleState;

    private final MutableLiveData<DaysOfWeek> mMutableDaysOfWeek;
    private MutableLiveData<Set<String>> mMutableDaysOfWeekValues;
    private LiveData<Map.Entry<String[], String[]>> mObservableAllDaysOfWeekEntryValues;
    private LiveData<CharSequence> mObservableDayOfWeekSummary;

    private final MutableLiveData<LocalTime> mMutableStartTime;
    private LiveData<CharSequence> mObservableStartTimeSummary;

    private final MutableLiveData<LocalTime> mMutableEndTime;
    private LiveData<CharSequence> mObservableEndTimeSummary;

    private final MutableLiveData<Optional<PinEntity>> mMutablePinEntity =
        new MutableLiveData<>(Optional.empty());
    private LiveData<CharSequence> mObservablePinPresenceSummary;
    private final MutableLiveData<Boolean> mMutablePinTaskLock = new MutableLiveData<>(false);

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

        mMutableStartSchedule = new MutableLiveData<>(getDefaultSchedule(TimeType.START_TIME));
        mMutableEndSchedule = new MutableLiveData<>(getDefaultSchedule(TimeType.END_TIME));
        mMutableDaysOfWeek = new MutableLiveData<>(mDaysOfWeekFactory.create());
        mMutableStartTime = new MutableLiveData<>(mMutableStartSchedule.getValue().getTime());
        mMutableEndTime = new MutableLiveData<>(mMutableEndSchedule.getValue().getTime());

        // Fetch data from the database
        mHandler.post(() -> {
            int dayOfWeekBits = 0;
            final List<SubscriptionScheduleEntity> schedules =
                mSubscriptionScheduler.findAllBySubscriptionId(subscriptionId);
            for (final SubscriptionScheduleEntity schedule : schedules) {
                if (schedule.getSubscriptionEnabled()) {
                    mMutableStartSchedule.postValue(schedule);
                    mMutableStartTime.postValue(schedule.getTime());
                } else {
                    mMutableEndSchedule.postValue(schedule);
                    mMutableEndTime.postValue(schedule.getTime());
                }
                dayOfWeekBits |= schedule.getDaysOfWeek().getBits();
            }
            if (dayOfWeekBits != 0) {
                mMutableDaysOfWeek.postValue(mDaysOfWeekFactory.create(dayOfWeekBits));
            }
            final Optional<PinEntity> pinEntity = mPinStorage.getPin(subscriptionId);
            if (pinEntity.isPresent()) {
                mMutablePinEntity.postValue(pinEntity);
            }
        });

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        ContextCompat.registerReceiver(mContext, mIntentReceiver, filter,
               ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * @return An observable human-readable string summarizing the next upcoming schedule for this
     * scheduler's SIM subscription.
     */
    LiveData<CharSequence> getNextUpcomingScheduleSummary() {
        return mMutableNextUpcomingScheduleSummary;
    }

    /**
     * @return An observable of the enabled state of this scheduler.
     */
    LiveData<Boolean> getSchedulerEnabledState() {
        if (mMediatorSchedulerEnabledState == null) {
            mMediatorSchedulerEnabledState = new MediatorLiveData<>();
            mMediatorSchedulerEnabledState.setValue(false);
            final Observer<SubscriptionScheduleEntity> observer = (schedule) -> {
                final boolean enabled = mMutableStartSchedule.getValue().getEnabled() &
                    mMutableEndSchedule.getValue().getEnabled();
                mMediatorSchedulerEnabledState.postValue(enabled);
            };
            mMediatorSchedulerEnabledState.addSource(mMutableStartSchedule, observer);
            mMediatorSchedulerEnabledState.addSource(mMutableEndSchedule, observer);
        }
        return mMediatorSchedulerEnabledState;
    }

    /**
     * @return An observable of the weekly repeat cycle state from the {@link DaysOfWeek} instance
     * of this scheduler.
     */
    LiveData<Boolean> getSchedulerWeeklyRepeatCycleState() {
        if (mMutableSchedulerRepeatCycleState == null) {
            mMutableSchedulerRepeatCycleState = new MutableLiveData<>(false);
            mMutableDaysOfWeek.observeForever((daysOfWeek) -> {
                final boolean isRepeating = daysOfWeek.isRepeating();
                mMutableSchedulerRepeatCycleState.postValue(isRepeating);
            });
        }
        return mMutableSchedulerRepeatCycleState;
    }

    /**
     * @return An observable of the {@link DaysOfWeek} instance of this scheduler converted to a set
     * of {@link DayOfWeek} values as strings.
     */
    LiveData<Set<String>> getDaysOfWeekValues() {
        if (mMutableDaysOfWeekValues == null) {
            mMutableDaysOfWeekValues = new MutableLiveData<>(new ArraySet<>(7));
            mMutableDaysOfWeek.observeForever((daysOfWeek) -> {
                final Set<String> values = mMutableDaysOfWeekValues.getValue();
                values.clear();
                for (final @DayOfWeek int dayOfWeek : daysOfWeek) {
                    if (daysOfWeek.isBitOn(dayOfWeek)) {
                        values.add(Integer.toString(dayOfWeek));
                    }
                }
                mMutableDaysOfWeekValues.postValue(values);
            });
        }
        return mMutableDaysOfWeekValues;
    }

    /**
     * @return An observable of all days of the week mapped from the {@link DayOfWeek} values in
     * human-readable format to corresponding numeric values, ordered according to the current
     * locale.
     */
    LiveData<Map.Entry<String[], String[]>> getAllDaysOfWeekEntryMap() {
        if (mObservableAllDaysOfWeekEntryValues == null) {
            final String[] daysOfWeekNames = new String[7];
            final String[] daysOfWeekValues = new String[7];
            final Map.Entry<String[], String[]> entryMap =
                new SimpleEntry<>(daysOfWeekNames, daysOfWeekValues);
            mObservableAllDaysOfWeekEntryValues =
                Transformations.map(mMutableDaysOfWeek, (daysOfWeek) -> {
                    int i = 0;
                    for (final Iterator<Integer> it = daysOfWeek.iterator(); it.hasNext(); i++) {
                        final @DayOfWeek int dayOfWeek = it.next();
                        daysOfWeekNames[i] = daysOfWeek.getDisplayName(dayOfWeek,
                                /*useLongName=*/ true);
                        daysOfWeekValues[i] = Integer.toString(dayOfWeek);
                    }
                    return entryMap;
                });
        }
        return mObservableAllDaysOfWeekEntryValues;
    }

    /**
     * @return An observable containing a "pretty" representation of this scheduler's weekly repeat
     * schedule as a string.
     */
    LiveData<CharSequence> getDaysOfWeekSummary() {
        if (mObservableDayOfWeekSummary == null) {
            mObservableDayOfWeekSummary = Transformations.map(mMutableDaysOfWeek, (daysOfWeek) -> {
                if (!daysOfWeek.isRepeating()) {
                    return mResources.getString(R.string.scheduler_days_of_week_none);
                } else if (daysOfWeek.isFullWeek()) {
                    return mResources.getString(R.string.scheduler_days_of_week_all);
                } else {
                    final boolean useLongNames = daysOfWeek.getCount() == 1;
                    return daysOfWeek.toString(useLongNames);
                }
            });
        }
        return mObservableDayOfWeekSummary;
    }

    /**
     * @param which One of {@link TimeType}.
     * @return An observable of the time of the requested type as {@link LocalTime} instance of this
     * scheduler.
     */
    LiveData<LocalTime> getTime(final TimeType which) {
        return which == TimeType.START_TIME ? mMutableStartTime : mMutableEndTime;
    }

    /**
     * @return An observable containing a "pretty" representation of this scheduler's start time.
     */
    LiveData<CharSequence> getStartTimeSummary() {
        if (mObservableStartTimeSummary == null) {
            mObservableStartTimeSummary = Transformations.map(mMutableStartTime, (time) ->
                    DateTimeUtils.getPrettyTime(mContext, time));
        }
        return mObservableStartTimeSummary;
    }

    /**
     * @return An observable containing a "pretty" representation of this scheduler's end time.
     */
    LiveData<CharSequence> getEndTimeSummary() {
        if (mObservableEndTimeSummary == null) {
            mObservableEndTimeSummary = Transformations.map(mMutableEndTime, (time) ->
                    DateTimeUtils.getPrettyTime(mContext, time));
        }
        return mObservableEndTimeSummary;
    }

    /**
     * @return An observable human-readable status indicating PIN presence.
     */
    LiveData<CharSequence> getPinPresenceSummary() {
        if (mObservablePinPresenceSummary == null) {
            mObservablePinPresenceSummary = Transformations.map(mMutablePinEntity, (pinEntity) ->
                    mResources.getString(pinEntity.isPresent() ? R.string.scheduler_pin_set_summary
                        : R.string.scheduler_pin_unset_summary));
        }
        return mObservablePinPresenceSummary;
    }

    /**
     * @return An observable lock state of the SIM PIN task.
     */
    LiveData<Boolean> getPinTaskLock() {
        return mMutablePinTaskLock;
    }

    /**
     * @param enabled {@code true} if the scheduler is being enabled, otherwise {@code false}.
     */
    void handleOnEnabledStateChanged(final boolean enabled) {
        mLogger.d("handleOnEnabledStateChanged(enabled=%s).", enabled);

        mMediatorSchedulerEnabledState.setValue(enabled);
        persist();
    }

    /**
     * @param values The set of {@link DayOfWeek} as strings.
     */
    void handleOnDaysOfWeekChanged(final @NonNull Set<String> values) {
        final DaysOfWeek daysOfWeek = mDaysOfWeekFactory.create(values.stream()
                .mapToInt(Integer::parseInt).toArray());

        mLogger.d("handleOnWeekdaysChanged(values={%s}) : daysOfWeek=%s.", String.join(",", values),
                daysOfWeek);

        mMutableDaysOfWeek.setValue(daysOfWeek);
        persist();
    }

    /**
     * @param which One of {@link TimeType}.
     * @param value The time value in form "H:m", where "H", is the hour of day from 0 to 23
     * (inclusive), and "m", is the minute of hour from 0 to 59 (inclusive).
     */
    void handleOnTimeChanged(final @NonNull TimeType which, final @NonNull String value) {
        mLogger.d("handleOnTimeChanged(which=%s,time=%s).", which, value);

        final LocalTime time = LocalTime.parse(value, DateTimeFormatter.ofPattern("H:m"));
        final MutableLiveData<LocalTime> mutableTime =
            which == TimeType.START_TIME ? mMutableStartTime : mMutableEndTime;
        mutableTime.setValue(time);
        persist();
    }

    /**
     * @param pin The SIM PIN as string.
     */
    void handleOnPinChanged(final @NonNull String pin) {
        mLogger.d("handleOnPinChanged().");

        mHandler.post(() -> {
            // Acquire lock till asynchronous request completes
            mMutablePinTaskLock.postValue(true);

            final PinEntity pinEntity = mMutablePinEntity.getValue().orElseGet(() -> {
                final PinEntity p = new PinEntity();
                p.setSubscriptionId(mSubscriptionId);
                return p;
            });
            pinEntity.setClearPin(pin);
            mPinStorage.storePin(pinEntity);
            mMutablePinEntity.postValue(Optional.of(pinEntity));

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
                    .buildNextUpcomingSubscriptionScheduleSummary(sub, LocalDateTime.now()))
            .orElseGet(() -> mResources.getString(R.string.sim_missing));

        mMutableNextUpcomingScheduleSummary.postValue(summary);
    }

    /**
     * Entirely purge the scheduler and all relative data.
     */
    void removeScheduler() {
        mLogger.d("removeScheduler().");

        final List<SubscriptionScheduleEntity> schedulesToRemove = new ArrayList<>(2);

        mMediatorSchedulerEnabledState.setValue(false);
        mMutableDaysOfWeek.setValue(mDaysOfWeekFactory.create());

        if (mMutableStartSchedule.getValue().getId() > 0L) {
            final SubscriptionScheduleEntity defaultSchedule =
                getDefaultSchedule(TimeType.START_TIME);
            mMutableStartTime.setValue(defaultSchedule.getTime());
            schedulesToRemove.add(mMutableStartSchedule.getValue());
            mMutableStartSchedule.setValue(defaultSchedule);
        }

        if (mMutableEndSchedule.getValue().getId() > 0L) {
            final SubscriptionScheduleEntity defaultSchedule =
                getDefaultSchedule(TimeType.END_TIME);
            mMutableEndTime.setValue(defaultSchedule.getTime());
            schedulesToRemove.add(mMutableEndSchedule.getValue());
            mMutableEndSchedule.setValue(defaultSchedule);
        }

        mMutablePinEntity.getValue().ifPresent((pin) -> {
            mMutablePinEntity.setValue(Optional.empty());
            mHandler.post(() -> mPinStorage.deletePin(pin));
        });

        if (!schedulesToRemove.isEmpty()) {
            mHandler.post(() -> mSubscriptionScheduler.deleteAll(schedulesToRemove));
        }

        refreshNextUpcomingScheduleSummaryAsync();
    }

    /**
     * @return {@code true} if the scheduler exists, otherwise {@code false}.
     */
    boolean schedulerExists() {
        return mMutableStartSchedule.getValue().getId() > 0L ||
            mMutableEndSchedule.getValue().getId() > 0L;
    }

    /**
     * @return {@code true} if the SIM PIN code has been set, otherwise {@code false}.
     */
    boolean isPinPresent() {
        return mMutablePinEntity.getValue().isPresent();
    }

    /**
     * Create a valid {@link SubscriptionScheduleEntity} instance with default values.
     *
     * @param which One of {@link TimeType} to determine what type of schedule to create.
     * @return The requested schedule entity instance.
     */
    private SubscriptionScheduleEntity getDefaultSchedule(final TimeType which) {
        final SubscriptionScheduleEntity schedule = new SubscriptionScheduleEntity();
        schedule.setSubscriptionId(mSubscriptionId);
        schedule.setSubscriptionEnabled(which == TimeType.START_TIME);
        schedule.setDaysOfWeek(mDaysOfWeekFactory.create());
        schedule.setTime(which.getDefaultLocalTime());
        return schedule;
    }

    /**
     * Persist scheduler changes to the database.
     */
    private void persist() {
        final List<SubscriptionScheduleEntity> inexistentSchedules = new ArrayList<>();
        final List<SubscriptionScheduleEntity> existentSchedules = new ArrayList<>();

        final BiConsumer<SubscriptionScheduleEntity, LocalTime> process = (outSchedule, time) -> {
            outSchedule.setEnabled(mMediatorSchedulerEnabledState.getValue());
            outSchedule.setDaysOfWeek(mMutableDaysOfWeek.getValue());
            outSchedule.setTime(time);
            final boolean scheduleExists = outSchedule.getId() > 0L;
            if (!scheduleExists) {
                inexistentSchedules.add(outSchedule);
            } else {
                existentSchedules.add(outSchedule);
            }
        };

        process.accept(mMutableStartSchedule.getValue(), mMutableStartTime.getValue());
        process.accept(mMutableEndSchedule.getValue(), mMutableEndTime.getValue());

        if (!inexistentSchedules.isEmpty()) {
            mHandler.post(() -> mSubscriptionScheduler.addAll(inexistentSchedules));
        }
        if (!existentSchedules.isEmpty()) {
            mHandler.post(() -> mSubscriptionScheduler.updateAll(existentSchedules));
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

    private final class IntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            mLogger.d("onReceive() : action=" + action);

            switch (action) {
                case Intent.ACTION_LOCALE_CHANGED:
                    // Re-post existing values to trigger the chain of listeners, which will
                    // regenerate locale-sensitive data
                    mMutableDaysOfWeek.postValue(mMutableDaysOfWeek.getValue());
                    mMutableStartTime.postValue(mMutableStartTime.getValue());
                    mMutableEndTime.postValue(mMutableEndTime.getValue());
                    mMutablePinEntity.postValue(mMutablePinEntity.getValue());
                    // Refresh the next upcoming schedule summary locale-sensitive part
                    refreshNextUpcomingScheduleSummaryAsync();
                    break;

                case Intent.ACTION_TIMEZONE_CHANGED:
                case Intent.ACTION_TIME_CHANGED:
                    // Re-post existing values to trigger the chain of listeners, which will
                    // regenerate time-sensitive data
                    mMutableStartTime.postValue(mMutableStartTime.getValue());
                    mMutableEndTime.postValue(mMutableEndTime.getValue());
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
     * @param assistedFactory An {@link AssistedFactory} to create the {@link SchedulerViewModel}
     * instance via the {@link AssistedInject} constructor.
     * @param subscriptionId The SIM subscription ID to find the associated schedules for.
     * @param looper The shared (non-main) {@link Looper} instance to perform database requests on.
     * @return An instance of the {@link ViewModelProvider}.
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
