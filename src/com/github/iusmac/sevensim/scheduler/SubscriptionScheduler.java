package com.github.iusmac.sevensim.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.telephony.SubscriptionController;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.TelephonyController;

import dagger.Lazy;
import dagger.hilt.android.qualifiers.ApplicationContext;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import static android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX;

/**
 * <p>This class is responsible for orchestrating the SIM subscription weekly repeat schedules when
 * added/removed etc.
 *
 * <p>It also prepares the {@link AlarmReceiver} to execute to start processing the weekly repeat
 * schedules for all available SIM subscriptions at a specific time.
 */
@Singleton
@WorkerThread
public final class SubscriptionScheduler {
    private enum ScheduleDatabaseOperationType { ADD, UPDATE, DELETE }

    private final Logger mLogger;
    private final Context mContext;
    private final Lazy<AlarmManager> mAlarmManagerLazy;
    private final SubscriptionSchedulesDao mSubscriptionSchedulesDao;
    private final Lazy<Subscriptions> mSubscriptionsLazy;
    private final Lazy<SubscriptionController> mSubscriptionControllerLazy;
    private final Lazy<TelephonyController> mTelephonyControllerLazy;

    private final Intent mAlarmIntent;

    @Inject
    public SubscriptionScheduler(final Logger.Factory loggerFactory,
            final @ApplicationContext Context context, final Lazy<AlarmManager> alarmManagerLazy,
            final AppDatabaseDE appDatabaseDE,
            final Lazy<Subscriptions> subscriptionsLazy,
            final Lazy<SubscriptionController> subscriptionControllerLazy,
            final Lazy<TelephonyController> telephonyControllerLazy) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mContext = context;
        mAlarmManagerLazy = alarmManagerLazy;
        mSubscriptionSchedulesDao = appDatabaseDE.subscriptionSchedulerDao();
        mSubscriptionsLazy = subscriptionsLazy;
        mSubscriptionControllerLazy = subscriptionControllerLazy;
        mTelephonyControllerLazy = telephonyControllerLazy;

        mAlarmIntent = new Intent(context, AlarmReceiver.class);
    }

    /**
     * Add a list of new SIM subscription weekly repeat schedules.
     *
     * @param schedules The schedule entities to add.
     */
    public void addAll(final @NonNull List<SubscriptionScheduleEntity> schedules) {
        doSchedulesDatabaseRequest(schedules, ScheduleDatabaseOperationType.ADD);
    }

    /**
     * Update a list of existing SIM subscription weekly repeat schedules.
     *
     * @param schedules The schedule entities to update.
     */
    public void updateAll(final @NonNull List<SubscriptionScheduleEntity> schedules) {
        doSchedulesDatabaseRequest(schedules, ScheduleDatabaseOperationType.UPDATE);
    }

    /**
     * Delete a list of SIM subscription weekly repeat schedules.
     *
     * @param schedules The schedule entities to delete.
     */
    public void deleteAll(final @NonNull List<SubscriptionScheduleEntity> schedules) {
        doSchedulesDatabaseRequest(schedules, ScheduleDatabaseOperationType.DELETE);
    }

    /**
     * Find all SIM subscription weekly repeat schedules associated with a SIM subscription ID.
     *
     * @param subId The ID of the subscription.
     * @return A list of schedules associated with the subscription ID.
     */
    public @NonNull List<SubscriptionScheduleEntity> findAllBySubscriptionId(final int subId) {
        return mSubscriptionSchedulesDao.findAllBySubscriptionId(subId);
    }

    /**
     * Find a SIM subscription weekly repeat schedule that occurs on or before the given date-time.
     *
     * @param subId The ID of the subscription.
     * @param subEnabled The scheduled enabled of the subscription.
     * @param dateTime The date-time object used for finding the nearest schedule.
     * @return An Optional containing the schedule, if found.
     */
    public Optional<SubscriptionScheduleEntity> findNearestBeforeDateTime(final int subId,
            final boolean subEnabled, final @NonNull LocalDateTime dateTime) {

        return mSubscriptionSchedulesDao.findNearestByDayOfWeekAndTime(subId, subEnabled,
                DaysOfWeek.getDayOfWeekFrom(dateTime), dateTime.toLocalTime(),
                /*reverseSearch=*/ true);
    }

    /**
     * Find a SIM subscription weekly repeat schedule that occurs on or after the given date-time.
     *
     * @param subId The ID of the subscription.
     * @param subEnabled The scheduled enabled state of the subscription.
     * @param dateTime The date-time object used for finding the nearest schedule.
     * @return An Optional containing the schedule, if found.
     */
    public Optional<SubscriptionScheduleEntity> findNearestAfterDateTime(final int subId,
            final boolean subEnabled, final @NonNull LocalDateTime dateTime) {

        return mSubscriptionSchedulesDao.findNearestByDayOfWeekAndTime(subId,
                    subEnabled, DaysOfWeek.getDayOfWeekFrom(dateTime), dateTime.toLocalTime(),
                    /*reverseSearch=*/ false);
    }

    /**
     * Sync the enabled state of all SIM subscriptions found on the device with their existing
     * weekly repeat schedules.
     *
     * @param compareTime The date-time object used for finding the eligible schedules.
     * @param overrideUserPreference Whether the user's preference should NOT take precedence over
     * schedules. For instance, if the SIM subscription is expected to be disabled, but the user
     * enabled it manually, then pass {@code false} to keep the state within the allowed period.
     */
    public void syncAllSubscriptionsEnabledState(final @NonNull LocalDateTime compareTime,
            final boolean overrideUserPreference) {

        boolean needSleep = false;
        for (final Subscription sub : mSubscriptionsLazy.get()) {
            if (sub.getSlotIndex() != INVALID_SIM_SLOT_INDEX) {
                // For reliability, we need to wait when performing multiple SIM power state
                // change requests consecutively, as the modem may hang, which requires manually
                // removing/re-inserting the SIM. Practice shows that this happens *very* rarely
                // and only when rapidly toggling one SIM after another
                if (needSleep) {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException ignored) { }
                }
            }

            mLogger.d("syncAllSubscriptionsEnabledState(compareTime=%s,overrideUserPreference=%s) "
                    + ": Syncing %s.", compareTime, overrideUserPreference, sub);

            final Optional<Boolean> newEnabledState = syncSubscriptionEnabledState(sub.getId(),
                    compareTime, overrideUserPreference);

            if (sub.getSlotIndex() != INVALID_SIM_SLOT_INDEX) {
                needSleep = newEnabledState.map((v) -> v != sub.isSimEnabled()).orElse(false);
            }
        }
    }

    /**
     * Sync the enabled state of a SIM subscription with its existing weekly repeat schedules.
     *
     * @param subId The ID of the subscription.
     * @param compareTime The date-time object used for finding the eligible schedules.
     * @param overrideUserPreference Whether the user's preference should NOT take precedence over
     * schedules. For instance, if the SIM subscription is expected to be disabled, but the user
     * manually enabled it, then pass {@code false} to keep the SIM state within the allowed period.
     * @return The new enabled state of the SIM subscription if changed.
     */
    public Optional<Boolean> syncSubscriptionEnabledState(final int subId,
            final @NonNull LocalDateTime compareTime, final boolean overrideUserPreference) {

        // Since we don't support seconds and milliseconds, drop them off to don't miss a sync
        final LocalDateTime compareTime2 = compareTime.truncatedTo(ChronoUnit.MINUTES);

        return mSubscriptionsLazy.get().getSubscriptionForSubId(subId).map((sub) -> {
            // Try to find the date-time of the nearest weekly repeat schedule that should have
            // enabled or actually enabled the SIM subscription on or before the stated time
            final Optional<LocalDateTime> nearestEnableTime = findNearestBeforeDateTime(subId,
                    /*subEnabled=*/ true, compareTime2).flatMap((schedule) ->
                    getDateTimeBefore(schedule, compareTime2));
            // Try to find the date-time of the nearest weekly repeat schedule that should have
            // disabled or actually disabled the SIM subscription on or before the stated time
            final Optional<LocalDateTime> nearestDisableTime = findNearestBeforeDateTime(subId,
                    /*subEnabled=*/ false, compareTime2).flatMap((schedule) ->
                    getDateTimeBefore(schedule, compareTime2));

            final boolean currentEnabled = sub.isSimEnabled();
            // Figure out the expected SIM subscription state using schedules from the past, if any
            final boolean expectedEnabled = getSubscriptionExpectedEnabledState(sub,
                    nearestEnableTime, nearestDisableTime, overrideUserPreference);

            mLogger.d("syncSubscriptionEnabledState(subId=%d,compareTime=%s," +
                    "overrideUserPreference=%s) : %s,nearestEnableTime=%s,nearestDisableTime=%s," +
                    "expectedEnabled=%s.", subId, compareTime, overrideUserPreference, sub,
                    nearestEnableTime, nearestDisableTime, expectedEnabled);

            // Sync the enabled state of the SIM subscription if it differs
            if (currentEnabled != expectedEnabled) {
                if (sub.getSlotIndex() == INVALID_SIM_SLOT_INDEX) {
                    mSubscriptionControllerLazy.get().setUiccApplicationsEnabled(subId,
                            expectedEnabled);
                } else {
                    boolean keepDisabledAcrossBoots =
                        Optional.ofNullable(sub.getKeepDisabledAcrossBoots()).orElse(false);
                    keepDisabledAcrossBoots &= !overrideUserPreference;
                    mTelephonyControllerLazy.get().setSimState(sub.getSlotIndex(), expectedEnabled,
                            keepDisabledAcrossBoots);
                }
                return expectedEnabled;
            }
            return null;
        });
    }

    /**
     * Get the total number of weekly repeat schedules for a particular SIM subscription.
     *
     * @param subId The ID of the subscription.
     * @return The number of {@link SubscriptionScheduleEntity} objects found.
     */
    public int getCountBySubscriptionId(final int subId) {
        return mSubscriptionSchedulesDao.getCount(subId);
    }

    /**
     * Re-schedule or schedule a new execution iteration in which the scheduler will process the
     * enabled state of SIM subscriptions through weekly repeat schedules at the time of the nearest
     * weekly repeat schedule that occurs on or after the given date-time.
     *
     * @param compareTime The date-time object to compare against.
     */
    public void updateNextWeeklyRepeatScheduleProcessingIter(final @NonNull LocalDateTime compareTime) {
        // Since we don't support seconds and milliseconds, drop them off before rescheduling to get
        // even more alarm accuracy
        final LocalDateTime compareTime2 = compareTime.truncatedTo(ChronoUnit.MINUTES);

        Optional<LocalDateTime> nextProcessingTime = Optional.empty();
        // Scan schedules only from currently active SIM subscriptions found on the device
        for (Subscription sub : mSubscriptionsLazy.get()) {
            // Try to find the date-time of the next weekly repeat schedule that will invert the
            // current enabled state of the subscription on or after the provided date-time
            final Optional<SubscriptionScheduleEntity> nearestSchedule =
                findNearestAfterDateTime(sub.getId(), !sub.isSimEnabled(), compareTime2);
            final Optional<LocalDateTime> nearestDateTime = nearestSchedule.flatMap((schedule) ->
                    getDateTimeAfter(schedule, compareTime2));

            mLogger.d("updateNextWeeklyRepeatScheduleProcessingIter(compareTime=%s) : Found %s, %s",
                    compareTime, sub, nearestSchedule);

            if (nearestDateTime.isPresent()) {
                if (nextProcessingTime.isPresent()) {
                    if (nearestDateTime.get().isBefore(nextProcessingTime.get())) {
                        nextProcessingTime = nearestDateTime;
                    }
                } else {
                    nextProcessingTime = nearestDateTime;
                }
            }
        }

        mLogger.d("updateNextWeeklyRepeatScheduleProcessingIter(compareTime=%s) : " +
                "nextProcessingTime=%s.", compareTime, nextProcessingTime);

        // If found an eligible schedule, then re-schedule the old alarm or schedule a new one,
        // otherwise, cancel the old alarm
        if (nextProcessingTime.isPresent()) {
            rescheduleNextScheduleProcessingIter(nextProcessingTime.get());
        } else {
            cancelNextScheduleProcessingIter();
        }
    }

    /**
     * Undo the {@link #rescheduleNextScheduleProcessingIter(LocalDateTime)}.
     */
    private void cancelNextScheduleProcessingIter() {
        mLogger.d("cancelNextScheduleProcessingIter().");

        mAlarmManagerLazy.get().cancel(getPendingIntent());
    }

    /**
     * Perform a database operation on a list of schedule entities.
     *
     * @param schedules The list of schedule entities.
     * @param opType The operation name that is performed on the provided schedule entities.
     */
    private void doSchedulesDatabaseRequest(final List<SubscriptionScheduleEntity> schedules,
            final ScheduleDatabaseOperationType opType) {

        switch (opType) {
            case ADD:
                final List<Long> ids = mSubscriptionSchedulesDao.insertAll(schedules);
                final Iterator<SubscriptionScheduleEntity> schedulesIter = schedules.iterator();
                ids.forEach((id) -> schedulesIter.next().setId(id));
                break;

            case UPDATE:
                mSubscriptionSchedulesDao.updateAll(schedules);
                break;

            case DELETE:
                mSubscriptionSchedulesDao.deleteAll(schedules);
                break;

            default: throw new RuntimeException("Unhandled operation type: " + opType);
        }

        mLogger.d("doSchedulesDatabaseRequest(schedules=[%s],opType=%s).",
                schedules.stream().map(Object::toString).collect(Collectors.joining(",")), opType);

        final LocalDateTime now = LocalDateTime.now();
        // We expect the schedules to take precedence over the user's preference when schedules
        // are explicitly mutated by the user
        final boolean overrideUserPreference = true;
        syncAllSubscriptionsEnabledState(now, overrideUserPreference);
        updateNextWeeklyRepeatScheduleProcessingIter(now.plusMinutes(1));
    }

    /**
     * (Re-)schedule the next iteration processing of SIM subscription weekly repeat schedules.
     *
     * @param dateTime A date-time object containing the exact time for the alarm to fire.
     */
    private void rescheduleNextScheduleProcessingIter(final LocalDateTime dateTime) {
        mLogger.d("rescheduleNextScheduleProcessingIter(dateTime=%s).", dateTime);

        final long millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        mAlarmManagerLazy.get().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis,
                getPendingIntent());
    }

    /**
     * Shortcut for {@link PendingIntent#getBroadcast(Context,int,Intent,int)} to retrieve the
     * existing or new operation to be passed to the system's alarm service.
     *
     * @return The pending operation instance.
     */
    private PendingIntent getPendingIntent() {
        return PendingIntent.getBroadcast(mContext, /*requestCode=*/ 0, mAlarmIntent,
                PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Determine the expected SIM subscription enabled state using an opened interval
     * ({@code a<x<b}) of two opposite schedules.
     *
     * @param sub The subscription for which to determine the expected enabled state.
     * @param startDateTime The interval start date-time value. This is expected to be date-time of
     * the weekly repeat schedule, if any, that should enable the SIM subscription.
     * @param endDateTime The interval end date-time value. This is expected to be the date-time of
     * the weekly repeat schedule, if any, that should disable the SIM subscription.
     * @param overrideUserPreference Whether the user's preference should NOT take precedence over
     * schedule intervals. For instance, if the SIM subscription is expected to be disabled, but the
     * user enabled it manually, then pass {@code false} to keep the state within the allowed
     * period.
     * @return {@code true} if the SIM subscription is expected to be enabled, {@code false}
     * otherwise.
     */
    private static boolean getSubscriptionExpectedEnabledState(final Subscription sub,
            final Optional<LocalDateTime> startDateTime, final Optional<LocalDateTime> endDateTime,
            final boolean overrideUserPreference) {

        if (sub.isSimEnabled()) {
            return endDateTime.map((end) -> {
                if (!overrideUserPreference && sub.getLastActivatedTime().isAfter(end)) {
                    return true;
                }

                // Note that, as per TelephonyController specs, turning off a SIM card on a device
                // using legacy RIL won't persist across boots. On reboot, SIM will turn on
                // normally, but the user may want it to be turned off until turned on again
                // manually or through a weekly repeat schedule, if any
                final boolean keepDisabledAcrossBoots =
                    Optional.ofNullable(sub.getKeepDisabledAcrossBoots()).orElse(false);

                return startDateTime.map((start) -> start.isAfter(end) &&
                        sub.getLastDeactivatedTime().isBefore(start) &&
                        !keepDisabledAcrossBoots).orElse(false);
            }).orElseGet(() -> {
                return Optional.ofNullable(sub.getKeepDisabledAcrossBoots()).filter((v) -> v)
                    .map((v) -> startDateTime.map((start) ->
                            sub.getLastDeactivatedTime().isBefore(start)).orElse(false))
                    .orElse(true);
            });
        }
        return startDateTime.map((start) -> {
            if (!overrideUserPreference && sub.getLastDeactivatedTime().isAfter(start)) {
                return false;
            }
            return endDateTime.map((end) -> end.isBefore(start)).orElse(true);
        }).orElse(false);
    }

    /**
     * Get the first date-time of a SIM subscription weekly repeat schedule that occurs on or before
     * the provided date-time.
     *
     * @param schedule The schedule to get the first date-time from.
     * @param compareTime The date-time object to compare against.
     * @return An Optional containing the date-time object if the provided schedule is not empty.
     */
    private static Optional<LocalDateTime> getDateTimeBefore(
            final @NonNull SubscriptionScheduleEntity schedule,
            final @NonNull LocalDateTime compareTime) {

        if (!schedule.getDaysOfWeek().isRepeating()) {
            return Optional.empty();
        }

        final @DayOfWeek int compareDayOfWeek = DaysOfWeek.getDayOfWeekFrom(compareTime);
        final int distanceToPreviousDay;
        // Check if the schedule is occurring on the same day
        if (schedule.getDaysOfWeek().isBitOn(compareDayOfWeek) &&
                (schedule.getTime().equals(compareTime.toLocalTime()) ||
                 schedule.getTime().isBefore(compareTime.toLocalTime()))) {
            distanceToPreviousDay = 0;
        } else {
            // Start seeking from the previous day of the week when there's no schedule occurring on
            // the same day
            distanceToPreviousDay = schedule.getDaysOfWeek()
                .getDistanceToPreviousDayOfWeek(compareDayOfWeek)
                .getAsInt();
        }
        return Optional.of(compareTime.minusDays(distanceToPreviousDay)
                .withHour(schedule.getTime().getHour()).withMinute(schedule.getTime().getMinute()));
    }

    /**
     * Get the first date-time of a SIM subscription weekly repeat schedule that occurs on or after
     * the provided date-time.
     *
     * @param schedule The schedule to get the first date-time from.
     * @param compareTime The date-time object to compare against.
     * @return An Optional containing the date-time object if the provided schedule is not empty.
     */
    public static Optional<LocalDateTime> getDateTimeAfter(
            final @NonNull SubscriptionScheduleEntity schedule,
            final @NonNull LocalDateTime compareTime) {

        if (!schedule.getDaysOfWeek().isRepeating()) {
            return Optional.empty();
        }

        @DayOfWeek int compareDayOfWeek = DaysOfWeek.getDayOfWeekFrom(compareTime);
        final int distanceToNextDay;
        // Check if the schedule is occurring on the same day
        if (schedule.getDaysOfWeek().isBitOn(compareDayOfWeek) &&
                (schedule.getTime().equals(compareTime.toLocalTime()) ||
                 schedule.getTime().isAfter(compareTime.toLocalTime()))) {
            distanceToNextDay = 0;
        } else {
            compareDayOfWeek++; // skip the current day
            if (compareDayOfWeek > DayOfWeek.SATURDAY) {
                compareDayOfWeek = DayOfWeek.SUNDAY;
            }
            // Start seeking from the next day of the week when there's no schedule occurring on the
            // same day
            distanceToNextDay = schedule.getDaysOfWeek()
                .getDistanceToNextDayOfWeek(compareDayOfWeek)
                .getAsInt() + 1;
        }
        return Optional.of(compareTime.plusDays(distanceToNextDay)
                .withHour(schedule.getTime().getHour()).withMinute(schedule.getTime().getMinute()));
    }
}
