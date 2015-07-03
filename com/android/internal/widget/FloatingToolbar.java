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

package com.android.internal.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.android.internal.R;
import com.android.internal.util.Preconditions;

/**
 * A floating toolbar for showing contextual menu items.
 * This view shows as many menu item buttons as can fit in the horizontal toolbar and the
 * the remaining menu items in a vertical overflow view when the overflow button is clicked.
 * The horizontal toolbar morphs into the vertical overflow view.
 */
public final class FloatingToolbar {

    // This class is responsible for the public API of the floating toolbar.
    // It delegates rendering operations to the FloatingToolbarPopup.

    private static final MenuItem.OnMenuItemClickListener NO_OP_MENUITEM_CLICK_LISTENER =
            new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return false;
                }
            };

    private final Context mContext;
    private final FloatingToolbarPopup mPopup;

    private final Rect mContentRect = new Rect();
    private final Point mCoordinates = new Point();

    private Menu mMenu;
    private List<CharSequence> mShowingTitles = new ArrayList<CharSequence>();
    private MenuItem.OnMenuItemClickListener mMenuItemClickListener = NO_OP_MENUITEM_CLICK_LISTENER;

    private int mSuggestedWidth;
    private boolean mWidthChanged = true;
    private int mOverflowDirection;

    /**
     * Initializes a floating toolbar.
     */
    public FloatingToolbar(Context context, Window window) {
        mContext = Preconditions.checkNotNull(context);
        mPopup = new FloatingToolbarPopup(window.getDecorView());
    }

    /**
     * Sets the menu to be shown in this floating toolbar.
     * NOTE: Call {@link #updateLayout()} or {@link #show()} to effect visual changes to the
     * toolbar.
     */
    public FloatingToolbar setMenu(Menu menu) {
        mMenu = Preconditions.checkNotNull(menu);
        return this;
    }

    /**
     * Sets the custom listener for invocation of menu items in this floating toolbar.
     */
    public FloatingToolbar setOnMenuItemClickListener(
            MenuItem.OnMenuItemClickListener menuItemClickListener) {
        if (menuItemClickListener != null) {
            mMenuItemClickListener = menuItemClickListener;
        } else {
            mMenuItemClickListener = NO_OP_MENUITEM_CLICK_LISTENER;
        }
        return this;
    }

    /**
     * Sets the content rectangle. This is the area of the interesting content that this toolbar
     * should avoid obstructing.
     * NOTE: Call {@link #updateLayout()} or {@link #show()} to effect visual changes to the
     * toolbar.
     */
    public FloatingToolbar setContentRect(Rect rect) {
        mContentRect.set(Preconditions.checkNotNull(rect));
        return this;
    }

    /**
     * Sets the suggested width of this floating toolbar.
     * The actual width will be about this size but there are no guarantees that it will be exactly
     * the suggested width.
     * NOTE: Call {@link #updateLayout()} or {@link #show()} to effect visual changes to the
     * toolbar.
     */
    public FloatingToolbar setSuggestedWidth(int suggestedWidth) {
        // Check if there's been a substantial width spec change.
        int difference = Math.abs(suggestedWidth - mSuggestedWidth);
        mWidthChanged = difference > (mSuggestedWidth * 0.2);

        mSuggestedWidth = suggestedWidth;
        return this;
    }

    /**
     * Shows this floating toolbar.
     */
    public FloatingToolbar show() {
        List<MenuItem> menuItems = getVisibleAndEnabledMenuItems(mMenu);
        if (!isCurrentlyShowing(menuItems) || mWidthChanged) {
            mPopup.dismiss();
            mPopup.layoutMenuItems(menuItems, mMenuItemClickListener, mSuggestedWidth);
            mShowingTitles = getMenuItemTitles(menuItems);
        }
        refreshCoordinates();
        mPopup.setOverflowDirection(mOverflowDirection);
        mPopup.updateCoordinates(mCoordinates.x, mCoordinates.y);
        if (!mPopup.isShowing()) {
            mPopup.show(mCoordinates.x, mCoordinates.y);
        }
        mWidthChanged = false;
        return this;
    }

    /**
     * Updates this floating toolbar to reflect recent position and view updates.
     * NOTE: This method is a no-op if the toolbar isn't showing.
     */
    public FloatingToolbar updateLayout() {
        if (mPopup.isShowing()) {
            // show() performs all the logic we need here.
            show();
        }
        return this;
    }

    /**
     * Dismisses this floating toolbar.
     */
    public void dismiss() {
        mPopup.dismiss();
    }

    /**
     * Hides this floating toolbar. This is a no-op if the toolbar is not showing.
     * Use {@link #isHidden()} to distinguish between a hidden and a dismissed toolbar.
     */
    public void hide() {
        mPopup.hide();
    }

    /**
     * Returns {@code true} if this toolbar is currently showing. {@code false} otherwise.
     */
    public boolean isShowing() {
        return mPopup.isShowing();
    }

    /**
     * Returns {@code true} if this toolbar is currently hidden. {@code false} otherwise.
     */
    public boolean isHidden() {
        return mPopup.isHidden();
    }

    /**
     * Refreshes {@link #mCoordinates} with values based on {@link #mContentRect}.
     */
    private void refreshCoordinates() {
        int x = mContentRect.centerX() - mPopup.getWidth() / 2;
        int y;
        if (mContentRect.top > mPopup.getHeight()) {
            y = mContentRect.top - mPopup.getHeight();
            mOverflowDirection = FloatingToolbarPopup.OVERFLOW_DIRECTION_UP;
        } else if (mContentRect.top > mPopup.getToolbarHeightWithVerticalMargin()) {
            y = mContentRect.top - mPopup.getToolbarHeightWithVerticalMargin();
            mOverflowDirection = FloatingToolbarPopup.OVERFLOW_DIRECTION_DOWN;
        } else {
            y = mContentRect.bottom;
            mOverflowDirection = FloatingToolbarPopup.OVERFLOW_DIRECTION_DOWN;
        }
        mCoordinates.set(x, y);
    }

    /**
     * Returns true if this floating toolbar is currently showing the specified menu items.
     */
    private boolean isCurrentlyShowing(List<MenuItem> menuItems) {
        return mShowingTitles.equals(getMenuItemTitles(menuItems));
    }

    /**
     * Returns the visible and enabled menu items in the specified menu.
     * This method is recursive.
     */
    private List<MenuItem> getVisibleAndEnabledMenuItems(Menu menu) {
        List<MenuItem> menuItems = new ArrayList<MenuItem>();
        for (int i = 0; (menu != null) && (i < menu.size()); i++) {
            MenuItem menuItem = menu.getItem(i);
            if (menuItem.isVisible() && menuItem.isEnabled()) {
                Menu subMenu = menuItem.getSubMenu();
                if (subMenu != null) {
                    menuItems.addAll(getVisibleAndEnabledMenuItems(subMenu));
                } else {
                    menuItems.add(menuItem);
                }
            }
        }
        return menuItems;
    }

    private List<CharSequence> getMenuItemTitles(List<MenuItem> menuItems) {
        List<CharSequence> titles = new ArrayList<CharSequence>();
        for (MenuItem menuItem : menuItems) {
            titles.add(menuItem.getTitle());
        }
        return titles;
    }


    /**
     * A popup window used by the floating toolbar.
     *
     * This class is responsible for the rendering/animation of the floating toolbar.
     * It can hold one of 2 panels (i.e. main panel and overflow panel) at a time.
     * It delegates specific panel functionality to the appropriate panel.
     */
    private static final class FloatingToolbarPopup {

        public static final int OVERFLOW_DIRECTION_UP = 0;
        public static final int OVERFLOW_DIRECTION_DOWN = 1;

        private final View mParent;
        private final PopupWindow mPopupWindow;
        private final ViewGroup mContentContainer;
        private final int mMarginHorizontal;
        private final int mMarginVertical;

        private final Animation.AnimationListener mOnOverflowOpened =
                new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        setOverflowPanelAsContent();
                        mOverflowPanel.fadeIn(true);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                };
        private final Animation.AnimationListener mOnOverflowClosed =
                new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        setMainPanelAsContent();
                        mMainPanel.fadeIn(true);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                };
        private final AnimatorSet mShowAnimation;
        private final AnimatorSet mDismissAnimation;
        private final AnimatorSet mHideAnimation;
        private final AnimationSet mOpenOverflowAnimation = new AnimationSet(true) {
            @Override
            public void cancel() {
                if (hasStarted() && !hasEnded()) {
                    super.cancel();
                    setOverflowPanelAsContent();
                }
            }
        };
        private final AnimationSet mCloseOverflowAnimation = new AnimationSet(true) {
            @Override
            public void cancel() {
                if (hasStarted() && !hasEnded()) {
                    super.cancel();
                    setMainPanelAsContent();
                }
            }
        };

        private final Runnable mOpenOverflow = new Runnable() {
            @Override
            public void run() {
                openOverflow();
            }
        };
        private final Runnable mCloseOverflow = new Runnable() {
            @Override
            public void run() {
                closeOverflow();
            }
        };

        private final Region mTouchableRegion = new Region();
        private final ViewTreeObserver.OnComputeInternalInsetsListener mInsetsComputer =
                new ViewTreeObserver.OnComputeInternalInsetsListener() {
                    public void onComputeInternalInsets(
                            ViewTreeObserver.InternalInsetsInfo info) {
                        info.contentInsets.setEmpty();
                        info.visibleInsets.setEmpty();
                        info.touchableRegion.set(mTouchableRegion);
                        info.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo
                                .TOUCHABLE_INSETS_REGION);
                    }
                };

        private boolean mDismissed = true; // tracks whether this popup is dismissed or dismissing.
        private boolean mHidden; // tracks whether this popup is hidden or hiding.

        private FloatingToolbarOverflowPanel mOverflowPanel;
        private FloatingToolbarMainPanel mMainPanel;
        private int mOverflowDirection;

        /**
         * Initializes a new floating toolbar popup.
         *
         * @param parent  A parent view to get the {@link android.view.View#getWindowToken()} token
         *      from.
         */
        public FloatingToolbarPopup(View parent) {
            mParent = Preconditions.checkNotNull(parent);
            mContentContainer = createContentContainer(parent.getContext());
            mPopupWindow = createPopupWindow(mContentContainer);
            mShowAnimation = createGrowFadeInFromBottom(mContentContainer);
            mDismissAnimation = createShrinkFadeOutFromBottomAnimation(
                    mContentContainer,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mPopupWindow.dismiss();
                            mContentContainer.removeAllViews();
                        }
                    });
            mHideAnimation = createShrinkFadeOutFromBottomAnimation(
                    mContentContainer,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mPopupWindow.dismiss();
                        }
                    });
            mMarginHorizontal = parent.getResources()
                    .getDimensionPixelSize(R.dimen.floating_toolbar_horizontal_margin);
            mMarginVertical = parent.getResources()
                    .getDimensionPixelSize(R.dimen.floating_toolbar_vertical_margin);
        }

        /**
         * Lays out buttons for the specified menu items.
         */
        public void layoutMenuItems(List<MenuItem> menuItems,
                MenuItem.OnMenuItemClickListener menuItemClickListener, int suggestedWidth) {
            mContentContainer.removeAllViews();
            if (mMainPanel == null) {
                mMainPanel = new FloatingToolbarMainPanel(mParent.getContext(), mOpenOverflow);
            }
            List<MenuItem> overflowMenuItems =
                    mMainPanel.layoutMenuItems(menuItems, suggestedWidth);
            mMainPanel.setOnMenuItemClickListener(menuItemClickListener);
            if (!overflowMenuItems.isEmpty()) {
                if (mOverflowPanel == null) {
                    mOverflowPanel =
                            new FloatingToolbarOverflowPanel(mParent.getContext(), mCloseOverflow);
                }
                mOverflowPanel.setMenuItems(overflowMenuItems);
                mOverflowPanel.setOnMenuItemClickListener(menuItemClickListener);
            }
            updatePopupSize();
        }

        /**
         * Shows this popup at the specified coordinates.
         * The specified coordinates may be adjusted to make sure the popup is entirely on-screen.
         */
        public void show(int x, int y) {
            if (isShowing()) {
                return;
            }

            mHidden = false;
            mDismissed = false;
            cancelDismissAndHideAnimations();
            cancelOverflowAnimations();
            // Make sure a panel is set as the content.
            if (mContentContainer.getChildCount() == 0) {
                setMainPanelAsContent();
                // If we're yet to show the popup, set the container visibility to zero.
                // The "show" animation will make this visible.
                mContentContainer.setAlpha(0);
            }
            preparePopupContent();
            mPopupWindow.showAtLocation(mParent, Gravity.NO_GRAVITY, x, y);
            setTouchableSurfaceInsetsComputer();
            runShowAnimation();
        }

        /**
         * Gets rid of this popup. If the popup isn't currently showing, this will be a no-op.
         */
        public void dismiss() {
            if (mDismissed) {
                return;
            }

            mHidden = false;
            mDismissed = true;
            mHideAnimation.cancel();
            runDismissAnimation();
            setZeroTouchableSurface();
        }

        /**
         * Hides this popup. This is a no-op if this popup is not showing.
         * Use {@link #isHidden()} to distinguish between a hidden and a dismissed popup.
         */
        public void hide() {
            if (!isShowing()) {
                return;
            }

            mHidden = true;
            runHideAnimation();
            setZeroTouchableSurface();
        }

        /**
         * Returns {@code true} if this popup is currently showing. {@code false} otherwise.
         */
        public boolean isShowing() {
            return !mDismissed && !mHidden;
        }

        /**
         * Returns {@code true} if this popup is currently hidden. {@code false} otherwise.
         */
        public boolean isHidden() {
            return mHidden;
        }

        /**
         * Updates the coordinates of this popup.
         * The specified coordinates may be adjusted to make sure the popup is entirely on-screen.
         * This is a no-op if this popup is not showing.
         */
        public void updateCoordinates(int x, int y) {
            if (!isShowing() || !mPopupWindow.isShowing()) {
                return;
            }

            cancelOverflowAnimations();
            preparePopupContent();
            mPopupWindow.update(x, y, getWidth(), getHeight());
        }

        /**
         * Sets the direction in which the overflow will open. i.e. up or down.
         *
         * @param overflowDirection Either {@link #OVERFLOW_DIRECTION_UP}
         *   or {@link #OVERFLOW_DIRECTION_DOWN}.
         */
        public void setOverflowDirection(int overflowDirection) {
            mOverflowDirection = overflowDirection;
            if (mOverflowPanel != null) {
                mOverflowPanel.setOverflowDirection(mOverflowDirection);
            }
        }

        /**
         * Returns the width of this popup.
         */
        public int getWidth() {
            return mPopupWindow.getWidth();
        }

        /**
         * Returns the height of this popup.
         */
        public int getHeight() {
            return mPopupWindow.getHeight();
        }

        /**
         * Returns the context this popup is running in.
         */
        public Context getContext() {
            return mContentContainer.getContext();
        }

        int getToolbarHeightWithVerticalMargin() {
            return getEstimatedToolbarHeight(mParent.getContext()) + mMarginVertical * 2;
        }

        /**
         * Performs the "show" animation on the floating popup.
         */
        private void runShowAnimation() {
            mShowAnimation.start();
        }

        /**
         * Performs the "dismiss" animation on the floating popup.
         */
        private void runDismissAnimation() {
            mDismissAnimation.start();
        }

        /**
         * Performs the "hide" animation on the floating popup.
         */
        private void runHideAnimation() {
            mHideAnimation.start();
        }

        private void cancelDismissAndHideAnimations() {
            mDismissAnimation.cancel();
            mHideAnimation.cancel();
        }

        private void cancelOverflowAnimations() {
            mOpenOverflowAnimation.cancel();
            mCloseOverflowAnimation.cancel();
        }

        /**
         * Opens the floating toolbar overflow.
         * This method should not be called if menu items have not been laid out with
         * {@link #layoutMenuItems(java.util.List, MenuItem.OnMenuItemClickListener, int)}.
         *
         * @throws IllegalStateException if called when menu items have not been laid out.
         */
        private void openOverflow() {
            Preconditions.checkState(mMainPanel != null);
            Preconditions.checkState(mOverflowPanel != null);

            mMainPanel.fadeOut(true);
            Size overflowPanelSize = mOverflowPanel.measure();
            final int targetWidth = overflowPanelSize.getWidth();
            final int targetHeight = overflowPanelSize.getHeight();
            final boolean morphUpwards = (mOverflowDirection == OVERFLOW_DIRECTION_UP);
            final int startWidth = mContentContainer.getWidth();
            final int startHeight = mContentContainer.getHeight();
            final float startY = mContentContainer.getY();
            final float right = mContentContainer.getX() + mContentContainer.getWidth();
            Animation widthAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
                    int deltaWidth = (int) (interpolatedTime * (targetWidth - startWidth));
                    params.width = startWidth + deltaWidth;
                    mContentContainer.setLayoutParams(params);
                    mContentContainer.setX(right - mContentContainer.getWidth());
                }
            };
            Animation heightAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
                    int deltaHeight = (int) (interpolatedTime * (targetHeight - startHeight));
                    params.height = startHeight + deltaHeight;
                    mContentContainer.setLayoutParams(params);
                    if (morphUpwards) {
                        float y = startY - (mContentContainer.getHeight() - startHeight);
                        mContentContainer.setY(y);
                    }
                }
            };
            widthAnimation.setDuration(240);
            heightAnimation.setDuration(180);
            heightAnimation.setStartOffset(60);
            mOpenOverflowAnimation.getAnimations().clear();
            mOpenOverflowAnimation.setAnimationListener(mOnOverflowOpened);
            mOpenOverflowAnimation.addAnimation(widthAnimation);
            mOpenOverflowAnimation.addAnimation(heightAnimation);
            mContentContainer.startAnimation(mOpenOverflowAnimation);
        }

        /**
         * Opens the floating toolbar overflow.
         * This method should not be called if menu items have not been laid out with
         * {@link #layoutMenuItems(java.util.List, MenuItem.OnMenuItemClickListener, int)}.
         *
         * @throws IllegalStateException if called when menu items have not been laid out.
         */
        private void closeOverflow() {
            Preconditions.checkState(mMainPanel != null);
            Preconditions.checkState(mOverflowPanel != null);

            mOverflowPanel.fadeOut(true);
            Size mainPanelSize = mMainPanel.measure();
            final int targetWidth = mainPanelSize.getWidth();
            final int targetHeight = mainPanelSize.getHeight();
            final int startWidth = mContentContainer.getWidth();
            final int startHeight = mContentContainer.getHeight();
            final float right = mContentContainer.getX() + mContentContainer.getWidth();
            final float bottom = mContentContainer.getY() + mContentContainer.getHeight();
            final boolean morphedUpwards = (mOverflowDirection == OVERFLOW_DIRECTION_UP);
            Animation widthAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
                    int deltaWidth = (int) (interpolatedTime * (targetWidth - startWidth));
                    params.width = startWidth + deltaWidth;
                    mContentContainer.setLayoutParams(params);
                    mContentContainer.setX(right - mContentContainer.getWidth());
                }
            };
            Animation heightAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
                    int deltaHeight = (int) (interpolatedTime * (targetHeight - startHeight));
                    params.height = startHeight + deltaHeight;
                    mContentContainer.setLayoutParams(params);
                    if (morphedUpwards) {
                        mContentContainer.setY(bottom - mContentContainer.getHeight());
                    }
                }
            };
            widthAnimation.setDuration(150);
            widthAnimation.setStartOffset(150);
            heightAnimation.setDuration(210);
            mCloseOverflowAnimation.getAnimations().clear();
            mCloseOverflowAnimation.setAnimationListener(mOnOverflowClosed);
            mCloseOverflowAnimation.addAnimation(widthAnimation);
            mCloseOverflowAnimation.addAnimation(heightAnimation);
            mContentContainer.startAnimation(mCloseOverflowAnimation);
        }

        /**
         * Prepares the content container for show and update calls.
         */
        private void preparePopupContent() {
            // Reset visibility.
            if (mMainPanel != null) {
                mMainPanel.fadeIn(false);
            }
            if (mOverflowPanel != null) {
                mOverflowPanel.fadeIn(false);
            }

            // Reset position.
            if (mMainPanel != null
                    && mContentContainer.getChildAt(0) == mMainPanel.getView()) {
                positionMainPanel();
            }
            if (mOverflowPanel != null
                    && mContentContainer.getChildAt(0) == mOverflowPanel.getView()) {
                positionOverflowPanel();
            }
        }

        /**
         * Sets the current content to be the main view panel.
         */
        private void setMainPanelAsContent() {
            // This should never be called if the main panel has not been initialized.
            Preconditions.checkNotNull(mMainPanel);
            mContentContainer.removeAllViews();
            Size mainPanelSize = mMainPanel.measure();
            ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
            params.width = mainPanelSize.getWidth();
            params.height = mainPanelSize.getHeight();
            mContentContainer.setLayoutParams(params);
            mContentContainer.addView(mMainPanel.getView());
            setContentAreaAsTouchableSurface();
        }

        /**
         * Sets the current content to be the overflow view panel.
         */
        private void setOverflowPanelAsContent() {
            // This should never be called if the overflow panel has not been initialized.
            Preconditions.checkNotNull(mOverflowPanel);
            mContentContainer.removeAllViews();
            Size overflowPanelSize = mOverflowPanel.measure();
            ViewGroup.LayoutParams params = mContentContainer.getLayoutParams();
            params.width = overflowPanelSize.getWidth();
            params.height = overflowPanelSize.getHeight();
            mContentContainer.setLayoutParams(params);
            mContentContainer.addView(mOverflowPanel.getView());
            setContentAreaAsTouchableSurface();
        }

        /**
         * Places the main view panel at the appropriate resting coordinates.
         */
        private void positionMainPanel() {
            Preconditions.checkNotNull(mMainPanel);
            float x = mPopupWindow.getWidth()
                    - (mMainPanel.getView().getMeasuredWidth() + mMarginHorizontal);
            mContentContainer.setX(x);

            float y = mMarginVertical;
            if  (mOverflowDirection == OVERFLOW_DIRECTION_UP) {
                y = getHeight()
                        - (mMainPanel.getView().getMeasuredHeight() + mMarginVertical);
            }
            mContentContainer.setY(y);
            setContentAreaAsTouchableSurface();
        }

        /**
         * Places the main view panel at the appropriate resting coordinates.
         */
        private void positionOverflowPanel() {
            Preconditions.checkNotNull(mOverflowPanel);
            float x = mPopupWindow.getWidth()
                    - (mOverflowPanel.getView().getMeasuredWidth() + mMarginHorizontal);
            mContentContainer.setX(x);
            mContentContainer.setY(mMarginVertical);
            setContentAreaAsTouchableSurface();
        }

        private void updatePopupSize() {
            int width = 0;
            int height = 0;
            if (mMainPanel != null) {
                Size mainPanelSize = mMainPanel.measure();
                width = mainPanelSize.getWidth();
                height = mainPanelSize.getHeight();
            }
            if (mOverflowPanel != null) {
                Size overflowPanelSize = mOverflowPanel.measure();
                width = Math.max(width, overflowPanelSize.getWidth());
                height = Math.max(height, overflowPanelSize.getHeight());
            }
            mPopupWindow.setWidth(width + mMarginHorizontal * 2);
            mPopupWindow.setHeight(height + mMarginVertical * 2);
        }

        /**
         * Sets the touchable region of this popup to be zero. This means that all touch events on
         * this popup will go through to the surface behind it.
         */
        private void setZeroTouchableSurface() {
            mTouchableRegion.setEmpty();
        }

        /**
         * Sets the touchable region of this popup to be the area occupied by its content.
         */
        private void setContentAreaAsTouchableSurface() {
            if (!mPopupWindow.isShowing()) {
                mContentContainer.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            }
            int width = mContentContainer.getMeasuredWidth();
            int height = mContentContainer.getMeasuredHeight();
            mTouchableRegion.set(
                    (int) mContentContainer.getX(),
                    (int) mContentContainer.getY(),
                    (int) mContentContainer.getX() + width,
                    (int) mContentContainer.getY() + height);
        }

        /**
         * Make the touchable area of this popup be the area specified by mTouchableRegion.
         * This should be called after the popup window has been dismissed (dismiss/hide)
         * and is probably being re-shown with a new content root view.
         */
        private void setTouchableSurfaceInsetsComputer() {
            ViewTreeObserver viewTreeObserver = mPopupWindow.getContentView()
                    .getRootView()
                    .getViewTreeObserver();
            viewTreeObserver.removeOnComputeInternalInsetsListener(mInsetsComputer);
            viewTreeObserver.addOnComputeInternalInsetsListener(mInsetsComputer);
        }
    }

    /**
     * A widget that holds the primary menu items in the floating toolbar.
     */
    private static final class FloatingToolbarMainPanel {

        private final Context mContext;
        private final ViewGroup mContentView;
        private final View.OnClickListener mMenuItemButtonOnClickListener =
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (v.getTag() instanceof MenuItem) {
                            if (mOnMenuItemClickListener != null) {
                                mOnMenuItemClickListener.onMenuItemClick((MenuItem) v.getTag());
                            }
                        }
                    }
                };
        private final ViewFader viewFader;
        private final Runnable mOpenOverflow;

        private View mOpenOverflowButton;
        private MenuItem.OnMenuItemClickListener mOnMenuItemClickListener;

        /**
         * Initializes a floating toolbar popup main view panel.
         *
         * @param context
         * @param openOverflow  The code that opens the toolbar popup overflow.
         */
        public FloatingToolbarMainPanel(Context context, Runnable openOverflow) {
            mContext = Preconditions.checkNotNull(context);
            mContentView = new LinearLayout(context);
            viewFader = new ViewFader(mContentView);
            mOpenOverflow = Preconditions.checkNotNull(openOverflow);
        }

        /**
         * Fits as many menu items in the main panel and returns a list of the menu items that
         * were not fit in.
         *
         * @return The menu items that are not included in this main panel.
         */
        public List<MenuItem> layoutMenuItems(List<MenuItem> menuItems, int suggestedWidth) {
            final int toolbarWidth = getAdjustedToolbarWidth(mContext, suggestedWidth)
                    // Reserve space for the "open overflow" button.
                    - getEstimatedOpenOverflowButtonWidth(mContext);

            int availableWidth = toolbarWidth;
            final LinkedList<MenuItem> remainingMenuItems = new LinkedList<MenuItem>(menuItems);

            mContentView.removeAllViews();

            boolean isFirstItem = true;
            while (!remainingMenuItems.isEmpty()) {
                final MenuItem menuItem = remainingMenuItems.peek();
                Button menuItemButton = createMenuItemButton(mContext, menuItem);

                // Adding additional start padding for the first button to even out button spacing.
                if (isFirstItem) {
                    menuItemButton.setPaddingRelative(
                            (int) (1.5 * menuItemButton.getPaddingStart()),
                            menuItemButton.getPaddingTop(),
                            menuItemButton.getPaddingEnd(),
                            menuItemButton.getPaddingBottom());
                    isFirstItem = false;
                }

                // Adding additional end padding for the last button to even out button spacing.
                if (remainingMenuItems.size() == 1) {
                    menuItemButton.setPaddingRelative(
                            menuItemButton.getPaddingStart(),
                            menuItemButton.getPaddingTop(),
                            (int) (1.5 * menuItemButton.getPaddingEnd()),
                            menuItemButton.getPaddingBottom());
                }

                menuItemButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                int menuItemButtonWidth = Math.min(menuItemButton.getMeasuredWidth(), toolbarWidth);
                if (menuItemButtonWidth <= availableWidth) {
                    menuItemButton.setTag(menuItem);
                    menuItemButton.setOnClickListener(mMenuItemButtonOnClickListener);
                    mContentView.addView(menuItemButton);
                    ViewGroup.LayoutParams params = menuItemButton.getLayoutParams();
                    params.width = menuItemButtonWidth;
                    menuItemButton.setLayoutParams(params);
                    availableWidth -= menuItemButtonWidth;
                    remainingMenuItems.pop();
                } else {
                    if (mOpenOverflowButton == null) {
                        mOpenOverflowButton = (ImageButton) LayoutInflater.from(mContext)
                                .inflate(R.layout.floating_popup_open_overflow_button, null);
                        mOpenOverflowButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (mOpenOverflowButton != null) {
                                    mOpenOverflow.run();
                                }
                            }
                        });
                    }
                    mContentView.addView(mOpenOverflowButton);
                    break;
                }
            }
            return remainingMenuItems;
        }

        public void setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
            mOnMenuItemClickListener = listener;
        }

        public View getView() {
            return mContentView;
        }

        public void fadeIn(boolean animate) {
            viewFader.fadeIn(animate);
        }

        public void fadeOut(boolean animate) {
            viewFader.fadeOut(animate);
        }

        /**
         * Returns how big this panel's view should be.
         * This method should only be called when the view has not been attached to a parent
         * otherwise it will throw an illegal state.
         */
        public Size measure() throws IllegalStateException {
            Preconditions.checkState(mContentView.getParent() == null);
            mContentView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            return new Size(mContentView.getMeasuredWidth(), mContentView.getMeasuredHeight());
        }
    }


    /**
     * A widget that holds the overflow items in the floating toolbar.
     */
    private static final class FloatingToolbarOverflowPanel {

        private final LinearLayout mContentView;
        private final ViewGroup mBackButtonContainer;
        private final View mBackButton;
        private final ListView mListView;
        private final TextView mListViewItemWidthCalculator;
        private final ViewFader mViewFader;
        private final Runnable mCloseOverflow;

        private MenuItem.OnMenuItemClickListener mOnMenuItemClickListener;
        private int mOverflowWidth = 0;

        /**
         * Initializes a floating toolbar popup overflow view panel.
         *
         * @param context
         * @param closeOverflow  The code that closes the toolbar popup's overflow.
         */
        public FloatingToolbarOverflowPanel(Context context, Runnable closeOverflow) {
            mCloseOverflow = Preconditions.checkNotNull(closeOverflow);

            mContentView = new LinearLayout(context);
            mContentView.setOrientation(LinearLayout.VERTICAL);
            mViewFader = new ViewFader(mContentView);

            mBackButton = LayoutInflater.from(context)
                    .inflate(R.layout.floating_popup_close_overflow_button, null);
            mBackButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCloseOverflow.run();
                }
            });
            mBackButtonContainer = new LinearLayout(context);
            mBackButtonContainer.addView(mBackButton);

            mListView = createOverflowListView();
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    MenuItem menuItem = (MenuItem) mListView.getAdapter().getItem(position);
                    if (mOnMenuItemClickListener != null) {
                        mOnMenuItemClickListener.onMenuItemClick(menuItem);
                    }
                }
            });

            mContentView.addView(mListView);
            mContentView.addView(mBackButtonContainer);

            mListViewItemWidthCalculator = createOverflowMenuItemButton(context);
            mListViewItemWidthCalculator.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        /**
         * Sets the menu items to be displayed in the overflow.
         */
        public void setMenuItems(List<MenuItem> menuItems) {
            ArrayAdapter overflowListViewAdapter = (ArrayAdapter) mListView.getAdapter();
            overflowListViewAdapter.clear();
            overflowListViewAdapter.addAll(menuItems);
            setListViewHeight();
            setOverflowWidth();
        }

        public void setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
            mOnMenuItemClickListener = listener;
        }

        /**
         * Notifies the overflow of the current direction in which the overflow will be opened.
         *
         * @param overflowDirection  {@link FloatingToolbarPopup#OVERFLOW_DIRECTION_UP}
         *   or {@link FloatingToolbarPopup#OVERFLOW_DIRECTION_DOWN}.
         */
        public void setOverflowDirection(int overflowDirection) {
            mContentView.removeView(mBackButtonContainer);
            int index = (overflowDirection == FloatingToolbarPopup.OVERFLOW_DIRECTION_UP)? 1 : 0;
            mContentView.addView(mBackButtonContainer, index);
        }

        /**
         * Returns the content view of the overflow.
         */
        public View getView() {
            return mContentView;
        }

        public void fadeIn(boolean animate) {
            mViewFader.fadeIn(animate);
        }

        public void fadeOut(boolean animate) {
            mViewFader.fadeOut(animate);
        }

        /**
         * Returns how big this panel's view should be.
         * This method should only be called when the view has not been attached to a parent.
         *
         * @throws IllegalStateException
         */
        public Size measure() {
            Preconditions.checkState(mContentView.getParent() == null);
            mContentView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            return new Size(mContentView.getMeasuredWidth(), mContentView.getMeasuredHeight());
        }

        private void setListViewHeight() {
            int itemHeight = getEstimatedToolbarHeight(mContentView.getContext());
            int height = mListView.getAdapter().getCount() * itemHeight;
            int maxHeight = mContentView.getContext().getResources().
                    getDimensionPixelSize(R.dimen.floating_toolbar_minimum_overflow_height);
            ViewGroup.LayoutParams params = mListView.getLayoutParams();
            params.height = Math.min(height, maxHeight);
            mListView.setLayoutParams(params);
        }

        private int setOverflowWidth() {
            for (int i = 0; i < mListView.getAdapter().getCount(); i++) {
                MenuItem menuItem = (MenuItem) mListView.getAdapter().getItem(i);
                Preconditions.checkNotNull(menuItem);
                mListViewItemWidthCalculator.setText(menuItem.getTitle());
                mListViewItemWidthCalculator.measure(
                        MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                mOverflowWidth = Math.max(
                        mListViewItemWidthCalculator.getMeasuredWidth(), mOverflowWidth);
            }
            return mOverflowWidth;
        }

        private ListView createOverflowListView() {
            final Context context = mContentView.getContext();
            final ListView overflowListView = new ListView(context);
            overflowListView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overflowListView.setDivider(null);
            overflowListView.setDividerHeight(0);
            final ArrayAdapter overflowListViewAdapter =
                    new ArrayAdapter<MenuItem>(context, 0) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            TextView menuButton;
                            if (convertView != null) {
                                menuButton = (TextView) convertView;
                            } else {
                                menuButton = createOverflowMenuItemButton(context);
                            }
                            MenuItem menuItem = getItem(position);
                            menuButton.setText(menuItem.getTitle());
                            menuButton.setContentDescription(menuItem.getTitle());
                            menuButton.setMinimumWidth(mOverflowWidth);
                            return menuButton;
                        }
                    };
            overflowListView.setAdapter(overflowListViewAdapter);
            return overflowListView;
        }
    }


    /**
     * A helper for fading in or out a view.
     */
    private static final class ViewFader {

        private static final int FADE_OUT_DURATION = 250;
        private static final int FADE_IN_DURATION = 150;

        private final View mView;
        private final ObjectAnimator mFadeOutAnimation;
        private final ObjectAnimator mFadeInAnimation;

        private ViewFader(View view) {
            mView = Preconditions.checkNotNull(view);
            mFadeOutAnimation = ObjectAnimator.ofFloat(view, View.ALPHA, 1, 0)
                    .setDuration(FADE_OUT_DURATION);
            mFadeInAnimation = ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1)
                    .setDuration(FADE_IN_DURATION);
        }

        public void fadeIn(boolean animate) {
            cancelFadeAnimations();
            if (animate) {
                mFadeInAnimation.start();
            } else {
                mView.setAlpha(1);
            }
        }

        public void fadeOut(boolean animate) {
            cancelFadeAnimations();
            if (animate) {
                mFadeOutAnimation.start();
            } else {
                mView.setAlpha(0);
            }
        }

        private void cancelFadeAnimations() {
            mFadeInAnimation.cancel();
            mFadeOutAnimation.cancel();
        }
    }


    /**
     * Creates and returns a menu button for the specified menu item.
     */
    private static Button createMenuItemButton(Context context, MenuItem menuItem) {
        Button menuItemButton = (Button) LayoutInflater.from(context)
                .inflate(R.layout.floating_popup_menu_button, null);
        menuItemButton.setText(menuItem.getTitle());
        menuItemButton.setContentDescription(menuItem.getTitle());
        return menuItemButton;
    }

    /**
     * Creates and returns a styled floating toolbar overflow list view item.
     */
    private static TextView createOverflowMenuItemButton(Context context) {
        return (TextView) LayoutInflater.from(context)
                .inflate(R.layout.floating_popup_overflow_list_item, null);
    }

    private static ViewGroup createContentContainer(Context context) {
        return (ViewGroup) LayoutInflater.from(context)
                .inflate(R.layout.floating_popup_container, null);
    }

    private static PopupWindow createPopupWindow(View content) {
        ViewGroup popupContentHolder = new LinearLayout(content.getContext());
        PopupWindow popupWindow = new PopupWindow(popupContentHolder);
        popupWindow.setWindowLayoutType(
                WindowManager.LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL);
        popupWindow.setAnimationStyle(0);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupContentHolder.addView(content);
        return popupWindow;
    }

    /**
     * Creates a "grow and fade in from the bottom" animation for the specified view.
     *
     * @param view  The view to animate
     */
    private static AnimatorSet createGrowFadeInFromBottom(View view) {
        AnimatorSet growFadeInFromBottomAnimation =  new AnimatorSet();
        growFadeInFromBottomAnimation.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1).setDuration(125),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1).setDuration(125),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1).setDuration(75));
        growFadeInFromBottomAnimation.setStartDelay(50);
        return growFadeInFromBottomAnimation;
    }

    /**
     * Creates a "shrink and fade out from bottom" animation for the specified view.
     *
     * @param view  The view to animate
     * @param listener  The animation listener
     */
    private static AnimatorSet createShrinkFadeOutFromBottomAnimation(
            View view, Animator.AnimatorListener listener) {
        AnimatorSet shrinkFadeOutFromBottomAnimation =  new AnimatorSet();
        shrinkFadeOutFromBottomAnimation.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1, 0.5f).setDuration(125),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1, 0).setDuration(75));
        shrinkFadeOutFromBottomAnimation.setStartDelay(150);
        shrinkFadeOutFromBottomAnimation.addListener(listener);
        return shrinkFadeOutFromBottomAnimation;
    }

    private static int getEstimatedToolbarHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.floating_toolbar_height);
    }

    private static int getEstimatedOpenOverflowButtonWidth(Context context) {
        return context.getResources()
                .getDimensionPixelSize(R.dimen.floating_toolbar_menu_button_minimum_width);
    }

    private static int getAdjustedToolbarWidth(Context context, int width) {
        int maximumWidth = getScreenWidth(context) - 2 * context.getResources()
                .getDimensionPixelSize(R.dimen.floating_toolbar_horizontal_margin);

        if (width <= 0 || width > maximumWidth) {
            int defaultWidth = context.getResources()
                    .getDimensionPixelSize(R.dimen.floating_toolbar_preferred_width);
            width = Math.min(defaultWidth, maximumWidth);
        }
        return width;
    }

    /**
     * Returns the device's screen width.
     */
    private static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * Returns the device's screen height.
     */
    private static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * Returns value, restricted to the range min->max (inclusive).
     * If maximum is less than minimum, the result is undefined.
     *
     * @param value  The value to clamp.
     * @param minimum  The minimum value in the range.
     * @param maximum  The maximum value in the range. Must not be less than minimum.
     */
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
