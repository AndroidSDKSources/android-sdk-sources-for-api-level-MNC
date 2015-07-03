/**
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.fingerprint;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.hardware.fingerprint.FingerprintManager.EnrollmentCallback;
import android.security.AndroidKeyStoreProvider;
import android.util.Log;
import android.util.Slog;

import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

/**
 * A class that coordinates access to the fingerprint hardware.
 * <p>
 * Use {@link android.content.Context#getSystemService(java.lang.String)}
 * with argument {@link android.content.Context#FINGERPRINT_SERVICE} to get
 * an instance of this class.
 */

public class FingerprintManager {
    private static final String TAG = "FingerprintManager";
    private static final boolean DEBUG = true;
    private static final int MSG_ENROLL_RESULT = 100;
    private static final int MSG_ACQUIRED = 101;
    private static final int MSG_AUTHENTICATED = 102;
    private static final int MSG_ERROR = 103;
    private static final int MSG_REMOVED = 104;

    //
    // Error messages from fingerprint hardware during initilization, enrollment, authentication or
    // removal. Must agree with the list in fingerprint.h
    //

    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FINGERPRINT_ERROR_HW_UNAVAILABLE = 1;

    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int FINGERPRINT_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the fingerprint sensor indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int FINGERPRINT_ERROR_TIMEOUT = 3;

    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */
    public static final int FINGERPRINT_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the fingerprint sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FINGERPRINT_ERROR_CANCELED = 5;

    /**
     * The {@link FingerprintManager#remove(Fingerprint, RemovalCallback)} call failed. Typically
     * this will happen when the provided fingerprint id was incorrect.
     *
     * @hide
     */
    public static final int FINGERPRINT_ERROR_UNABLE_TO_REMOVE = 6;

   /**
     * The operation was canceled because the API is locked out due to too many attempts.
     */
    public static final int FINGERPRINT_ERROR_LOCKOUT = 7;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     */
    public static final int FINGERPRINT_ERROR_VENDOR_BASE = 1000;

    //
    // Image acquisition messages. Must agree with those in fingerprint.h
    //

    /**
     * The image acquired was good.
     */
    public static final int FINGERPRINT_ACQUIRED_GOOD = 0;

    /**
     * Only a partial fingerprint image was detected. During enrollment, the user should be
     * informed on what needs to happen to resolve this problem, e.g. "press firmly on sensor."
     */
    public static final int FINGERPRINT_ACQUIRED_PARTIAL = 1;

    /**
     * The fingerprint image was too noisy to process due to a detected condition (i.e. dry skin) or
     * a possibly dirty sensor (See {@link #FINGERPRINT_ACQUIRED_IMAGER_DIRTY}).
     */
    public static final int FINGERPRINT_ACQUIRED_INSUFFICIENT = 2;

    /**
     * The fingerprint image was too noisy due to suspected or detected dirt on the sensor.
     * For example, it's reasonable return this after multiple
     * {@link #FINGERPRINT_ACQUIRED_INSUFFICIENT} or actual detection of dirt on the sensor
     * (stuck pixels, swaths, etc.). The user is expected to take action to clean the sensor
     * when this is returned.
     */
    public static final int FINGERPRINT_ACQUIRED_IMAGER_DIRTY = 3;

    /**
     * The fingerprint image was unreadable due to lack of motion. This is most appropriate for
     * linear array sensors that require a swipe motion.
     */
    public static final int FINGERPRINT_ACQUIRED_TOO_SLOW = 4;

    /**
     * The fingerprint image was incomplete due to quick motion. While mostly appropriate for
     * linear array sensors,  this could also happen if the finger was moved during acquisition.
     * The user should be asked to move the finger slower (linear) or leave the finger on the sensor
     * longer.
     */
    public static final int FINGERPRINT_ACQUIRED_TOO_FAST = 5;

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     */
    public static final int FINGERPRINT_ACQUIRED_VENDOR_BASE = 1000;

    private IFingerprintService mService;
    private Context mContext;
    private IBinder mToken = new Binder();
    private AuthenticationCallback mAuthenticationCallback;
    private EnrollmentCallback mEnrollmentCallback;
    private RemovalCallback mRemovalCallback;
    private CryptoObject mCryptoObject;
    private Fingerprint mRemovalFingerprint;

    private class OnEnrollCancelListener implements OnCancelListener {
        @Override
        public void onCancel() {
            cancelEnrollment();
        }
    }

    private class OnAuthenticationCancelListener implements OnCancelListener {
        private CryptoObject mCrypto;

        public OnAuthenticationCancelListener(CryptoObject crypto) {
            mCrypto = crypto;
        }

        @Override
        public void onCancel() {
            cancelAuthentication(mCrypto);
        }
    }

    /**
     * A wrapper class for the crypto objects supported by FingerprintManager. Currently the
     * framework supports {@link Signature}, {@link Cipher} and {@link Mac} objects.
     */
    public static class CryptoObject {

        public CryptoObject(@NonNull Signature signature) {
            mSignature = signature;
            mCipher = null;
            mMac = null;
        }

        public CryptoObject(@NonNull Cipher cipher) {
            mCipher = cipher;
            mSignature = null;
            mMac = null;
        }

        public CryptoObject(@NonNull Mac mac) {
            mMac = mac;
            mCipher = null;
            mSignature = null;
        }

        /**
         * Get {@link Signature} object.
         * @return {@link Signature} object or null if this doesn't contain one.
         */
        public Signature getSignature() { return mSignature; }

        /**
         * Get {@link Cipher} object.
         * @return {@link Cipher} object or null if this doesn't contain one.
         */
        public Cipher getCipher() { return mCipher; }

        /**
         * Get {@link Mac} object.
         * @return {@link Mac} object or null if this doesn't contain one.
         */
        public Mac getMac() { return mMac; }

        /**
         * @hide
         * @return the opId associated with this object or 0 if none
         */
        public long getOpId() {
            if (mSignature != null) {
                return AndroidKeyStoreProvider.getKeyStoreOperationHandle(mSignature);
            } else if (mCipher != null) {
                return AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCipher);
            } else if (mMac != null) {
                return AndroidKeyStoreProvider.getKeyStoreOperationHandle(mMac);
            }
            return 0;
        }

        private final Signature mSignature;
        private final Cipher mCipher;
        private final Mac mMac;
    };

    /**
     * Container for callback data from {@link FingerprintManager#authenticate(CryptoObject,
     *     CancellationSignal, AuthenticationCallback, int)}.
     */
    public static final class AuthenticationResult {
        private Fingerprint mFingerprint;
        private CryptoObject mCryptoObject;

        public AuthenticationResult(CryptoObject crypto, Fingerprint fingerprint) {
            mCryptoObject = crypto;
            mFingerprint = fingerprint;
        }

        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link FingerprintManager#authenticate(CryptoObject,
         *     CancellationSignal, AuthenticationCallback, int)}.
         */
        public CryptoObject getCryptoObject() { return mCryptoObject; }

        /**
         * Obtain the Fingerprint associated with this operation. Applications are strongly
         * discouraged from associating specific fingers with specific applications or operations.
         *
         * @hide
         */
        public Fingerprint getFingerprint() { return mFingerprint; }
    };

    /**
     * Callback structure provided to {@link FingerprintManager#authenticate(CryptoObject,
     * CancellationSignal, AuthenticationCallback, int)}. Users of {@link
     * FingerprintManager#authenticate(CryptoObject, CancellationSignal,
     * AuthenticationCallback, int) } must provide an implementation of this for listening to
     * fingerprint events.
     */
    public static abstract class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errMsgId An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onAuthenticationError(int errMsgId, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it."
         * @param helpMsgId An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) { }

        /**
         * Called when a fingerprint is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) { }

        /**
         * Called when a fingerprint is valid but not recognized.
         */
        public void onAuthenticationFailed() { }
    };

    /**
     * Callback structure provided to {@link FingerprintManager#enroll(long, EnrollmentCallback,
     * CancellationSignal, int). Users of {@link #FingerprintManager()}
     * must provide an implementation of this to {@link FingerprintManager#enroll(long,
     * CancellationSignal, EnrollmentCallback, int) for listening to fingerprint events.
     *
     * @hide
     */
    public static abstract class EnrollmentCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errMsgId An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onEnrollmentError(int errMsgId, CharSequence errString) { }

        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it" or what they need to do next, such as
         * "Touch sensor again."
         * @param helpMsgId An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) { }

        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * remaining reaches 0. This function will not be called if enrollment fails. See
         * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)}
         * @param remaining The number of remaining steps
         */
        public void onEnrollmentProgress(int remaining) { }
    };

    /**
     * Callback structure provided to {@link FingerprintManager#remove(int). Users of
     * {@link #FingerprintManager()} may optionally provide an implementation of this to
     * {@link FingerprintManager#remove(int, int, RemovalCallback)} for listening to
     * fingerprint template removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {
        /**
         * Called when the given fingerprint can't be removed.
         * @param fp The fingerprint that the call attempted to remove
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the fingerprint id can't be removed
         */
        public void onRemovalError(Fingerprint fp, int errMsgId, CharSequence errString) { }

        /**
         * Called when a given fingerprint is successfully removed.
         * @param fingerprint the fingerprint template that was removed.
         */
        public void onRemovalSucceeded(Fingerprint fingerprint) { }
    };

    /**
     * Request authentication of a crypto object. This call warms up the fingerprint hardware
     * and starts scanning for a fingerprint. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult) is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto object associated with the call or null if none required.
     * @param cancel an object that can be used to cancel authentication
     * @param callback an object to receive authentication events
     * @param flags optional flags; should be 0
     */
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            @NonNull AuthenticationCallback callback, int flags) {
        authenticate(crypto, cancel, callback, flags, UserHandle.myUserId());
    }

    /**
     * Request authentication of a crypto object. This call warms up the fingerprint hardware
     * and starts scanning for a fingerprint. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult) is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto object associated with the call or null if none required.
     * @param cancel an object that can be used to cancel authentication
     * @param callback an object to receive authentication events
     * @param flags optional flags; should be 0
     * @param userId the userId the fingerprint belongs to
     * @hide
     */
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            @NonNull AuthenticationCallback callback, int flags, int userId) {
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an authentication callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "authentication already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnAuthenticationCancelListener(crypto));
            }
        }

        if (mService != null) try {
            mAuthenticationCallback = callback;
            mCryptoObject = crypto;
            long sessionId = crypto != null ? crypto.getOpId() : 0;
            mService.authenticate(mToken, sessionId, userId, mServiceReceiver, flags,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception while authenticating: ", e);
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onAuthenticationError(FINGERPRINT_ERROR_HW_UNAVAILABLE,
                        getErrorString(FINGERPRINT_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Request fingerprint enrollment. This call warms up the fingerprint hardware
     * and starts scanning for fingerprints. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with remaining == 0, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     * @param token a unique token provided by a recent creation or verification of device
     * credentials (e.g. pin, pattern or password).
     * @param cancel an object that can be used to cancel enrollment
     * @param callback an object to receive enrollment events
     * @param flags optional flags
     * @hide
     */
    public void enroll(byte [] token, CancellationSignal cancel, EnrollmentCallback callback,
            int flags) {
        if (callback == null) {
            throw new IllegalArgumentException("Must supply an enrollment callback");
        }

        if (cancel != null) {
            if (cancel.isCanceled()) {
                Log.w(TAG, "enrollment already canceled");
                return;
            } else {
                cancel.setOnCancelListener(new OnEnrollCancelListener());
            }
        }

        if (mService != null) try {
            mEnrollmentCallback = callback;
            mService.enroll(mToken, token, getCurrentUserId(), mServiceReceiver, flags);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in enroll: ", e);
            if (callback != null) {
                // Though this may not be a hardware issue, it will cause apps to give up or try
                // again later.
                callback.onEnrollmentError(FINGERPRINT_ERROR_HW_UNAVAILABLE,
                        getErrorString(FINGERPRINT_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     * @hide
     */
    public long preEnroll() {
        long result = 0;
        if (mService != null) try {
            result = mService.preEnroll(mToken);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in enroll: ", e);
        }
        return result;
    }

    /**
     * Remove given fingerprint template from fingerprint hardware and/or protected storage.
     * @param fp the fingerprint item to remove
     * @param callback an optional callback to verify that fingerprint templates have been
     * successfully removed. May be null of no callback is required.
     *
     * @hide
     */
    public void remove(Fingerprint fp, RemovalCallback callback) {
        if (mService != null) try {
            mRemovalCallback = callback;
            mRemovalFingerprint = fp;
            mService.remove(mToken, fp.getFingerId(), getCurrentUserId(), mServiceReceiver);
        } catch (RemoteException e) {
            Log.w(TAG, "Remote exception in remove: ", e);
            if (callback != null) {
                callback.onRemovalError(fp, FINGERPRINT_ERROR_HW_UNAVAILABLE,
                        getErrorString(FINGERPRINT_ERROR_HW_UNAVAILABLE));
            }
        }
    }

    /**
     * Renames the given fingerprint template
     * @param fpId the fingerprint id
     * @param newName the new name
     *
     * @hide
     */
    public void rename(int fpId, String newName) {
        // Renames the given fpId
        if (mService != null) {
            try {
                mService.rename(fpId, getCurrentUserId(), newName);
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in rename(): ", e);
            }
        } else {
            Log.w(TAG, "rename(): Service not connected!");
        }
    }

    /**
     * Obtain the list of enrolled fingerprints templates.
     * @return list of current fingerprint items
     *
     * @hide
     */
    public List<Fingerprint> getEnrolledFingerprints(int userId) {
        if (mService != null) try {
            return mService.getEnrolledFingerprints(userId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.v(TAG, "Remote exception in getEnrolledFingerprints: ", e);
        }
        return null;
    }

    /**
     * Obtain the list of enrolled fingerprints templates.
     * @return list of current fingerprint items
     *
     * @hide
     */
    public List<Fingerprint> getEnrolledFingerprints() {
        return getEnrolledFingerprints(UserHandle.myUserId());
    }

    /**
     * Determine if there is at least one fingerprint enrolled.
     *
     * @return true if at least one fingerprint is enrolled, false otherwise
     */
    public boolean hasEnrolledFingerprints() {
        if (mService != null) try {
            return mService.hasEnrolledFingerprints(UserHandle.myUserId(),
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            Log.v(TAG, "Remote exception in getEnrolledFingerprints: ", e);
        }
        return false;
    }

    /**
     * Determine if fingerprint hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    public boolean isHardwareDetected() {
        if (mService != null) {
            try {
                long deviceId = 0; /* TODO: plumb hardware id to FPMS */
                return mService.isHardwareDetected(deviceId, mContext.getOpPackageName());
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in isFingerprintHardwareDetected(): ", e);
            }
        } else {
            Log.w(TAG, "isFingerprintHardwareDetected(): Service not connected!");
        }
        return false;
    }

    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the current set of fingerprints. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() {
        if (mService != null) {
            try {
                return mService.getAuthenticatorId(mContext.getOpPackageName());
            } catch (RemoteException e) {
                Log.v(TAG, "Remote exception in getAuthenticatorId(): ", e);
            }
        } else {
            Log.w(TAG, "getAuthenticatorId(): Service not connected!");
        }
        return 0;
    }

    private Handler mHandler;

    private class MyHandler extends Handler {
        private MyHandler(Context context) {
            super(context.getMainLooper());
        }

        public void handleMessage(android.os.Message msg) {
            switch(msg.what) {
                case MSG_ENROLL_RESULT:
                    sendEnrollResult((Fingerprint) msg.obj, msg.arg1 /* remaining */);
                    break;
                case MSG_ACQUIRED:
                    sendAcquiredResult((Long) msg.obj /* deviceId */, msg.arg1 /* acquire info */);
                    break;
                case MSG_AUTHENTICATED:
                    sendAuthenticatedResult((Fingerprint) msg.obj);
                    break;
                case MSG_ERROR:
                    sendErrorResult((Long) msg.obj /* deviceId */, msg.arg1 /* errMsgId */);
                    break;
                case MSG_REMOVED:
                    sendRemovedResult((Long) msg.obj /* deviceId */, msg.arg1 /* fingerId */,
                            msg.arg2 /* groupId */);
            }
        }

        private void sendRemovedResult(long deviceId, int fingerId, int groupId) {
            if (mRemovalCallback != null) {
                int reqFingerId = mRemovalFingerprint.getFingerId();
                int reqGroupId = mRemovalFingerprint.getGroupId();
                if (fingerId != reqFingerId) {
                    Log.w(TAG, "Finger id didn't match: " + fingerId + " != " + reqFingerId);
                }
                if (fingerId != reqFingerId) {
                    Log.w(TAG, "Group id didn't match: " + groupId + " != " + reqGroupId);
                }
                mRemovalCallback.onRemovalSucceeded(mRemovalFingerprint);
            }
        }

        private void sendErrorResult(long deviceId, int errMsgId) {
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentError(errMsgId, getErrorString(errMsgId));
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationError(errMsgId, getErrorString(errMsgId));
            } else if (mRemovalCallback != null) {
                mRemovalCallback.onRemovalError(mRemovalFingerprint, errMsgId,
                        getErrorString(errMsgId));
            }
        }

        private void sendEnrollResult(Fingerprint fp, int remaining) {
            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentProgress(remaining);
            }
        }

        private void sendAuthenticatedResult(Fingerprint fp) {
            if (mAuthenticationCallback != null) {
                if (fp.getFingerId() == 0) {
                    // Fingerprint template valid but doesn't match one in database
                    mAuthenticationCallback.onAuthenticationFailed();
                } else {
                    final AuthenticationResult result = new AuthenticationResult(mCryptoObject, fp);
                    mAuthenticationCallback.onAuthenticationSucceeded(result);
                }
            }
        }

        private void sendAcquiredResult(long deviceId, int acquireInfo) {
            final String msg = getAcquiredString(acquireInfo);
            if (msg == null) return;

            if (mEnrollmentCallback != null) {
                mEnrollmentCallback.onEnrollmentHelp(acquireInfo, msg);
            } else if (mAuthenticationCallback != null) {
                mAuthenticationCallback.onAuthenticationHelp(acquireInfo, msg);
            }
        }
    };

    /**
     * @hide
     */
    public FingerprintManager(Context context, IFingerprintService service) {
        mContext = context;
        mService = service;
        if (mService == null) {
            Slog.v(TAG, "FingerprintManagerService was null");
        }
        mHandler = new MyHandler(context);
    }

    private int getCurrentUserId() {
        try {
            return ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get current user id\n");
            return UserHandle.USER_NULL;
        }
    }

    private void clearCallbacks() {
        mAuthenticationCallback = null;
        mEnrollmentCallback = null;
        mRemovalCallback = null;
    }

    private void cancelEnrollment() {
        if (mService != null) try {
            mService.cancelEnrollment(mToken);
        } catch (RemoteException e) {
            if (DEBUG) Log.w(TAG, "Remote exception while canceling enrollment");
        }
    }

    private void cancelAuthentication(CryptoObject cryptoObject) {
        if (mService != null) try {
            mService.cancelAuthentication(mToken, mContext.getOpPackageName());
        } catch (RemoteException e) {
            if (DEBUG) Log.w(TAG, "Remote exception while canceling enrollment");
        }
    }

    private String getErrorString(int errMsg) {
        switch (errMsg) {
            case FINGERPRINT_ERROR_UNABLE_TO_PROCESS:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_error_unable_to_process);
            case FINGERPRINT_ERROR_HW_UNAVAILABLE:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_error_hw_not_available);
            case FINGERPRINT_ERROR_NO_SPACE:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_error_no_space);
            case FINGERPRINT_ERROR_TIMEOUT:
                return mContext.getString(com.android.internal.R.string.fingerprint_error_timeout);
            case FINGERPRINT_ERROR_CANCELED:
                return mContext.getString(com.android.internal.R.string.fingerprint_error_canceled);
            case FINGERPRINT_ERROR_LOCKOUT:
                return mContext.getString(com.android.internal.R.string.fingerprint_error_lockout);
            default:
                if (errMsg >= FINGERPRINT_ERROR_VENDOR_BASE) {
                    int msgNumber = errMsg - FINGERPRINT_ERROR_VENDOR_BASE;
                    String[] msgArray = mContext.getResources().getStringArray(
                            com.android.internal.R.array.fingerprint_error_vendor);
                    if (msgNumber < msgArray.length) {
                        return msgArray[msgNumber];
                    }
                }
                return null;
        }
    }

    private String getAcquiredString(int acquireInfo) {
        switch (acquireInfo) {
            case FINGERPRINT_ACQUIRED_GOOD:
                return null;
            case FINGERPRINT_ACQUIRED_PARTIAL:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_acquired_partial);
            case FINGERPRINT_ACQUIRED_INSUFFICIENT:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_acquired_insufficient);
            case FINGERPRINT_ACQUIRED_IMAGER_DIRTY:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_acquired_imager_dirty);
            case FINGERPRINT_ACQUIRED_TOO_SLOW:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_acquired_too_slow);
            case FINGERPRINT_ACQUIRED_TOO_FAST:
                return mContext.getString(
                    com.android.internal.R.string.fingerprint_acquired_too_fast);
            default:
                if (acquireInfo >= FINGERPRINT_ACQUIRED_VENDOR_BASE) {
                    int msgNumber = acquireInfo - FINGERPRINT_ACQUIRED_VENDOR_BASE;
                    String[] msgArray = mContext.getResources().getStringArray(
                            com.android.internal.R.array.fingerprint_acquired_vendor);
                    if (msgNumber < msgArray.length) {
                        return msgArray[msgNumber];
                    }
                }
                return null;
        }
    }

    private IFingerprintServiceReceiver mServiceReceiver = new IFingerprintServiceReceiver.Stub() {

        public void onEnrollResult(long deviceId, int fingerId, int groupId, int remaining) {
            mHandler.obtainMessage(MSG_ENROLL_RESULT, remaining, 0,
                    new Fingerprint(null, groupId, fingerId, deviceId)).sendToTarget();
        }

        public void onAcquired(long deviceId, int acquireInfo) {
            mHandler.obtainMessage(MSG_ACQUIRED, acquireInfo, 0, deviceId).sendToTarget();
        }

        public void onAuthenticated(long deviceId, int fingerId, int groupId) {
            mHandler.obtainMessage(MSG_AUTHENTICATED,
                    new Fingerprint(null, groupId, fingerId, deviceId)).sendToTarget();
        }

        public void onError(long deviceId, int error) {
            mHandler.obtainMessage(MSG_ERROR, error, 0, deviceId).sendToTarget();
        }

        public void onRemoved(long deviceId, int fingerId, int groupId) {
            mHandler.obtainMessage(MSG_REMOVED, fingerId, groupId, deviceId).sendToTarget();
        }
    };

}
