package com.github.iusmac.sevensim.inject;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.UserManager;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.biometric.BiometricManager;
import androidx.core.app.NotificationManagerCompat;
import androidx.room.Room;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.RoomTypeConverters;
import com.github.iusmac.sevensim.SevenSimApplication;
import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.test.FakeAndroidKeyStoreProvider;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.testing.TestInstallIn;

import java.security.KeyStore;
import java.security.Security;
import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import static org.mockito.Mockito.spy;

@TestInstallIn(
    components = {SingletonComponent.class},
    replaces = {SevenSimModule.class}
)
@Module
public final class SevenSimTestModule {
    @Singleton
    @Provides
    static AppDatabaseDE provideAppDatabaseDE(final @ApplicationContext Context context,
            final RoomTypeConverters typeConverter) {

        final var builder = Room.inMemoryDatabaseBuilder(context
                .createDeviceProtectedStorageContext(), AppDatabaseDE.class);

        builder.addMigrations(AppDatabaseDE.MIGRATION_1_2);

        return builder.addTypeConverter(typeConverter).build();
    }

    @Singleton
    @Provides
    static AppDatabaseCE provideAppDatabaseCE(final @ApplicationContext Context context) {
        final var builder = Room.inMemoryDatabaseBuilder(context, AppDatabaseCE.class);

        return builder.build();
    }

    @Named("Debug")
    @Provides
    static boolean provideDebugState() {
        final var debug = new SysProp("debug", /*isPersistent=*/ false);
        final var debugPersistent = new SysProp("debug", /*isPersistent=*/ true);
        if (debug.get(Optional.empty()).isPresent() ||
                debugPersistent.get(Optional.empty()).isPresent()) {
            return debug.isTrue() || debugPersistent.isTrue();
        }
        return true;
    }

    @Provides
    static SevenSimApplication provideApplicationInstance(
            final @ApplicationContext Context context) {

        return SevenSimModule.provideApplicationInstance(context);
    }

    @Singleton
    @Provides
    static SharedPreferences provideSharedPreferences(final @ApplicationContext Context context) {
        return SevenSimModule.provideSharedPreferences(context);
    }

    @Singleton
    @Provides
    static NotificationManagerCompat provideNotificationManagerCompat(
            final @ApplicationContext Context context) {

        return spy(SevenSimModule.provideNotificationManagerCompat(context));
    }

    @Singleton
    @Provides
    static AlarmManager provideAlarmManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideAlarmManager(context));
    }

    @Singleton
    @Provides
    static ActivityManager provideActivityManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideActivityManager(context));
    }

    @Singleton
    @Provides
    static TelecomManager provideTelecomManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideTelecomManager(context));
    }

    @Singleton
    @Provides
    static AudioManager provideAudioManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideAudioManager(context));
    }

    @Singleton
    @Provides
    static BiometricManager provideBiometricManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideBiometricManager(context));
    }

    @Singleton
    @Provides
    static KeyguardManager provideKeyguardManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideKeyguardManager(context));
    }

    @Singleton
    @Provides
    static KeyStore provideKeyStore() {
        Security.addProvider(new FakeAndroidKeyStoreProvider());
        return spy(SevenSimModule.provideKeyStore());
    }

    @Singleton
    @Provides
    static UserManager provideUserManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideUserManager(context));
    }

    @Singleton
    @Provides
    static DevicePolicyManager provideDevicePolicyManager(
            final @ApplicationContext Context context) {

        return spy(SevenSimModule.provideDevicePolicyManager(context));
    }

    @Singleton
    @Provides
    static TelephonyManager provideTelephonyManager(final @ApplicationContext Context context) {
        return spy(SevenSimModule.provideTelephonyManager(context));
    }

    @Singleton
    @Provides
    static SubscriptionManager provideSubscriptionManager(
            final @ApplicationContext Context context) {

        return spy(SevenSimModule.provideSubscriptionManager(context));
    }

    @Named("LockedBootCompleted")
    @Singleton
    @Provides
    static SysProp provideLockedBootCompletedSysProp() {
        return SevenSimModule.provideLockedBootCompletedSysProp();
    }

    @Singleton
    @Provides
    static Runtime provideJavaRuntime() {
        return spy(SevenSimModule.provideJavaRuntime());
    }

    /** Do not initialize. */
    private SevenSimTestModule() {}
}
