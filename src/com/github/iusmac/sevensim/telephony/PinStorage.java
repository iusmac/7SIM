package com.github.iusmac.sevensim.telephony;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.Logger;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

/** This class is responsible for managing the SIM PIN codes stored on the disk. */
@WorkerThread
@Singleton
public final class PinStorage {
    private final Logger mLogger;
    private final PinStorageDao mPinStorageDao;

    @Inject
    public PinStorage(final Logger.Factory loggerFactory, final AppDatabaseCE database) {
        mLogger = loggerFactory.create(getClass().getSimpleName());
        mPinStorageDao = database.pinStorageDao();
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
}
