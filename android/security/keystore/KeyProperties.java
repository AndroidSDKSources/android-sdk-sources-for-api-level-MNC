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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringDef;
import android.security.keymaster.KeymasterDefs;

import libcore.util.EmptyArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Locale;

/**
 * Properties of <a href="{@docRoot}training/articles/keystore.html">Android Keystore</a> keys.
 */
public abstract class KeyProperties {
    private KeyProperties() {}

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {
                PURPOSE_ENCRYPT,
                PURPOSE_DECRYPT,
                PURPOSE_SIGN,
                PURPOSE_VERIFY,
                })
    public @interface PurposeEnum {}

    /**
     * Purpose of key: encryption.
     */
    public static final int PURPOSE_ENCRYPT = 1 << 0;

    /**
     * Purpose of key: decryption.
     */
    public static final int PURPOSE_DECRYPT = 1 << 1;

    /**
     * Purpose of key: signing or generating a Message Authentication Code (MAC).
     */
    public static final int PURPOSE_SIGN = 1 << 2;

    /**
     * Purpose of key: signature or Message Authentication Code (MAC) verification.
     */
    public static final int PURPOSE_VERIFY = 1 << 3;

    /**
     * @hide
     */
    public static abstract class Purpose {
        private Purpose() {}

        public static int toKeymaster(@PurposeEnum int purpose) {
            switch (purpose) {
                case PURPOSE_ENCRYPT:
                    return KeymasterDefs.KM_PURPOSE_ENCRYPT;
                case PURPOSE_DECRYPT:
                    return KeymasterDefs.KM_PURPOSE_DECRYPT;
                case PURPOSE_SIGN:
                    return KeymasterDefs.KM_PURPOSE_SIGN;
                case PURPOSE_VERIFY:
                    return KeymasterDefs.KM_PURPOSE_VERIFY;
                default:
                    throw new IllegalArgumentException("Unknown purpose: " + purpose);
            }
        }

        public static @PurposeEnum int fromKeymaster(int purpose) {
            switch (purpose) {
                case KeymasterDefs.KM_PURPOSE_ENCRYPT:
                    return PURPOSE_ENCRYPT;
                case KeymasterDefs.KM_PURPOSE_DECRYPT:
                    return PURPOSE_DECRYPT;
                case KeymasterDefs.KM_PURPOSE_SIGN:
                    return PURPOSE_SIGN;
                case KeymasterDefs.KM_PURPOSE_VERIFY:
                    return PURPOSE_VERIFY;
                default:
                    throw new IllegalArgumentException("Unknown purpose: " + purpose);
            }
        }

        @NonNull
        public static int[] allToKeymaster(@PurposeEnum int purposes) {
            int[] result = getSetFlags(purposes);
            for (int i = 0; i < result.length; i++) {
                result[i] = toKeymaster(result[i]);
            }
            return result;
        }

        public static @PurposeEnum int allFromKeymaster(@NonNull Collection<Integer> purposes) {
            @PurposeEnum int result = 0;
            for (int keymasterPurpose : purposes) {
                result |= fromKeymaster(keymasterPurpose);
            }
            return result;
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        KEY_ALGORITHM_RSA,
        KEY_ALGORITHM_EC,
        KEY_ALGORITHM_AES,
        KEY_ALGORITHM_HMAC_SHA1,
        KEY_ALGORITHM_HMAC_SHA224,
        KEY_ALGORITHM_HMAC_SHA256,
        KEY_ALGORITHM_HMAC_SHA384,
        KEY_ALGORITHM_HMAC_SHA512,
        })
    public @interface KeyAlgorithmEnum {}

    /** Rivest Shamir Adleman (RSA) key. */
    public static final String KEY_ALGORITHM_RSA = "RSA";

    /** Elliptic Curve (EC) Cryptography key. */
    public static final String KEY_ALGORITHM_EC = "EC";

    /** Advanced Encryption Standard (AES) key. */
    public static final String KEY_ALGORITHM_AES = "AES";

    /** Keyed-Hash Message Authentication Code (HMAC) key using SHA-1 as the hash. */
    public static final String KEY_ALGORITHM_HMAC_SHA1 = "HmacSHA1";

    /** Keyed-Hash Message Authentication Code (HMAC) key using SHA-224 as the hash. */
    public static final String KEY_ALGORITHM_HMAC_SHA224 = "HmacSHA224";

    /** Keyed-Hash Message Authentication Code (HMAC) key using SHA-256 as the hash. */
    public static final String KEY_ALGORITHM_HMAC_SHA256 = "HmacSHA256";

    /** Keyed-Hash Message Authentication Code (HMAC) key using SHA-384 as the hash. */
    public static final String KEY_ALGORITHM_HMAC_SHA384 = "HmacSHA384";

    /** Keyed-Hash Message Authentication Code (HMAC) key using SHA-512 as the hash. */
    public static final String KEY_ALGORITHM_HMAC_SHA512 = "HmacSHA512";

    /**
     * @hide
     */
    public static abstract class KeyAlgorithm {
        private KeyAlgorithm() {}

        public static int toKeymasterSecretKeyAlgorithm(
                @NonNull @KeyAlgorithmEnum String algorithm) {
            if (KEY_ALGORITHM_AES.equalsIgnoreCase(algorithm)) {
                return KeymasterDefs.KM_ALGORITHM_AES;
            } else if (algorithm.toUpperCase(Locale.US).startsWith("HMAC")) {
                return KeymasterDefs.KM_ALGORITHM_HMAC;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported secret key algorithm: " + algorithm);
            }
        }

        @NonNull
        public static @KeyAlgorithmEnum String fromKeymasterSecretKeyAlgorithm(
                int keymasterAlgorithm, int keymasterDigest) {
            switch (keymasterAlgorithm) {
                case KeymasterDefs.KM_ALGORITHM_AES:
                    if (keymasterDigest != -1) {
                        throw new IllegalArgumentException("Digest not supported for AES key: "
                                + Digest.fromKeymaster(keymasterDigest));
                    }
                    return KEY_ALGORITHM_AES;
                case KeymasterDefs.KM_ALGORITHM_HMAC:
                    switch (keymasterDigest) {
                        case KeymasterDefs.KM_DIGEST_SHA1:
                            return KEY_ALGORITHM_HMAC_SHA1;
                        case KeymasterDefs.KM_DIGEST_SHA_2_224:
                            return KEY_ALGORITHM_HMAC_SHA224;
                        case KeymasterDefs.KM_DIGEST_SHA_2_256:
                            return KEY_ALGORITHM_HMAC_SHA256;
                        case KeymasterDefs.KM_DIGEST_SHA_2_384:
                            return KEY_ALGORITHM_HMAC_SHA384;
                        case KeymasterDefs.KM_DIGEST_SHA_2_512:
                            return KEY_ALGORITHM_HMAC_SHA512;
                        default:
                            throw new IllegalArgumentException("Unsupported HMAC digest: "
                                    + Digest.fromKeymaster(keymasterDigest));
                    }
                default:
                    throw new IllegalArgumentException(
                            "Unsupported key algorithm: " + keymasterAlgorithm);
            }
        }

        /**
         * @hide
         *
         * @return keymaster digest or {@code -1} if the algorithm does not involve a digest.
         */
        public static int toKeymasterDigest(@NonNull @KeyAlgorithmEnum String algorithm) {
            String algorithmUpper = algorithm.toUpperCase(Locale.US);
            if (algorithmUpper.startsWith("HMAC")) {
                String digestUpper = algorithmUpper.substring("HMAC".length());
                switch (digestUpper) {
                    case "SHA1":
                        return KeymasterDefs.KM_DIGEST_SHA1;
                    case "SHA224":
                        return KeymasterDefs.KM_DIGEST_SHA_2_224;
                    case "SHA256":
                        return KeymasterDefs.KM_DIGEST_SHA_2_256;
                    case "SHA384":
                        return KeymasterDefs.KM_DIGEST_SHA_2_384;
                    case "SHA512":
                        return KeymasterDefs.KM_DIGEST_SHA_2_512;
                    default:
                        throw new IllegalArgumentException(
                                "Unsupported HMAC digest: " + digestUpper);
                }
            } else {
                return -1;
            }
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        BLOCK_MODE_ECB,
        BLOCK_MODE_CBC,
        BLOCK_MODE_CTR,
        BLOCK_MODE_GCM,
        })
    public @interface BlockModeEnum {}

    /** Electronic Codebook (ECB) block mode. */
    public static final String BLOCK_MODE_ECB = "ECB";

    /** Cipher Block Chaining (CBC) block mode. */
    public static final String BLOCK_MODE_CBC = "CBC";

    /** Counter (CTR) block mode. */
    public static final String BLOCK_MODE_CTR = "CTR";

    /** Galois/Counter Mode (GCM) block mode. */
    public static final String BLOCK_MODE_GCM = "GCM";

    /**
     * @hide
     */
    public static abstract class BlockMode {
        private BlockMode() {}

        public static int toKeymaster(@NonNull @BlockModeEnum String blockMode) {
            if (BLOCK_MODE_ECB.equalsIgnoreCase(blockMode)) {
                return KeymasterDefs.KM_MODE_ECB;
            } else if (BLOCK_MODE_CBC.equalsIgnoreCase(blockMode)) {
                return KeymasterDefs.KM_MODE_CBC;
            } else if (BLOCK_MODE_CTR.equalsIgnoreCase(blockMode)) {
                return KeymasterDefs.KM_MODE_CTR;
            } else if (BLOCK_MODE_GCM.equalsIgnoreCase(blockMode)) {
                return KeymasterDefs.KM_MODE_GCM;
            } else {
                throw new IllegalArgumentException("Unsupported block mode: " + blockMode);
            }
        }

        @NonNull
        public static @BlockModeEnum String fromKeymaster(int blockMode) {
            switch (blockMode) {
                case KeymasterDefs.KM_MODE_ECB:
                    return BLOCK_MODE_ECB;
                case KeymasterDefs.KM_MODE_CBC:
                    return BLOCK_MODE_CBC;
                case KeymasterDefs.KM_MODE_CTR:
                    return BLOCK_MODE_CTR;
                case KeymasterDefs.KM_MODE_GCM:
                    return BLOCK_MODE_GCM;
                default:
                    throw new IllegalArgumentException("Unsupported block mode: " + blockMode);
            }
        }

        @NonNull
        public static @BlockModeEnum String[] allFromKeymaster(
                @NonNull Collection<Integer> blockModes) {
            if ((blockModes == null) || (blockModes.isEmpty())) {
                return EmptyArray.STRING;
            }
            @BlockModeEnum String[] result = new String[blockModes.size()];
            int offset = 0;
            for (int blockMode : blockModes) {
                result[offset] = fromKeymaster(blockMode);
                offset++;
            }
            return result;
        }

        public static int[] allToKeymaster(@Nullable @BlockModeEnum String[] blockModes) {
            if ((blockModes == null) || (blockModes.length == 0)) {
                return EmptyArray.INT;
            }
            int[] result = new int[blockModes.length];
            for (int i = 0; i < blockModes.length; i++) {
                result[i] = toKeymaster(blockModes[i]);
            }
            return result;
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        ENCRYPTION_PADDING_NONE,
        ENCRYPTION_PADDING_PKCS7,
        ENCRYPTION_PADDING_RSA_PKCS1,
        ENCRYPTION_PADDING_RSA_OAEP,
        })
    public @interface EncryptionPaddingEnum {}

    /**
     * No encryption padding.
     */
    public static final String ENCRYPTION_PADDING_NONE = "NoPadding";

    /**
     * PKCS#7 encryption padding scheme.
     */
    public static final String ENCRYPTION_PADDING_PKCS7 = "PKCS7Padding";

    /**
     * RSA PKCS#1 v1.5 padding scheme for encryption.
     */
    public static final String ENCRYPTION_PADDING_RSA_PKCS1 = "PKCS1Padding";

    /**
     * RSA Optimal Asymmetric Encryption Padding (OAEP) scheme.
     */
    public static final String ENCRYPTION_PADDING_RSA_OAEP = "OAEPPadding";

    /**
     * @hide
     */
    public static abstract class EncryptionPadding {
        private EncryptionPadding() {}

        public static int toKeymaster(@NonNull @EncryptionPaddingEnum String padding) {
            if (ENCRYPTION_PADDING_NONE.equalsIgnoreCase(padding)) {
                return KeymasterDefs.KM_PAD_NONE;
            } else if (ENCRYPTION_PADDING_PKCS7.equalsIgnoreCase(padding)) {
                return KeymasterDefs.KM_PAD_PKCS7;
            } else if (ENCRYPTION_PADDING_RSA_PKCS1.equalsIgnoreCase(padding)) {
                return KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_ENCRYPT;
            } else if (ENCRYPTION_PADDING_RSA_OAEP.equalsIgnoreCase(padding)) {
                return KeymasterDefs.KM_PAD_RSA_OAEP;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported encryption padding scheme: " + padding);
            }
        }

        @NonNull
        public static @EncryptionPaddingEnum String fromKeymaster(int padding) {
            switch (padding) {
                case KeymasterDefs.KM_PAD_NONE:
                    return ENCRYPTION_PADDING_NONE;
                case KeymasterDefs.KM_PAD_PKCS7:
                    return ENCRYPTION_PADDING_PKCS7;
                case KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_ENCRYPT:
                    return ENCRYPTION_PADDING_RSA_PKCS1;
                case KeymasterDefs.KM_PAD_RSA_OAEP:
                    return ENCRYPTION_PADDING_RSA_OAEP;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported encryption padding: " + padding);
            }
        }

        @NonNull
        public static int[] allToKeymaster(@Nullable @EncryptionPaddingEnum String[] paddings) {
            if ((paddings == null) || (paddings.length == 0)) {
                return EmptyArray.INT;
            }
            int[] result = new int[paddings.length];
            for (int i = 0; i < paddings.length; i++) {
                result[i] = toKeymaster(paddings[i]);
            }
            return result;
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        SIGNATURE_PADDING_RSA_PKCS1,
        SIGNATURE_PADDING_RSA_PSS,
        })
    public @interface SignaturePaddingEnum {}

    /**
     * RSA PKCS#1 v1.5 padding for signatures.
     */
    public static final String SIGNATURE_PADDING_RSA_PKCS1 = "PKCS1";

    /**
     * RSA PKCS#1 v2.1 Probabilistic Signature Scheme (PSS) padding.
     */
    public static final String SIGNATURE_PADDING_RSA_PSS = "PSS";

    static abstract class SignaturePadding {
        private SignaturePadding() {}

        static int toKeymaster(@NonNull @SignaturePaddingEnum String padding) {
            switch (padding.toUpperCase(Locale.US)) {
                case SIGNATURE_PADDING_RSA_PKCS1:
                    return KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_SIGN;
                case SIGNATURE_PADDING_RSA_PSS:
                    return KeymasterDefs.KM_PAD_RSA_PSS;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported signature padding scheme: " + padding);
            }
        }

        @NonNull
        static @SignaturePaddingEnum String fromKeymaster(int padding) {
            switch (padding) {
                case KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_SIGN:
                    return SIGNATURE_PADDING_RSA_PKCS1;
                case KeymasterDefs.KM_PAD_RSA_PSS:
                    return SIGNATURE_PADDING_RSA_PSS;
                default:
                    throw new IllegalArgumentException("Unsupported signature padding: " + padding);
            }
        }

        @NonNull
        static int[] allToKeymaster(@Nullable @SignaturePaddingEnum String[] paddings) {
            if ((paddings == null) || (paddings.length == 0)) {
                return EmptyArray.INT;
            }
            int[] result = new int[paddings.length];
            for (int i = 0; i < paddings.length; i++) {
                result[i] = toKeymaster(paddings[i]);
            }
            return result;
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        DIGEST_NONE,
        DIGEST_MD5,
        DIGEST_SHA1,
        DIGEST_SHA224,
        DIGEST_SHA256,
        DIGEST_SHA384,
        DIGEST_SHA512,
        })
    public @interface DigestEnum {}

    /**
     * No digest: sign/authenticate the raw message.
     */
    public static final String DIGEST_NONE = "NONE";

    /**
     * MD5 digest.
     */
    public static final String DIGEST_MD5 = "MD5";

    /**
     * SHA-1 digest.
     */
    public static final String DIGEST_SHA1 = "SHA-1";

    /**
     * SHA-2 224 (aka SHA-224) digest.
     */
    public static final String DIGEST_SHA224 = "SHA-224";

    /**
     * SHA-2 256 (aka SHA-256) digest.
     */
    public static final String DIGEST_SHA256 = "SHA-256";

    /**
     * SHA-2 384 (aka SHA-384) digest.
     */
    public static final String DIGEST_SHA384 = "SHA-384";

    /**
     * SHA-2 512 (aka SHA-512) digest.
     */
    public static final String DIGEST_SHA512 = "SHA-512";

    /**
     * @hide
     */
    public static abstract class Digest {
        private Digest() {}

        public static int toKeymaster(@NonNull @DigestEnum String digest) {
            switch (digest.toUpperCase(Locale.US)) {
                case DIGEST_SHA1:
                    return KeymasterDefs.KM_DIGEST_SHA1;
                case DIGEST_SHA224:
                    return KeymasterDefs.KM_DIGEST_SHA_2_224;
                case DIGEST_SHA256:
                    return KeymasterDefs.KM_DIGEST_SHA_2_256;
                case DIGEST_SHA384:
                    return KeymasterDefs.KM_DIGEST_SHA_2_384;
                case DIGEST_SHA512:
                    return KeymasterDefs.KM_DIGEST_SHA_2_512;
                case DIGEST_NONE:
                    return KeymasterDefs.KM_DIGEST_NONE;
                case DIGEST_MD5:
                    return KeymasterDefs.KM_DIGEST_MD5;
                default:
                    throw new IllegalArgumentException("Unsupported digest algorithm: " + digest);
            }
        }

        @NonNull
        public static @DigestEnum String fromKeymaster(int digest) {
            switch (digest) {
                case KeymasterDefs.KM_DIGEST_NONE:
                    return DIGEST_NONE;
                case KeymasterDefs.KM_DIGEST_MD5:
                    return DIGEST_MD5;
                case KeymasterDefs.KM_DIGEST_SHA1:
                    return DIGEST_SHA1;
                case KeymasterDefs.KM_DIGEST_SHA_2_224:
                    return DIGEST_SHA224;
                case KeymasterDefs.KM_DIGEST_SHA_2_256:
                    return DIGEST_SHA256;
                case KeymasterDefs.KM_DIGEST_SHA_2_384:
                    return DIGEST_SHA384;
                case KeymasterDefs.KM_DIGEST_SHA_2_512:
                    return DIGEST_SHA512;
                default:
                    throw new IllegalArgumentException("Unsupported digest algorithm: " + digest);
            }
        }

        @NonNull
        public static @DigestEnum String[] allFromKeymaster(@NonNull Collection<Integer> digests) {
            if (digests.isEmpty()) {
                return EmptyArray.STRING;
            }
            String[] result = new String[digests.size()];
            int offset = 0;
            for (int digest : digests) {
                result[offset] = fromKeymaster(digest);
                offset++;
            }
            return result;
        }

        @NonNull
        public static int[] allToKeymaster(@Nullable @DigestEnum String[] digests) {
            if ((digests == null) || (digests.length == 0)) {
                return EmptyArray.INT;
            }
            int[] result = new int[digests.length];
            int offset = 0;
            for (@DigestEnum String digest : digests) {
                result[offset] = toKeymaster(digest);
                offset++;
            }
            return result;
        }
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        ORIGIN_GENERATED,
        ORIGIN_IMPORTED,
        ORIGIN_UNKNOWN,
        })
    public @interface OriginEnum {}

    /** Key was generated inside AndroidKeyStore. */
    public static final int ORIGIN_GENERATED = 1 << 0;

    /** Key was imported into AndroidKeyStore. */
    public static final int ORIGIN_IMPORTED = 1 << 1;

    /**
     * Origin of the key is unknown. This can occur only for keys backed by an old TEE-backed
     * implementation which does not record origin information.
     */
    public static final int ORIGIN_UNKNOWN = 1 << 2;

    /**
     * @hide
     */
    public static abstract class Origin {
        private Origin() {}

        public static @OriginEnum int fromKeymaster(int origin) {
            switch (origin) {
                case KeymasterDefs.KM_ORIGIN_GENERATED:
                    return ORIGIN_GENERATED;
                case KeymasterDefs.KM_ORIGIN_IMPORTED:
                    return ORIGIN_IMPORTED;
                case KeymasterDefs.KM_ORIGIN_UNKNOWN:
                    return ORIGIN_UNKNOWN;
                default:
                    throw new IllegalArgumentException("Unknown origin: " + origin);
            }
        }
    }

    private static int[] getSetFlags(int flags) {
        if (flags == 0) {
            return EmptyArray.INT;
        }
        int result[] = new int[getSetBitCount(flags)];
        int resultOffset = 0;
        int flag = 1;
        while (flags != 0) {
            if ((flags & 1) != 0) {
                result[resultOffset] = flag;
                resultOffset++;
            }
            flags >>>= 1;
            flag <<= 1;
        }
        return result;
    }

    private static int getSetBitCount(int value) {
        if (value == 0) {
            return 0;
        }
        int result = 0;
        while (value != 0) {
            if ((value & 1) != 0) {
                result++;
            }
            value >>>= 1;
        }
        return result;
    }
}
