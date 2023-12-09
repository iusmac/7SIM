package com.github.iusmac.sevensim.scheduler;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Calendar;

/**
 * Sunday-based type hint representing the 7 days of the week as integers from Sunday to Saturday.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
})
public @interface DayOfWeek {
    int SUNDAY = Calendar.SUNDAY; // 1
    int MONDAY = Calendar.MONDAY; // 2
    int TUESDAY = Calendar.TUESDAY; // 3
    int WEDNESDAY = Calendar.WEDNESDAY; // 4
    int THURSDAY = Calendar.THURSDAY; // 5
    int FRIDAY = Calendar.FRIDAY; // 6
    int SATURDAY = Calendar.SATURDAY; // 7
}
