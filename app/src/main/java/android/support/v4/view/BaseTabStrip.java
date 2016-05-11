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

package android.support.v4.view;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * BasePagerTabStrip ViewPager滑动对应变化效果
 *
 * @author Alex
 */
public abstract class BaseTabStrip extends View implements ViewPager.Decor {

    private ViewPager mPager;
    private final PageListener mPageListener = new PageListener();
    private WeakReference<PagerAdapter> mWatchingAdapter;
    private int mLastKnownPosition = 0;
    private float mLastKnownPositionOffset = -1;
    private int mCurrentPager = 0;
    private int mPosition = 0;
    private boolean clickSelectedItem = false;
    private Drawable mTabItemBackground;
    private ArrayList<Drawable> mTabItemBackgrounds = new ArrayList<>();
    private boolean tabClickable;
    private boolean clickSmoothScroll;
    private GestureDetectorCompat mTabGestureDetector;
    private TabOnGestureListener mTabOnGestureListener = new TabOnGestureListener();
    private OnItemClickListener clickListener;
    private ArrayList<OnChangeListener> changeListeners;

    public BaseTabStrip(Context context) {
        this(context, null);
    }

    public BaseTabStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseTabStrip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setItemClickable(false);
        setClickSmoothScroll(false);
        mTabGestureDetector = new GestureDetectorCompat(context, mTabOnGestureListener);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final ViewParent parent = getParent();
        if (mPager == null && parent != null && parent instanceof ViewPager) {
            bindViewPager((ViewPager) parent);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        bindViewPager(null);
        clearItemBackground();
    }

    /**
     * 捆绑ViewPager
     *
     * @param pager 关联的ViewPager
     */
    public void bindViewPager(ViewPager pager) {
        PagerAdapter oldAdapter = null;
        PagerAdapter newAdapter = null;
        if (mPager != null) {
            mPager.setInternalPageChangeListener(null);
            mPager.setOnAdapterChangeListener(null);
            oldAdapter = mPager.getAdapter();
        }
        mPager = pager;
        if (mPager != null) {
            mPager.setInternalPageChangeListener(mPageListener);
            mPager.setOnAdapterChangeListener(mPageListener);
            newAdapter = mPager.getAdapter();
        }
        bindPagerAdapter(oldAdapter, newAdapter);
    }

    private void clearItemBackground() {
        for (Drawable drawable : mTabItemBackgrounds) {
            drawable.setCallback(null);
        }
        mTabItemBackgrounds.clear();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!tabClickable) {
            return super.onTouchEvent(ev);
        }
        final boolean tab = mTabGestureDetector.onTouchEvent(ev);
        return super.onTouchEvent(ev) || tab;
    }

    @Override
    @Deprecated
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * 点击
     *
     * @param position 位置
     */
    @SuppressWarnings("unused")
    public boolean performClick(int position) {
        return performClick(position, false, true);
    }

    /**
     * 点击
     *
     * @param position     位置
     * @param smoothScroll 是否平滑滚动
     */
    public boolean performClick(int position, boolean smoothScroll, boolean notifyListener) {
        if (getViewPager() != null && position >= 0 && position < getItemCount()) {
            clickSelectedItem = position == mPosition;
            mPosition = position;
            if (!clickSelectedItem) {
                if (!smoothScroll) {
                    mCurrentPager = position;
                    mLastKnownPosition = mCurrentPager;
                    mLastKnownPositionOffset = 0;
                    jumpTo(mCurrentPager);
                    notifyJumpTo(mCurrentPager);
                }
                getViewPager().setCurrentItem(position, smoothScroll);
            }
            if (clickListener != null && notifyListener) {
                clickListener.onItemClick(mPosition);
            }
            return true;
        }
        return false;
    }

    /**
     * 捆绑PagerAdapter
     *
     * @param oldAdapter 旧Adapter
     * @param newAdapter 新Adapter
     */
    protected void bindPagerAdapter(PagerAdapter oldAdapter, PagerAdapter newAdapter) {
        if (oldAdapter != null) {
            oldAdapter.unregisterDataSetObserver(mPageListener);
            mWatchingAdapter = null;
        }
        if (newAdapter != null) {
            newAdapter.registerDataSetObserver(mPageListener);
            mWatchingAdapter = new WeakReference<>(newAdapter);
        }
        createItemBackgrounds();
        onBindPagerAdapter();
        checkCurrentItem();
        requestLayout();
        invalidate();
    }

    /**
     * 创建子项背景
     */
    protected void createItemBackgrounds() {
        if (mTabItemBackground == null)
            return;
        int count = getItemCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                if (i < mTabItemBackgrounds.size()) {
                    mTabItemBackgrounds.get(i).setState(onCreateDrawableState(0));
                } else {
                    Drawable tag = mTabItemBackground.getConstantState().newDrawable();
                    tag.setCallback(this);
                    mTabItemBackgrounds.add(tag);
                }
            }
        } else {
            for (Drawable drawable : mTabItemBackgrounds) {
                drawable.setState(onCreateDrawableState(0));
            }
        }
    }

    /**
     * 重新创建子项背景
     */
    protected void recreateItemBackgrounds() {
        clearItemBackground();
        if (mTabItemBackground == null)
            return;
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            Drawable tag = mTabItemBackground.getConstantState().newDrawable();
            tag.setCallback(this);
            mTabItemBackgrounds.add(tag);
        }
    }

    /**
     * 获取当前选中的子项
     *
     * @return 当前选中的子项
     */
    public int getCurrentItem() {
        return mPager == null ? -1 : mPager.getCurrentItem();
    }

    /**
     * 检查选中子项
     */
    public void checkCurrentItem() {
        final int position = getCurrentItem();
        mPosition = position;
        if (position >= 0 && position != mCurrentPager) {
            mCurrentPager = position;
            mLastKnownPosition = mCurrentPager;
            mLastKnownPositionOffset = 0;
            jumpTo(mCurrentPager);
            notifyJumpTo(mCurrentPager);
        }
    }

    /**
     * 捆绑PagerAdapter
     */
    protected void onBindPagerAdapter() {

    }

    /**
     * 由触摸点转为Position
     *
     * @param x X坐标
     * @param y Y坐标
     * @return 坐标对应位置
     */
    @SuppressWarnings("unused")
    protected int pointToPosition(float x, float y) {
        return 0;
    }

    @Override
    protected void drawableStateChanged() {
        final float downMotionX = mTabOnGestureListener.getDownMotionX();
        final float downMotionY = mTabOnGestureListener.getDownMotionY();
        int position = pointToPosition(downMotionX, downMotionY);
        if (position >= 0 && position < mTabItemBackgrounds.size()) {
            Drawable tag = mTabItemBackgrounds.get(position);
            DrawableCompat.setHotspot(tag, getHotspotX(tag, position, downMotionX, downMotionY),
                    getHotspotY(tag, position, downMotionX, downMotionY));
            if (tag.isStateful()) {
                tag.setState(getDrawableState());
            }
        }
        super.drawableStateChanged();
    }

    /**
     * set hotspot's x location
     *
     * @param background 背景图
     * @param position   图片Position
     * @param motionX    点击位置X
     * @param motionY    点击位置Y
     * @return x location
     */
    @SuppressWarnings("unused")
    protected float getHotspotX(Drawable background, int position, float motionX, float motionY) {
        return background.getIntrinsicWidth() * 0.5f;
    }

    /**
     * set hotspot's y location
     *
     * @param background 背景图
     * @param position   图片Position
     * @param motionX    点击位置X
     * @param motionY    点击位置Y
     * @return y location
     */
    @SuppressWarnings("unused")
    protected float getHotspotY(Drawable background, int position, float motionX, float motionY) {
        return background.getIntrinsicHeight() * 0.5f;
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        boolean isTag = false;
        for (Drawable tag : mTabItemBackgrounds) {
            if (who == tag) {
                isTag = true;
                break;
            }
        }
        return isTag || super.verifyDrawable(who);
    }

    /**
     * 获取Tab子项背景
     *
     * @param position 位置
     * @return 背景
     */
    protected Drawable getItemBackground(int position) {
        return position < mTabItemBackgrounds.size() ? mTabItemBackgrounds.get(position) : null;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        BaseTabStripSavedState ss = new BaseTabStripSavedState(superState);
        ss.currentPager = mCurrentPager;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        BaseTabStripSavedState ss = (BaseTabStripSavedState) state;
        performClick(ss.currentPager, false, false);
        super.onRestoreInstanceState(ss.getSuperState());
    }

    static class BaseTabStripSavedState extends BaseSavedState {
        int currentPager;

        BaseTabStripSavedState(Parcelable superState) {
            super(superState);
        }

        private BaseTabStripSavedState(Parcel in) {
            super(in);
            currentPager = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentPager);
        }

        public static final Creator<BaseTabStripSavedState> CREATOR =
                new Creator<BaseTabStripSavedState>() {
                    public BaseTabStripSavedState createFromParcel(Parcel in) {
                        return new BaseTabStripSavedState(in);
                    }

                    public BaseTabStripSavedState[] newArray(int size) {
                        return new BaseTabStripSavedState[size];
                    }
                };
    }

    private class PageListener extends DataSetObserver implements
            ViewPager.OnPageChangeListener, ViewPager.OnAdapterChangeListener {
        private int mScrollState;

        @Override
        public void onPageScrolled(int position, float positionOffset,
                                   int positionOffsetPixels) {
            updateView(position, positionOffset, false);
        }

        @Override
        public void onPageSelected(int position) {
            if (mScrollState == ViewPager.SCROLL_STATE_IDLE) {
                float offset = mLastKnownPositionOffset >= 0 ? mLastKnownPositionOffset : 0;
                updateView(position, offset, false);
            }
            mPosition = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            mScrollState = state;
        }

        @Override
        public void onAdapterChanged(PagerAdapter oldAdapter,
                                     PagerAdapter newAdapter) {
            bindPagerAdapter(oldAdapter, newAdapter);
        }

        @Override
        public void onChanged() {
            final float offset = mLastKnownPositionOffset >= 0 ? mLastKnownPositionOffset : 0;
            final int position = getCurrentItem();
            mPosition = position;
            updateView(position, offset, true);
        }
    }

    /**
     * 更新View
     *
     * @param position       位置
     * @param positionOffset 位置偏移
     * @param force          是否强制更新
     */
    private void updateView(int position, float positionOffset, boolean force) {
        if (mLastKnownPositionOffset == -1) {
            mLastKnownPositionOffset = positionOffset;
        }
        if (!force && positionOffset == mLastKnownPositionOffset) {
            return;
        }
        float mPositionOffset = positionOffset;
        if (mLastKnownPositionOffset == 0 || mLastKnownPositionOffset == 1)
            if (mPositionOffset > 0.5f)
                mLastKnownPositionOffset = 1;
            else
                mLastKnownPositionOffset = 0;
        int nextPager;
        if (position > mLastKnownPosition) {
            mLastKnownPosition = position - 1;
            if (mLastKnownPositionOffset > mPositionOffset) {
                if (mPositionOffset == 0) {
                    mPositionOffset = 1;
                } else {
                    mLastKnownPosition = position;
                }
                mCurrentPager = mLastKnownPosition;
                nextPager = mLastKnownPosition + 1;
                gotoRight(mCurrentPager, nextPager, mPositionOffset);
                notifyGotoRight(mCurrentPager, nextPager, mPositionOffset);
            } else {
                mCurrentPager = mLastKnownPosition + 1;
                nextPager = mLastKnownPosition;
                gotoLeft(mCurrentPager, nextPager, mPositionOffset);
                notifyGotoLeft(mCurrentPager, nextPager, mPositionOffset);
            }
        } else {
            mLastKnownPosition = position;
            if (mLastKnownPositionOffset > mPositionOffset) {
                mCurrentPager = mLastKnownPosition + 1;
                nextPager = mLastKnownPosition;
                gotoLeft(mCurrentPager, nextPager, mPositionOffset);
                notifyGotoLeft(mCurrentPager, nextPager, mPositionOffset);
            } else {
                mPositionOffset = mPositionOffset == 0 ? 1 : mPositionOffset;
                mCurrentPager = mLastKnownPosition;
                nextPager = mLastKnownPosition + 1;
                gotoRight(mCurrentPager, nextPager, mPositionOffset);
                notifyGotoRight(mCurrentPager, nextPager, mPositionOffset);
            }
        }
        mLastKnownPosition = position;
        mLastKnownPositionOffset = positionOffset;
    }

    /**
     * 通知跳转到
     *
     * @param current 位置
     */
    private void notifyJumpTo(int current) {
        if (changeListeners == null)
            return;
        for (OnChangeListener listener : changeListeners) {
            listener.jumpTo(current);
        }
    }

    /**
     * 通知滑向左边
     *
     * @param current 当前页
     * @param next    目标页
     * @param offset  偏移
     */
    private void notifyGotoLeft(int current, int next, float offset) {
        if (changeListeners == null)
            return;
        for (OnChangeListener listener : changeListeners) {
            listener.gotoLeft(current, next, offset);
        }
    }

    /**
     * 通知滑向右边
     *
     * @param current 当前页
     * @param next    目标页
     * @param offset  偏移
     */
    private void notifyGotoRight(int current, int next, float offset) {
        if (changeListeners == null)
            return;
        for (OnChangeListener listener : changeListeners) {
            listener.gotoRight(current, next, offset);
        }
    }

    /**
     * 直接跳转到
     *
     * @param current 位置
     */
    protected abstract void jumpTo(int current);

    /**
     * 滑向左边
     *
     * @param current 当前页
     * @param next    目标页
     * @param offset  偏移
     */
    protected abstract void gotoLeft(int current, int next, float offset);

    /**
     * 滑向右边
     *
     * @param current 当前页
     * @param next    目标页
     * @param offset  偏移
     */
    protected abstract void gotoRight(int current, int next, float offset);

    private class TabOnGestureListener extends GestureDetector.SimpleOnGestureListener {

        private float mDownMotionX = -1;
        private float mDownMotionY = -1;

        @Override
        public boolean onDown(MotionEvent e) {
            mDownMotionX = e.getX();
            mDownMotionY = e.getY();
            return super.onDown(e);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return performClick(pointToPosition(e.getX(), e.getY()), clickSmoothScroll, true);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            int position = pointToPosition(e.getX(), e.getY());
            if (position < 0)
                return false;
            if (clickSelectedItem && clickListener != null) {
                clickListener.onSelectedClick(mPosition);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            int position = pointToPosition(e.getX(), e.getY());
            if (position < 0)
                return false;
            if (clickListener != null) {
                clickListener.onDoubleClick(mPosition);
            }
            return true;
        }

        public float getDownMotionX() {
            return mDownMotionX;
        }

        public float getDownMotionY() {
            return mDownMotionY;
        }
    }

    public final ViewPager getViewPager() {
        return mPager;
    }

    public final int getItemCount() {
        try {
            return mWatchingAdapter.get().getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public final CharSequence getItemText(int position) {
        try {
            return mWatchingAdapter.get().getPageTitle(position);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置Tab子项背景
     *
     * @param background 背景
     */
    public void setItemBackground(Drawable background) {
        if (mTabItemBackground != background) {
            mTabItemBackground = background;
            recreateItemBackgrounds();
            requestLayout();
            invalidate();
        }
    }

    /**
     * 设置Tab子项背景
     *
     * @param background 背景
     */
    @SuppressWarnings("unused")
    public void setItemBackground(int background) {
        setItemBackground(ContextCompat.getDrawable(getContext(), background));
    }

    /**
     * 设置Tab是否可以点击
     *
     * @param clickable 是否可以点击
     */
    public void setItemClickable(boolean clickable) {
        tabClickable = clickable;
        if (tabClickable) {
            setClickable(true);
        }
    }

    /**
     * Tab是否可以点击
     *
     * @return Tab是否可以点击
     */
    @SuppressWarnings("unused")
    public boolean isTabClickable() {
        return tabClickable;
    }

    /**
     * 是否点击时平滑滚动
     *
     * @return 是否点击时平滑滚动
     */
    @SuppressWarnings("unused")
    public boolean isClickSmoothScroll() {
        return clickSmoothScroll;
    }

    /**
     * 设置点击时是否平滑滚动
     *
     * @param smooth 点击时是否平滑滚动
     */
    public void setClickSmoothScroll(boolean smooth) {
        clickSmoothScroll = smooth;
    }

    /**
     * 设置点击监听器
     *
     * @param listener 监听器
     */
    @SuppressWarnings("unused")
    public void setOnItemClickListener(OnItemClickListener listener) {
        clickListener = listener;
    }

    /**
     * 点击监听
     */
    public interface OnItemClickListener {
        /**
         * 点击子项
         *
         * @param position 位置
         */
        void onItemClick(int position);

        /**
         * 点击已选中的子项
         *
         * @param position 位置
         */
        void onSelectedClick(int position);

        /**
         * 双击子项
         *
         * @param position 位置
         */
        void onDoubleClick(int position);
    }

    /**
     * 添加变化监听器
     *
     * @param listener 变化监听器
     */
    @SuppressWarnings("unused")
    public void addOnChangeListener(OnChangeListener listener) {
        if (listener == null)
            return;
        if (changeListeners == null)
            changeListeners = new ArrayList<>();
        changeListeners.add(listener);
        listener.jumpTo(mCurrentPager);
    }

    /**
     * 移除变化监听器
     *
     * @param listener 变化监听器
     */
    @SuppressWarnings("unused")
    public void removeOnChangeListener(OnChangeListener listener) {
        if (changeListeners == null)
            return;
        changeListeners.remove(listener);
    }

    /**
     * 获取默认Tag背景
     *
     * @return 默认Tag背景
     */
    protected Drawable getDefaultTagBackground() {
        final float density = getResources().getDisplayMetrics().density;
        final GradientDrawable mBackground = new GradientDrawable();
        mBackground.setShape(GradientDrawable.RECTANGLE);
        mBackground.setColor(0xffff4444);
        mBackground.setCornerRadius(10 * density);
        mBackground.setSize((int) (10 * density), (int) (10 * density));
        return mBackground;
    }

    /**
     * 变化监听
     */
    public interface OnChangeListener {
        /**
         * 跳转到当前位置
         *
         * @param correct 当前位置
         */
        void jumpTo(int correct);

        /**
         * 往左滚动
         *
         * @param correct 当前位置
         * @param next    将要抵达位置
         * @param offset  移动便宜
         */
        void gotoLeft(int correct, int next, float offset);

        /**
         * 往右滚动
         *
         * @param correct 当前位置
         * @param next    将要抵达位置
         * @param offset  移动便宜
         */
        void gotoRight(int correct, int next, float offset);
    }

    /**
     * 角标数据容器Adapter
     */
    public interface ItemTabAdapter {

        /**
         * 是否启用角标
         *
         * @param position Item位置
         * @return 是否启用
         */
        boolean isTagEnable(int position);

        /**
         * 获取角标值
         *
         * @param position Item位置
         * @return 角标值
         */
        String getTag(int position);
    }
}