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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.security.ArrayUtils;

import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.util.Date;

import javax.crypto.SecretKey;

/**
 * Information about a key from the <a href="{@docRoot}training/articles/keystore.html">Android
 * Keystore system</a>. This class describes whether the key material is available in
 * plaintext outside of secure hardware, whether user authentication is required for using the key
 * and whether this requirement is enforced by secure hardware, the key's origin, what uses the key
 * is authorized for (e.g., only in {@code CBC} mode, or signing only), whether the key should be
 * encrypted at rest, the key's and validity start and end dates.
 *
 * <p><h3>Example: Symmetric Key</h3>
 * The following example illustrates how to obtain a {@code KeyInfo} describing the provided Android
 * Keystore {@link SecretKey}.
 * <pre> {@code
 * SecretKey key = ...; // Android Keystore key
 *
 * SecretKeyFactory factory = SecretKeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
 * KeyInfo keyInfo;
 * try &#123;
 *     keyInfo = (KeyInfo) factory.getKeySpec(key, KeyInfo.class);
 * &#125; catch (InvalidKeySpecException e) &#123;
 *     // Not an Android KeyStore key.
 * &#125;
 * }</pre>
 *
 * <p><h3>Example: Private Key</h3>
 * The following example illustrates how to obtain a {@code KeyInfo} describing the provided
 * Android KeyStore {@link PrivateKey}.
 * <pre> {@code
 * PrivateKey key = ...; // Android KeyStore key
 *
 * KeyFactory factory = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
 * KeyInfo keyInfo;
 * try &#123;
 *     keyInfo = factory.getKeySpec(key, KeyInfo.class);
 * &#125; catch (InvalidKeySpecException e) &#123;
 *     // Not an Android KeyStore key.
 * &#125;
 * }</pre>
 */
public class KeyInfo implements KeySpec {
    private final String mKeystoreAlias;
    private final int mKeySize;
    private final boolean mInsideSecureHardware;
    private final @KeyProperties.OriginEnum int mOrigin;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final @KeyProperties.PurposeEnum int mPurposes;
    private final @KeyProperties.EncryptionPaddingEnum String[] mEncryptionPaddings;
    private final @KeyProperties.SignaturePaddingEnum String[] mSignaturePaddings;
    private final @KeyProperties.DigestEnum String[] mDigests;
    private final @KeyProperties.BlockModeEnum String[] mBlockModes;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;
    private final boolean mUserAuthenticationRequirementEnforcedBySecureHardware;

    /**
     * @hide
     */
    public KeyInfo(String keystoreKeyAlias,
            boolean insideSecureHardware,
            @KeyProperties.OriginEnum int origin,
            int keySize,
            Date keyValidityStart,
            Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd,
            @KeyProperties.PurposeEnum int purposes,
            @KeyProperties.EncryptionPaddingEnum String[] encryptionPaddings,
            @KeyProperties.SignaturePaddingEnum String[] signaturePaddings,
            @KeyProperties.DigestEnum String[] digests,
            @KeyProperties.BlockModeEnum String[] blockModes,
            boolean userAuthenticationRequired,
            int userAuthenticationValidityDurationSeconds,
            boolean userAuthenticationRequirementEnforcedBySecureHardware) {
        mKeystoreAlias = keystoreKeyAlias;
        mInsideSecureHardware = insideSecureHardware;
        mOrigin = origin;
        mKeySize = keySize;
        mKeyValidityStart = keyValidityStart;
        mKeyValidityForOriginationEnd = keyValidityForOriginationEnd;
        mKeyValidityForConsumptionEnd = keyValidityForConsumptionEnd;
        mPurposes = purposes;
        mEncryptionPaddings =
                ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(encryptionPaddings));
        mSignaturePaddings =
                ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(signaturePaddings));
        mDigests = ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(digests));
        mBlockModes = ArrayUtils.cloneIfNotEmpty(ArrayUtils.nullToEmpty(blockModes));
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
        mUserAuthenticationRequirementEnforcedBySecureHardware =
                userAuthenticationRequirementEnforcedBySecureHardware;
    }

    /**
     * Gets the entry alias under which the key is stored in the {@code AndroidKeyStore}.
     */
    public String getKeystoreAlias() {
        return mKeystoreAlias;
    }

    /**
     * Returns {@code true} if the key resides inside secure hardware (e.g., Trusted Execution
     * Environment (TEE) or Secure Element (SE)). Key material of such keys is available in
     * plaintext only inside the secure hardware and is not exposed outside of it.
     */
    public boolean isInsideSecureHardware() {
        return mInsideSecureHardware;
    }

    /**
     * Gets the origin of the key. See {@link KeyProperties}.{@code ORIGIN} constants.
     */
    public @KeyProperties.OriginEnum int getOrigin() {
        return mOrigin;
    }

    /**
     * Gets the size of the key in bits.
     */
    public int getKeySize() {
        return mKeySize;
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
     */
    @NonNull
    public @KeyProperties.DigestEnum String[] getDigests() {
        return ArrayUtils.cloneIfNotEmpty(mDigests);
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
     * Returns {@code true} if the requirement that this key can only be used if the user has been
     * authenticated if enforced by secure hardware (e.g., Trusted Execution Environment (TEE) or
     * Secure Element (SE)).
     *
     * @see #isUserAuthenticationRequired()
     */
    public boolean isUserAuthenticationRequirementEnforcedBySecureHardware() {
        return mUserAuthenticationRequirementEnforcedBySecureHardware;
    }
}
