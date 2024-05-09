package com.github.iusmac.sevensim.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt.PromptInfo;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.github.iusmac.sevensim.Utils;
import com.github.iusmac.sevensim.telephony.PinStorage;
import com.github.iusmac.sevensim.Logger;

import dagger.Lazy;
import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

/**
 * <p>This activity hosts a biometric prompt that allows the user to authenticate with their
 * credentials needed to unlock the hardware-backed KeyStore for further crypto operations.
 *
 * <p>Note, the prompt will use any secure screen lock method (password/PIN/pattern or biometric) on
 * versions prior to Android 11 (API 30). On newer versions only user credentials
 * (password/PIN/pattern) are used as secure screen lock method.
 */
@AndroidEntryPoint(FragmentActivity.class)
public final class AuthenticationPromptActivity extends Hilt_AuthenticationPromptActivity {
    private final ActivityResultLauncher<Intent> mVerifyAuthLock = Utils.IS_AT_LEAST_R ? null :
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) ->
                onAuthResult(result != null ? result.getResultCode() : Activity.RESULT_CANCELED));

    @Inject
    Logger.Factory mLoggerFactory;

    @Inject
    Lazy<BiometricManager> mBiometricManagerLazy;

    @Inject
    Lazy<KeyguardManager> mKeyguardManagerLazy;

    private Logger mLogger;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogger = mLoggerFactory.create(getClass().getSimpleName());

        mLogger.d("onCreate().");

        if (savedInstanceState == null) {
            if (Utils.IS_AT_LEAST_R) {
                launchBiometricPrompt();
            } else {
                final Intent credentialIntent = ApiDeprecated.createConfirmDeviceCredentialIntent(
                        mKeyguardManagerLazy.get(), /*title=*/ null, /*description=*/ null);
                if (credentialIntent == null) {
                    mLogger.e("onCreate() : Confirm device credential intent is null.");
                    onAuthResult(Activity.RESULT_CANCELED);
                    return;
                }
                mVerifyAuthLock.launch(credentialIntent);
            }
        }
    }

    private void onAuthResult(final int resultCode) {
        mLogger.d("onAuthResult(resultCode=%s).", ActivityResult.resultCodeToString(resultCode));

        if (resultCode == Activity.RESULT_OK) {
            // For reliability, we add a tolerance of 1.5s to avoid any time discrepancies when
            // calculating authentication validity the next time
            final int toleranceSec = 1_500;
            PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime() - toleranceSec);
        }

        final Intent propagateIntent = getIntent() != null ? getIntent() : new Intent();
        setResult(resultCode, propagateIntent);
        finish();
    }

    private void launchBiometricPrompt() {
        final BiometricPrompt.AuthenticationCallback authenticationCallback =
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(
                        final BiometricPrompt.AuthenticationResult result) {

                    mLogger.d("onAuthenticationSucceeded().");

                    onAuthResult(Activity.RESULT_OK);
                }

                @Override
                public void onAuthenticationError(final int errCode, final CharSequence errStr) {
                    mLogger.d("onAuthenticationError(errCode=%d,errStr=%s).", errCode, errStr);

                    onAuthResult(Activity.RESULT_CANCELED);
                }
            };
        new BiometricPrompt(this, authenticationCallback).authenticate(buildBiometricPromptInfo());
    }

    /**
     * Build a {@link PromptInfo} to be used via {@link BiometricPrompt#authenticate(PromptInfo)}.
     */
    private PromptInfo buildBiometricPromptInfo() {
        final BiometricManager.Strings strings = mBiometricManagerLazy.get()
            .getStrings(BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        final PromptInfo.Builder promptBuilder = new PromptInfo.Builder();
        promptBuilder.setTitle(strings.getSettingName()).setSubtitle(strings.getPromptMessage());
        promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        return promptBuilder.build();
    }

    /** Nested class to suppress warnings only for API methods annotated as Deprecated. */
    @SuppressWarnings({"deprecation"})
    private static class ApiDeprecated {
        static Intent createConfirmDeviceCredentialIntent(final KeyguardManager km,
                final CharSequence title, final CharSequence description) {

            return km.createConfirmDeviceCredentialIntent(title, description);
        }
    }
}
