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
import androidx.room.RoomDatabase;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.AppDatabaseDE;
import com.github.iusmac.sevensim.ApplicationInfo;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.NotificationManager;
import com.github.iusmac.sevensim.RoomTypeConverters;
import com.github.iusmac.sevensim.SevenSimApplication;
import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.SystemTimeProvider;
import com.github.iusmac.sevensim.SystemTimeProviderFakeImpl;
import com.github.iusmac.sevensim.scheduler.SubscriptionScheduler;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.telephony.SubscriptionController;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.TelephonyController;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;
import com.github.iusmac.sevensim.test.AsyncTestTaskExecutor;
import com.github.iusmac.sevensim.test.FakeAndroidKeyStoreProvider;
import com.github.iusmac.sevensim.test.TestUtils;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.testing.TestInstallIn;

import java.security.KeyStore;
import java.security.Security;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.robolectric.util.ReflectionHelpers;

import static org.mockito.Mockito.spy;

import static org.robolectric.Shadows.shadowOf;

@TestInstallIn(
    components = {SingletonComponent.class},
    replaces = {SevenSimModule.class}
)
@Module
public final class SevenSimTestModule {
    private static final AtomicReferenceArray<RoomDatabase> DATABASES =
        new AtomicReferenceArray<>(2);

    @Singleton
    @Provides
    static AppDatabaseDE provideAppDatabaseDE(final @ApplicationContext Context context,
            final RoomTypeConverters typeConverter) {

        final var builder = Room.inMemoryDatabaseBuilder(context
                .createDeviceProtectedStorageContext(), AppDatabaseDE.class);

        builder.addMigrations(AppDatabaseDE.MIGRATION_1_2);
        builder.addTypeConverter(typeConverter);
        builder.setQueryExecutor(AsyncTestTaskExecutor.INSTANCE);

        return (AppDatabaseDE) DATABASES.updateAndGet(0, (oldDB) -> builder.build());
    }

    @Singleton
    @Provides
    static AppDatabaseCE provideAppDatabaseCE(final @ApplicationContext Context context) {
        final var builder = Room.inMemoryDatabaseBuilder(context, AppDatabaseCE.class);

        builder.setQueryExecutor(AsyncTestTaskExecutor.INSTANCE);

        return (AppDatabaseCE) DATABASES.updateAndGet(1, (oldDB) -> builder.build());
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

    @Singleton
    @Provides
    static SevenSimApplication provideApplicationInstance(final @ApplicationContext Context context,
            final Provider<ApplicationInfo> applicationInfoProvider,
            final Logger.Factory loggerFactory,
            final Provider<NotificationManager> notificationManagerProvider) {

        // Note that, since a dagger.hilt.android.HiltAndroidApp annotated class cannot be used in
        // tests, thus it cannot be simply cast from Application class, we'll manually create it and
        // inject all the required dependencies
        final var app = new SevenSimApplication();
        ReflectionHelpers.setField(app, "mApplicationInfoProvider", applicationInfoProvider);
        ReflectionHelpers.setField(app, "mNotificationManager", notificationManagerProvider);
        ReflectionHelpers.setField(app, "mLoggerFactory", loggerFactory);

        // Certificates are undefined in unit tests by default, so initialize them to avoid NPEs
        if (shadowOf(context.getPackageManager()).getInternalMutablePackageInfo(
                    context.getPackageName()).signingInfo == null) {
            TestUtils.useAospPlatformSignature(context, false);
        }

        // Flip the internal flag to short-circuit the hiltInternalInject() call during onCreate(),
        // otherwise Dagger will override our injected objects with invalid ones that don't have
        // access to the Application's context and will crash with an NPE upon accessing it
        ReflectionHelpers.setField(app, "injected", true);

        app.onCreate();

        return app;
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
        final var fakeProvider = new FakeAndroidKeyStoreProvider();
        // The previous provider *must* be absolutely removed before adding a new one since the
        // provider manager checks (by name) if it has already been installed, otherwise the same
        // instance will be used for the whole JVM session, and cause sneaky behaviors after
        // changing SDK version, such as "android.some.clazz is in unnamed module of loader
        // org.robolectric.internal.AndroidSandbox$SdkSandboxClassLoader"
        Security.removeProvider(fakeProvider.getName());
        Security.addProvider(fakeProvider);
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

    @Singleton
    @Provides
    static SubscriptionScheduler provideSubscriptionScheduler(final Logger.Factory loggerFactory,
            final @ApplicationContext Context context, final Lazy<AlarmManager> alarmManagerLazy,
            final AppDatabaseDE appDatabaseDE,
            final Lazy<Subscriptions> subscriptionsLazy,
            final Lazy<SubscriptionController> subscriptionControllerLazy,
            final Lazy<TelephonyController> telephonyControllerLazy,
            final Provider<TelephonyUtils> telephonyUtilsProvider,
            final Lazy<PinStorage> pinStorageLazy,
            final Lazy<UserManager> userManagerLazy) {

        return spy(new SubscriptionScheduler(loggerFactory, context, alarmManagerLazy,
                    appDatabaseDE, subscriptionsLazy, subscriptionControllerLazy,
                    telephonyControllerLazy, telephonyUtilsProvider, pinStorageLazy,
                    userManagerLazy));
    }

    @Singleton
    @Provides
    static SystemTimeProvider provideSystemTimeProvider() {
        return new SystemTimeProviderFakeImpl();
    }

    /**
     * Call this to close all opened {@link RoomDatabase}s during the tests.
     * <p>
     * It could be helpful to avoid warnings by {@link dalvik.system.CloseGuard} in testing infra.
     * You need to be sure that the used database finished all operations and won't be accessed
     * anymore.
     *
     * @see https://android-review.googlesource.com/c/platform/frameworks/support/+/2625941
     */
    public static void closeAllDatabases() {
        for (int i = 0, size = DATABASES.length(); i < size; i++) {
            final var db = DATABASES.get(i);
            if (db != null) {
                if (db.isOpen()) {
                    db.close();
                }
                DATABASES.set(i, null);
            }
        }
    }

    /** Do not initialize. */
    private SevenSimTestModule() {}
}
