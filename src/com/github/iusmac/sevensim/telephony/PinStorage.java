package com.github.iusmac.sevensim.telephony;

import android.app.KeyguardManager;
import android.os.SystemClock;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.Logger;

import dagger.Lazy;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

/** This class is responsible for managing the SIM PIN codes stored on the disk. */
@WorkerThread
@Singleton
public final class PinStorage {
    /**
     * The {@link SystemClock#elapsedRealtime()}-based time, when the hardware-backed KeyStore has
     * been unlocked.
     */
    @GuardedBy("this")
    private static long sLastKeystoreAuthTimestamp;

    /** The default duration, in seconds, of the user authentication bound secret key. */
    private static final int DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS = 60 * 5; // 5 minutes

    private final Logger mLogger;
    private final PinStorageDao mPinStorageDao;
    private final Lazy<KeyguardManager> mKeyguardManagerLazy;

    @Inject
    public PinStorage(final Logger.Factory loggerFactory, final AppDatabaseCE database,
            final Lazy<KeyguardManager> keyguardManagerLazy) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mPinStorageDao = database.pinStorageDao();
        mKeyguardManagerLazy = keyguardManagerLazy;
    }

    /**
     * Retrieve the SIM PIN entity for a SIM subscription ID from the storage.
     *
     * @param subId The subscription ID for which to retrieve the SIM PIN entity.
     * @return An Optional containing the PIN entity, if any.
     */
    public Optional<PinEntity> getPin(final int subId) {
        return mPinStorageDao.findBySubscriptionId(subId);
    }

    /**
     * Persist the SIM PIN entity in the storage.
     *
     * @param pinEntity The SIM PIN entity to persist in the storage.
     */
    public void storePin(final @NonNull PinEntity pinEntity) {
        final long id = mPinStorageDao.upsert(pinEntity);
        if (id != -1L) { // -1 is returned when record has been updated
            pinEntity.setId(id);
        }

        mLogger.d("storePin(pinEntity=%s).", pinEntity);
    }

    /**
     * Delete a SIM PIN entity from the storage.
     *
     * @param pinEntity The SIM PIN entity to remove from the storage.
     */
    public void deletePin(final @NonNull PinEntity pinEntity) {
        mLogger.d("deletePin(pinEntity=%s).", pinEntity);

        mPinStorageDao.delete(pinEntity);
    }

    /**
     * Check whether the user should be authenticated with their credentials in order to unlock the
     * hardware-backed KeyStore for further crypto operations.
     */
    public synchronized boolean isAuthenticationRequired() {
        final long authTimeout = sLastKeystoreAuthTimestamp == 0 ? 0 : sLastKeystoreAuthTimestamp +
            DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS * 1000L;
        final boolean isKeystoreAuthExpired = authTimeout - SystemClock.elapsedRealtime() < 0;
        return mKeyguardManagerLazy.get().isDeviceSecure() && isKeystoreAuthExpired;
    }

    /**
     * Set the most recent time, in milliseconds, when the user has been authenticated to unlock
     * the hardware-backed KeyStore.
     *
     * @param timestamp The {@link SystemClock#elapsedRealtime()}-based time.
     */
    public static synchronized void setLastKeystoreAuthTimestamp(final long timestamp) {
        sLastKeystoreAuthTimestamp = timestamp;
    }
}
