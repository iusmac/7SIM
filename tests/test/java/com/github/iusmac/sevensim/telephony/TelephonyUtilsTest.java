package com.github.iusmac.sevensim.telephony;

import android.media.AudioManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.github.iusmac.sevensim.SysProp;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowSubscriptionManagerHiddenApi;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;

import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.R;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class TelephonyUtilsTest extends MockitoHiltAndroidTestBase {
    @Inject
    Provider<TelephonyUtils> mTelephonyUtilsProvider;

    @Inject
    Provider<SubscriptionManager> mSubscriptionManagerProvider;

    @Inject
    Provider<TelephonyManager> mTelephonyManagerProvider;

    @Inject
    Provider<TelecomManager> mTelecomManagerProvider;

    @Inject
    Provider<AudioManager> mAudioManagerProvider;

    @Test
    @Config(minSdk = Q)
    public void test_canDisableUiccSubscription_HasNoPlatformSupport() {
        assertFalse(mTelephonyUtilsProvider.get().canDisableUiccSubscription());
    }

    @Test
    @Config(minSdk = R, shadows = { ShadowSubscriptionManagerHiddenApi.class })
    public void test_canDisableUiccSubscription_HasPlatformSupport() {
        Shadow.<ShadowSubscriptionManagerHiddenApi>extract(mSubscriptionManagerProvider.get())
            .setCanDisablePhysicalSubscription(true);

        assertTrue(mTelephonyUtilsProvider.get().canDisableUiccSubscription());
    }

    @Test
    @Config(minSdk = R, shadows = { ShadowSubscriptionManagerHiddenApi.class })
    public void test_canDisableUiccSubscription_UiccSubscriptionToggleCapabilityDisabledViaSysProp() {
        new SysProp("debug.uicc_sub_toggle_disabled",
                /*isPersistent=*/ false).set(Optional.of("true"));

        Shadow.<ShadowSubscriptionManagerHiddenApi>extract(mSubscriptionManagerProvider.get())
            .setCanDisablePhysicalSubscription(true);

        assertFalse(mTelephonyUtilsProvider.get().canDisableUiccSubscription());
    }

    @Test
    @Config(minSdk = R)
    public void test_getActiveSlotCount_SinceR() {
        final var expectedActiveSlotCount = 2;
        shadowOf(mTelephonyManagerProvider.get()).setActiveModemCount(expectedActiveSlotCount);
        assertThat(mTelephonyUtilsProvider.get().getActiveSlotCount(), is(expectedActiveSlotCount));
    }

    @Test
    @Config(sdk = Q)
    public void test_getActiveSlotCount_BeforeR() {
        final var expectedActiveSlotCount = 2;
        shadowOf(mTelephonyManagerProvider.get()).setPhoneCount(expectedActiveSlotCount);
        assertThat(mTelephonyUtilsProvider.get().getActiveSlotCount(), is(expectedActiveSlotCount));
    }

    @Test
    public void test_isInCall_NotIsInCallNorHasVoiceCall() {
        assertFalse(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_isInCall_IsInCall_TelecomApi() {
        shadowOf(mTelecomManagerProvider.get()).setIsInCall(true);
        assertTrue(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_isInCall_IsInCall_TelecomApi_SecurityException() {
        final var telecomManager = mTelecomManagerProvider.get();
        shadowOf(telecomManager).setIsInCall(true);
        when(telecomManager.isInCall()).thenThrow(SecurityException.class);
        assertFalse(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_isInCall_AudioModeInCall() {
        mAudioManagerProvider.get().setMode(AudioManager.MODE_IN_CALL);
        assertTrue(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_isInCall_AudioModeInCommunication() {
        mAudioManagerProvider.get().setMode(AudioManager.MODE_IN_COMMUNICATION);
        assertTrue(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_isInCall_AudioModeInCall_RemoteException() {
        final var audioManager = mAudioManagerProvider.get();
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        doAnswer((invocation) -> {
            invocation.callRealMethod();
            throw new RemoteException();
        }).when(audioManager).getMode();
        assertFalse(mTelephonyUtilsProvider.get().isInCall());
    }

    @Test
    public void test_simStateInt() {
        assertThat(TelephonyUtils.simStateInt(true), is(SimState.ENABLED));
        assertThat(TelephonyUtils.simStateInt(false), is(SimState.DISABLED));
    }

    @Test
    public void test_simStateToString() {
        assertThat(TelephonyUtils.simStateToString(SimState.UNKNOWN), is("UNKNOWN"));
        assertThat(TelephonyUtils.simStateToString(SimState.ENABLED), is("ENABLED"));
        assertThat(TelephonyUtils.simStateToString(SimState.DISABLED), is("DISABLED"));
        assertThat(TelephonyUtils.simStateToString(-1), is("UNKNOWN(-1)"));
    }

    @Test
    public void test_isAirplaneModeOn() {
        assertFalse(TelephonyUtils.isAirplaneModeOn(mApplicationContext));

        Settings.Global.putInt(mApplicationContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 1);
        assertTrue(TelephonyUtils.isAirplaneModeOn(mApplicationContext));
    }

    @Test
    public void test_isValidPin() {
        assertFalse(TelephonyUtils.isValidPin(null));
        assertFalse(TelephonyUtils.isValidPin(""));
        assertFalse(TelephonyUtils.isValidPin("123"));
        assertFalse(TelephonyUtils.isValidPin("123456789"));
        assertTrue(TelephonyUtils.isValidPin("1234"));
        assertTrue(TelephonyUtils.isValidPin("12345"));
        assertTrue(TelephonyUtils.isValidPin("1234567"));
        assertTrue(TelephonyUtils.isValidPin("12345678"));
    }
}
