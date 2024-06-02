package com.github.iusmac.sevensim.scheduler;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Update;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Dao
public interface SubscriptionSchedulesDao {
    @Insert
    List<Long> insertAll(List<SubscriptionScheduleEntity> scheduleEntities);

    @Update
    void updateAll(List<SubscriptionScheduleEntity> scheduleEntities);

    @Delete
    void deleteAll(List<SubscriptionScheduleEntity> scheduleEntities);

    @Query("SELECT * FROM subscription_schedules WHERE sub_id = :subId")
    List<SubscriptionScheduleEntity> findAllBySubscriptionId(int subId);

    /**
     * Search for the nearest SIM subscription weekly repeat schedule that occurs on or after the
     * given day of the week and time.
     *
     * @param subId The ID of the subscription.
     * @param subEnabled The scheduled enabled state of the subscription.
     * @param dayOfWeek Any of {@link DayOfWeek} values.
     * @param time Time as seen on a wall clock.
     * @param reverseSearch Whether to search for the nearest schedule that occurs on or before the
     * given day of the week and time.
     * @return An Optional containing the {@link SubscriptionScheduleEntity} instance, if any.
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT *,"
            // Auxiliary fields to make the placeholder substitution easier for the Room lib
            + ":dayOfWeek AS day_of_week, :time AS time "
        + "FROM subscription_schedules "
        + "WHERE "
            // Include only schedules corresponding to a particular subscription and enabled state
            + "sub_id = :subId AND sub_enabled = :subEnabled "
            // Include only enabled schedules
            + "AND enabled = TRUE "
            // Include only schedules having at least one day of the week enabled
            + "AND days_of_week_bits > 0 "
        // Calculate the time gap in minutes to the nearest schedule that will occur on or after the
        // given day of the week and time, sorting all in ascending order to appear the nearest one
        // (with the smallest time gap) at the top of the list
        + "ORDER BY ABS(CASE WHEN NOT :reverseSearch THEN "
            + "CASE "
                + "WHEN days_of_week_bits & (1 << day_of_week - 1) > 0 " // wanted day of the week
                    // Check if the schedule is occurring on the same day
                    + "AND minutes_since_midnight >= time THEN 0 "
                + "WHEN days_of_week_bits & (1 << day_of_week % 7) > 0 THEN 1 " // +1 day
                + "WHEN days_of_week_bits & (1 << (day_of_week + 1) % 7) > 0 THEN 2 " // +2 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 2) % 7) > 0 THEN 3 " // +3 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 3) % 7) > 0 THEN 4 " // +4 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 4) % 7) > 0 THEN 5 " // +5 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 5) % 7) > 0 THEN 6 " // +6 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 6) % 7) > 0 THEN 7 " // +7 days
            + "END "
        + "ELSE "
            + "CASE "
                + "WHEN days_of_week_bits & (1 << day_of_week - 1) > 0 "
                    + "AND minutes_since_midnight <= time THEN 0 "
                + "WHEN days_of_week_bits & (1 << (day_of_week + 5) % 7) > 0 THEN -1 " // -1 day
                + "WHEN days_of_week_bits & (1 << (day_of_week + 4) % 7) > 0 THEN -2 " // -2 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 3) % 7) > 0 THEN -3 " // -3 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 2) % 7) > 0 THEN -4 " // -4 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 1) % 7) > 0 THEN -5 " // -5 days
                + "WHEN days_of_week_bits & (1 << day_of_week % 7) > 0 THEN -6 " // -6 days
                + "WHEN days_of_week_bits & (1 << (day_of_week + 6) % 7) > 0 THEN -7 " // -7 days
            + "END "
        + "END * 24 * 60 + minutes_since_midnight - time) "
    + "LIMIT 1")
    Optional<SubscriptionScheduleEntity> findNearestByDayOfWeekAndTime(int subId,
            boolean subEnabled, @DayOfWeek int dayOfWeek, LocalTime time, boolean reverseSearch);

    /**
     * Get the total number of weekly repeat schedules for a particular SIM subscription.
     *
     * @param subId The ID of the subscription.
     * @return The row count.
    */
    @Query("SELECT COUNT(*) FROM subscription_schedules WHERE sub_id = :subId")
    int getCount(int subId);

    /**
     * Get the total number of weekly repeat schedules currently in the storage.
     *
     * @return The row count.
     */
    @Query("SELECT COUNT(*) FROM subscription_schedules")
    int getCount();
}
