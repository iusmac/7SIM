package com.github.iusmac.sevensim;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.ui.scheduler.SchedulerActivity;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;

/**
 * This class encapsulates managing of the application's notifications.
 */
public final class NotificationManager {
    static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int BACKGROUND_RESTRICTED_NOTIFICATION_ID = 2;
    static final int CALL_IN_PROGRESS_NOTIFICATION_ID = 3;
    static final int UNLOCK_TO_CONTINUE_NOTIFICATION_ID = 4;
    private static final int SIM_PIN_ERROR_BASE_NOTIFICATION_ID = 0x1000;
    private static final String FOREGROUND_NOTIFICATION_CHANNEL_ID =
        "foreground_notification_channel";
    private static final String IMPORTANT_NOTIFICATION_CHANNEL_ID =
        "important_notification_channel";

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManagerCompat;
    private final ApplicationInfo mApplicationInfo;

    private final Resources mResources;

    @Inject
    NotificationManager(final @ApplicationContext Context context,
            final NotificationManagerCompat notificationManagerCompat,
            final ApplicationInfo applicationInfo) {

        mContext = context;
        mNotificationManagerCompat = notificationManagerCompat;
        mApplicationInfo = applicationInfo;

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

    /**
     * <p>Show a notification to inform the user that this app has been prevented from running in
     * the background.
     *
     * <p>The user will be invited to prioritize app in screen of details about this application
     * within the built-in Settings app.
     */
    void showBackgroundRestrictedNotification() {
        final PendingIntent pIntent = PendingIntent.getActivityAsUser(mContext, /*requestCode=*/ 0,
                mApplicationInfo.getAppBatterySettingsActivityIntent(),
                PendingIntent.FLAG_IMMUTABLE, /*options=*/ null, UserHandle.CURRENT);

        final String text = mResources.getString(R.string.background_restricted_notification_text);
        final Notification notification = new NotificationCompat.Builder(mContext,
                IMPORTANT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_sim_icon)
            .setContentTitle(mResources.getString(R.string.background_restricted_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pIntent)
            .setAutoCancel(true)
            .build();

        try {
            mNotificationManagerCompat.notify(BACKGROUND_RESTRICTED_NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {}
    }

    /**
     * Build and return a new notification to inform the user that currently, execution of a
     * background task via {@link PhoneCallEndObserverService} is halted due to a phone call.
     */
    Notification buildCallInProgressForegroundNotification() {
        final String text =
            mContext.getString(R.string.foreground_notification_call_in_progress_text);
        final Notification notification = new NotificationCompat.Builder(mContext,
                buildForegroundServiceNotification())
            .setContentTitle(mResources.getString(
                        R.string.foreground_notification_paused_in_background_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .build();

        return notification;
    }

    /**
     * Build and return a new notification to inform the user that currently, execution of a
     * background task via {@link UserAuthenticationObserverService} is halted because device is
     * locked.
     */
    Notification buildUnlockToContinueNotification() {
        final String text =
            mResources.getString(R.string.foreground_notification_unlock_to_continue_text);
        final Notification notification = new NotificationCompat.Builder(mContext,
                buildForegroundServiceNotification())
            .setContentTitle(mResources.getString(
                        R.string.foreground_notification_paused_in_background_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .build();

        return notification;
    }

    /**
     * <p>Show a notification to inform the user that the last SIM PIN operation for a SIM
     * subscription, such as PIN code decryption, was unsuccessful.
     *
     * <p>The user will be invited to fix the issue by touching the notification.
     *
     * @param sub The target SIM subscription for which to show the notification.
     */
    public void showSimPinOperationFailedNotification(final @NonNull Subscription sub) {
        showSimPinErrorNotification(sub, mResources.getString(R.string.sim_pin_operation_failed));
    }

    /**
     * <p>Show a notification to inform the user that the last attempt to unlock the SIM card using
     * the provided PIN code failed.
     *
     * <p>The user will be invited to fix the issue by touching the notification.
     *
     * @param sub The target SIM subscription for which to show the notification.
     */
    public void showSimUnlockFailedNotification(final @NonNull Subscription sub) {
        showSimPinErrorNotification(sub, mResources.getString(R.string.sim_unlock_failed));
    }

    /**
     * <p>Show a notification to inform the user about a SIM PIN code-related error.
     *
     * <p>The can jump to the corresponding SIM scheduler (hosting the PIN) when touching the
     * notification.
     *
     * @param sub The target SIM subscription for which to show the notification.
     * @param reason The message of the notification describing the reason of the issue.
     */
    private void showSimPinErrorNotification(final Subscription sub, final String reason) {
        // Launch scheduler activity for the subscription that hosts the PIN on click
        final Intent aIntent = new Intent(mContext, SchedulerActivity.class);
        aIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        aIntent.putExtra(SchedulerActivity.EXTRA_SUBSCRIPTION, sub);
        final PendingIntent pIntent = PendingIntent.getActivityAsUser(mContext, /*requestCode=*/
                sub.getId(), aIntent, PendingIntent.FLAG_IMMUTABLE |
                PendingIntent.FLAG_UPDATE_CURRENT, /*options=*/ null, UserHandle.CURRENT);

        final String text = mResources.getString(R.string.notification_tap_to_fix_text);
        final Notification notification = new NotificationCompat.Builder(mContext,
                IMPORTANT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_sim_icon)
            .setContentTitle(mResources.getString(R.string.pin_error_with_carrier_name_template,
                        reason, sub.getSimName()))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pIntent)
            .setAutoCancel(true)
            .build();

        try {
            mNotificationManagerCompat.notify(SIM_PIN_ERROR_BASE_NOTIFICATION_ID + sub.getId(),
                    notification);
        } catch (SecurityException ignored) {}
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

    /** Prepare a channel for important notifications. */
    void createImportantNotificationChannel() {
        final NotificationChannelCompat.Builder builder = new NotificationChannelCompat.Builder(
                IMPORTANT_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(mResources.getString(R.string.notification_important_channel_name));
        mNotificationManagerCompat.createNotificationChannel(builder.build());
    }
}
