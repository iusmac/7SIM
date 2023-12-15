package com.github.iusmac.sevensim;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Locale;
import java.util.Optional;

public final class DateTimeUtils {
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

    /** Do not initialize. */
    private DateTimeUtils() {}
}
