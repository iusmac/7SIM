package com.github.iusmac.sevensim;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.core.app.NotificationManagerCompat;

import com.github.iusmac.sevensim.telephony.SimState;
import com.github.iusmac.sevensim.telephony.Subscription;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.ui.scheduler.SchedulerActivity;

import dagger.hilt.android.testing.HiltAndroidTest;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mockito;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowNotificationManager;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class NotificationManagerTest extends MockitoHiltAndroidTestBase {
    private static final int BACKGROUND_RESTRICTED_NOTIFICATION_ID = 2;
    private static final int SIM_PIN_ERROR_BASE_NOTIFICATION_ID = 0x1000;
    private static final String FOREGROUND_NOTIFICATION_CHANNEL_ID =
        "foreground_notification_channel";
    private static final String IMPORTANT_NOTIFICATION_CHANNEL_ID =
        "important_notification_channel";

    @Inject
    ApplicationInfo mApplicationInfo;

    @Inject
    NotificationManagerCompat mNotificationManagerCompat;

    @Inject
    NotificationManager mNotificationManager;

    private Resources mResources;
    private ShadowNotificationManager mShadowNotificationManager;

    @Override
    public void setUp() {
        super.setUp();

        mResources = mApplicationContext.getResources();
        mShadowNotificationManager = shadowOf(mApplicationContext
                .getSystemService(android.app.NotificationManager.class));
    }

    @Test
    public void test_buildForegroundServiceNotification() {
        final var notification = mNotificationManager.buildForegroundServiceNotification();
        final Bundle extras = notification.extras;

        assertIsForegroundNotification(notification);
        assertThat(extras.getString(Notification.EXTRA_TITLE),
                is(mResources.getString(R.string.foreground_notification_title)));
    }

    @Test
    public void test_showBackgroundRestrictedNotification() {
        mNotificationManager.showBackgroundRestrictedNotification();
        final var notification =
            mShadowNotificationManager.getNotification(BACKGROUND_RESTRICTED_NOTIFICATION_ID);
        final Bundle extras = notification.extras;
        final int flags = notification.flags;
        final var text = mResources.getString(R.string.background_restricted_notification_text);
        final var pIntent = PendingIntent.getActivityAsUser(mApplicationContext,
                /*requestCode=*/ 0, mApplicationInfo.getAppBatterySettingsActivityIntent(),
                PendingIntent.FLAG_IMMUTABLE, /*options=*/ null, UserHandle.CURRENT);

        assertIsImportantNotification(notification);
        assertThat(extras.getString(Notification.EXTRA_TITLE),
                is(mResources.getString(R.string.background_restricted_title)));
        assertThat(extras.getString(Notification.EXTRA_TEXT), is(text));
        assertThat(extras.getString(Notification.EXTRA_BIG_TEXT), is(text));
        assertThat(notification.contentIntent, is(pIntent));
        assertThat(flags & Notification.FLAG_AUTO_CANCEL, is(Notification.FLAG_AUTO_CANCEL));
    }

    @Test
    public void test_showBackgroundRestrictedNotificationWithSecurityException() {
        doThrow(SecurityException.class).when(mNotificationManagerCompat)
            .notify(eq(BACKGROUND_RESTRICTED_NOTIFICATION_ID), Mockito.any(Notification.class));

        mNotificationManager.showBackgroundRestrictedNotification();

        assertThat(mShadowNotificationManager
                .getNotification(BACKGROUND_RESTRICTED_NOTIFICATION_ID), is(nullValue()));
    }

    @Test
    public void test_buildCallInProgressForegroundNotification() {
        final var notification = mNotificationManager.buildCallInProgressForegroundNotification();
        final Bundle extras = notification.extras;
        final var text = mResources.getString(R.string
                .foreground_notification_call_in_progress_text);

        assertIsForegroundNotification(notification);
        assertThat(extras.getString(Notification.EXTRA_TITLE), is(mResources
                    .getString(R.string.foreground_notification_paused_in_background_title)));
        assertThat(extras.getString(Notification.EXTRA_TEXT), is(text));
        assertThat(extras.getString(Notification.EXTRA_BIG_TEXT), is(text));
    }

    @Test
    public void test_buildUnlockToContinueNotification() {
        final var notification = mNotificationManager.buildUnlockToContinueNotification();
        final Bundle extras = notification.extras;
        final var text = mResources.getString(R.string
                .foreground_notification_unlock_to_continue_text);

        assertIsForegroundNotification(notification);
        assertThat(extras.getString(Notification.EXTRA_TITLE), is(mResources
                    .getString(R.string.foreground_notification_paused_in_background_title)));
        assertThat(extras.getString(Notification.EXTRA_TEXT), is(text));
        assertThat(extras.getString(Notification.EXTRA_BIG_TEXT), is(text));
    }

    @Test
    public void test_showSimPinOperationFailedNotification() {
        final var sub = new Subscription();
        sub.setId(2);
        sub.setSimState(SimState.ENABLED);
        sub.setSimName("SIM 1");

        mNotificationManager.showSimPinOperationFailedNotification(sub);
        assertSimPinErrorNotificationIsShown(sub,
                mResources.getString(R.string.sim_pin_operation_failed));
    }

    @Test
    public void test_showSimPinOperationFailedNotificationWithSecurityException() {
        final var sub = new Subscription();
        sub.setId(2);

        final int notificationId = SIM_PIN_ERROR_BASE_NOTIFICATION_ID + sub.getId();

        doThrow(SecurityException.class).when(mNotificationManagerCompat).notify(eq(notificationId),
                Mockito.any(Notification.class));

        mNotificationManager.showSimPinOperationFailedNotification(sub);
        assertThat(mShadowNotificationManager.getNotification(notificationId), is(nullValue()));
    }

    @Test
    public void test_showSimUnlockFailedNotification() {
        final var sub = new Subscription();
        sub.setId(5);
        sub.setSimState(SimState.ENABLED);
        sub.setSimName("SIM 2");

        mNotificationManager.showSimUnlockFailedNotification(sub);
        assertSimPinErrorNotificationIsShown(sub, mResources.getString(R.string.sim_unlock_failed));
    }

    @Test
    public void test_showSimUnlockFailedNotificationWithSecurityException() {
        final var sub = new Subscription();
        sub.setId(5);

        final int notificationId = SIM_PIN_ERROR_BASE_NOTIFICATION_ID + sub.getId();

        doThrow(SecurityException.class).when(mNotificationManagerCompat).notify(eq(notificationId),
                Mockito.any(Notification.class));

        mNotificationManager.showSimUnlockFailedNotification(sub);
        assertThat(mShadowNotificationManager.getNotification(notificationId), is(nullValue()));
    }

    @Test
    public void test_createForegroundNotificationChannel() {
        mNotificationManager.createForegroundNotificationChannel();
        final var channel = mNotificationManagerCompat
            .getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL_ID);

        assertThat(channel.getImportance(), is(NotificationManagerCompat.IMPORTANCE_LOW));
        assertThat(channel.getName().toString(), is(mResources.getString(R.string
                        .foreground_notification_channel_name)));
        assertThat(channel.getDescription(), is(mResources.getString(R.string
                        .foreground_notification_channel_description)));
    }

    @Test
    public void test_createImportantNotificationChannel() {
        mNotificationManager.createImportantNotificationChannel();
        final var channel = mNotificationManagerCompat
            .getNotificationChannel(IMPORTANT_NOTIFICATION_CHANNEL_ID);

        assertThat(channel.getImportance(), is(NotificationManagerCompat.IMPORTANCE_HIGH));
        assertThat(channel.getName().toString(), is(mResources.getString(R.string
                        .notification_important_channel_name)));
        assertThat(channel.getDescription(), is(nullValue()));
    }

    private void assertIsForegroundNotification(final Notification notification) {
        final Bundle extras = notification.extras;
        final int flags = notification.flags;

        assertThat(notification.getChannelId(), is(FOREGROUND_NOTIFICATION_CHANNEL_ID));
        assertThat(notification.getSmallIcon().getResId(), is(R.drawable.ic_qs_sim_icon));
        assertFalse(extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
        assertThat(flags & Notification.FLAG_LOCAL_ONLY, is(Notification.FLAG_LOCAL_ONLY));
    }

    private void assertIsImportantNotification(final Notification notification) {
        assertThat(notification.getChannelId(), is(IMPORTANT_NOTIFICATION_CHANNEL_ID));
        assertThat(notification.getSmallIcon().getResId(), is(R.drawable.ic_qs_sim_icon));
    }

    private void assertSimPinErrorNotificationIsShown(final Subscription sub, final String reason) {
        final var aIntent = new Intent(mApplicationContext, SchedulerActivity.class);
        aIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        aIntent.putExtra(SchedulerActivity.EXTRA_SUBSCRIPTION, sub);

        final var notification = mShadowNotificationManager
            .getNotification(SIM_PIN_ERROR_BASE_NOTIFICATION_ID + sub.getId());
        final int flags = notification.flags;
        final Bundle extras = notification.extras;
        final var text = mResources.getString(R.string.notification_tap_to_fix_text);
        final var pIntent = PendingIntent.getActivityAsUser(mApplicationContext, /*requestCode=*/
                sub.getId(), aIntent, PendingIntent.FLAG_IMMUTABLE |
                PendingIntent.FLAG_UPDATE_CURRENT, /*options=*/ null, UserHandle.CURRENT);

        assertIsImportantNotification(notification);
        assertThat(extras.getString(Notification.EXTRA_TITLE), is(mResources
                    .getString(R.string.pin_error_with_carrier_name_template, reason,
                        sub.getSimName())));
        assertThat(extras.getString(Notification.EXTRA_TEXT), is(text));
        assertThat(extras.getString(Notification.EXTRA_BIG_TEXT), is(text));
        assertThat(notification.contentIntent, is(pIntent));
        assertThat(flags & Notification.FLAG_AUTO_CANCEL, is(Notification.FLAG_AUTO_CANCEL));
    }
}
