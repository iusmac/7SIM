package com.github.iusmac.sevensim;

import android.app.Notification;
import android.content.Context;
import android.content.res.Resources;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;

/**
 * This class encapsulates managing of the application's notifications.
 */
public final class NotificationManager {
    static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final String FOREGROUND_NOTIFICATION_CHANNEL_ID =
        "foreground_notification_channel";

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManagerCompat;
    private final Resources mResources;

    @Inject
    NotificationManager(final @ApplicationContext Context context,
            final NotificationManagerCompat notificationManagerCompat) {

        mContext = context;
        mNotificationManagerCompat = notificationManagerCompat;
        mResources = context.getResources();
    }

    /**
     * Build and return a new notification for the {@link ForegroundService}.
     *
     * @return An instance of {@link Notification}.
     */
    Notification buildForegroundServiceNotification() {
        final Notification notification = new NotificationCompat.Builder(mContext,
                FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_sim_icon)
            .setContentTitle(mResources.getString(R.string.foreground_notification_title))
            .setShowWhen(false)
            .setLocalOnly(true)
            .build();

        return notification;
    }

    /** Prepare a channel for foreground notifications. */
    void createForegroundNotificationChannel() {
        final NotificationChannelCompat.Builder builder = new NotificationChannelCompat.Builder(
                FOREGROUND_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(mResources.getString(R.string.foreground_notification_channel_name))
            .setDescription(mResources.getString(
                        R.string.foreground_notification_channel_description));
        mNotificationManagerCompat.createNotificationChannel(builder.build());
    }
}
