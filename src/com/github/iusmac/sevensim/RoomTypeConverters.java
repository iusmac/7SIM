package com.github.iusmac.sevensim;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ProvidedTypeConverter;
import androidx.room.TypeConverter;

import com.github.iusmac.sevensim.scheduler.DaysOfWeek;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import javax.inject.Inject;

@ProvidedTypeConverter
public final class RoomTypeConverters {
    private final DaysOfWeek.Factory mDaysOfWeekFactory;

    @Inject
    public RoomTypeConverters(final DaysOfWeek.Factory daysOfWeekFactory) {
        mDaysOfWeekFactory = daysOfWeekFactory;
    }

    @TypeConverter
    public @Nullable LocalDateTime fromLocalDateTimeToString(final @Nullable String dateTime) {
        try {
            if (dateTime != null) {
                return LocalDateTime.parse(dateTime);
            }
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    @TypeConverter
    public @NonNull String fromStringToLocalDateTime(final @NonNull LocalDateTime ldt) {
        return ldt.toString();
    }

    @TypeConverter
    public @NonNull DaysOfWeek fromBitsToDaysOfWeek(final @Nullable Integer daysOfWeekBits) {
        return daysOfWeekBits == null ? mDaysOfWeekFactory.create() :
            mDaysOfWeekFactory.create(daysOfWeekBits);
    }

    @TypeConverter
    public @Nullable Integer toDaysOfWeekBits(final @Nullable DaysOfWeek daysOfWeek) {
        return daysOfWeek == null ? null : daysOfWeek.getBits();
    }

    @TypeConverter
    @Nullable
    public LocalTime fromMinutesSinceMidnight(final @Nullable Integer minutesSinceMidnight) {
        if (minutesSinceMidnight == null) {
            return null;
        }
        return LocalTime.of(minutesSinceMidnight / 60, minutesSinceMidnight % 60);
    }

    @TypeConverter
    public @Nullable Integer toMinutesSinceMidnight(final @Nullable LocalTime time) {
        return time == null ? null : time.getHour() * 60 + time.getMinute();
    }
}
