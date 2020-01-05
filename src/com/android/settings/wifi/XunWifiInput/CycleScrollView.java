package com.android.settings.wifi.XunWifiInput;

/**
 * Created by mayanjun on 2017/11/4.
 */
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Scroller;

import com.android.settings.R;


public class CycleScrollView<T> extends ViewGroup implements OnGestureListener {
    static final String TAG = "CycleScrollView";
    Context mContext;

    /**
     * Scroll velocity.
     */
    public static final long SCROLL_VELOCITY = 50;

    /**
     * Scroll offset.
     */
    public static final int SCROLL_OFFSET = -1;

    /**
     * Touch delay.
     */
    public static final long TOUCH_DELAYMILLIS = 2000;

    /**
     * Fling duration.
     */
    public static final int FLING_DURATION = 2000;

    /**
     * Filing max velocity x.
     */
    public static final int MAX_VELOCITY_X = 1000;

    /**
     * the view gap between every key.
     */
    public static final int VIEW_GAP = 12;
    /**
     * scroll adjust times.
     */
    public static final int ADJUST_TIME = 30;

    /**
     * key width.
     */
    public static final int KEY_WIDTH = 76;

    private GestureDetector detector;
    private Handler mHandler;
    private Scroller mScroller;
    /**
     * Callback interface adapter and OnItemClick.
     */
    private CycleScrollAdapter<T> mAdapter;
    private OnItemClickListener mOnItemClickListener;

    /**
     * Scroll index
     */
    private int mPreIndex;
    private int mCurrentIndex;
    private int mNextIndex;
    private View mCurrentView;
    private View mPreView;
    private View mNextView;

    private float mLastMotionX;

    // The reLayout is false can not invoke onLayout.
    private boolean reLayout = false;

    // If the item count more than screen that can scroll.
    private boolean canScroll = false;

    // A flag for switch current view.
    private boolean mCurrentViewAtLeft = true;

    // Fling distance.
    private int mFlingX = 0;

    private static int sViewGap = 0;

    private static int sKeyWidth = 0;

    private boolean isMoveAction = false;

    private int defaultItemY = 0;

    private int maxItemCount = 10;

    private int initItemX = VIEW_GAP;

    private int initItemGap = VIEW_GAP;

    private boolean mAutoScrollFin = false;
    private boolean mScrollFinish = false;
    /**
     * The screen width.
     */
    private int screenWidth;

    /**
     * Item view height.
     */
    private int itemHeight;

    /**
     * Item view width.
     */
    private int itemWidth;

    /**
     * Item view layout x.
     */
    private int itemX = getInitItemX();

    /**
     * Item view layout y.
     */
    private int itemY = defaultItemY;

    // Auto scroll view task.
    private final Runnable mScrollTask = new Runnable() {

        @Override
        public void run() {
            if (canScroll) {
                scrollView(SCROLL_OFFSET);
                mHandler.postDelayed(this, SCROLL_VELOCITY);// Loop self.
            }
        }
    };

    public CycleScrollView(Context context) {
        super(context);
        onCreate(context);
    }

    public CycleScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        onCreate(context);
    }

    public CycleScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        onCreate(context);
    }

    private void onCreate(Context context) {
        mContext = context;
        sKeyWidth = (int)mContext.getResources().getDimension(R.dimen.ri_keyborad_width);
        sViewGap = (int)mContext.getResources().getDimension(R.dimen.ri_keyborad_gap);
        initItemX = sViewGap;
        initItemGap = sViewGap;
        Log.i(TAG, "onCreate-sKeyWidth = " + sKeyWidth + ", sViewGap = " + sViewGap);
        detector = new GestureDetector(this);
        mHandler = new Handler();
        mScroller = new Scroller(context);
    }

    /**
     * Create scroll index.
     */
    public void createIndex() {
        if (canScroll) {
            mCurrentViewAtLeft = true;
            mPreIndex = maxItemCount - 1;
            mCurrentIndex = 0;
            mNextIndex = 1;
            mPreView = getChildAt(mPreIndex);
            mCurrentView = getChildAt(mCurrentIndex);
            mNextView = getChildAt(mNextIndex);
        }
    }

    /**
     * Set item click callback.
     *
     * @param onItemClickListener
     *            The callback
     */
    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    /**
     * Set itemAdapter for addItem and bindItem.
     *
     * @param adapter
     */
    public void setAdapter(CycleScrollAdapter<T> adapter) {
        mAdapter = adapter;
    }

    /**
     * Start auto scroll.
     */
    public void startScroll() {
        if (canScroll) {
            mHandler.post(mScrollTask);
        }
    }

    /**
     * Stop auto scroll and filing scroll task.
     */
    public void stopScroll() {
        mHandler.removeCallbacks(mScrollTask);
        canScroll = false;
        computeScroll();
    }

    /**
     * Delay start auto scroll task.
     */
    public void delayStartScroll() {
        if (canScroll) {
            mHandler.postDelayed(mScrollTask, TOUCH_DELAYMILLIS);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int count = this.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = this.getChildAt(i);
            child.measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        /**
         * On layout set the child view layout by x y width and height.
         */
        if (reLayout) {// Run one times.
            for (int i = 0; i < getChildCount(); i++) {
                Log.i(TAG, "onLayout: itemX = " + itemX + ", i = " + i);
                View child = this.getChildAt(i);
                child.setVisibility(View.VISIBLE);
                child.layout(itemX, getItemY(), itemX + getItemWidth(),
                        getItemY() + getItemHeight());
                itemX += getItemMargin();
            }
            reLayout = !reLayout;
        }
    }

    /**
     * When fling view run the fling task scroll view.
     */
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {

        if (e1 == null || e2 == null) {
            return false;
        }

        // When deltaX and velocityX not good return false.
        if (Math.abs(velocityX) < MAX_VELOCITY_X) {
            return false;
        }

        // Get the delta x.
        float deltaX = (e1.getX() - e2.getX());
        /**
         * If can fling stop other scroll task at first , delay the task after
         * fling.
         */
        mHandler.removeCallbacks(mScrollTask);
        if (false) {
            mHandler.postDelayed(mScrollTask, TOUCH_DELAYMILLIS
                    + FLING_DURATION - 1000);
        }

        /**
         * The flingX is fling distance.
         */
        mFlingX = (int) deltaX;
        mScrollFinish = false;

        Log.i(TAG, "onFling-->mFlingx = " + mFlingX);
        // Start scroll with fling x.
        mScroller.startScroll(0, 0, mFlingX, 0, FLING_DURATION);
        return false;
    }

    @Override
    public void computeScroll() {
        Log.i(TAG, "computeScroll");

        if (canScroll && mScroller.computeScrollOffset()) {
            int currentX = mScroller.getCurrX();
            int remainTime = FLING_DURATION - mScroller.timePassed();
            /**
             * The Scroller.getCurrX() approach mFlingX , the deltaX more and
             * more small.
             */
            //Log.i(TAG, "computeScroll-remainTime = " + remainTime);
            //Log.i(TAG, "computeScroll-mScroller.getCurrX() = " + currentX);
            int deltaX = currentX - mFlingX;

            if((remainTime < 300 || deltaX == 0) && !mScrollFinish){
                View view = getChildAt(mCurrentIndex);
                int ajustDelta = 0;
                int viewX = (int)view.getX();
                int ajustOri = (viewX - sViewGap) % sKeyWidth;

                //Log.i(TAG, "computeScroll, viewX = " + viewX);
                //Log.i(TAG, "computeScroll, ajustOri = " + ajustOri);

                if(ajustOri != 0 && mFlingX < 0) {
                    ajustDelta = sKeyWidth - ajustOri;
                }else if(ajustOri != 0){
                    ajustDelta = -ajustOri;
                }

                // When canScroll is true, scrollView width deltaX.
                if (canScroll) {
                    //Log.i(TAG, "computeScroll, canScroll ajustOri = " + ajustOri);
                    scrollView(ajustDelta);
                }

                mScrollFinish = true;
            }

            if(deltaX != 0 && !mScrollFinish){
                //Log.i(TAG, "computeScroll-mScroller.deltaX = " + deltaX);
                int moveDelta = (int)deltaX / 6;
                if(Math.abs(moveDelta) > 4){
                    scrollView(moveDelta);
                }else{
                    View view = getChildAt(mCurrentIndex);
                    int ajustDelta = 0;
                    int viewX = (int)view.getX();
                    int ajustOri = (viewX - sViewGap) % sKeyWidth;

                    //Log.i(TAG, "computeScroll, canScroll ajustOri2 = " + ajustOri);

                    if(ajustOri == 0){
                        mScrollFinish = true;
                    }else if(Math.abs(ajustOri) < 4){
                        scrollView(-ajustOri);
                        mScrollFinish = true;
                    }else{
                        if(mFlingX > 0)
                            scrollView(-4);
                        else
                            scrollView(4);
                    }
 
                }
                postInvalidate();
            }
        }else{
            if(!mScrollFinish){
                View view = getChildAt(mCurrentIndex);
                int ajustDelta = 0;
                int viewX = (int)view.getX();
                int ajustOri = (viewX - sViewGap) % sKeyWidth;

                //Log.i(TAG, "computeScroll, viewX = " + viewX);
                //Log.i(TAG, "computeScroll, ajustOri = " + ajustOri);

                if(ajustOri != 0 && mFlingX < 0) {
                    ajustDelta = sKeyWidth - ajustOri;
                }else if(ajustOri != 0){
                    ajustDelta = -ajustOri;
                }

                // When canScroll is true, scrollView width deltaX.
                if (canScroll) {
                    //Log.i(TAG, "computeScroll, canScroll ajustOri = " + ajustOri);
                    scrollView(ajustDelta);
                }

                mScrollFinish = true;
            }
        }
    }

    /**
     * When touch event is move scroll child view.
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        // Get event x,y at parent view.
        final float x = ev.getX();

        /**
         * Get event x,y at screen.
         */
        final int rawX = (int) ev.getRawX();
        final int rawY = (int) ev.getRawY();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i(TAG, "touch event down");
                // Reset isMoveAction.
                isMoveAction = false;
                // Get motionX.
                mLastMotionX = x;
                // reset auto scroll fin flag
                mAutoScrollFin = false;
                break;
            case MotionEvent.ACTION_MOVE:
                // When action move set isMoveAction true.
                int delta = (int)(Math.abs(ev.getX() - mLastMotionX));
                Log.i(TAG, "touch event move,delta = " + delta);
                if(delta > 10) {
                    isMoveAction = true;
                    // Only support one pointer.
                    if (ev.getPointerCount() == 1) {
                        // Compute delta X.
                        int deltaX = 0;
                        Log.i(TAG, "scrollView, x = " + x);
                        Log.i(TAG, "scrollView, mLastMotionX = " + mLastMotionX);
                        deltaX = (int) (x - mLastMotionX);
                        mLastMotionX = x;
                        // When canScroll is true, scrollView width deltaX.
                        if (canScroll) {
                            scrollView(deltaX);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                /**
                 * If not move find click item and invoke click event.
                 */
                Log.i(TAG, "touch event up");
                if (!isMoveAction) {
                    View view = getClickItem(rawX, rawY);
                    if (view != null) {
                        Log.i(TAG, "touch event up,onItemClick tag= " + view.getTag());
                        mOnItemClickListener.onItemClick(Integer.parseInt(view.getTag().toString()));
                    }
                }else {
                    View view = getChildAt(mCurrentIndex);
                    int ajustDelta = 0;
                    int viewX = (int)view.getX();
                    int ajustOri = (viewX - sViewGap) % sKeyWidth;
                    int lastDeltaX = (int) (x - mLastMotionX);

                    Log.i(TAG, "touch event up, viewX = " + viewX);
                    //Log.i(TAG, "touch event up, ajustOri = " + ajustOri);
                    //Log.i(TAG, "touch event up, lastDeltaX = " + lastDeltaX);

                    if(ajustOri != 0 && Math.abs((ajustOri - sViewGap)) > 10) {
                        if(ajustOri > 0){
                            ajustDelta = sKeyWidth - ajustOri;
                        }else{
                            ajustDelta = -sKeyWidth - ajustOri;
                            //Log.i(TAG, "touch event up, ajustDelta11 = " + ajustDelta);
                        }
                    }else if(ajustOri != 0){
                        ajustDelta = -ajustOri;
                        //Log.i(TAG, "touch event up, ajustDelta12 = " + ajustDelta);
                    }

                    /*
                    if(ajustOri != 0 && lastDeltaX > 0){
                        if(ajustOri > 0)
                            ajustDelta = sKeyWidth - ajustOri;
                        else
                            ajustDelta = sKeyWidth + ajustOri;
                    }else if(ajustOri != 0 && lastDeltaX < 0){
                        if(ajustOri > 0)
                            ajustDelta = ajustOri;
                        else
                            ajustDelta = -ajustOri;  
                    }*/

                    // When canScroll is true, scrollView width deltaX.
                    if (canScroll) {
                        //Log.i(TAG, "touch event up, canScroll ajustOri = " + ajustDelta);
                        scrollView(ajustDelta);
                    }

                    //Log.i(TAG, "ACTION_UP, view.getX()  = " + view.getX());
                    //Log.i(TAG, "ACTION_UP, view.getY()  = " + view.getY());
                    //Log.i(TAG, "ACTION_UP, view.getTag()  = " + view.getTag());

                }
                break;
        }
        return this.detector.onTouchEvent(ev);
    }

    /**
     * Get click item view by rawX and rawY.
     * @param rawX the x at screen.
     * @param rawY the y at screen.
     * @return the click item view.
     */
    private View getClickItem(final int rawX, final int rawY) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            // Get item view rect.
            Rect rect = new Rect();
            child.getGlobalVisibleRect(rect);
            // If click point on the item view, invoke the click event.
            if (rect.contains(rawX, rawY)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Scroll view by delta x.
     *
     * @param deltaX
     *            The scroll distance.
     */
    private void scrollView(int deltaX) {
        // Move child view by deltaX.
        Log.i(TAG, "scrollView, deltaX = " + deltaX);
        moveChildView(deltaX);
        // After move change index.
        if (deltaX < 0) {// move left
            // If current at right switch current view to left.
            switchCurrentViewToLeft();
            // change previous current next index.
            moveToNext();
        } else {// move right
            // If current at left switch current view to right.
            switchCurrentViewToRight();
            // change previous current next index.
            moveToPre();
        }
        invalidate();
    }

    /**
     * Move view by delta x.
     *
     * @param deltaX
     *            The move distance.
     */
    private void moveChildView(int deltaX) {
        Log.i(TAG, "moveChildView, deltaX = " + deltaX);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(child.getLeft() + deltaX, child.getTop(),
                    child.getRight() + deltaX, child.getBottom());
        }
    }

    /**
     * Current event is move to left, if current view at right switch current
     * view to left.
     */
    private void switchCurrentViewToLeft() {
        //Log.i(TAG, "switchCurrentViewToLeft, mCurrentIndex = " + mCurrentIndex);
        if (!mCurrentViewAtLeft) {
            mPreIndex = mCurrentIndex;
            mCurrentIndex = mNextIndex;
            mNextIndex++;
            if (mNextIndex > maxItemCount - 1) {
                mNextIndex = 0;
            }
            mCurrentView = getChildAt(mCurrentIndex);
            mPreView = getChildAt(mPreIndex);
            mNextView = getChildAt(mNextIndex);
            mCurrentViewAtLeft = !mCurrentViewAtLeft;
        }
    }

    /**
     * Current event is move to right, if current view at left switch current
     * view to right.
     */
    private void switchCurrentViewToRight() {
        //Log.i(TAG, "switchCurrentViewToRight, mCurrentIndex = " + mCurrentIndex);
        if (mCurrentViewAtLeft) {
            mNextIndex = mCurrentIndex;
            mCurrentIndex = mPreIndex;
            mPreIndex--;
            if (mPreIndex < 0) {
                mPreIndex = maxItemCount - 1;
            }
            mCurrentView = getChildAt(mCurrentIndex);
            mPreView = getChildAt(mPreIndex);
            mNextView = getChildAt(mNextIndex);
            mCurrentViewAtLeft = !mCurrentViewAtLeft;
        }
    }

    /**
     * Current event is move to left,if current view move out of screen move the
     * current view to right and reBind the item change index.
     */
    private void moveToNext() {
        Log.i(TAG, "moveToNext, mCurrentIndex = " + mCurrentIndex);
        if (mCurrentView.getRight() < 0) {
            mCurrentView.layout(mPreView.getLeft() + getItemMargin(),
                    getItemY(), mPreView.getLeft() + getItemMargin()
                            + getItemWidth(), getItemY() + getItemHeight());

            if (mCurrentView.getTag() != null) {
                int listIndex = (Integer) mCurrentView.getTag();
                Log.i(TAG, "moveToNext, listIndex = " + listIndex);
                int index = (listIndex + maxItemCount) % mAdapter.getCount();
                Log.i(TAG, "moveToNext, index = " + index);
                mAdapter.bindView(mCurrentView, mAdapter.get(index));
                mCurrentView.setTag(index);
            }

            mPreIndex = mCurrentIndex;
            mCurrentIndex = mNextIndex;
            mNextIndex++;
            if (mNextIndex > maxItemCount - 1) {
                mNextIndex = 0;
            }
            mCurrentView = getChildAt(mCurrentIndex);
            mPreView = getChildAt(mPreIndex);
            mNextView = getChildAt(mNextIndex);
            moveToNext();
        }
    }

    /**
     * Current event is move to right,if current view move out of screen move
     * the current view to left and reBind the item change index.
     */
    private void moveToPre() {
        Log.i(TAG, "moveToPre, mCurrentIndex = " + mCurrentIndex);
        Log.i(TAG, "moveToPre, mCurrentIndex = " + mCurrentIndex);
        if (mCurrentView.getLeft() > getScreenWidth()) {
            mCurrentView.layout(mNextView.getLeft() - getItemMargin(),
                    getItemY(), mNextView.getLeft() - getItemMargin()
                            + getItemWidth(), getItemY() + getItemHeight());

            if (mCurrentView.getTag() != null) {
                int listIndex = (Integer) mCurrentView.getTag();
                Log.i(TAG, "moveToPre, listIndex = " + listIndex);
                int index = (listIndex - maxItemCount + mAdapter.getCount())
                        % mAdapter.getCount();
                Log.i(TAG, "moveToPre, index = " + index);
                mAdapter.bindView(mCurrentView, mAdapter.get(index));
                mCurrentView.setTag(index);
            }

            mNextIndex = mCurrentIndex;
            mCurrentIndex = mPreIndex;
            mPreIndex--;
            if (mPreIndex < 0) {
                mPreIndex = maxItemCount - 1;
            }
            mCurrentView = getChildAt(mCurrentIndex);
            mPreView = getChildAt(mPreIndex);
            mNextView = getChildAt(mNextIndex);
            moveToPre();
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        Log.i(TAG, "press down.");
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        Log.i(TAG, "press hold.");
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        Log.i(TAG, "onSingleTapUp.");
        return false;
    }


    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        //Log.i(TAG, "onScroll.");
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        Log.i(TAG, "onLongPress.");
    }

    public int getMaxItemCount() {
        return maxItemCount;
    }

    public void setMaxItemCount(int maxItemCount) {
        this.maxItemCount = maxItemCount;
    }

    public void setReLayout(boolean reLayout) {
        this.reLayout = reLayout;
    }

    public void setCanScroll(boolean canScroll) {
        this.canScroll = canScroll;
    }

    public int getItemX() {
        return itemX;
    }

    public void setItemX(int itemX) {
        this.itemX = itemX;
    }

    public int getItemY() {
        return itemY;
    }

    public void setItemY(int itemY) {
        this.itemY = itemY;
    }

    public int getItemWidth() {
        return itemWidth;
    }

    public void setItemWidth(int itemWidth) {
        this.itemWidth = itemWidth;
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    public int getItemMargin() {
        //return (screenWidth - itemWidth * (maxItemCount - 1) - initItemX * 2 - initItemGap * (maxItemCount - 1))/(maxItemCount - 2) + itemWidth;
        return initItemGap + itemWidth;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getInitItemX() {
        return initItemX;
    }

    public void setInitItemX(int initItemX) {
        this.initItemX = initItemX;
    }

    /**
     * The interface for item click callback.
     */
    public interface OnItemClickListener {
        public boolean onItemClick(int tag);
    }

}
