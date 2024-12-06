package com.github.iusmac.sevensim.test;

import android.hardware.biometrics.IAuthService;
import android.hardware.biometrics.IBiometricServiceReceiver;
import android.hardware.biometrics.PromptInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import static android.hardware.biometrics.BiometricAuthenticator.TYPE_CREDENTIAL;
import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_ERROR_CANCELED;
import static android.hardware.biometrics.BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.S_V2;

@Implements(value = IAuthService.Stub.class, minSdk = R)
public class ShadowIAuthService {
    @GuardedBy("ShadowIAuthService.class")
    private static IAuthServiceCommon sIAuthServiceImpl;

    private IBiometricServiceReceiver mIBiometricServiceReceiver;

    /**
     * Notify the {@link android.hardware.biometrics.BiometricPrompt} that authentication with type
     * {@link android.hardware.biometrics.BiometricManager.Authenticators#DEVICE_CREDENTIAL} made
     * via {@link android.hardware.biometrics.BiometricPrompt#authenticate} request was successful.
     * <p>
     * When the above request is performed via {@link androidx.biometric.BiometricPrompt} API, then
     * expect the result to be propagated to
     * {@link androidx.biometric.BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded}.
     */
    public void sendCredentialAuthenticationSucceeded() throws RemoteException {
        mIBiometricServiceReceiver
            .onAuthenticationSucceeded(AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL);
    }

    /**
     * Notify the {@link android.hardware.biometrics.BiometricPrompt} that authentication with type
     * {@link android.hardware.biometrics.BiometricManager.Authenticators#DEVICE_CREDENTIAL} made
     * via {@link android.hardware.biometrics.BiometricPrompt#authenticate} request was canceled.
     * <p>
     * When the above request is performed via {@link androidx.biometric.BiometricPrompt} API, then
     * expect the result to be propagated to
     * {@link androidx.biometric.BiometricPrompt.AuthenticationCallback#onAuthenticationError}.
     */
    public void sendCredentialAuthenticationCanceled() throws RemoteException {
        mIBiometricServiceReceiver.onError(TYPE_CREDENTIAL, BIOMETRIC_ERROR_CANCELED,
                /*vendorCode=*/ 0);
    }

    @Implementation
    protected static @Nullable IAuthService asInterface(@Nullable IBinder obj) {
        if (obj == null) {
            return null;
        }
        synchronized (ShadowIAuthService.class) {
            // Since ServiceManager always returns a singleton of the IBinder instance, we need to
            // ensure that we bound it to the same ShadowIAuthService and IAuthService
            // implementation
            if (sIAuthServiceImpl == null || sIAuthServiceImpl.asBinder() != obj) {
                final var shadow = new ShadowIAuthService();
                sIAuthServiceImpl = switch (RuntimeEnvironment.getApiLevel()) {
                    case R -> shadow.new IAuthServiceApi30Impl(obj, shadow);
                    case S -> shadow.new IAuthServiceApi31Impl(obj, shadow);
                    default -> shadow.new IAuthServiceApi32Impl(obj, shadow);
                };
            }
            if (RuntimeEnvironment.getApiLevel() >= S_V2) {
                return (IAuthService) sIAuthServiceImpl;
            } else {
                return ReflectionHelpers.createDelegatingProxy(IAuthService.class,
                        sIAuthServiceImpl);
            }
        }
    }

    /**
     * Returns a {@link ShadowIAuthService} object associated with the provided {@link IAuthService}
     * instance.
     *
     * @throws IllegalArgumentException When the {@link IAuthService#asBinder} returns {@code null}.
     * @throws IllegalStateException When the provided {@link IAuthService} is an inappropriate
     * object, i.e., it was not created through {@link IAuthService.Stub#asInterface} API where the
     * association happens, or there's a newer association, so the provided {@link IAuthService} is
     * effectively obsolete.
     */
    @NonNull
    public static synchronized ShadowIAuthService asShadowInterface(
            @NonNull IAuthService iauthService) {

        if (iauthService.asBinder() != null) {
            if (sIAuthServiceImpl == null || iauthService.asBinder() !=
                    sIAuthServiceImpl.asBinder()) {

                throw new IllegalStateException(
                        "Cannot retrieve a Shadow object for the provided IAuthService instance.");
            }
            return sIAuthServiceImpl.asShadowInterface();
        }
        throw new IllegalArgumentException("IAuthService.asBinder() returns null. " +
                "Ensure to use IAuthService.Stub.asInterface().");
    }

    private interface IAuthServiceCommon extends IInterface {
        @SuppressWarnings("unused")
        default CharSequence getSettingName(int userId, String opPackageName, int authenticators)
                throws RemoteException {

            return "Set and not empty setting name";
        }

        @SuppressWarnings("unused")
        default CharSequence getPromptMessage(int userId, String opPackageName, int authenticators)
                throws RemoteException {

            return "Set and not empty prompt massage";
        }

        ShadowIAuthService asShadowInterface();
    }

    private class IAuthServiceApi30Impl implements IAuthServiceCommon {
        final IBinder mIBinder;
        final ShadowIAuthService mShadow;

        IAuthServiceApi30Impl(final IBinder binder, final ShadowIAuthService shadow) {
            mIBinder = binder;
            mShadow = shadow;
        }

        @SuppressWarnings("unused")
        public void authenticate(IBinder token, long sessionId, int userId,
                IBiometricServiceReceiver receiver, String opPackageName, Bundle bundle)
                throws RemoteException {

            mIBiometricServiceReceiver = receiver;
        }

        @Override
        public IBinder asBinder() {
            return mIBinder;
        }

        @Override
        public ShadowIAuthService asShadowInterface() {
            return mShadow;
        }
    }

    private class IAuthServiceApi31Impl implements IAuthServiceCommon {
        final IBinder mIBinder;
        final ShadowIAuthService mShadow;

        IAuthServiceApi31Impl(final IBinder binder, final ShadowIAuthService shadow) {
            mIBinder = binder;
            mShadow = shadow;
        }

        @SuppressWarnings("unused")
        public void authenticate(IBinder token, long sessionId, int userId,
                IBiometricServiceReceiver receiver, String opPackageName, PromptInfo promptInfo)
                throws RemoteException {

            mIBiometricServiceReceiver = receiver;
        }

        @Override
        public IBinder asBinder() {
            return mIBinder;
        }

        @Override
        public ShadowIAuthService asShadowInterface() {
            return mShadow;
        }
    }

    private class IAuthServiceApi32Impl extends IAuthService.Default implements IAuthServiceCommon {
        final IBinder mIBinder;
        final ShadowIAuthService mShadow;

        IAuthServiceApi32Impl(final IBinder binder, final ShadowIAuthService shadow) {
            mIBinder = binder;
            mShadow = shadow;
        }

        @Override
        public CharSequence getSettingName(int userId, String opPackageName, int authenticators)
                throws RemoteException {

            return "Set and not empty setting name";
        }

        @Override
        public CharSequence getPromptMessage(int userId, String opPackageName, int authenticators)
                throws RemoteException {

            return "Set and not empty prompt massage";
        }

        @Override
        public long authenticate(IBinder token, long sessionId, int userId,
                IBiometricServiceReceiver receiver, String opPackageName, PromptInfo promptInfo)
                throws RemoteException {

            mIBiometricServiceReceiver = receiver;
            return 0;
        }

        @Override
        public IBinder asBinder() {
            return mIBinder;
        }

        @Override
        public ShadowIAuthService asShadowInterface() {
            return mShadow;
        }
    }
}
