package com.github.iusmac.sevensim;

import android.content.Context;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.TimeZone;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Formatter;
import java.util.Locale;
import java.util.Optional;

public final class DateTimeUtils {
    private static final int DAY_IN_MS = 24 * 60 * 60 * 1000;
    private static final int EPOCH_JULIAN_DAY = 2440588;

    /**
     * Obtain an instance of {@link LocalDateTime} from a text representation of date-time string.
     *
     * @param dateTime The date-time as text such as 2007-12-03T10:15:30.
     * @return An Optional containing the date-time object, if it can be parsed.
     */
    public static Optional<LocalDateTime> parseDateTime(final @Nullable String dateTime) {
        try {
            if (dateTime != null) {
                return Optional.of(LocalDateTime.parse(dateTime));
            }
        } catch (DateTimeParseException ignored) { }
        return Optional.empty();
    }

    /**
     * @param context The context for detecting the 12-/24-hour format.
     * @param time The {@link LocalTime} to express in human-readable format according to the
     * current locale.
     * @return The time in pretty format, such as 10:00 AM.
     */
    public static @NonNull CharSequence getPrettyTime(final @NonNull Context context,
            final @NonNull LocalTime time) {

        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, time.getHour());
        calendar.set(Calendar.MINUTE, time.getMinute());

        final CharSequence pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(context) ? "Hm" : "hma");
        return DateFormat.format(pattern, calendar);
    }

    /**
     * <p>Return string formatted like "[relative time/date], [time]", that describes the 'time' as
     * a time relative to 'now'.
     *
     * <p>Example output strings for the US date format:
     *
     * <ul>
     * <li>Tomorrow, 12:20 PM</li>
     * <li>Mon, Dec 12, 8 AM</li>
     * <li>Wed, 11/14/2007, 8:20 AM</li>
     * </ul>
     *
     * <p>This method combines these two built-in methods:
     * {@link DateUtils#getRelativeDateTimeString(Context,long,long,long,int)}
     * {@link DateUtils#getRelativeTimeSpanString(long,long,long,int)}
     *
     * <p>The "minResolution" parameter defaults to {@link DateUtils#DAY_IN_MILLIS} and the
     * "transitionResolution" to 1 day, so that a relative string like "In 4 days, 8 AM", will
     * transition to a more straightforward form like "Mon, Dec 12, 8 AM". The "flags" parameter
     * will always contain the {@link DateUtils#FORMAT_ABBREV_ALL} flag, so that the returned
     * relative string is shortened as much as possible.
     *
     * <p>Unlike the {@link DateUtils#getRelativeDateTimeString(Context,long,long,long,int)}, this
     * implementation prefers the user-chosen 12- and 24-hour format over that provided by the
     * current locale.
     *
     * @param context The context for l10n.
     * @param formatter The Formatter used for formatting the relative data/time string. Note: be
     * sure to call {@code setLength(0)} on {@link StringBuilder} passed to the Formatter
     * constructor unless you want the results to accumulate.
     * @param relativeFormatter Relative Formatter to concatenate the relative date-time via the
     * {@link RelativeDateTimeFormatter#combineDateAndTime(String,String)}.
     * @param time The date-time to describe.
     * @param now The current date-time.
     * @param zoneId The zone ID to compute the string in. Pass {@code null} to use system default.
     * @return A relative date-time string to display the date-time to describe.
     */
    public static @NonNull CharSequence getRelativeDateTimeSpanString(
            final @NonNull Context context, final @NonNull Formatter formatter,
            final @NonNull RelativeDateTimeFormatter relativeFormatter,
            final @NonNull LocalDateTime time, final @NonNull LocalDateTime now,
            @Nullable ZoneId zoneId) {

        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        final ZonedDateTime zonedTime = time.atZone(zoneId);
        final ZonedDateTime zonedNow = now.atZone(zoneId);
        final long timeMillis = zonedTime.toInstant().toEpochMilli();
        final long nowMillis = zonedNow.toInstant().toEpochMilli();
        final TimeZone tz = TimeZone.getFrozenTimeZone(zoneId.getId());
        final int dayDistance = Math.abs(dayDistance(tz, nowMillis, timeMillis));

        // Always show the time and abbreviate everything possible
        int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL;
        if (dayDistance > 1) {
            // Include the day/month like "Wed, Nov 14"
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
            // Include the year if it differs in both 'time' and 'now'; also compact all to numeric
            // format (e.g.: "14 Nov 2007" -> "11/14/2007")
            if (zonedNow.getYear() != zonedTime.getYear()) {
                flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_NUMERIC_DATE;
            }
        }

        final Formatter commonFormatter = DateUtils.formatDateRange(context, formatter, timeMillis,
                timeMillis, flags, tz.getID());

        // Format like "[yesterday/today/tomorrow], [time]" when appropriate
        if (dayDistance < 2) {
            final boolean past = (nowMillis >= timeMillis);

            final RelativeDateTimeFormatter.Direction direction;
            if (dayDistance == 0) { // Show "Today" if 'time' and 'now' are on the same day
                direction = RelativeDateTimeFormatter.Direction.THIS;
            } else if (past) { // Show "Yesterday" instead of "1 day ago"
                direction = RelativeDateTimeFormatter.Direction.LAST;
            } else { // Show "Tomorrow" instead of "In 1 day"
                direction = RelativeDateTimeFormatter.Direction.NEXT;
            }

            final String dateClause = relativeFormatter.format(direction,
                    RelativeDateTimeFormatter.AbsoluteUnit.DAY);
            final String timeClause = commonFormatter.toString();
            return relativeFormatter.combineDateAndTime(dateClause, timeClause);
        }
        // otherwise transition to the "[full date], [time]" format
        return commonFormatter.toString();
    }

    /**
     * Like {@link #getRelativeDateTimeSpanString(Context,Formatter,RelativeDateTimeFormatter,LocalDateTime,LocalDateTime,ZoneId)},
     * but use the default zone ID.
     */
    public static @NonNull CharSequence getRelativeDateTimeSpanString(
            final @NonNull Context context, final @NonNull Formatter formatter,
            final @NonNull RelativeDateTimeFormatter relativeFormatter,
            final @NonNull LocalDateTime time, final @NonNull LocalDateTime now) {

        return getRelativeDateTimeSpanString(context, formatter, relativeFormatter, time, now,
                /*zoneId=*/ null);
    }

    /**
     * @return The date difference for the two times in a given timezone.
     */
    private static int dayDistance(final TimeZone icuTimeZone, long startTime, long endTime) {
        return julianDay(icuTimeZone, endTime) - julianDay(icuTimeZone, startTime);
    }

    private static int julianDay(final TimeZone icuTimeZone, final long time) {
        long utcMs = time + icuTimeZone.getOffset(time);
        return (int) (utcMs / DAY_IN_MS) + EPOCH_JULIAN_DAY;
    }

    /** Do not initialize. */
    private DateTimeUtils() {}
}
