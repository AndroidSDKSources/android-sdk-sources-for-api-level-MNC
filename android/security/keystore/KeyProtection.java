/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.KeyguardManager;
import android.content.Context;
import android.security.ArrayUtils;
import android.security.KeyStore;

import java.security.Key;
import java.security.KeyStore.ProtectionParameter;
import java.security.cert.Certificate;
import java.util.Date;

import javax.crypto.Cipher;

/**
 * Specification of how a key or key pair is secured when imported into the
 * <a href="{@docRoot}training/articles/keystore.html">Android KeyStore facility</a>. This class
 * specifies parameters such as whether user authentication is required for using the key, what uses
 * the key is authorized for (e.g., only in {@code CTR} mode, or only for signing -- decryption not
 * permitted), whether the key should be encrypted at rest, the key's and validity start and end
 * dates.
 *
 * <p>To import a key or key pair into the Android KeyStore, create an instance of this class using
 * the {@link Builder} and pass the instance into {@link java.security.KeyStore#setEntry(String, java.security.KeyStore.Entry, ProtectionParameter) KeyStore.setEntry}
 * with the key or key pair being imported.
 *
 * <p>To obtain the secret/symmetric or private key from the Android KeyStore use
 * {@link java.security.KeyStore#getKey(String, char[]) KeyStore.getKey(String, null)} or
 * {@link java.security.KeyStore#getEntry(String, java.security.KeyStore.ProtectionParameter) KeyStore.getEntry(String, null)}.
 * To obtain the public key from the Android KeyStore use
 * {@link java.security.KeyStore#getCertificate(String)} and then
 * {@link Certificate#getPublicKey()}.
 *
 * <p>NOTE: The key material of keys stored in the Android KeyStore is not accessible.
 *
 * <p><h3>Example: Symmetric Key</h3>
 * The following example illustrates how to import an AES key into the Android KeyStore under alias
 * {@code key1} authorized to be used only for encryption/decryption in CBC mode with PKCS#7
 * padding. The key must export its key material via {@link Key#getEncoded()} in {@code RAW} format.
 * <pre> {@code
 * SecretKey key = ...; // AES key
 *
 * KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
 * keyStore.load(null);
 * keyStore.setEntry(
 *         "key1",
 *         new KeyStore.SecretKeyEntry(key),
 *         new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
 *                 .setBlockMode(KeyProperties.BLOCK_MODE_CBC)
 *                 .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
 *                 .build());
 * // Key imported, obtain a reference to it.
 * SecretKey keyStoreKey = (SecretKey) keyStore.getKey("key1", null);
 * // The original key can now be thrown away.
 * }</pre>
 *
 * <p><h3>Example: Asymmetric Key Pair</h3>
 * The following example illustrates how to import an EC key pair into the Android KeyStore under
 * alias {@code key2} authorized to be used only for signing with SHA-256 digest and only if
 * the user has been authenticated within the last ten minutes. Both the private and the public key
 * must export their key material via {@link Key#getEncoded()} in {@code PKCS#8} and {@code X.509}
 * format respectively.
 * <pre> {@code
 * PrivateKey privateKey = ...;   // EC private key
 * Certificate[] certChain = ...; // Certificate chain with the first certificate
 *                                // containing the corresponding EC public key.
 *
 * KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
 * keyStore.load(null);
 * keyStore.setEntry(
 *         "key2",
 *         new KeyStore.PrivateKeyEntry(privateKey, certChain),
 *         new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
 *                 .setDigests(KeyProperties.DIGEST_SHA256)
 *                 // Only permit this key to be used if the user
 *                 // authenticated within the last ten minutes.
 *                 .setUserAuthenticationRequired(true)
 *                 .setUserAuthenticationValidityDurationSeconds(10 * 60)
 *                 .build());
 * // Key pair imported, obtain a reference to it.
 * PrivateKey keyStorePrivateKey = (PrivateKey) keyStore.getKey("key2", null);
 * PublicKey publicKey = keyStore.getCertificate("key2").getPublicKey();
 * // The original private key can now be thrown away.
 * }</pre>
 */
public final class KeyProtection implements ProtectionParameter {
    private final int mFlags;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final @KeyProperties.PurposeEnum int mPurposes;
    private final @KeyProperties.EncryptionPaddingEnum String[] mEncryptionPaddings;
    private final @KeyProperties.SignaturePaddingEnum String[] mSignaturePaddings;
    private final @KeyProperties.DigestEnum String[] mDigests;
    private final @KeyProperties.BlockModeEnum String[] mBlockModes;
    private final boolean mRandomizedEncryptionRequired;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;

    private KeyProtection(
            int flags,
            Date keyValidityStart,
            Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd,
            @KeyProperties.PurposeEnum int purposes,
            @KeyProperties.EncryptionPaddingEnum String[] encryptionPaddings,
            @KeyProperties.SignaturePaddingEnum String[] signaturePaddings,
            @KeyProperties.DigestEnum String[] digests,
            @KeyProperties.BlockModeEnum String[] blockModes,
            boolean randomizedEncryptionRequired,
            boolean userAuthenticationRequired,
            int userAuthenticationValidityDurationSeconds) {
        if ((userAuthenticationValidityDurationSeconds < 0)
                && (userAuthenticationValidityDurationSeconds != -1)) {
            throw new IllegalArgumentException(
                    "userAuthenticationValidityDurationSeconds must not be negative");
        }

        mFlags = flags;
        mKeyValidityStart = keyValidityStart;
        mKeyValidityForOriginationEnd = keyValidityForOriginationEnd;
        mKeyValidityForConsumptionEnd = keyValidityForConsumptionEnd;
        mPurposes = purposes;
        mEncryptionPaddings =
                ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(encryptionPaddings));
        mSignaturePaddings =
                ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(signaturePaddings));
        mDigests = ArrayUtils.cloneIfNotEmpty(digests);
        mBlockModes = ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(blockModes));
        mRandomizedEncryptionRequired = randomizedEncryptionRequired;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
    }

    /**
     * @hide
     */
    public int getFlags() {
        return mFlags;
    }

    /**
     * Returns {@code true} if the {@link java.security.KeyStore} entry must be encrypted at rest.
     * This will protect the entry with the secure lock screen credential (e.g., password, PIN, or
     * pattern).
     */
    public boolean isEncryptionAtRestRequired() {
        return (mFlags & KeyStore.FLAG_ENCRYPTED) != 0;
    }

    /**
     * Gets the time instant before which the key is not yet valid.
     *
     * @return instant or {@code null} if not restricted.
     */
    @Nullable
    public Date getKeyValidityStart() {
        return mKeyValidityStart;
    }

    /**
     * Gets the time instant after which the key is no long valid for decryption and verification.
     *
     * @return instant or {@code null} if not restricted.
     */
    @Nullable
    public Date getKeyValidityForConsumptionEnd() {
        return mKeyValidityForConsumptionEnd;
    }

    /**
     * Gets the time instant after which the key is no long valid for encryption and signing.
     *
     * @return instant or {@code null} if not restricted.
     */
    @Nullable
    public Date getKeyValidityForOriginationEnd() {
        return mKeyValidityForOriginationEnd;
    }

    /**
     * Gets the set of purposes (e.g., encrypt, decrypt, sign) for which the key can be used.
     * Attempts to use the key for any other purpose will be rejected.
     *
     * <p>See {@link KeyProperties}.{@code PURPOSE} flags.
     */
    public @KeyProperties.PurposeEnum int getPurposes() {
        return mPurposes;
    }

    /**
     * Gets the set of padding schemes (e.g., {@code PKCS7Padding}, {@code PKCS1Padding},
     * {@code NoPadding}) with which the key can be used when encrypting/decrypting. Attempts to use
     * the key with any other padding scheme will be rejected.
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
     * Gets the set of digest algorithms (e.g., {@code SHA-256}, {@code SHA-384}) with which the key
     * can be used.
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
    public boolean isDigestsSpecified() {
        return mDigests != null;
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
     * weaknesses due to which ciphertext may leak information about plaintext. For example, if a
     * given plaintext always produces the same ciphertext, an attacker may see the repeated
     * ciphertexts and be able to deduce something about the plaintext.
     */
    public boolean isRandomizedEncryptionRequired() {
        return mRandomizedEncryptionRequired;
    }

    /**
     * Returns {@code true} if user authentication is required for this key to be used.
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
     * @return duration in seconds or {@code -1} if authentication is required for every use of the
     *         key.
     *
     * @see #isUserAuthenticationRequired()
     */
    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }

    /**
     * Builder of {@link KeyProtection} instances.
     */
    public final static class Builder {
        private @KeyProperties.PurposeEnum int mPurposes;

        private int mFlags;
        private Date mKeyValidityStart;
        private Date mKeyValidityForOriginationEnd;
        private Date mKeyValidityForConsumptionEnd;
        private @KeyProperties.EncryptionPaddingEnum String[] mEncryptionPaddings;
        private @KeyProperties.SignaturePaddingEnum String[] mSignaturePaddings;
        private @KeyProperties.DigestEnum String[] mDigests;
        private @KeyProperties.BlockModeEnum String[] mBlockModes;
        private boolean mRandomizedEncryptionRequired = true;
        private boolean mUserAuthenticationRequired;
        private int mUserAuthenticationValidityDurationSeconds = -1;

        /**
         * Creates a new instance of the {@code Builder}.
         *
         * @param purposes set of purposes (e.g., encrypt, decrypt, sign) for which the key can be
         *        used. Attempts to use the key for any other purpose will be rejected.
         *
         *        <p><b>NOTE: The {@code purposes} parameter has currently no effect on asymmetric
         *        key pairs.</b>
         *
         *        <p>See {@link KeyProperties}.{@code PURPOSE} flags.
         */
        public Builder(@KeyProperties.PurposeEnum int purposes) {
            mPurposes = purposes;
        }

        /**
         * Sets whether this {@link java.security.KeyStore} entry must be encrypted at rest.
         * Encryption at rest will protect the entry with the secure lock screen credential (e.g.,
         * password, PIN, or pattern).
         *
         * <p>Note that enabling this feature requires that the secure lock screen (e.g., password,
         * PIN, pattern) is set up, otherwise setting the {@code KeyStore} entry will fail.
         * Moreover, this entry will be deleted when the secure lock screen is disabled or reset
         * (e.g., by the user or a Device Administrator). Finally, this entry cannot be used until
         * the user unlocks the secure lock screen after boot.
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
         * Sets the set of padding schemes (e.g., {@code OAEPPadding}, {@code PKCS7Padding},
         * {@code NoPadding}) with which the key can be used when encrypting/decrypting. Attempts to
         * use the key with any other padding scheme will be rejected.
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
         * Sets the set of digest algorithms (e.g., {@code SHA-256}, {@code SHA-384}) with which the
         * key can be used when signing/verifying or generating MACs. Attempts to use the key with
         * any other digest algorithm will be rejected.
         *
         * <p>For HMAC keys, the default is the digest algorithm specified in
         * {@link Key#getAlgorithm()}. For asymmetric signing keys the set of digest algorithms
         * must be specified.
         *
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
         *
         * <p>See {@link KeyProperties}.{@code DIGEST} constants.
         */
        @NonNull
        public Builder setDigests(@KeyProperties.DigestEnum String... digests) {
            mDigests = ArrayUtils.cloneIfNotEmpty(digests);
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
         * <li>transformation which do not offer {@code IND-CPA}, such as symmetric ciphers using
         * {@code ECB} mode or RSA encryption without padding, are prohibited;</li>
         * <li>in transformations which use an IV, such as symmetric ciphers in {@code CBC},
         * {@code CTR}, and {@code GCM} block modes, caller-provided IVs are rejected when
         * encrypting, to ensure that only random IVs are used.</li>
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
         * <li>If you are using RSA encryption without padding, consider switching to padding
         * schemes which offer {@code IND-CPA}, such as PKCS#1 or OAEP.</li>
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
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
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
         * <p><b>NOTE: This has currently no effect on asymmetric key pairs.</b>
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
         * Builds an instance of {@link KeyProtection}.
         *
         * @throws IllegalArgumentException if a required field is missing
         */
        @NonNull
        public KeyProtection build() {
            return new KeyProtection(
                    mFlags,
                    mKeyValidityStart,
                    mKeyValidityForOriginationEnd,
                    mKeyValidityForConsumptionEnd,
                    mPurposes,
                    mEncryptionPaddings,
                    mSignaturePaddings,
                    mDigests,
                    mBlockModes,
                    mRandomizedEncryptionRequired,
                    mUserAuthenticationRequired,
                    mUserAuthenticationValidityDurationSeconds);
        }
    }
}
