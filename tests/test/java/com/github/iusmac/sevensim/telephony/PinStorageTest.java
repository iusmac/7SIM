package com.github.iusmac.sevensim.telephony;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Build;
import android.os.SystemClock;
import android.security.keystore.KeyProperties;
import android.telephony.SubscriptionManager;
import android.util.ExceptionUtils;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.github.iusmac.sevensim.R;
import com.github.iusmac.sevensim.test.FakeAndroidKeyStoreProvider.AesKeyGenerator;
import com.github.iusmac.sevensim.test.FakeAndroidKeyStoreProvider;
import com.github.iusmac.sevensim.test.MockitoHiltAndroidTestBase;

import dagger.hilt.android.testing.HiltAndroidTest;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.collection.IsIterableWithSize;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowSystemClock;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.github.iusmac.sevensim.test.TestUtils.corrupted;
import static com.github.iusmac.sevensim.test.TestUtils.encrypted;
import static com.github.iusmac.sevensim.test.TestUtils.invalid;
import static com.github.iusmac.sevensim.test.TestUtils.withSubId;

import static org.awaitility.Awaitility.await;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

@HiltAndroidTest
@RunWith(RobolectricTestRunner.class)
public final class PinStorageTest extends MockitoHiltAndroidTestBase {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String KEYSTORE_ALIAS = "7SIM_PinStorage";
    private static final int DEFAULT_AES_GCM_MASTER_KEY_SIZE = 256;
    private static final Duration DEFAULT_AUTHENTICATION_VALIDITY_DURATION = Duration.ofMinutes(5);

    @Rule
    public final InstantTaskExecutorRule mInstantTaskExecutorRule = new InstantTaskExecutorRule();

    @Inject
    PinStorage mPinStorage;

    @Inject
    KeyStore mKeyStore;

    @Inject
    KeyguardManager mKeyguardManager;

    @Inject
    SubscriptionManager mSubscriptionManager;

    @After
    public void tearDown() {
        PinStorage.setLastKeystoreAuthTimestamp(0L);
    }

    @Test
    public void test_getPinEntities_Empty() {
        assertThat(assertFutureDone(EXECUTOR.submit(mPinStorage::getPinEntities)), is(empty()));
    }

    @Test
    public void test_getPinEntities_NonEmptyAndAllEncrypted() {
        final var pinEntityEncrypted1 = new PinEntity();
        pinEntityEncrypted1.setSubscriptionId(1);
        pinEntityEncrypted1.setClearPin("1234");
        assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.encrypt(pinEntityEncrypted1));
            assertThat(pinEntityEncrypted1, is(encrypted()));
            mPinStorage.storePin(pinEntityEncrypted1);
        }));

        final var pinEntityEncrypted2 = new PinEntity();
        pinEntityEncrypted2.setSubscriptionId(2);
        pinEntityEncrypted2.setClearPin("56784321");
        assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.encrypt(pinEntityEncrypted2));
            assertThat(pinEntityEncrypted2, is(encrypted()));
            mPinStorage.storePin(pinEntityEncrypted2);
        }));

        assertThat(assertFutureDone(EXECUTOR.submit(mPinStorage::getPinEntities)),
                is(both(IsIterableWithSize.<PinEntity>iterableWithSize(2))
                    .and(everyItem(encrypted()))));
    }

    @Test
    public void test_getPin_InvalidSubscriptionId() {
        final var pinEntity = assertFutureDone(EXECUTOR.submit(() ->
                    mPinStorage.getPin(INVALID_SUBSCRIPTION_ID)));
        assertThat(pinEntity, is(Optional.empty()));
    }

    @Test
    public void test_getPin_MatchesSubscriptionIdAndEncrypted() {
        final var pinEntityEncrypted = new PinEntity();
        pinEntityEncrypted.setSubscriptionId(1);
        pinEntityEncrypted.setClearPin("1234");
        assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.encrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            mPinStorage.storePin(pinEntityEncrypted);
        }));

        final var actualPinEntity = assertFutureDone(EXECUTOR.submit(() ->
                    mPinStorage.getPin(pinEntityEncrypted.getSubscriptionId())));
        assertThat(actualPinEntity, is(not(Optional.empty())));
        assertThat(actualPinEntity.get(),
                is(both(encrypted()).and(withSubId(equalTo(pinEntityEncrypted.getSubscriptionId())))));
    }

    @Test
    public void test_getObservablePin_InvalidSubscriptionId() {
        final var observablePinEntity = mPinStorage.getObservablePin(INVALID_SUBSCRIPTION_ID);
        // Add an observer to trigger data fetching from database
        assertFutureDone(EXECUTOR.submit(() -> observablePinEntity
                    .observeForever((pinEntity) -> {})));

        assertThat(observablePinEntity.getValue(), is(Optional.empty()));
    }

    @Test
    public void test_getObservablePin_MatchesSubscriptionIdAndEncrypted() {
        final var pinEntityEncrypted = new PinEntity();
        pinEntityEncrypted.setSubscriptionId(1);
        pinEntityEncrypted.setClearPin("1234");
        assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.encrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            mPinStorage.storePin(pinEntityEncrypted);
        }));

        final var observablePinEntity = mPinStorage.getObservablePin(
                pinEntityEncrypted.getSubscriptionId());
        // Add an observer to trigger data fetching from database
        assertFutureDone(EXECUTOR.submit(() -> observablePinEntity
                    .observeForever((pinEntity_) -> {})));

        assertThat(observablePinEntity.getValue(), is(not(Optional.empty())));
        assertThat(observablePinEntity.getValue().get(), is(both(encrypted())
                    .and(withSubId(equalTo(pinEntityEncrypted.getSubscriptionId())))));
    }

    @Test
    public void test_getCount_Zero() {
        final int count = assertFutureDone(EXECUTOR.submit(() -> mPinStorage.getCount()));
        assertThat(count, is(0));
    }

    @Test
    public void test_getCount_NonZero() {
        final var pinEntityEncrypted = new PinEntity();
        pinEntityEncrypted.setSubscriptionId(1);
        pinEntityEncrypted.setClearPin("1234");
        assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.encrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            mPinStorage.storePin(pinEntityEncrypted);
        }));

        final int count = assertFutureDone(EXECUTOR.submit(() -> mPinStorage.getCount()));
        assertThat(count, is(1));
    }

    @Test(expected = RuntimeException.class)
    public void test_storePin_ShouldNotAllowUnencryptedEntities() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");
        assertThat(pinEntity, is(not(encrypted())));
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.storePin(pinEntity)));
    }

    @Test
    public void test_storePin_ShouldAssignIdWhenInserted() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));
        assertThat(pinEntityEncrypted.getId(), is(greaterThan(0L)));
    }

    @Test
    public void test_storePin_ShouldUpdateWhenAlreadyInserted() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final long id = pinEntityEncrypted.getId();
        pinEntityEncrypted.setSubscriptionId(pinEntityEncrypted.getSubscriptionId() + 1);
        pinEntityEncrypted.setCorrupted(!pinEntityEncrypted.isCorrupted());

        final var pinEntityEncryptedUpdated = assertFutureDone(EXECUTOR.submit(() -> {
            mPinStorage.storePin(pinEntityEncrypted);
            return mPinStorage.getPin(pinEntityEncrypted.getSubscriptionId()).get();
        }));

        assertThat(pinEntityEncryptedUpdated.getId(), is(id));
        assertThat(pinEntityEncryptedUpdated.getSubscriptionId(),
                is(pinEntityEncrypted.getSubscriptionId()));
        assertThat(pinEntityEncryptedUpdated.isCorrupted(), is(pinEntityEncrypted.isCorrupted()));
    }

    @Test
    public void test_deletePin() {
        final var pinEntityEncrypted1 = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final var pinEntityEncrypted2 = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(2);
            pinEntity.setClearPin("5678");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final var deletedPinEntityEncrypted1 = assertFutureDone(EXECUTOR.submit(() -> {
            mPinStorage.deletePin(pinEntityEncrypted1);
            return mPinStorage.getPin(pinEntityEncrypted1.getSubscriptionId());
        }));

        assertThat(deletedPinEntityEncrypted1, is(Optional.empty()));

        final var presentPinEntity2 = assertFutureDone(EXECUTOR.submit(() ->
                    mPinStorage.getPin(pinEntityEncrypted2.getSubscriptionId())));

        assertThat(presentPinEntity2, is(not(Optional.empty())));
    }

    @Test
    public void test_deletePin_ShouldNotDeleteSecretKeyWhenPinStorageIsNotEmpty() {
        final var pinEntityEncrypted1 = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(2);
            pinEntity.setClearPin("5678");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.deletePin(pinEntityEncrypted1)));

        final var removedEntry = ((FakeAndroidKeyStoreProvider)
                mKeyStore.getProvider()).getLatestRemovedEntry();
        assertThat(removedEntry, is(Optional.empty()));
        assertThat(getSecretKey(), is(not(Optional.empty())));
    }

    @Test
    public void test_deletePin_ShouldDeleteSecretKeyWhenPinStorageIsEmpty() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.deletePin(pinEntityEncrypted)));

        final var removedEntry = ((FakeAndroidKeyStoreProvider)
                mKeyStore.getProvider()).getLatestRemovedEntry();
        assertThat(removedEntry, is(not(Optional.empty())));
        assertThat(getSecretKey(), is(Optional.empty()));
    }

    @Test
    public void test_deletePin_ShouldFailDeleteSecretKeyWhenPinStorageIsEmptyAndAndroidKeyStoreIsNotInitialized() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        // Simulate Android's KeyStore has not been initialized
        fakeProvider.setAndroidKeyStoreInitialized(false);

        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.deletePin(pinEntityEncrypted)));

        assertThat(fakeProvider.getLatestRemovedEntry(), is(Optional.empty()));

        fakeProvider.setAndroidKeyStoreInitialized(true);
        assertThat(getSecretKey(), is(not(Optional.empty())));
    }

    @Test
    public void test_encrypt() {
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
    }

    @Test
    public void test_encrypt_ShouldNotAllowEntitiesWithoutClearPinProvided() {
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin(null);
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(2);
            pinEntity.setClearPin("");
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));
    }

    @Test
    public void test_encrypt_ShouldReuseSecretKeyWhenAlreadyExists() {
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
        final var secretKey1 = getSecretKey();

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        final var secretKey2 = getSecretKey();
        assertThat(secretKey2, sameSecretKey(secretKey1));
    }

    @Test
    public void test_encrypt_ShouldFailWhenAESKeyGenerationFails() {
        ((FakeAndroidKeyStoreProvider) mKeyStore.getProvider())
            .setAESKeyGenerationAlwaysFails(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));

        assertThat(getSecretKey(), is(Optional.empty()));
    }

    @Test(expected = KeyStoreException.class)
    public void test_encrypt_ShouldFailWhenAndroidKeyStoreIsNotInitialized() throws Throwable {
        // Simulate Android's KeyStore has not been initialized
        ((FakeAndroidKeyStoreProvider) mKeyStore.getProvider())
            .setAndroidKeyStoreInitialized(false);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));

        try {
            getSecretKey();
        } catch (RuntimeException e) {
            throw ExceptionUtils.getRootCause(e);
        }
    }

    @Test
    public void test_encrypt_ShouldFailWhenBadSecretKey() throws KeyStoreException {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        // Swap the current secret key with the one whose length is 1 byte to make it inappropriate
        // (will raise an InvalidKeyException) when initializing the cipher with this encryption key
        final var currentSecretKey = getSecretKey().get();
        final var badSecretKey = new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[1],
                    currentSecretKey.getAlgorithm()));
        mKeyStore.setEntry(KEYSTORE_ALIAS, badSecretKey, /*protParam=*/ null);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));
    }

    @Test
    public void test_encrypt_ShouldRegenerateSecretKeyWhenRecoveryAlgorithmNotFound() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
        final var oldSecretKey = getSecretKey();

        // Throw a NoSuchAlgorithmException to simulate an unrecoverable SecretKey when re-used
        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        fakeProvider.needThrowNoSuchAlgorithmExceptionWhenReadingAlias(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        fakeProvider.needThrowNoSuchAlgorithmExceptionWhenReadingAlias(false);
        assertThat(getSecretKey(), is(not(sameSecretKey(oldSecretKey))));
    }

    @Test
    public void test_encrypt_ShouldDeleteUnrecoverableSecretKeys() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
        final var unrecoverableSecretKey = getSecretKey();
        assertThat(unrecoverableSecretKey, is(not(Optional.empty())));

        // Throw an UnrecoverableEntryException to simulate a bad SecretKey when re-used
        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        fakeProvider.needThrowUnrecoverableEntryExceptionWhenReadingAlias(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        final var removedEntry = fakeProvider.getLatestRemovedEntry();
        assertThat(removedEntry, is(not(Optional.empty())));
        assertThat(((KeyStore.SecretKeyEntry) removedEntry.get()).getSecretKey(),
                is(unrecoverableSecretKey.get()));
    }

    @Test
    public void test_encrypt_ShouldRegenerateUnrecoverableSecretKeys() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
        final var oldSecretKey = getSecretKey();

        // Throw an UnrecoverableEntryException to simulate a bad SecretKey when re-used
        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        fakeProvider.needThrowUnrecoverableEntryExceptionWhenReadingAlias(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        fakeProvider.needThrowUnrecoverableEntryExceptionWhenReadingAlias(false);
        assertThat(getSecretKey(), is(not(sameSecretKey(oldSecretKey))));
    }

    @Test
    public void test_encrypt_ShouldNotRegenerateSecretKeysWhenAndroidKeyStoreIsNotInitialized() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));
        final var oldSecretKey = getSecretKey();

        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        // Simulate Android's KeyStore has not been initialized
        fakeProvider.setAndroidKeyStoreInitialized(false);

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertFalse(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
        }));

        fakeProvider.setAndroidKeyStoreInitialized(true);
        assertThat(getSecretKey(), sameSecretKey(oldSecretKey));
    }

    @Test
    public void test_decrypt() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");

            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));

        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(pinEntityEncrypted.getSubscriptionId());
            pinEntity.setData(pinEntityEncrypted.getData());
            pinEntity.setIV(pinEntityEncrypted.getIV());

            assertThat(pinEntity, is(encrypted()));
            assertTrue(mPinStorage.decrypt(pinEntity));
            assertThat(pinEntity, is(not(encrypted())));
            assertThat(pinEntity, is(not(corrupted())));
            assertThat(pinEntity.getClearPin(), is(pinEntityEncrypted.getClearPin()));
        }));
    }

    @Test
    public void test_decrypt_ShouldFailWhenBadEntityEncryptionData() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));

        final var data = pinEntityEncrypted.getData();
        // Mangle the encryption data by flipping the first bit every 3 bytes
        for (int i = 0; i < data.length; i += 3) {
            data[i] = (byte) (data[i] ^ 1);
        }

        assertFutureDone(EXECUTOR.submit(() -> {
            assertFalse(mPinStorage.decrypt(pinEntityEncrypted));
            assertTrue(pinEntityEncrypted.isEncrypted());
            assertTrue(pinEntityEncrypted.isCorrupted());
        }));
    }

    @Test(expected = RuntimeException.class)
    public void test_decrypt_ShouldNotAllowUnencryptedEntities() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setClearPin("1234");
        assertThat(pinEntity, is(not(encrypted())));
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.decrypt(pinEntity)));
    }

    @Test
    public void test_decrypt_ShouldNotAllowCorruptedEntities() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));

        assertThat(pinEntityEncrypted, is(not(corrupted())));
        pinEntityEncrypted.setCorrupted(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            assertFalse(mPinStorage.decrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            assertThat(pinEntityEncrypted, is(corrupted()));
        }));
    }

    @Test
    public void test_decrypt_ShouldFailWhenAndroidKeyStoreIsNotInitialized() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));

        // Simulate Android's KeyStore has not been initialized
        ((FakeAndroidKeyStoreProvider) mKeyStore.getProvider())
            .setAndroidKeyStoreInitialized(false);

        assertFutureDone(EXECUTOR.submit(() -> {
            assertFalse(mPinStorage.decrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            assertThat(pinEntityEncrypted, is(corrupted()));
        }));
    }

    @Test
    public void test_decrypt_ShouldNotDeleteSecretKeyWhenAndroidKeyStoreIsNotInitialized() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));
        final var oldSecretKey = getSecretKey();

        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        // Simulate Android's KeyStore has not been initialized
        fakeProvider.setAndroidKeyStoreInitialized(false);

        assertFutureDone(EXECUTOR.submit(() -> {
            assertFalse(mPinStorage.decrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            assertThat(pinEntityEncrypted, is(corrupted()));
        }));

        assertThat(fakeProvider.getLatestRemovedEntry(), is(Optional.empty()));

        fakeProvider.setAndroidKeyStoreInitialized(true);
        assertThat(getSecretKey(), sameSecretKey(oldSecretKey));
    }

    @Test
    public void test_decrypt_ShouldNotDeleteUnrecoverableSecretKeys() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            return pinEntity;
        }));
        final var oldSecretKey = getSecretKey();

        // Throw an UnrecoverableEntryException to simulate a bad SecretKey when re-used
        final var fakeProvider = (FakeAndroidKeyStoreProvider) mKeyStore.getProvider();
        fakeProvider.needThrowUnrecoverableEntryExceptionWhenReadingAlias(true);

        assertFutureDone(EXECUTOR.submit(() -> {
            assertFalse(mPinStorage.decrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(encrypted()));
            assertThat(pinEntityEncrypted, is(corrupted()));
        }));

        assertThat(fakeProvider.getLatestRemovedEntry(), is(Optional.empty()));

        fakeProvider.needThrowUnrecoverableEntryExceptionWhenReadingAlias(false);
        final var unrecoverableSecretKey = getSecretKey();
        assertThat(unrecoverableSecretKey, sameSecretKey(oldSecretKey));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotMemoizeBadEntities() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            assertThat(pinEntity, is(not(invalid())));
            assertThat(pinEntity, is(not(corrupted())));
            mPinStorage.handleBadPinEntity(pinEntity);
            return mPinStorage.getPin(pinEntity.getSubscriptionId());
        }));

        assertThat(pinEntityEncrypted, is(Optional.empty()));
    }

    @Test
    public void test_handleBadPinEntity_ShouldMemoizeBadEntities_Decrypted() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final var badPinEntity = assertFutureDone(EXECUTOR.submit(() -> {
            assertTrue(mPinStorage.decrypt(pinEntityEncrypted));
            assertThat(pinEntityEncrypted, is(not(encrypted())));
            assertThat(pinEntityEncrypted, is(not(invalid())));
            assertThat(pinEntityEncrypted, is(not(corrupted())));
            pinEntityEncrypted.setInvalid(true);
            pinEntityEncrypted.setCorrupted(true);
            mPinStorage.handleBadPinEntity(pinEntityEncrypted);
            return mPinStorage.getPin(pinEntityEncrypted.getSubscriptionId());
        }));

        assertThat(badPinEntity, is(not(Optional.empty())));
        assertThat(badPinEntity.get(), is(invalid()));
        assertThat(badPinEntity.get(), is(corrupted()));
    }

    @Test
    public void test_handleBadPinEntity_ShouldMemoizeBadEntities_Encrypted() {
        final var pinEntityEncrypted = assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
            mPinStorage.storePin(pinEntity);
            return pinEntity;
        }));

        final var badPinEntity = assertFutureDone(EXECUTOR.submit(() -> {
            assertThat(pinEntityEncrypted, is(not(invalid())));
            assertThat(pinEntityEncrypted, is(not(corrupted())));
            pinEntityEncrypted.setInvalid(true);
            pinEntityEncrypted.setCorrupted(true);
            mPinStorage.handleBadPinEntity(pinEntityEncrypted);
            return mPinStorage.getPin(pinEntityEncrypted.getSubscriptionId());
        }));

        assertThat(badPinEntity, is(not(Optional.empty())));
        assertThat(badPinEntity.get(), is(invalid()));
        assertThat(badPinEntity.get(), is(corrupted()));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotNotifyUserAboutNotBadEntity() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        assertThat(pinEntity, is(not(invalid())));
        assertThat(pinEntity, is(not(corrupted())));
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.handleBadPinEntity(pinEntity)));

        assertThat(getShadowNotificationManager().getAllNotifications(), is(empty()));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotifyUserAboutInvalidEntity() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setInvalid(true);
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.handleBadPinEntity(pinEntity)));

        assertLastNotificationTitle(containsString(mApplicationContext
                .getString(R.string.sim_unlock_failed)));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotifyUserAboutCorruptedEntity() {
        final var subInfo = SubscriptionInfoBuilder.newBuilder()
            .setId(1)
            .setSimSlotIndex(0)
            .buildSubscriptionInfo();
        shadowOf(mSubscriptionManager).setAvailableSubscriptionInfos(subInfo);

        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(subInfo.getSubscriptionId());
        pinEntity.setCorrupted(true);
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.handleBadPinEntity(pinEntity)));

        assertLastNotificationTitle(containsString(mApplicationContext
                .getString(R.string.sim_pin_operation_failed)));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotNotifyAboutInvalidEntitiesWithoutSubscription() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setInvalid(true);
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.handleBadPinEntity(pinEntity)));

        assertThat(getShadowNotificationManager().getAllNotifications(), is(empty()));
    }

    @Test
    public void test_handleBadPinEntity_ShouldNotNotifyAboutCorruptedEntitiesWithoutSubscription() {
        final var pinEntity = new PinEntity();
        pinEntity.setSubscriptionId(1);
        pinEntity.setCorrupted(true);
        assertFutureDone(EXECUTOR.submit(() -> mPinStorage.handleBadPinEntity(pinEntity)));

        assertThat(getShadowNotificationManager().getAllNotifications(), is(empty()));
    }

    @Test
    public void test_isAuthenticationRequired_KeyStoreAuthShouldNotBeExpiredWhenDeviceIsNotSecure() {
        assertFalse(mPinStorage.isAuthenticationRequired());
    }

    @Test
    public void test_isAuthenticationRequired_KeyStoreAuthShouldBeExpiredWhenNeverAuthenticatedAndDeviceIsSecure() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    public void test_isAuthenticationRequired_KeyStoreAuthShouldBeExpiredWhenAuthenticatedAtLeastOnceAndDeviceIsSecure() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);
        PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime());
        ShadowSystemClock.advanceBy(DEFAULT_AUTHENTICATION_VALIDITY_DURATION.plusMillis(1));
        assertTrue(mPinStorage.isAuthenticationRequired());
    }

    @Test
    public void test_isAuthenticationRequired_KeyStoreAuthShouldNotBeExpiredWhenDeviceIsSecure() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);
        PinStorage.setLastKeystoreAuthTimestamp(SystemClock.elapsedRealtime());
        assertFalse(mPinStorage.isAuthenticationRequired());
    }

    @Test
    public void test_SecretKeyCreationUsingAndroidSpecificAlgorithmParameterSpec_RequiredFields() {
        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        final var keyGenParameterSpec = AesKeyGenerator.getLatestParameterSpec();
        assertThat(keyGenParameterSpec, is(not(Optional.empty())));
        final var spec = keyGenParameterSpec.get();
        assertThat(spec.getKeystoreAlias(), is(KEYSTORE_ALIAS));
        assertThat(spec.getPurposes(), is(KeyProperties.PURPOSE_ENCRYPT |
                    KeyProperties.PURPOSE_DECRYPT));
        assertThat(spec.getBlockModes(), is(array(equalTo(KeyProperties.BLOCK_MODE_GCM))));
        assertThat(spec.getEncryptionPaddings(),
                is(array(equalTo(KeyProperties.ENCRYPTION_PADDING_NONE))));
        assertThat(spec.getKeySize(), is(DEFAULT_AES_GCM_MASTER_KEY_SIZE));
        assertFalse(spec.isUserAuthenticationRequired());
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.R)
    public void test_SecretKeyCreationUsingAndroidSpecificAlgorithmParameterSpec_ShouldRequireUserAuthenticationWithFixedValidityDurationWhenDeviceIsSecure_SinceR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        final var keyGenParameterSpec = AesKeyGenerator.getLatestParameterSpec();
        assertThat(keyGenParameterSpec, is(not(Optional.empty())));
        final var spec = keyGenParameterSpec.get();
        assertTrue(spec.isUserAuthenticationRequired());
        assertThat(spec.getUserAuthenticationType(), is(KeyProperties.AUTH_DEVICE_CREDENTIAL));
        assertThat(Duration.ofSeconds(spec.getUserAuthenticationValidityDurationSeconds()),
                is(DEFAULT_AUTHENTICATION_VALIDITY_DURATION));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void test_SecretKeyCreationUsingAndroidSpecificAlgorithmParameterSpec_ShouldRequireUserAuthenticationWithFixedValidityDurationWhenDeviceIsSecure_BeforeR() {
        shadowOf(mKeyguardManager).setIsDeviceSecure(true);

        // Make a successful encryption to trigger SecretKey generation in the (FakeAndroid)KeyStore
        assertFutureDone(EXECUTOR.submit(() -> {
            final var pinEntity = new PinEntity();
            pinEntity.setSubscriptionId(1);
            pinEntity.setClearPin("1234");
            assertTrue(mPinStorage.encrypt(pinEntity));
            assertThat(pinEntity, is(encrypted()));
        }));

        final var keyGenParameterSpec = AesKeyGenerator.getLatestParameterSpec();
        assertThat(keyGenParameterSpec, is(not(Optional.empty())));
        final var spec = keyGenParameterSpec.get();
        assertTrue(spec.isUserAuthenticationRequired());
        assertThat(Duration.ofSeconds(spec.getUserAuthenticationValidityDurationSeconds()),
                is(DEFAULT_AUTHENTICATION_VALIDITY_DURATION));
    }

    private void assertLastNotificationTitle(final Matcher<String> matcher) {
        final var notif = getShadowNotificationManager().getAllNotifications().stream().findFirst();
        assertThat(notif, is(not(Optional.empty())));
        final var title = notif.get().extras.getString(Notification.EXTRA_TITLE);
        assertThat(title, matcher);
    }

    private Optional<SecretKey> getSecretKey() {
        try {
            final var secretKeyEntry = (KeyStore.SecretKeyEntry) mKeyStore.getEntry(KEYSTORE_ALIAS,
                    /*protParam=*/ null);
            if (secretKeyEntry != null) {
                return Optional.of(secretKeyEntry.getSecretKey());
            }
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private ShadowNotificationManager getShadowNotificationManager() {
        return shadowOf(mApplicationContext.getSystemService(NotificationManager.class));
    }

    private static <S extends SecretKey> FeatureMatcher<Optional<S>, Optional<S>> sameSecretKey(
            final Optional<S> secretKey) {

        final var matcher = both(not(Optional.<S>empty())).and(is(secretKey));
        return new FeatureMatcher<>(matcher, "same SecretKey", "SecretKey") {
            @Override
            protected Optional<S> featureValueOf(final Optional<S> other) {
                return other;
            }
        };
    }

    private static <T> T assertFutureDone(final Future<T> future) {
        await()
            .dontCatchUncaughtExceptions()
            .pollInSameThread()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(50))
            .until(future::isDone);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
