package com.github.iusmac.sevensim.test;

import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.telephony.ITelephony;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(ITelephony.Stub.class)
public class ShadowITelephony {
    @GuardedBy("ShadowITelephony.class")
    private static ITelephonyImpl sITelephonyImpl;

    private int[] mPinReportResult = new int[0];

    /**
     * Sets the {@link PinResult.PinResultType} and the number of PIN attempts remaining to be
     * returned by {@link ITelephony#supplyPinReportResultForSubscriber}.
     */
    public void setSupplyPinReportResultForSubscriber(int[] pinReportResult) {
        mPinReportResult = pinReportResult;
    }

    @Implementation
    protected static @Nullable ITelephony asInterface(@Nullable IBinder obj) {
        if (obj == null) {
            return null;
        }
        synchronized (ShadowITelephony.class) {
            // Since ServiceManager always returns a singleton of the IBinder instance, we need to
            // ensure that we bound it to the same ShadowITelephony and ITelephony implementation
            if (sITelephonyImpl == null || sITelephonyImpl.asBinder() != obj) {
                final var shadow = new ShadowITelephony();
                sITelephonyImpl = shadow.new ITelephonyImpl(obj, shadow);
            }
            return sITelephonyImpl;
        }
    }

    /**
     * Returns a {@link ShadowITelephony} object associated with the provided {@link ITelephony}
     * instance.
     *
     * @throws IllegalArgumentException When the {@link ITelephony#asBinder} returns {@code null}.
     * @throws IllegalStateException When the provided {@link ITelephony} is an inappropriate
     * object, i.e., it was not created through {@link ITelephony.Stub#asInterface} API where the
     * association happens, or there's a newer association, so the provided {@link ITelephony} is
     * effectively obsolete.
     */
    @NonNull
    public static synchronized ShadowITelephony asShadowInterface(@NonNull ITelephony itelephony) {
        if (itelephony.asBinder() != null) {
            if (sITelephonyImpl == null || itelephony.asBinder() != sITelephonyImpl.asBinder()) {
                throw new IllegalStateException(
                        "Cannot retrieve a Shadow object for the provided ITelephony instance.");
            }
            return sITelephonyImpl.asShadowInterface();
        }
        throw new IllegalArgumentException(
                "ITelephony.asBinder() returns null. Ensure to use ITelephony.Stub.asInterface().");
    }

    private class ITelephonyImpl extends ITelephony.Default {
        final IBinder mIBinder;
        final ShadowITelephony mShadow;

        ITelephonyImpl(final IBinder binder, final ShadowITelephony shadow) {
            mIBinder = binder;
            mShadow = shadow;
        }

        @Override
        public int[] supplyPinReportResultForSubscriber(int subId, String pin)
                throws RemoteException {

            return mPinReportResult;
        }

        @Override
        public IBinder asBinder() {
            return mIBinder;
        }

        private ShadowITelephony asShadowInterface() {
            return mShadow;
        }
    }
}
