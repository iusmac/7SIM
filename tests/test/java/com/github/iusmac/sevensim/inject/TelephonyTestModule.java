package com.github.iusmac.sevensim.inject;

import android.app.KeyguardManager;
import android.content.Context;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.ITelephony;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.NotificationManager;
import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.SubscriptionsImpl;
import com.github.iusmac.sevensim.telephony.SubscriptionsImplLegacy;
import com.github.iusmac.sevensim.telephony.TelephonyController;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.testing.TestInstallIn;

import java.security.KeyStore;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import static org.mockito.Mockito.spy;

@TestInstallIn(
    components = {SingletonComponent.class},
    replaces = {TelephonyModule.class}
)
@Module
public final class TelephonyTestModule {
    @Singleton
    @Provides
    static Subscriptions provideSubscriptions(final TelephonyUtils telephonyUtils,
            final Provider<SubscriptionsImpl> subscriptionsImplProvider,
            final Provider<SubscriptionsImplLegacy> subscriptionsImplLegacyProvider) {

        return spy(TelephonyModule.provideSubscriptions(telephonyUtils, subscriptionsImplProvider,
                    subscriptionsImplLegacyProvider));
    }

    @Singleton
    @Provides
    static PinStorage providePinStorage(final Logger.Factory loggerFactory,
            final AppDatabaseCE database, final Lazy<KeyguardManager> keyguardManagerLazy,
            final Lazy<KeyStore> keyStoreLazy, final Lazy<Subscriptions> subscriptionsLazy,
            final Lazy<NotificationManager> notificationManager) {

        return spy(new PinStorage(loggerFactory, database, keyguardManagerLazy, keyStoreLazy,
                    subscriptionsLazy, notificationManager));
    }

    @Singleton
    @Provides
    static TelephonyController provideTelephonyController(final @ApplicationContext Context context,
            final Logger.Factory loggerFactory,
            final TelephonyManager telephonyManager,
            final SubscriptionsImplLegacy subscriptions) {

        return spy(new TelephonyController(context, loggerFactory, telephonyManager,
                    subscriptions));
    }

    @Singleton
    @Provides
    static ITelephony provideITelephony() {
        return spy(TelephonyModule.provideITelephony());
    }

    @Named("Telephony/SimSubId")
    @Singleton
    @Provides
    static SysProp provideSimSubIdSysProp() {
        return TelephonyModule.provideSimSubIdSysProp();
    }

    @Named("Telephony/SimState")
    @Singleton
    @Provides
    static SysProp provideSimStateSysProp() {
        return TelephonyModule.provideSimStateSysProp();
    }

    @Named("Telephony/SimIconTint")
    @Singleton
    @Provides
    static SysProp provideSimIconTintSysProp() {
        return TelephonyModule.provideSimIconTintSysProp();
    }

    @Named("Telephony/SimName")
    @Singleton
    @Provides
    static SysProp provideSimNameSysProp() {
        return TelephonyModule.provideSimNameSysProp();
    }

    @Named("Telephony/SubState")
    @Singleton
    @Provides
    static SysProp provideSubscriptionStateSysProp() {
        return TelephonyModule.provideSubscriptionStateSysProp();
    }

    @Named("Telephony/UsableSubIds")
    @Singleton
    @Provides
    static SysProp provideUsableSubIdSysProp() {
        return TelephonyModule.provideUsableSubIdSysProp();
    }

    @Named("Telephony/UiccSubscriptionToggleCapabilityDisabledSetting")
    @Singleton
    @Provides
    static boolean provideUiccSubscriptionToggleCapabilityDisabledSetting() {
        return TelephonyModule.provideUiccSubscriptionToggleCapabilityDisabledSetting();
    }

    /** Do not initialize. */
    private TelephonyTestModule() {}
}
