package com.github.iusmac.sevensim;

import androidx.core.app.NotificationManagerCompat;

import dagger.hilt.android.testing.HiltAndroidTest;

import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public class SevenSimApplicationTest extends MockitoHiltAndroidTestBase {
    private static final String FOREGROUND_NOTIFICATION_CHANNEL_ID =
        "foreground_notification_channel";

    private static final String IMPORTANT_NOTIFICATION_CHANNEL_ID =
        "important_notification_channel";

    @Inject
    NotificationManagerCompat mNotificationManagerCompat;

    @Inject
    SevenSimApplication mApplication;

    @Test
    public void test_onCreate_foregroundNotificationChannelStringsRegen() {
        final var oldChannel = mNotificationManagerCompat
            .getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL_ID);
        final String oldChannelName = oldChannel.getName().toString();
        final String oldChannelDescription = oldChannel.getDescription();

        RuntimeEnvironment.setQualifiers("it");
        mApplication.onCreate();

        final var newChannel = mNotificationManagerCompat
            .getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL_ID);
        final String newChannelName = newChannel.getName().toString();
        final String newChannelDescription = newChannel.getDescription();

        assertThat(newChannelName, is(not(equalTo(oldChannelName))));
        if (oldChannelDescription != null) {
            assertThat(newChannelDescription, is(not(equalTo(oldChannelDescription))));
        }
    }

    @Test
    public void test_onCreate_createImportantNotificationChannelStringsRegen() {
        final var oldChannel = mNotificationManagerCompat
            .getNotificationChannel(IMPORTANT_NOTIFICATION_CHANNEL_ID);
        final String oldChannelName = oldChannel.getName().toString();
        final String oldChannelDescription = oldChannel.getDescription();

        RuntimeEnvironment.setQualifiers("it");
        mApplication.onCreate();

        final var newChannel = mNotificationManagerCompat
            .getNotificationChannel(IMPORTANT_NOTIFICATION_CHANNEL_ID);
        final String newChannelName = newChannel.getName().toString();
        final String newChannelDescription = newChannel.getDescription();

        assertThat(newChannelName, is(not(equalTo(oldChannelName))));
        if (oldChannelDescription != null) {
            assertThat(newChannelDescription, is(not(equalTo(oldChannelDescription))));
        }
    }

    @Test
    public void test_hasAospPlatformSignature() {
        assertThat(mApplication.hasAospPlatformSignature(), is(any(boolean.class)));
    }

    @Test
    public void test_getPackageVersionName() {
        assertThat(mApplication.getPackageVersionName(), is(any(String.class)));
    }

    @Test
    public void test_isSystemApplication() {
        assertThat(mApplication.isSystemApplication(), is(any(boolean.class)));
    }
}
