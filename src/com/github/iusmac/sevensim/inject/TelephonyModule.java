package com.github.iusmac.sevensim.inject;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.telephony.SimState;
import com.github.iusmac.sevensim.telephony.Subscriptions;
import com.github.iusmac.sevensim.telephony.SubscriptionsImpl;
import com.github.iusmac.sevensim.telephony.SubscriptionsImplLegacy;
import com.github.iusmac.sevensim.telephony.TelephonyUtils;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * This module provides telephony-related dependencies.
 */
@InstallIn(SingletonComponent.class)
@Module
public final class TelephonyModule {
    @Singleton
    @Provides
    static TelephonyManager provideTelephonyManager(final @ApplicationContext Context context) {
        return ContextCompat.getSystemService(context, TelephonyManager.class);
    }

    @Singleton
    @Provides
    static SubscriptionManager provideSubscriptionManager(
            final @ApplicationContext Context context) {

        return ContextCompat.getSystemService(context, SubscriptionManager.class);
    }

    @Singleton
    @Provides
    static Subscriptions provideSubscriptions(final TelephonyUtils telephonyUtils,
            final Provider<SubscriptionsImpl> subscriptionsImplProvider,
            final Provider<SubscriptionsImplLegacy> subscriptionsImplLegacyProvider) {

        return (telephonyUtils.canDisableUiccSubscription() ? subscriptionsImplProvider :
                subscriptionsImplLegacyProvider).get();
    }

    /**
     * The system property that maintains the subscription ID inserted in a SIM card.
     */
    @Named("Telephony/SimSubId")
    @Singleton
    @Provides
    static SysProp provideSimSubIdSysProp() {
        return new SysProp("sim%d.sub_id", /*isPersistent=*/ false);
    }

    /**
     * The system property that maintains the state of a SIM card.
     *
     * @see SimState
     */
    @Named("Telephony/SimState")
    @Singleton
    @Provides
    static SysProp provideSimStateSysProp() {
        return new SysProp("sim%d.state", /*isPersistent=*/ false);
    }

    /**
     * The system property that maintains the icon tint of a SIM card.
     */
    @Named("Telephony/SimIconTint")
    @Singleton
    @Provides
    static SysProp provideSimIconTintSysProp() {
        return new SysProp("sim%d.tint", /*isPersistent=*/ false);
    }

    /**
     * The system property that maintains the name of a SIM card.
     */
    @Named("Telephony/SimName")
    @Singleton
    @Provides
    static SysProp provideSimNameSysProp() {
        return new SysProp("sim%d.name", /*isPersistent=*/ false);
    }

    /**
     * The system property that maintains the state of a SIM subscription.
     *
     * @see SimState
     */
    @Named("Telephony/SubState")
    @Singleton
    @Provides
    static SysProp provideSubscriptionStateSysProp() {
        return new SysProp("sub%d.state", /*isPersistent=*/ false);
    }

    /**
     * The system property that maintains a comma-separated list of usable SIM subscription IDs.
     */
    @Named("Telephony/UsableSubIds")
    @Singleton
    @Provides
    static SysProp provideUsableSubIdSysProp() {
        return new SysProp("usable_sub_ids", /*isPersistent*/ false);
    }

    /**
     * <p>The system property that maintains a Boolean flag, indicating whether the application
     * should override device's capability to disable / re-enable a subscription on a physical
     * (non-eUICC) SIM (pSIM), even if availability is configured by the platform.
     *
     * <p>Modules based on this flag will instead opt for slot power control as a fallback.
     *
     * <p>This is used for debugging purpose only.
     */
    @Named("Telephony/UiccSubscriptionToggleCapabilityDisabledSetting")
    @Singleton
    @Provides
    static boolean provideUiccSubscriptionToggleCapabilityDisabledSetting() {
        final String prop = "debug.uicc_sub_toggle_disabled";
        return new SysProp(prop, /*isPersistent=*/ false).isTrue() ||
            new SysProp(prop, /*isPersistent=*/ true).isTrue();
    }

    /** Do not initialize. */
    private TelephonyModule() {}
}
