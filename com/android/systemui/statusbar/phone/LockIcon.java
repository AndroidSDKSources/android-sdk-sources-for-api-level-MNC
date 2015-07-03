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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.policy.AccessibilityController;

/**
 * Manages the different states and animations of the unlock icon.
 */
public class LockIcon extends KeyguardAffordanceView {


    private static final int STATE_LOCKED = 0;
    private static final int STATE_LOCK_OPEN = 1;
    private static final int STATE_FACE_UNLOCK = 2;
    private static final int STATE_FINGERPRINT = 3;
    private static final int STATE_FINGERPRINT_ERROR = 4;

    private int mLastState = 0;
    private boolean mTransientFpError;
    private final TrustDrawable mTrustDrawable;
    private final UnlockMethodCache mUnlockMethodCache;
    private AccessibilityController mAccessibilityController;

    public LockIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTrustDrawable = new TrustDrawable(context);
        setBackground(mTrustDrawable);
        mUnlockMethodCache = UnlockMethodCache.getInstance(context);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isShown()) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTrustDrawable.stop();
    }

    public void setTransientFpError(boolean transientFpError) {
        mTransientFpError = transientFpError;
        update();
    }

    public void update() {
        boolean visible = isShown() && KeyguardUpdateMonitor.getInstance(mContext).isScreenOn();
        if (visible) {
            mTrustDrawable.start();
        } else {
            mTrustDrawable.stop();
        }
        if (!visible) {
            return;
        }
        // TODO: Real icon for facelock.
        int state = getState();
        boolean anyFingerprintIcon = state == STATE_FINGERPRINT || state == STATE_FINGERPRINT_ERROR;
        if (state != mLastState) {
            int iconRes = getAnimationResForTransition(mLastState, state);
            if (iconRes == -1) {
                iconRes = getIconForState(state);
            }
            Drawable icon = mContext.getDrawable(iconRes);
            AnimatedVectorDrawable animation = null;
            if (icon instanceof AnimatedVectorDrawable) {
                animation = (AnimatedVectorDrawable) icon;
            }
            int iconHeight = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_height);
            int iconWidth = getResources().getDimensionPixelSize(
                    R.dimen.keyguard_affordance_icon_width);
            if (!anyFingerprintIcon && (icon.getIntrinsicHeight() != iconHeight
                    || icon.getIntrinsicWidth() != iconWidth)) {
                icon = new IntrinsicSizeDrawable(icon, iconWidth, iconHeight);
            }
            setPaddingRelative(0, 0, 0, anyFingerprintIcon
                    ? getResources().getDimensionPixelSize(
                    R.dimen.fingerprint_icon_additional_padding)
                    : 0);
            setRestingAlpha(
                    anyFingerprintIcon ? 1f : KeyguardAffordanceHelper.SWIPE_RESTING_ALPHA_AMOUNT);
            setImageDrawable(icon);
            if (animation != null) {
                animation.start();
            }
        }

        // Hide trust circle when fingerprint is running.
        boolean trustManaged = mUnlockMethodCache.isTrustManaged() && !anyFingerprintIcon;
        mTrustDrawable.setTrustManaged(trustManaged);
        mLastState = state;
        updateClickability();
    }

    private void updateClickability() {
        if (mAccessibilityController == null) {
            return;
        }
        boolean clickToUnlock = mAccessibilityController.isTouchExplorationEnabled();
        boolean clickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !mAccessibilityController.isAccessibilityEnabled();
        boolean longClickToForceLock = mUnlockMethodCache.isTrustManaged()
                && !clickToForceLock;
        setClickable(clickToForceLock || clickToUnlock);
        setLongClickable(longClickToForceLock);
        setFocusable(mAccessibilityController.isAccessibilityEnabled());
    }

    public void setAccessibilityController(AccessibilityController accessibilityController) {
        mAccessibilityController = accessibilityController;
    }

    private int getIconForState(int state) {
        switch (state) {
            case STATE_LOCKED:
                return R.drawable.ic_lock_24dp;
            case STATE_LOCK_OPEN:
                return R.drawable.ic_lock_open_24dp;
            case STATE_FACE_UNLOCK:
                return com.android.internal.R.drawable.ic_account_circle;
            case STATE_FINGERPRINT:
                return R.drawable.ic_fingerprint;
            case STATE_FINGERPRINT_ERROR:
                return R.drawable.ic_fingerprint_error;
            default:
                throw new IllegalArgumentException();
        }
    }

    private int getAnimationResForTransition(int oldState, int newState) {
        if (oldState == STATE_FINGERPRINT && newState == STATE_FINGERPRINT_ERROR) {
            return R.drawable.lockscreen_fingerprint_error_state_animation;
        } else {
            return -1;
        }
    }

    private int getState() {
        boolean fingerprintRunning =
                KeyguardUpdateMonitor.getInstance(mContext).isFingerprintDetectionRunning();
        if (mTransientFpError) {
            return STATE_FINGERPRINT_ERROR;
        } else if (fingerprintRunning) {
            return STATE_FINGERPRINT;
        } else if (mUnlockMethodCache.isFaceUnlockRunning()) {
            return STATE_FACE_UNLOCK;
        } else if (mUnlockMethodCache.isCurrentlyInsecure()) {
            return STATE_LOCK_OPEN;
        } else {
            return STATE_LOCKED;
        }
    }

    /**
     * A wrapper around another Drawable that overrides the intrinsic size.
     */
    private static class IntrinsicSizeDrawable extends InsetDrawable {

        private final int mIntrinsicWidth;
        private final int mIntrinsicHeight;

        public IntrinsicSizeDrawable(Drawable drawable, int intrinsicWidth, int intrinsicHeight) {
            super(drawable, 0);
            mIntrinsicWidth = intrinsicWidth;
            mIntrinsicHeight = intrinsicHeight;
        }

        @Override
        public int getIntrinsicWidth() {
            return mIntrinsicWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mIntrinsicHeight;
        }
    }
}
