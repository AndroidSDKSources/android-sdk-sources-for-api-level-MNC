/*
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

package android.support.v7.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.view.TintableBackgroundView;
import android.support.v7.appcompat.R;
import android.support.v7.internal.text.AllCapsTransformationMethod;
import android.support.v7.internal.widget.ThemeUtils;
import android.support.v7.internal.widget.TintInfo;
import android.support.v7.internal.widget.TintManager;
import android.support.v7.internal.widget.TintTypedArray;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/**
 * A tint aware {@link android.widget.Button}.
 * <p>
 * This will automatically be used when you use {@link android.widget.Button} in your layouts. You
 * should only need to manually use this class when writing custom views.
 */
public class AppCompatButton extends Button implements TintableBackgroundView {

    private static final int[] TINT_ATTRS = {
            android.R.attr.background
    };

    private TintInfo mInternalBackgroundTint;
    private TintInfo mBackgroundTint;
    private TintManager mTintManager;

    public AppCompatButton(Context context) {
        this(context, null);
    }

    public AppCompatButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.buttonStyle);
    }

    public AppCompatButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (TintManager.SHOULD_BE_USED) {
            TintTypedArray a = TintTypedArray.obtainStyledAttributes(getContext(), attrs,
                    TINT_ATTRS, defStyleAttr, 0);
            if (a.hasValue(0)) {
                ColorStateList tint = a.getTintManager().getTintList(a.getResourceId(0, -1));
                if (tint != null) {
                    setInternalBackgroundTint(tint);
                }
            }
            mTintManager = a.getTintManager();
            a.recycle();
        }

        // First read the TextAppearance style id
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppCompatTextView,
                defStyleAttr, 0);
        final int ap = a.getResourceId(R.styleable.AppCompatTextView_android_textAppearance, -1);
        a.recycle();

        // Now check TextAppearance's textAllCaps value
        if (ap != -1) {
            TypedArray appearance = context.obtainStyledAttributes(ap, R.styleable.TextAppearance);
            if (appearance.hasValue(R.styleable.TextAppearance_textAllCaps)) {
                setAllCaps(appearance.getBoolean(R.styleable.TextAppearance_textAllCaps, false));
            }
            appearance.recycle();
        }

        // Now read the style's value
        a = context.obtainStyledAttributes(attrs, R.styleable.AppCompatTextView, defStyleAttr, 0);
        if (a.hasValue(R.styleable.AppCompatTextView_textAllCaps)) {
            setAllCaps(a.getBoolean(R.styleable.AppCompatTextView_textAllCaps, false));
        }
        a.recycle();

        final ColorStateList textColors = getTextColors();
        if (textColors != null && !textColors.isStateful()) {
            // If we have a ColorStateList which isn't stateful, create one which includes
            // a disabled state

            final int disabledTextColor;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // Pre-Lollipop, we will use textColorSecondary with android:disabledAlpha
                // applied
                disabledTextColor = ThemeUtils.getDisabledThemeAttrColor(context,
                        android.R.attr.textColorSecondary);
            } else {
                // With certain styles on Lollipop, there is a StateListAnimator which sets
                // an alpha on the whole view, so we don't need to apply disabledAlpha to
                // textColorSecondary
                disabledTextColor = ThemeUtils.getThemeAttrColor(context,
                        android.R.attr.textColorSecondary);
            }

            setTextColor(ThemeUtils.createDisabledStateList(
                    textColors.getDefaultColor(), disabledTextColor));
        }
    }

    @Override
    public void setBackgroundResource(int resId) {
        super.setBackgroundResource(resId);
        // Update the default background tint
        setInternalBackgroundTint(mTintManager != null ? mTintManager.getTintList(resId) : null);
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        super.setBackgroundDrawable(background);
        // We don't know that this drawable is, so we need to clear the default background tint
        setInternalBackgroundTint(null);
    }

    /**
     * This should be accessed via
     * {@link android.support.v4.view.ViewCompat#setBackgroundTintList(android.view.View,
     * android.content.res.ColorStateList)}
     *
     * @hide
     */
    @Override
    public void setSupportBackgroundTintList(@Nullable ColorStateList tint) {
        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintList = tint;
        mBackgroundTint.mHasTintList = true;

        applySupportBackgroundTint();
    }

    /**
     * This should be accessed via
     * {@link android.support.v4.view.ViewCompat#getBackgroundTintList(android.view.View)}
     *
     * @hide
     */
    @Override
    @Nullable
    public ColorStateList getSupportBackgroundTintList() {
        return mBackgroundTint != null ? mBackgroundTint.mTintList : null;
    }

    /**
     * This should be accessed via
     * {@link android.support.v4.view.ViewCompat#setBackgroundTintMode(android.view.View, android.graphics.PorterDuff.Mode)}
     *
     * @hide
     */
    @Override
    public void setSupportBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintMode = tintMode;
        mBackgroundTint.mHasTintMode = true;

        applySupportBackgroundTint();
    }

    /**
     * This should be accessed via
     * {@link android.support.v4.view.ViewCompat#getBackgroundTintMode(android.view.View)}
     *
     * @hide
     */
    @Override
    @Nullable
    public PorterDuff.Mode getSupportBackgroundTintMode() {
        return mBackgroundTint != null ? mBackgroundTint.mTintMode : null;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        applySupportBackgroundTint();
    }

    private void applySupportBackgroundTint() {
        if (getBackground() != null) {
            if (mBackgroundTint != null) {
                TintManager.tintViewBackground(this, mBackgroundTint);
            } else if (mInternalBackgroundTint != null) {
                TintManager.tintViewBackground(this, mInternalBackgroundTint);
            }
        }
    }

    private void setInternalBackgroundTint(ColorStateList tint) {
        if (tint != null) {
            if (mInternalBackgroundTint == null) {
                mInternalBackgroundTint = new TintInfo();
            }
            mInternalBackgroundTint.mTintList = tint;
            mInternalBackgroundTint.mHasTintList = true;
        } else {
            mInternalBackgroundTint = null;
        }
        applySupportBackgroundTint();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(Button.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
    }

    public void setAllCaps(boolean allCaps) {
        setTransformationMethod(allCaps ? new AllCapsTransformationMethod(getContext()) : null);
    }

    @Override
    public void setTextAppearance(Context context, int resId) {
        super.setTextAppearance(context, resId);

        TypedArray appearance = context.obtainStyledAttributes(resId, R.styleable.TextAppearance);
        if (appearance.hasValue(R.styleable.TextAppearance_textAllCaps)) {
            setAllCaps(appearance.getBoolean(R.styleable.TextAppearance_textAllCaps, false));
        }
        appearance.recycle();
    }

}
