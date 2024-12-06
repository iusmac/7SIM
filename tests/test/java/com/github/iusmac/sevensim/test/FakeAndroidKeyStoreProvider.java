package com.github.iusmac.sevensim.test;

import android.security.keystore.KeyGenParameterSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

public final class FakeAndroidKeyStoreProvider extends Provider {
    public FakeAndroidKeyStoreProvider() {
        super("AndroidKeyStore", 1.0, "Fake Android KeyStore security provider");

        put("KeyStore.AndroidKeyStore", FakeAndroidKeyStore.class.getName());
        put("KeyGenerator.AES", AesKeyGenerator.class.getName());

        setAESKeyGenerationAlwaysFails(false);
    }

    /**
     * Return the latest {@link KeyStore.Entry} removed using {@link KeyStore#deleteEntry}, if any.
     */
    public Optional<KeyStore.Entry> getLatestRemovedEntry() {
        return Optional.ofNullable(FakeAndroidKeyStore.latestRemovedEntry);
    }

    /**
     * Control whether AES key generation should always fail with a {@link RuntimeException} when
     * calling {@link KeyGenerator#generateKey} for <strong>all</strong> instances.
     */
    public void setAESKeyGenerationAlwaysFails(final boolean shouldAlwaysFail) {
        AesKeyGenerator.sKeyGenerationAlwaysFails = shouldAlwaysFail;
    }

    /**
     * Control whether a {@link NoSuchAlgorithmException} should be thrown when reading an alias
     * using {@link KeyStore#getEntry} or {@link KeyStore#getKey}.
     */
    public void needThrowNoSuchAlgorithmExceptionWhenReadingAlias(final boolean needThrow) {
        FakeAndroidKeyStore.throwNoSuchAlgorithmExceptionWhenReadingAlias = needThrow;
    }

    /**
     * Control whether an {@link UnrecoverableEntryException} should be thrown when reading an alias
     * using {@link KeyStore#getEntry}.
     */
    public void needThrowUnrecoverableEntryExceptionWhenReadingAlias(final boolean needThrow) {
        FakeAndroidKeyStore.throwUnrecoverableEntryExceptionWhenReadingAlias = needThrow;
    }

    /**
     * Control whether a {@link KeyStoreException} should be thrown on any interaction when the
     * (FakeAndroid)KeyStore is uninitialized.
     */
    public void setAndroidKeyStoreInitialized(final boolean initialized) {
        FakeAndroidKeyStore.initialized = initialized;
    }

    public static final class FakeAndroidKeyStore extends KeyStoreSpi {
        private final static Map<String, KeyStore.Entry> storedKeys = new ConcurrentHashMap<>();

        private final static KeyStore wrapped;
        static {
            try {
                wrapped = KeyStore.getInstance(KeyStore.getDefaultType());
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        private static volatile KeyStore.Entry latestRemovedEntry;
        private static volatile boolean initialized = true;
        private static volatile boolean throwNoSuchAlgorithmExceptionWhenReadingAlias;
        private static volatile boolean throwUnrecoverableEntryExceptionWhenReadingAlias;

        {
            storedKeys.clear();
            latestRemovedEntry = null;
            initialized = true;
            throwNoSuchAlgorithmExceptionWhenReadingAlias = false;
            throwUnrecoverableEntryExceptionWhenReadingAlias = false;
        }

        private static void assertInitialized() throws KeyStoreException {
            if (!initialized) {
                throw new KeyStoreException();
            }
        }

        @Override
        public KeyStore.Entry engineGetEntry(String alias, ProtectionParameter protParam)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException {

            assertInitialized();

            if (throwNoSuchAlgorithmExceptionWhenReadingAlias) {
                throw new NoSuchAlgorithmException();
            }
            if (throwUnrecoverableEntryExceptionWhenReadingAlias) {
                throw new UnrecoverableEntryException();
            }
            return storedKeys.get(alias);
        }

        @Override
        public void engineSetEntry(String alias, KeyStore.Entry entry, ProtectionParameter protParam)
                throws KeyStoreException {

            assertInitialized();
            storedKeys.put(alias, entry);
        }

        @Override
        public boolean engineEntryInstanceOf(String alias, Class<? extends KeyStore.Entry> entryClass) {
            try {
                return wrapped.entryInstanceOf(alias, entryClass);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Enumeration<String> engineAliases() {
            return Collections.enumeration(storedKeys.keySet());
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return storedKeys.containsKey(alias);
        }

        @Override
        public void engineDeleteEntry(String alias) throws KeyStoreException {
            assertInitialized();

            latestRemovedEntry = storedKeys.remove(alias);
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            try {
                return wrapped.getCertificate(alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            try {
                return wrapped.getCertificateAlias(cert);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            try {
                return wrapped.getCertificateChain(alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            try {
                return wrapped.getCreationDate(alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException,
               UnrecoverableKeyException {

            if (throwNoSuchAlgorithmExceptionWhenReadingAlias) {
                throw new NoSuchAlgorithmException();
            }
            final var entry = storedKeys.get(alias);
            return entry == null ? null : ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            try {
                return wrapped.isCertificateEntry(alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            try {
                return wrapped.isKeyEntry(alias);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void engineLoad(InputStream stream, char[] password) throws CertificateException,
               IOException, NoSuchAlgorithmException {

            wrapped.load(stream, password);
        }

        @Override
        public void engineLoad(LoadStoreParameter param)
                throws CertificateException, IOException, NoSuchAlgorithmException {

            wrapped.load(param);
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
            assertInitialized();

            wrapped.setCertificateEntry(alias, cert);
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
            assertInitialized();

            wrapped.setKeyEntry(alias, key, chain);
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain)
            throws KeyStoreException {

            assertInitialized();

            wrapped.setKeyEntry(alias, key, password, chain);
        }

        @Override
        public int engineSize() {
            try {
                return wrapped.size();
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void engineStore(OutputStream stream, char[] password)
                throws CertificateException, IOException, NoSuchAlgorithmException {

            try {
                wrapped.store(stream, password);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void engineStore(LoadStoreParameter param)
                throws CertificateException, IOException, NoSuchAlgorithmException {

            try {
                wrapped.store(param);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static final class AesKeyGenerator extends KeyGeneratorSpi {
        private final static KeyGenerator wrapped;
        static {
            try {
                wrapped = KeyGenerator.getInstance("AES");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        private static volatile KeyGenParameterSpec latestSpec;
        private static volatile boolean sKeyGenerationAlwaysFails;

        {
            latestSpec = null;
        }

        /**
         * Return the latest {@link KeyGenParameterSpec} used to create this generator, if any.
         */
        public static Optional<KeyGenParameterSpec> getLatestParameterSpec() {
            return Optional.ofNullable(latestSpec);
        }

        @Override
        protected SecretKey engineGenerateKey() {
            try {
                FakeAndroidKeyStore.assertInitialized();
            } catch (KeyStoreException e) {
                throw new ProviderException(e);
            }

            if (sKeyGenerationAlwaysFails) {
                throw new RuntimeException("Key generation was disabled.");
            }

            final var key = wrapped.generateKey();
            FakeAndroidKeyStore.storedKeys.put(latestSpec.getKeystoreAlias(),
                    new KeyStore.SecretKeyEntry(key));
            return key;
        }

        @Override
        protected void engineInit(SecureRandom random) {
            throw new UnsupportedOperationException("Cannot initialize without a "
                    + KeyGenParameterSpec.class.getName() + " parameter");
        }

        @Override
        protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidAlgorithmParameterException {

            if (params == null || !(params instanceof KeyGenParameterSpec)) {
                throw new InvalidAlgorithmParameterException("Cannot initialize without a "
                        + KeyGenParameterSpec.class.getName() + " parameter");
            }

            latestSpec = (KeyGenParameterSpec) params;

            if (latestSpec.getKeystoreAlias() == null) {
                throw new InvalidAlgorithmParameterException("KeyStore entry alias not provided");
            }

            wrapped.init(latestSpec.getKeySize(), random);
        }

        @Override
        protected void engineInit(int keySize, SecureRandom random) {
            throw new UnsupportedOperationException("Cannot initialize without a "
                    + KeyGenParameterSpec.class.getName() + " parameter");
        }
    }
}
