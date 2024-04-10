package com.github.iusmac.sevensim.scheduler;

import android.content.Context;
import android.content.res.Resources;
import android.icu.text.DisplayContext;
import android.icu.text.RelativeDateTimeFormatter;
import android.icu.util.ULocale;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.core.text.HtmlCompat;

import com.github.iusmac.sevensim.DateTimeUtils;
import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.telephony.Subscription;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Formatter;
import java.util.Locale;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>This class encapsulates the building process of a human-readable string summarizing the next
 * upcoming weekly repeat schedule for a particular SIM subscription.
 *
 * <p>This class is <strong>thread-safe</strong>.
 */
@Singleton
public final class SubscriptionSchedulerSummaryBuilder {
    @GuardedBy("this")
    private Formatter mFormatter;

    @GuardedBy("this")
    private StringBuilder mStringBuilder;

    @GuardedBy("this")
    private RelativeDateTimeFormatter mRelativeFormatter;

    @GuardedBy("this")
    private Locale mRelativeFormatterLocale;

    private final Context mContext;
    private final SubscriptionScheduler mSubscriptionScheduler;

    private final Resources mResources;

    @Inject
    public SubscriptionSchedulerSummaryBuilder(final @ApplicationContext Context context,
            final SubscriptionScheduler subscriptionScheduler) {

        mContext = context;
        mSubscriptionScheduler = subscriptionScheduler;

        mResources = context.getResources();
    }

    /**
     * Build a human-readable string summarizing the next upcoming weekly repeat schedule for a
     * particular SIM subscription.
     *
     * @param sub The subscription for which to create the summary.
     * @param dateTime The date-time object used for finding the nearest schedule.
     * @return The string containing the summary for the target subscription and date-time.
     */
    @WorkerThread
    public @NonNull CharSequence buildNextUpcomingSubscriptionScheduleSummary(
            final @NonNull Subscription sub, @NonNull LocalDateTime dateTime) {

        // Find the nearest weekly repeat schedule for the subscription that will invert its current
        // enabled state on or after the given date-time
        final Optional<SubscriptionScheduleEntity> nearestSchedule =
            mSubscriptionScheduler.findNearestAfterDateTime(sub.getId(), !sub.isSimEnabled(),
                    dateTime);

        if (!nearestSchedule.isPresent()) {
            final int total = mSubscriptionScheduler.getCountBySubscriptionId(sub.getId());
            if (total > 0) {
                return mResources.getText(sub.isSimEnabled() ?
                        R.string.scheduler_end_time_none_summary :
                        R.string.scheduler_start_time_none_summary);
            } else {
                return mResources.getString(R.string.scheduler_no_schedule_summary);
            }
        }

        final @StringRes int customTimeStringResId;
        if (nearestSchedule.get().getSubscriptionEnabled()) {
            customTimeStringResId = R.string.scheduler_start_time_custom_summary;
        } else {
            customTimeStringResId = R.string.scheduler_end_time_custom_summary;
        }

        // Since we don't support seconds and milliseconds, drop them off to avoid inexact summaries
        dateTime = dateTime.truncatedTo(ChronoUnit.MINUTES);

        // NOTE: if the SIM subscription state change time matches the target date-time, then we'll
        // start seeking for the next weekly repeat schedule date-time that happens no earlier than
        // one minute from the target date-time. This to avoid showing a summary for the schedule
        // that just happened
        final LocalDateTime dateTime2 = sub.getLastActivatedTime().equals(dateTime) ||
                sub.getLastDeactivatedTime().equals(dateTime) ? dateTime.plusMinutes(1) : dateTime;
        final LocalDateTime nearestScheduleDateTime =
            SubscriptionScheduler.getDateTimeAfter(nearestSchedule.get(), dateTime2).get();

        // NOTE: we *must* isolate the usage of the Formatter to avoid result aggregation from
        // concurrent threads, also protect the StringBuilder length resetting
        synchronized (this) {
            final CharSequence str = DateTimeUtils.getRelativeDateTimeSpanString(mContext,
                    getFormatter(Locale.getDefault()), getRelativeFormatter(Locale.getDefault()),
                    nearestScheduleDateTime, dateTime);
            return HtmlCompat.fromHtml(mResources.getString(customTimeStringResId, str),
                    HtmlCompat.FROM_HTML_MODE_COMPACT);
        }
    }

    /**
     * A reusable {@link Forammter} instance with a recyclable {@link StringBuilder}.
     *
     * @param loc The {@link Locale} for which to create an instance of {@link Formatter}.
     * @return The {@link Forammter} instance.
     */
    @GuardedBy("this")
    private Formatter getFormatter(final Locale loc) {
        if (mFormatter == null || !loc.equals(mFormatter.locale())) {
            if (mStringBuilder == null) {
                mStringBuilder = new StringBuilder(30);
            } else {
                mStringBuilder.setLength(0);
            }
            mFormatter = new Formatter(mStringBuilder, loc);
        } else {
            mStringBuilder.setLength(0);
        }
        return mFormatter;
    }

    /**
     * A reusable {@link RelativeDateTimeFormatter} instance.
     *
     * @param loc The {@link Locale} for which to create an instance of
     * {@link RelativeDateTimeFormatter}.
     * @return The {@link RelativeDateTimeFormatter} instance.
     */
    @GuardedBy("this")
    private RelativeDateTimeFormatter getRelativeFormatter(final Locale loc) {
        if (mRelativeFormatter == null || !loc.equals(mRelativeFormatterLocale)) {
            final RelativeDateTimeFormatter.Style style = RelativeDateTimeFormatter.Style.SHORT;
            final DisplayContext displayContext = DisplayContext.CAPITALIZATION_NONE;
            mRelativeFormatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(loc),
                    /*NumberFormat=*/ null, style, displayContext);
            mRelativeFormatterLocale = loc;
        }
        return mRelativeFormatter;
    }
}
