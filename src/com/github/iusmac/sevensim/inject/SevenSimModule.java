package com.github.iusmac.sevensim.inject;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.telecom.TelecomManager;

import androidx.biometric.BiometricManager;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.RoomTypeConverters;
import com.github.iusmac.sevensim.SevenSimApplication;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import java.security.KeyStore;

import javax.inject.Named;
import javax.inject.Singleton;

import com.github.iusmac.sevensim.SysProp;

import static com.github.iusmac.sevensim.telephony.PinStorage.ANDROID_KEYSTORE_PROVIDER;

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

    @Singleton
    @Provides
    static AppDatabaseCE provideAppDatabaseCE(final @ApplicationContext Context context) {
        final RoomDatabase.Builder<AppDatabaseCE> builder =
            Room.databaseBuilder(context, AppDatabaseCE.class, "app_database.sqlite");

        return builder.build();
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

    @Singleton
    @Provides
    static BiometricManager provideBiometricManager(final @ApplicationContext Context context) {
        return BiometricManager.from(context);
    }

    @Singleton
    @Provides
    static KeyguardManager provideKeyguardManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, KeyguardManager.class);
    }

    @Singleton
    @Provides
    static KeyStore provideKeyStore() {
        for (int i = 1; i <= 3; i++) {
            try {
                final KeyStore keystore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER);
                keystore.load(/*param=*/ null);
                if (keystore != null) {
                    return keystore;
                }
            } catch (Exception e) {
                android.util.Log.e("7SIM", "Attempt " + i + "/3 failed to open KeyStore.", e);
            }
        }
        throw new RuntimeException("Failed to instantiate Android KeyStore.");
    }

    /** Do not initialize. */
    private SevenSimModule() {}
}
