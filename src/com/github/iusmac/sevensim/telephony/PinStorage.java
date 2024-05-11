package com.github.iusmac.sevensim.telephony;

import android.app.KeyguardManager;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.github.iusmac.sevensim.AppDatabaseCE;
import com.github.iusmac.sevensim.Logger;
import com.github.iusmac.sevensim.Utils;

import dagger.Lazy;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.inject.Inject;
import javax.inject.Singleton;

/** This class is responsible for managing the SIM PIN codes stored on the disk. */
@WorkerThread
@Singleton
public final class PinStorage {
    /** The default instance name in the {@link KeyStore}. */
    public static final String ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore";

    /** The alias name of the user authentication bound secret key. */
    private static final String KEYSTORE_ALIAS = "7SIM_PinStorage";

    /** The default encryption options. */
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int DEFAULT_AES_GCM_MASTER_KEY_SIZE = 256;
    private static final int GCM_PARAMETER_TAG_BIT_LEN = 128;

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
    private final Lazy<KeyStore> mKeyStoreLazy;

    @Inject
    public PinStorage(final Logger.Factory loggerFactory, final AppDatabaseCE database,
            final Lazy<KeyguardManager> keyguardManagerLazy, final Lazy<KeyStore> keyStoreLazy) {

        mLogger = loggerFactory.create(getClass().getSimpleName());
        mPinStorageDao = database.pinStorageDao();
        mKeyguardManagerLazy = keyguardManagerLazy;
        mKeyStoreLazy = keyStoreLazy;
    }

    /**
     * Retrieve the SIM PIN entity for a SIM subscription ID from the storage.
     *
     * @param subId The subscription ID for which to retrieve the SIM PIN entity.
     * @return An Optional containing the SIM PIN entity, if any. The SIM PIN entity instance will
     * be <b>encrypted</b>. To decrypt use {@link #decrypt(PinEntity)}.
     */
    public Optional<PinEntity> getPin(final int subId) {
        return mPinStorageDao.findBySubscriptionId(subId);
    }

    /**
     * Persist the SIM PIN entity in the storage.
     *
     * @param pinEntity The SIM PIN entity to persist in the storage. The SIM PIN entity
     * instance <b>*must*</b> be encrypted via {@link #encrypt(PinEntity)} first.
     */
    public void storePin(final @NonNull PinEntity pinEntity) {
        if (!pinEntity.isEncrypted()) {
            throw new RuntimeException("Cannot persist an unencrypted entity. Encrypt first!");
        }

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

        if (mPinStorageDao.getCount() == 0) {
            mLogger.d("deletePin() : No more PIN entities left - deleting the secret key...");
            deleteSecretKey();
        }
    }

    /**
     * <p>Encrypt the SIM PIN entity "in-place". The instance can be persisted afterwards.
     *
     * <p><b>NOTE:</b> in order to perform crypto operations on the provided SIM PIN entity when the
     * device is secured, ensure the user has been authenticated using any secure screen lock method
     * (password/PIN/pattern or biometric) on versions prior to Android 11 (API 30). On newer
     * versions, only user credentials (password/PIN/pattern) can be used as secure screen lock
     * method. Once the user has been authenticated, the crypto operations are allowed within the
     * time defined by {@link #DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS}.
     *
     * @see #isAuthenticationRequired()
     *
     * @param pinEntity The SIM PIN entity to be encrypted.
     * @return Whether the PIN entity instance has been successfully encrypted.
     */
    public boolean encrypt(final PinEntity pinEntity) {
        final String clearPin = pinEntity.getClearPin();
        if (TextUtils.isEmpty(clearPin)) {
            mLogger.e("encrypt(pinEntity=%s) : Clear PIN code is empty. Nothing to encrypt!",
                    pinEntity);
            return false;
        }
        final SecretKey secretKey = getOrCreateSecretKey();
        if (secretKey == null) {
            mLogger.e("encrypt(pinEntity=%s) : SecretKey is null!", pinEntity);
            return false;
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            pinEntity.setData(cipher.doFinal(clearPin.getBytes()));
            pinEntity.setIV(cipher.getIV());
        } catch (Exception e) {
            mLogger.e("encrypt(pinEntity=%s) : %s", pinEntity, e);
            return false;
        }
        return true;
    }

    /**
     * <p>Decrypt the SIM PIN entity "in-place".
     *
     * <p><b>NOTE:</b> in order to be able to decrypt the provided SIM PIN entity when the device is
     * secured, ensure the user has been authenticated using any secure screen lock method
     * (password/PIN/pattern or biometric) on versions prior to Android 11 (API 30). On newer
     * versions, only user credentials (password/PIN/pattern) can be used as secure screen lock
     * method. Once the user has been authenticated, the crypto operations are allowed within the
     * time defined by {@link #DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS}.
     *
     * @see #isAuthenticationRequired()
     *
     * @param pinEntity The SIM PIN entity to be decrypted.
     * @return Whether the PIN entity has been successfully decrypted.
     */
    public boolean decrypt(final @NonNull PinEntity pinEntity) {
        if (!pinEntity.isEncrypted()) {
            throw new RuntimeException("Attempting to decrypt an unencrypted entity!");
        }
        final SecretKey secretKey = getOrCreateSecretKey();
        if (secretKey == null) {
            mLogger.e("decrypt(pinEntity=%s) : SecretKey is null!.", pinEntity);
            return false;
        }
        try {
            final Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            final GCMParameterSpec spec = new GCMParameterSpec(GCM_PARAMETER_TAG_BIT_LEN,
                    pinEntity.getIV());
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            pinEntity.setClearPin(new String(cipher.doFinal(pinEntity.getData())));
        } catch (Exception e) {
            mLogger.e("decrypt(pinEntity=%s) : %s", pinEntity, e);
            return false;
        }
        return true;
    }

    /**
     * Return the existing, or create a new instance of the {@link SecretKey}.
     *
     * @return An instance of {@link SecretKey}.
     */
    private SecretKey getOrCreateSecretKey() {
        try {
            final SecretKey secretKey = loadSecretKey();
            if (secretKey != null) {
                return secretKey;
            }
        } catch (UnrecoverableEntryException e) {
            mLogger.e("Could not read alias.", e);

            // Badly encrypted alias; delete the key to allow recreation
            deleteSecretKey();
        }

        mLogger.d("getOrCreateSecretKey() : Alias does not exist - creating...");
        return createSecretKey();
    }

    /**
     * @return An instance of {@link SecretKey}, otherwise {@code null} on alias reading errors or
     * if the key doesn't exist.
     */
    private SecretKey loadSecretKey() throws UnrecoverableEntryException {
        try {
            final KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry)
                mKeyStoreLazy.get().getEntry(KEYSTORE_ALIAS, /*protParam=*/ null);
            if (secretKeyEntry != null) {
                return secretKeyEntry.getSecretKey();
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            mLogger.e("Could not read alias.", e);
        }
        return null;
    }

    /**
     * Create a user authentication bound secret key that is valid for the time defined by
     * {@link #DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS}.
     *
     * <p><b>NOTE:</b> if the device is secured, then ensure the user has been authenticated first
     * using any secure screen lock method (password/PIN/pattern or biometric) on versions prior to
     * Android 11 (API 30). On newer versions only user credentials (password/PIN/pattern) can be
     * used as secure screen lock method.
     *
     * @see #isAuthenticationRequired()
     *
     * @return An instance of {@link SecretKey}, or {@code null} on error.
     */
    private SecretKey createSecretKey() {
        try {
            final KeyGenerator keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE_PROVIDER);

            final KeyGenParameterSpec.Builder keyGenParameterSpec =
                    new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(DEFAULT_AES_GCM_MASTER_KEY_SIZE);

            if (mKeyguardManagerLazy.get().isDeviceSecure()) {
                keyGenParameterSpec.setUserAuthenticationRequired(true);
                if (Utils.IS_AT_LEAST_R) {
                    keyGenParameterSpec.setUserAuthenticationParameters(
                            DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS,
                            KeyProperties.AUTH_DEVICE_CREDENTIAL);
                } else {
                    ApiDeprecated.setUserAuthenticationValidityDurationSeconds(keyGenParameterSpec,
                            DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS);
                }
            }

            keyGenerator.init(keyGenParameterSpec.build());

            return keyGenerator.generateKey();
        } catch (Exception e) {
            mLogger.e("Could not create secret key.", e);
        }
        return null;
    }

    /**
     * Delete the secret key entry in the Android's {@link KeyStore}.
     */
    private void deleteSecretKey() {
        mLogger.d("deleteSecretKey().");

        try {
            mKeyStoreLazy.get().deleteEntry(KEYSTORE_ALIAS);
        } catch (KeyStoreException e) {
            mLogger.e("Could not delete secret key.", e);
        }
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

    /** Nested class to suppress warning only for API methods annotated as Deprecated. */
    @SuppressWarnings("deprecation")
    private static final class ApiDeprecated {
        static KeyGenParameterSpec.Builder setUserAuthenticationValidityDurationSeconds(
                final KeyGenParameterSpec.Builder builder, final int seconds) {

            return builder.setUserAuthenticationValidityDurationSeconds(seconds);
        }
    }
}
