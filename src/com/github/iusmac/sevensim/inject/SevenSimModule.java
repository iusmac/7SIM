package com.github.iusmac.sevensim.inject;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.telecom.TelecomManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.RoomTypeConverters;
import com.github.iusmac.sevensim.SevenSimApplication;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Named;
import javax.inject.Singleton;

import com.github.iusmac.sevensim.SysProp;

/** Application level module. */
@InstallIn(SingletonComponent.class)
@Module
public final class SevenSimModule {
    @Singleton
    @Provides
    static AppDatabaseDE provideAppDatabaseDE(final @ApplicationContext Context context,
            final RoomTypeConverters typeConverter) {

        final RoomDatabase.Builder<AppDatabaseDE> builder =
            Room.databaseBuilder(context.createDeviceProtectedStorageContext(),
                    AppDatabaseDE.class, "app_database.sqlite");

        return builder.addTypeConverter(typeConverter).build();
    }

    @Named("Debug")
    @Singleton
    @Provides
    static boolean provideDebugState() {
        return new SysProp("debug", /*isPersistent=*/ false).isTrue() ||
            new SysProp("debug", /*isPersistent=*/ true).isTrue();
    }

    @Provides
    static SevenSimApplication provideApplicationInstance(
            final @ApplicationContext Context context) {

        return (SevenSimApplication) context;
    }

    @Singleton
    @Provides
    static SharedPreferences provideSharedPreferences(final @ApplicationContext Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Singleton
    @Provides
    static NotificationManagerCompat provideNotificationManagerCompat(
            final @ApplicationContext Context context) {

        return NotificationManagerCompat.from(context);
    }

    @Singleton
    @Provides
    static AlarmManager provideAlarmManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, AlarmManager.class);
    }

    @Singleton
    @Provides
    static ActivityManager provideActivityManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, ActivityManager.class);
    }

    @Singleton
    @Provides
    static TelecomManager provideTelecomManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, TelecomManager.class);
    }

    @Singleton
    @Provides
    static AudioManager provideAudioManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, AudioManager.class);
    }

    /** Do not initialize. */
    private SevenSimModule() {}
}
