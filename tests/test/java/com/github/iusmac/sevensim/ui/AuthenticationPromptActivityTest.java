package com.github.iusmac.sevensim.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.IAuthService;
import android.os.Build;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;

import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;
import com.github.iusmac.sevensim.test.ShadowIAuthService;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.time.Duration;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

import static com.github.iusmac.sevensim.test.ActivityResultMatcher.isCanceled;
import static com.github.iusmac.sevensim.test.ActivityResultMatcher.isOk;
import static com.github.iusmac.sevensim.test.SameBundleMatcher.sameBundle;

import static org.hamcrest.MatcherAssert.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
    ShadowIAuthService.class,
})
public class AuthenticationPromptActivityTest extends MockitoHiltAndroidTestBase {
    private static final Duration DEFAULT_AUTHENTICATION_VALIDITY_DURATION = Duration.ofMinutes(5);

    @Inject
    KeyguardManager mKeyguardManager;

    @Inject
    PinStorage mPinStorage;

    private Intent mIntent;

    @Override
    public void setUp() {
        super.setUp();

        mIntent = new Intent(mApplicationContext, AuthenticationPromptActivity.class);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldReturnResultCanceledWhenConfirmDeviceCredentialIntentIsNull_Q() {
        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            assertThat(scenario.getResult(), isCanceled());
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldReturnResultOkWhenAuthenticationSucceeded_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendSuccessfulConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isOk());
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldReturnResultCanceledWhenAuthenticationWithErrors_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendCanceledConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isCanceled());
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldSurviveAfterActivityRecreation_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario
                .onActivity((activity) -> assertFalse(activity.isFinishing()))
                .recreate()
                .onActivity((activity) -> assertFalse(activity.isFinishing()))
                .onActivity(this::sendSuccessfulConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isOk());
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldUpdateLastAuthenticationTimeWhenAuthenticationSucceeded_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendSuccessfulConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isOk());
        }
        assertFalse(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldNotUpdateLastAuthenticationTimeWhenAuthenticationWithErrors_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendCanceledConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isCanceled());
        }
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldAdjustTimeToleranceWhenUpdatingLastAuthenticationTime_Q() {
        final var beforePromptStartMillis = SystemClock.uptimeMillis();

        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendSuccessfulConfirmDeviceCredentialIntent);
            assertThat(scenario.getResult(), isOk());
        }
        assertFalse(mPinStorage.isAuthenticationRequired());

        // Simulate user authentication validity that is about to expire
        ShadowSystemClock.advanceBy(DEFAULT_AUTHENTICATION_VALIDITY_DURATION.minusMillis(1)
                // Include the time passed in auth prompt activity for exact arithmetic operation
                .minusMillis(SystemClock.uptimeMillis() - beforePromptStartMillis));
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldReturnSameIntentPayloadWhenAuthenticationSucceeded_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        mIntent.putExtra("setting", false);
        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendSuccessfulConfirmDeviceCredentialIntent);
            final var result = scenario.getResult();
            assertThat(result, isOk());
            assertThat(result.getResultData().getExtras(), sameBundle(mIntent.getExtras()));
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_ShouldReturnSameIntentPayloadWhenAuthenticationWithErrors_Q() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        mIntent.putExtra("setting", false);
        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario.onActivity(this::sendCanceledConfirmDeviceCredentialIntent);
            final var result = scenario.getResult();
            assertThat(result, isCanceled());
            assertThat(result.getResultData().getExtras(), sameBundle(mIntent.getExtras()));
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldReturnResultOkWhenAuthenticationSucceeded_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationSucceeded();
            assertThat(scenario.getResult(), isOk());
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldReturnResultCanceledWhenAuthenticationWithErrors_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationCanceled();
            assertThat(scenario.getResult(), isCanceled());
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldSurviveAfterActivityRecreation_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            scenario
                .onActivity((activity) -> assertFalse(activity.isFinishing()))
                .recreate()
                .onActivity((activity) -> assertFalse(activity.isFinishing()));
            sendCredentialAuthenticationSucceeded();
            assertThat(scenario.getResult(), isOk());
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldUpdateLastAuthenticationTimeWhenAuthenticationSucceeded_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationSucceeded();
            assertThat(scenario.getResult(), isOk());
        }
        assertFalse(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldNotUpdateLastAuthenticationTimeWhenAuthenticationWithErrors_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationCanceled();
            assertThat(scenario.getResult(), isCanceled());
        }
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldAdjustTimeToleranceWhenUpdatingLastAuthenticationTime_SinceR() {
        final var beforePromptStartMillis = SystemClock.uptimeMillis();

        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        PinStorage.setLastKeystoreAuthTimestamp(0);
        assertTrue(mPinStorage.isAuthenticationRequired());

        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationSucceeded();
            assertThat(scenario.getResult(), isOk());
        }
        assertFalse(mPinStorage.isAuthenticationRequired());

        // Simulate user authentication validity that is about to expire
        ShadowSystemClock.advanceBy(DEFAULT_AUTHENTICATION_VALIDITY_DURATION.minusMillis(1)
                // Include the time passed in auth prompt activity for exact arithmetic operation
                .minusMillis(SystemClock.uptimeMillis() - beforePromptStartMillis));
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldReturnSameIntentPayloadWhenAuthenticationSucceeded_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        mIntent.putExtra("setting", false);
        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationSucceeded();
            final var result = scenario.getResult();
            assertThat(result, isOk());
            assertThat(result.getResultData().getExtras(), sameBundle(mIntent.getExtras()));
        }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_ShouldReturnSameIntentPayloadWhenAuthenticationWithErrors_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        mIntent.putExtra("setting", false);
        try (final var scenario = ActivityScenario.launchActivityForResult(mIntent)) {
            sendCredentialAuthenticationCanceled();
            final var result = scenario.getResult();
            assertThat(result, isCanceled());
            assertThat(result.getResultData().getExtras(), sameBundle(mIntent.getExtras()));
        }
    }

    private void sendSuccessfulConfirmDeviceCredentialIntent(final Activity activity) {
        sendConfirmDeviceCredentialIntent(activity, Activity.RESULT_OK);
    }

    private void sendCanceledConfirmDeviceCredentialIntent(final Activity activity) {
        sendConfirmDeviceCredentialIntent(activity, Activity.RESULT_CANCELED);
    }

    private void sendConfirmDeviceCredentialIntent(final Activity activity, final int resultCode) {
        @SuppressWarnings("deprecation")
        final var requestCredentialIntent = mKeyguardManager
            .createConfirmDeviceCredentialIntent(/*title=*/ null, /*description=*/ null);

        shadowOf(activity).receiveResult(requestCredentialIntent, resultCode, null /* no data */);
    }

    private void sendCredentialAuthenticationSucceeded() {
        try {
            getShadowIAuthService().sendCredentialAuthenticationSucceeded();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void sendCredentialAuthenticationCanceled() {
        try {
            getShadowIAuthService().sendCredentialAuthenticationCanceled();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private ShadowIAuthService getShadowIAuthService() {
        final var binder = ServiceManager.getService(Context.AUTH_SERVICE);
        return ShadowIAuthService.asShadowInterface(IAuthService.Stub.asInterface(binder));
    }
}
