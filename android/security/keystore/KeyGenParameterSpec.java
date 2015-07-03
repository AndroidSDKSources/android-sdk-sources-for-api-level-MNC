/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.keystore;

import android.app.KeyguardManager;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.text.TextUtils;
import android.security.ArrayUtils;
import android.security.KeyStore;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

/**
 * {@link AlgorithmParameterSpec} for initializing a {@link KeyPairGenerator} or a
 * {@link KeyGenerator} of the <a href="{@docRoot}training/articles/keystore.html">Android Keystore
 * system</a>. The spec determines whether user authentication is required for using the key, what
 * uses the key is authorized for (e.g., only for signing -- decryption not permitted), whether the
 * key should be encrypted at rest, the key's and validity start and end dates.
 *
 * <p>To generate an asymmetric key pair or a symmetric key, create an instance of this class using
 * the {@link Builder}, initialize a {@code KeyPairGenerator} or a {@code KeyGenerator} of the
 * desired key type (e.g., {@code EC} or {@code AES} -- see
 * {@link KeyProperties}.{@code KEY_ALGORITHM} constants) from the {@code AndroidKeyStore} provider
 * with the {@code KeyPairGeneratorSpec} instance, and then generate a key or key pair using
 * {@link KeyPairGenerator#generateKeyPair()}.
 *
 * <p>The generated key pair or key will be returned by the generator and also stored in the Android
 * Keystore system under the alias specified in this spec. To obtain the secret or private key from
 * the Android KeyStore use {@link java.security.KeyStore#getKey(String, char[]) KeyStore.getKey(String, null)}
 * or {@link java.security.KeyStore#getEntry(String, java.security.KeyStore.ProtectionParameter) KeyStore.getEntry(String, null)}.
 * To obtain the public key from the Android Keystore system use
 * {@link java.security.KeyStore#getCertificate(String)} and then
 * {@link Certificate#getPublicKey()}.
 *
 * <p>For asymmetric key pairs, a self-signed X.509 certificate will be also generated and stored in
 * the Android KeyStore. This is because the {@link java.security.KeyStore} abstraction does not
 * support storing key pairs without a certificate. The subject, serial number, and validity dates
 * of the certificate can be customized in this spec. The self-signed certificate may be replaced at
 * a later time by a certificate signed by a Certificate Authority (CA).
 *
 * <p>NOTE: The key material of the generated symmetric and private keys is not accessible. The key
 * material of the public keys is accessible.
 *
 * <p><h3>Example: Asymmetric key pair</h3>
 * The following example illustrates how to generate an EC key pair in the Android KeyStore system
 * under alias {@code key1} authorized to be used only for signing using SHA-256, SHA-384,
 * or SHA-512 digest and only if the user has been authenticated within the last five minutes.
 * <pre> {@code
 * KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
 *         KeyProperties.KEY_ALGORITHM_EC,
 *         "AndroidKeyStore");
 * keyPairGenerator.initialize(
 *         new KeyGenParameterSpec.Builder("key1",
 *                 KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
 *                 .setDigests(KeyProperties.DIGEST_SHA256
 *                         | KeyProperties.DIGEST_SHA384
 *                         | KeyProperties.DIGEST_SHA512)
 *                 // Only permit this key to be used if the user authenticated
 *                 // within the last five minutes.
 *                 .setUserAuthenticationRequired(true)
 *                 .setUserAuthenticationValidityDurationSeconds(5 * 60)
 *                 .build());
 * KeyPair keyPair = keyPairGenerator.generateKeyPair();
 *
 * // The key pair can also be obtained from the Android Keystore any time as follows:
 * KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
 * keyStore.load(null);
 * PrivateKey privateKey = (PrivateKey) keyStore.getKey("key1", null);
 * PublicKey publicKey = keyStore.getCertificate("key1").getPublicKey();
 * }</pre>
 *
 * <p><h3>Example: Symmetric key</h3>
 * The following example illustrates how to generate an AES key in the Android KeyStore system under
 * alias {@code key2} authorized to be used only for encryption/decryption in CTR mode.
 * <pre> {@code
 * KeyGenerator keyGenerator = KeyGenerator.getInstance(
 *         KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
 *         "AndroidKeyStore");
 * keyGenerator.initialize(
 *         new KeyGenParameterSpec.Builder("key2",
 *                 KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
 *                 .setBlockModes(KeyProperties.BLOCK_MODE_CTR)
 *                 .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
 *                 .build());
 * SecretKey key = keyGenerator.generateKey();
 *
 * // The key can also be obtained from the Android Keystore any time as follows:
 * KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
 * keyStore.load(null);
 * key = (SecretKey) keyStore.getKey("key2", null);
 * }</pre>
 */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {

    private static final X500Principal DEFAULT_CERT_SUBJECT = new X500Principal("CN=fake");
    private static final BigInteger DEFAULT_CERT_SERIAL_NUMBER = new BigInteger("1");
    private static final Date DEFAULT_CERT_NOT_BEFORE = new Date(0L); // Jan 1 1970
    private static final Date DEFAULT_CERT_NOT_AFTER = new Date(2461449600000L); // Jan 1 2048

    private final String mKeystoreAlias;
    private final int mKeySize;
    private final AlgorithmParameterSpec mSpec;
    private final X500Principal mCertificateSubject;
    private final BigInteger mCertificateSerialNumber;
    private final Date mCertificateNotBefore;
    private final Date mCertificateNotAfter;
    private final int mFlags;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final @KeyProperties.PurposeEnum int mPurposes;
    private final @KeyProperties.DigestEnum String[] mDigests;
    private final @KeyProperties.EncryptionPaddingEnum String[] mEncryptionPaddings;
    private final @KeyProperties.SignaturePaddingEnum String[] mSignaturePaddings;
    private final @KeyProperties.BlockModeEnum String[] mBlockModes;
    private final boolean mRandomizedEncryptionRequired;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;

    /**
     * @hide should be built with Builder
     */
    public KeyGenParameterSpec(
            String keyStoreAlias,
            int keySize,
            AlgorithmParameterSpec spec,
            X500Principal certificateSubject,
            BigInteger certificateSerialNumber,
            Date certificateNotBefore,
            Date certificateNotAfter,
            int flags,
            Date keyValidityStart,
            Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd,
            @KeyProperties.PurposeEnum int purposes,
            @KeyProperties.DigestEnum String[] digests,
            @KeyProperties.EncryptionPaddingEnum String[] encryptionPaddings,
            @KeyProperties.SignaturePaddingEnum String[] signaturePaddings,
            @KeyProperties.BlockModeEnum String[] blockModes,
            boolean randomizedEncryptionRequired,
            boolean userAuthenticationRequired,
            int userAuthenticationValidityDurationSeconds) {
        if (TextUtils.isEmpty(keyStoreAlias)) {
            throw new IllegalArgumentException("keyStoreAlias must not be empty");
        } else if ((userAuthenticationValidityDurationSeconds < 0)
                && (userAuthenticationValidityDurationSeconds != -1)) {
            throw new IllegalArgumentException(
                    "userAuthenticationValidityDurationSeconds must not be negative");
        }

        if (certificateSubject == null) {
            certificateSubject = DEFAULT_CERT_SUBJECT;
        }
        if (certificateNotBefore == null) {
            certificateNotBefore = DEFAULT_CERT_NOT_BEFORE;
        }
        if (certificateNotAfter == null) {
            certificateNotAfter = DEFAULT_CERT_NOT_AFTER;
        }
        if (certificateSerialNumber == null) {
            certificateSerialNumber = DEFAULT_CERT_SERIAL_NUMBER;
        }

        if (certificateNotAfter.before(certificateNotBefore)) {
            throw new IllegalArgumentException("certificateNotAfter < certificateNotBefore");
        }

        mKeystoreAlias = keyStoreAlias;
        mKeySize = keySize;
        mSpec = spec;
        mCertificateSubject = certificateSubject;
        mCertificateSerialNumber = certificateSerialNumber;
        mCertificateNotBefore = certificateNotBefore;
        mCertificateNotAfter = certificateNotAfter;
        mFlags = flags;
        mKeyValidityStart = keyValidityStart;
        mKeyValidityForOriginationEnd = keyValidityForOriginationEnd;
        mKeyValidityForConsumptionEnd = keyValidityForConsumptionEnd;
        mPurposes = purposes;
        mDigests = ArrayUtils.cloneIfNotEmpty(digests);
        mEncryptionPaddings =
                ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(encryptionPaddings));
        mSignaturePaddings = ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(signaturePaddings));
        mBlockModes = ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(blockModes));
        mRandomizedEncryptionRequired = randomizedEncryptionRequired;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
    }

    /**
     * Returns the alias that will be used in the {@code java.security.KeyStore}
     * in conjunction with the {@code AndroidKeyStore}.
     */
    public String getKeystoreAlias() {
        return mKeystoreAlias;
    }

    /**
     * Returns the requested key size or {@code -1} if default size should be used.
     */
    public int getKeySize() {
        return mKeySize;
    }

    /**
     * Returns the {@link AlgorithmParameterSpec} that will be used for creation
     * of the key pair.
     */
    @NonNull
    public AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return mSpec;
    }

    /**
     * Returns the subject distinguished name to be used on the X.509 certificate that will be put
     * in the {@link java.security.KeyStore}.
     */
    @NonNull
    public X500Principal getCertificateSubject() {
        return mCertificateSubject;
    }

    /**
     * Returns the serial number to be used on the X.509 certificate that will be put in the
     * {@link java.security.KeyStore}.
     */
    @NonNull
    public BigInteger getCertificateSerialNumber() {
        return mCertificateSerialNumber;
    }

    /**
     * Returns the start date to be used on the X.509 certificate that will be put in the
     * {@link java.security.KeyStore}.
     */
    @NonNull
    public Date getCertificateNotBefore() {
        return mCertificateNotBefore;
    }

    /**
     * Returns the end date to be used on the X.509 certificate that will be put in the
     * {@link java.security.KeyStore}.
     */
    @NonNull
    public Date getCertificateNotAfter() {
        return mCertificateNotAfter;
    }

    /**
     * @hide
     */
    public int getFlags() {
        return mFlags;
    }

    /**
     * Returns {@code true} if the key must be encrypted at rest. This will protect the key with the
     * secure lock screen credential (e.g., password, PIN, or pattern).
     *
     * <p>Note that encrypting the key at rest requires that the secure lock screen (e.g., password,
     * PIN, pattern) is set up, otherwise key generation will fail. Moreover, this key will be
     * deleted when the secure lock screen is disabled or reset (e.g., by the user or a Device
     * Administrator). Finally, this key cannot be used until the user unlocks the secure lock
     * screen after boot.
     *
     * @see KeyguardManager#isDeviceSecure()
     */
    public boolean isEncryptionAtRestRequired() {
        return (mFlags & KeyStore.FLAG_ENCRYPTED) != 0;
    }

    /**
     * Returns the time instant before which the key is not yet valid or {@code null} if not
     * restricted.
     */
    @Nullable
    public Date getKeyValidityStart() {
        return mKeyValidityStart;
    }

    /**
     * Returns the time instant after which the key is no longer valid for decryption and
     * verification or {@code null} if not restricted.
     */
    @Nullable
    public Date getKeyValidityForConsumptionEnd() {
        return mKeyValidityForConsumptionEnd;
    }

    /**
     * Returns the time instant after which the key is no longer valid for encryption and signing
     * or {@code null} if not restricted.
     */
    @Nullable
    public Date getKeyValidityForOriginationEnd() {
        return mKeyValidityForOriginationEnd;
    }

    /**
     * Returns the set of purposes (e.g., encrypt, decrypt, sign) for which the key can be used.
     * Attempts to use the key for any other purpose will be rejected.
     *
     * <p>See {@link KeyProperties}.{@code PURPOSE} flags.
     */
    public @KeyProperties.PurposeEnum int getPurposes() {
        return mPurposes;
    }

    /**
     * Returns the set of digest algorithms (e.g., {@code SHA-256}, {@code SHA-384} with which the
     * key can be used or {@code null} if not specified.
     *
     * <p>See {@link KeyProperties}.{@code DIGEST} constants.
     *
     * @throws IllegalStateException if this set has not been specified.
     *
     * @see #isDigestsSpecified()
     */
    @NonNull
    public @KeyProperties.DigestEnum String[] getDigests() {
        if (mDigests == null) {
            throw new IllegalStateException("Digests not specified");
        }
        return ArrayUtils.cloneIfNotEmpty(mDigests);
    }

    /**
     * Returns {@code true} if the set of digest algorithms with which the key can be used has been
     * specified.
     *
     * @see #getDigests()
     */
    @NonNull
    public boolean isDigestsSpecified() {
        return mDigests != null;
    }

    /**
     * Returns the set of padding schemes (e.g., {@code PKCS7Padding}, {@code OEAPPadding},
     * {@code PKCS1Padding}, {@code NoPadding}) with which the key can be used when
     * encrypting/decrypting. Attempts to use the key with any other padding scheme will be
     * rejected.
     *
     * <p>See {@link KeyProperties}.{@code ENCRYPTION_PADDING} constants.
     */
    @NonNull
    public @KeyProperties.EncryptionPaddingEnum String[] getEncryptionPaddings() {
        return ArrayUtils.cloneIfNotEmpty(mEncryptionPaddings);
    }

    /**
     * Gets the set of padding schemes (e.g., {@code PSS}, {@code PKCS#1}) with which the key
     * can be used when signing/verifying. Attempts to use the key with any other padding scheme
     * will be rejected.
     *
     * <p>See {@link KeyProperties}.{@code SIGNATURE_PADDING} constants.
     */
    @NonNull
    public @KeyProperties.SignaturePaddingEnum String[] getSignaturePaddings() {
        return ArrayUtils.cloneIfNotEmpty(mSignaturePaddings);
    }

    /**
     * Gets the set of block modes (e.g., {@code CBC}, {@code CTR}) with which the key can be used
     * when encrypting/decrypting. Attempts to use the key with any other block modes will be
     * rejected.
     *
     * <p>See {@link KeyProperties}.{@code BLOCK_MODE} constants.
     */
    @NonNull
    public @KeyProperties.BlockModeEnum String[] getBlockModes() {
        return ArrayUtils.cloneIfNotEmpty(mBlockModes);
    }

    /**
     * Returns {@code true} if encryption using this key must be sufficiently randomized to produce
     * different ciphertexts for the same plaintext every time. The formal cryptographic property
     * being required is <em>indistinguishability under chosen-plaintext attack ({@code
     * IND-CPA})</em>. This property is important because it mitigates several classes of
     * weaknesses due to which ciphertext may leak information about plaintext.  For example, if a
     * given plaintext always produces the same ciphertext, an attacker may see the repeated
     * ciphertexts and be able to deduce something about the plaintext.
     */
    public boolean isRandomizedEncryptionRequired() {
        return mRandomizedEncryptionRequired;
    }

    /**
     * Returns {@code true} if user authentication is required for this key to be used.
     *
     * <p>This restriction applies only to private key operations. Public key operations are not
     * restricted.
     *
     * @see #getUserAuthenticationValidityDurationSeconds()
     */
    public boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    /**
     * Gets the duration of time (seconds) for which this key can be used after the user is
     * successfully authenticated. This has effect only if user authentication is required.
     *
     * <p>This restriction applies only to private key operations. Public key operations are not
     * restricted.
     *
     * @return duration in seconds or {@code -1} if authentication is required for every use of the
     *         key.
     *
     * @see #isUserAuthenticationRequired()
     */
    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }

    /**
     * Builder of {@link KeyGenParameterSpec} instances.
     */
    public final static class Builder {
        private final String mKeystoreAlias;
        private @KeyProperties.PurposeEnum int mPurposes;

        private int mKeySize = -1;
        private AlgorithmParameterSpec mSpec;
        private X500Principal mCertificateSubject;
        private BigInteger mCertificateSerialNumber;
        private Date mCertificateNotBefore;
        private Date mCertificateNotAfter;
        private int mFlags;
        private Date mKeyValidityStart;
        private Date mKeyValidityForOriginationEnd;
        private Date mKeyValidityForConsumptionEnd;
        private @KeyProperties.DigestEnum String[] mDigests;
        private @KeyProperties.EncryptionPaddingEnum String[] mEncryptionPaddings;
        private @KeyProperties.SignaturePaddingEnum String[] mSignaturePaddings;
        private @KeyProperties.BlockModeEnum String[] mBlockModes;
        private boolean mRandomizedEncryptionRequired = true;
        private boolean mUserAuthenticationRequired;
        private int mUserAuthenticationValidityDurationSeconds = -1;

        /**
         * Creates a new instance of the {@code Builder}.
         *
         * @param keystoreAlias alias of the entry in which the generated key will appear in
         *        Android KeyStore.
         * @param purposes set of purposes (e.g., encrypt, decrypt, sign) for which the key can be
         *        used. Attempts to use the key for any other purpose will be rejected.
         *
         *        <p>If the set of purposes for which the key can be used does not contain
         *        {@link KeyProperties#PURPOSE_SIGN}, the self-signed certificate generated by
         *        {@link KeyPairGenerator} of {@code AndroidKeyStore} provider will contain an
         *        invalid signature. This is OK if the certificate is only used for obtaining the
         *        public key from Android KeyStore.
         *
         *        <p><b>NOTE: The {@code purposes} parameter has currently no effect on asymmetric
         *        key pairs.</b>
         *
         *        <p>See {@link KeyProperties}.{@code PURPOSE} flags.
         */
        public Builder(@NonNull String keystoreAlias, @KeyProperties.PurposeEnum int purposes) {
            if (keystoreAlias == null) {
                throw new NullPointerException("keystoreAlias == null");
            }
            mKeystoreAlias = keystoreAlias;
            mPurposes = purposes;
        }

        /**
         * Sets the size (in bits) of the key to be generated. For instance, for RSA keys this sets
         * the modulus size, for EC keys this selects a curve with a matching field size, and for
         * symmetric keys this sets the size of the bitstring which is their key material.
         *
         * <p>The default key size is specific to each key algorithm.
         */
        @NonNull
        public Builder setKeySize(int keySize) {
            if (keySize < 0) {
                throw new IllegalArgumentException("keySize < 0");
            }
            mKeySize = keySize;
            return this;
        }

        /**
         * Sets the algorithm-specific key generation parameters. For example, for RSA keys this may
         * be an instance of {@link java.security.spec.RSAKeyGenParameterSpec}.
         */
        public Builder setAlgorithmParameterSpec(@NonNull AlgorithmParameterSpec spec) {
            if (spec == null) {
                throw new NullPointerException("spec == null");
            }
            mSpec = spec;
            return this;
        }

        /**
         * Sets the subject used for the self-signed certificate of the generated key pair.
         *
         * <p>By default, the subject is {@code CN=fake}.
         */
        @NonNull
        public Builder setCertificateSubject(@NonNull X500Principal subject) {
            if (subject == null) {
                throw new NullPointerException("subject == null");
            }
            mCertificateSubject = subject;
            return this;
        }

        /**
         * Sets the serial number used for the self-signed certificate of the generated key pair.
         *
         * <p>By default, the serial number is {@code 1}.
         */
        @NonNull
        public Builder setCertificateSerialNumber(@NonNull BigInteger serialNumber) {
            if (serialNumber == null) {
                throw new NullPointerException("serialNumber == null");
            }
            mCertificateSerialNumber = serialNumber;
            return this;
        }

        /**
         * Sets the start of the validity period for the self-signed certificate of the generated
         * key pair.
         *
         * <p>By default, this date is {@code Jan 1 1970}.
         */
        @NonNull
        public Builder setCertificateNotBefore(@NonNull Date date) {
            if (date == null) {
                throw new NullPointerException("date == null");
            }
            mCertificateNotBefore = date;
            return this;
        }

        /**
         * Sets the end of the validity period for the self-signed certificate of the generated key
         * pair.
         *
         * <p>By default, this date is {@code Jan 1 2048}.
         */
        @NonNull
        public Builder setCertificateNotAfter(@NonNull Date date) {
            if (date == null) {
                throw new NullPointerException("date == null");
            }
            mCertificateNotAfter = date;
            return this;
        }

        /**
         * Sets whether this key pair or key must be encrypted at rest. This will protect the key
         * pair or key with the secure lock screen credential (e.g., password, PIN, or pattern).
         *
         * <p>Note that enabling this feature requires that the secure lock screen (e.g., password,
         * PIN, pattern) is set up, otherwise key generation will fail. Moreover, this key will be
         * deleted when the secure lock screen is disabled or reset (e.g., by the user or a Device
         * Administrator). Finally, this key cannot be used until the user unlocks the secure lock
         * screen after boot.
         *
         * @see KeyguardManager#isDeviceSecure()
         */
        @NonNull
        public Builder setEncryptionAtRestRequired(boolean required) {
            if (required) {
                mFlags |= KeyStore.FLAG_ENCRYPTED;
            } else {
                mFlags &= ~KeyStore.FLAG_ENCRYPTED;
            }
            return this;
        }

        /**
         * Sets the time instant before which the key is not yet valid.
         *
         * <p>By default, the key is valid at any instant.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * @see #setKeyValidityEnd(Date)
         */
        @NonNull
        public Builder setKeyValidityStart(Date startDate) {
            mKeyValidityStart = startDate;
            return this;
        }

        /**
         * Sets the time instant after which the key is no longer valid.
         *
         * <p>By default, the key is valid at any instant.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * @see #setKeyValidityStart(Date)
         * @see #setKeyValidityForConsumptionEnd(Date)
         * @see #setKeyValidityForOriginationEnd(Date)
         */
        @NonNull
        public Builder setKeyValidityEnd(Date endDate) {
            setKeyValidityForOriginationEnd(endDate);
            setKeyValidityForConsumptionEnd(endDate);
            return this;
        }

        /**
         * Sets the time instant after which the key is no longer valid for encryption and signing.
         *
         * <p>By default, the key is valid at any instant.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * @see #setKeyValidityForConsumptionEnd(Date)
         */
        @NonNull
        public Builder setKeyValidityForOriginationEnd(Date endDate) {
            mKeyValidityForOriginationEnd = endDate;
            return this;
        }

        /**
         * Sets the time instant after which the key is no longer valid for decryption and
         * verification.
         *
         * <p>By default, the key is valid at any instant.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * @see #setKeyValidityForOriginationEnd(Date)
         */
        @NonNull
        public Builder setKeyValidityForConsumptionEnd(Date endDate) {
            mKeyValidityForConsumptionEnd = endDate;
            return this;
        }

        /**
         * Sets the set of digests algorithms (e.g., {@code SHA-256}, {@code SHA-384}) with which
         * the key can be used when signing/verifying. Attempts to use the key with any other digest
         * algorithm will be rejected.
         *
         * <p>This must be specified for keys which are used for signing/verification. For HMAC
         * keys, the set of digests defaults to the digest associated with the key algorithm (e.g.,
         * {@code SHA-256} for key algorithm {@code HmacSHA256}
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * @see KeyProperties.Digest
         */
        @NonNull
        public Builder setDigests(@KeyProperties.DigestEnum String... digests) {
            mDigests = ArrayUtils.cloneIfNotEmpty(digests);
            return this;
        }

        /**
         * Sets the set of padding schemes (e.g., {@code PKCS7Padding}, {@code OAEPPadding},
         * {@code PKCS1Padding}, {@code NoPadding}) with which the key can be used when
         * encrypting/decrypting. Attempts to use the key with any other padding scheme will be
         * rejected.
         *
         * <p>This must be specified for keys which are used for encryption/decryption.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * <p>See {@link KeyProperties}.{@code ENCRYPTION_PADDING} constants.
         */
        @NonNull
        public Builder setEncryptionPaddings(
                @KeyProperties.EncryptionPaddingEnum String... paddings) {
            mEncryptionPaddings = ArrayUtils.cloneIfNotEmpty(paddings);
            return this;
        }

        /**
         * Sets the set of padding schemes (e.g., {@code PSS}, {@code PKCS#1}) with which the key
         * can be used when signing/verifying. Attempts to use the key with any other padding scheme
         * will be rejected.
         *
         * <p>This must be specified for RSA keys which are used for signing/verification.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * <p>See {@link KeyProperties}.{@code SIGNATURE_PADDING} constants.
         */
        @NonNull
        public Builder setSignaturePaddings(
                @KeyProperties.SignaturePaddingEnum String... paddings) {
            mSignaturePaddings = ArrayUtils.cloneIfNotEmpty(paddings);
            return this;
        }

        /**
         * Sets the set of block modes (e.g., {@code CBC}, {@code CTR}, {@code ECB}) with which the
         * key can be used when encrypting/decrypting. Attempts to use the key with any other block
         * modes will be rejected.
         *
         * <p>This must be specified for encryption/decryption keys.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * <p>See {@link KeyProperties}.{@code BLOCK_MODE} constants.
         */
        @NonNull
        public Builder setBlockModes(@KeyProperties.BlockModeEnum String... blockModes) {
            mBlockModes = ArrayUtils.cloneIfNotEmpty(blockModes);
            return this;
        }

        /**
         * Sets whether encryption using this key must be sufficiently randomized to produce
         * different ciphertexts for the same plaintext every time. The formal cryptographic
         * property being required is <em>indistinguishability under chosen-plaintext attack
         * ({@code IND-CPA})</em>. This property is important because it mitigates several classes
         * of weaknesses due to which ciphertext may leak information about plaintext. For example,
         * if a given plaintext always produces the same ciphertext, an attacker may see the
         * repeated ciphertexts and be able to deduce something about the plaintext.
         *
         * <p>By default, {@code IND-CPA} is required.
         *
         * <p>When {@code IND-CPA} is required:
         * <ul>
         * <li>encryption/decryption transformation which do not offer {@code IND-CPA}, such as
         * {@code ECB} with a symmetric encryption algorithm, or RSA encryption/decryption without
         * padding, are prohibited;</li>
         * <li>in block modes which use an IV, such as {@code CBC}, {@code CTR}, and {@code GCM},
         * caller-provided IVs are rejected when encrypting, to ensure that only random IVs are
         * used.</li>
         * </ul>
         *
         * <p>Before disabling this requirement, consider the following approaches instead:
         * <ul>
         * <li>If you are generating a random IV for encryption and then initializing a {@code}
         * Cipher using the IV, the solution is to let the {@code Cipher} generate a random IV
         * instead. This will occur if the {@code Cipher} is initialized for encryption without an
         * IV. The IV can then be queried via {@link Cipher#getIV()}.</li>
         * <li>If you are generating a non-random IV (e.g., an IV derived from something not fully
         * random, such as the name of the file being encrypted, or transaction ID, or password,
         * or a device identifier), consider changing your design to use a random IV which will then
         * be provided in addition to the ciphertext to the entities which need to decrypt the
         * ciphertext.</li>
         * <li>If you are using RSA encryption without padding, consider switching to encryption
         * padding schemes which offer {@code IND-CPA}, such as PKCS#1 or OAEP.</li>
         * </ul>
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         */
        @NonNull
        public Builder setRandomizedEncryptionRequired(boolean required) {
            mRandomizedEncryptionRequired = required;
            return this;
        }

        /**
         * Sets whether user authentication is required to use this key.
         *
         * <p>By default, the key can be used without user authentication.
         *
         * <p>When user authentication is required, the user authorizes the use of the key by
         * authenticating to this Android device using a subset of their secure lock screen
         * credentials. Different authentication methods are used depending on whether the every
         * use of the key must be authenticated (as specified by
         * {@link #setUserAuthenticationValidityDurationSeconds(int)}).
         * <a href="{@docRoot}training/articles/keystore.html#UserAuthentication">More
         * information</a>.
         *
         * <p>This restriction applies only to private key operations. Public key operations are not
         * restricted.
         *
         * <p><b>NOTE: This has currently no effect.</b>
         *
         * @see #setUserAuthenticationValidityDurationSeconds(int)
         */
        @NonNull
        public Builder setUserAuthenticationRequired(boolean required) {
            mUserAuthenticationRequired = required;
            return this;
        }

        /**
         * Sets the duration of time (seconds) for which this key can be used after the user is
         * successfully authenticated. This has effect only if user authentication is required.
         *
         * <p>By default, the user needs to authenticate for every use of the key.
         *
         * <p><b>NOTE: This has currently no effect.</b>
         *
         * @param seconds duration in seconds or {@code -1} if the user needs to authenticate for
         *        every use of the key.
         *
         * @see #setUserAuthenticationRequired(boolean)
         */
        @NonNull
        public Builder setUserAuthenticationValidityDurationSeconds(
                @IntRange(from = -1) int seconds) {
            mUserAuthenticationValidityDurationSeconds = seconds;
            return this;
        }

        /**
         * Builds an instance of {@code KeyGenParameterSpec}.
         *
         * @throws IllegalArgumentException if a required field is missing
         */
        @NonNull
        public KeyGenParameterSpec build() {
            return new KeyGenParameterSpec(
                    mKeystoreAlias,
                    mKeySize,
                    mSpec,
                    mCertificateSubject,
                    mCertificateSerialNumber,
                    mCertificateNotBefore,
                    mCertificateNotAfter,
                    mFlags,
                    mKeyValidityStart,
                    mKeyValidityForOriginationEnd,
                    mKeyValidityForConsumptionEnd,
                    mPurposes,
                    mDigests,
                    mEncryptionPaddings,
                    mSignaturePaddings,
                    mBlockModes,
                    mRandomizedEncryptionRequired,
                    mUserAuthenticationRequired,
                    mUserAuthenticationValidityDurationSeconds);
        }
    }
}
