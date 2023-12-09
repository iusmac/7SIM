package com.github.iusmac.sevensim;

import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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

    /** Do not initialize. */
    private DateTimeUtils() {}
}
