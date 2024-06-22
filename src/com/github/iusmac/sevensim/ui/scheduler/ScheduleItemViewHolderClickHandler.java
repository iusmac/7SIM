package com.github.iusmac.sevensim.ui.scheduler;

import com.github.iusmac.sevensim.scheduler.DayOfWeek;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduleEntity;

/**
 * Click handler for a schedule item.
 */
interface ScheduleItemViewHolderClickHandler {
    void onClockClicked(SubscriptionScheduleEntity schedule);

    void onEditLabelClicked(SubscriptionScheduleEntity schedule);

    void onDayOfWeekChanged(SubscriptionScheduleEntity schedule, @DayOfWeek int dayOfWeek,
            boolean enabled);

    void onEnabledStateChanged(SubscriptionScheduleEntity schedule, boolean enabled);

    void onSubscriptionEnabledStateChanged(SubscriptionScheduleEntity schedule, boolean enabled);

    void onDeleteClicked(SubscriptionScheduleEntity schedule);
}
