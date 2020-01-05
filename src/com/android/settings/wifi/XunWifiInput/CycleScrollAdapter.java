package com.android.settings.wifi.XunWifiInput;

/**
 * Created by mayanjun on 2017/11/4.
 */
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

public abstract class CycleScrollAdapter<T>  {
    private List<T> list;
    private CycleScrollView<T> mCycleScrollView;
    Context mContext;

    /**
     * Initial CycleScrollAdapter bind list to view.
     *
     * @param list
     *            The list data.
     * @param cycleScrollView
     *            The CycleScrollView.
     * @param context
     *            The Context.
     */
    public CycleScrollAdapter(List<T> list, CycleScrollView<T> cycleScrollView,
                              Context context) {
        this.list = list;
        mContext = context;
        mCycleScrollView = cycleScrollView;
        mCycleScrollView.setAdapter(this);
        GetScreenWidthPixels();
        initView(list);
    }

    /**
     * Get screen width pixels.
     */
    private void GetScreenWidthPixels() {
        DisplayMetrics dm = new DisplayMetrics();
//        Activity a = (Activity) mContext;
//        a.getWindowManager().getDefaultDisplay().getMetrics(dm);
        WindowManager wm = (WindowManager) mContext.getSystemService(mContext.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        mCycleScrollView.setScreenWidth(dm.widthPixels);
    }

    /**
     * Bind list to view.
     *
     * @param list
     *            The list data.
     */
    public void initView(List<T> list) {
        if (list == null || list.size() == 0) {
            return;
        }

        // Clear all view from ViewGroup at first.
        mCycleScrollView.removeAllViewsInLayout();

        // Loop list.
        for (int i = 0; i < list.size(); i++) {
            /**
             * If list size more than MaxItemCount break the loop, only create
             * view count is MaxItemCount.
             */
            if (i == mCycleScrollView.getMaxItemCount()) {
                break;
            }

            /**
             * If list size less than MaxItemCount at the last loop reLayout
             * otherwise at the MaxItemCount index reLayout.
             */
            if (i == list.size() - 1
                    || i == mCycleScrollView.getMaxItemCount() - 1) {
                mCycleScrollView.setItemX(mCycleScrollView.getInitItemX());
                mCycleScrollView.setReLayout(true);
            }
            add(list.get(i), i);
        }

        /**
         * If list count more than MaxItemCount the view can scroll otherwise
         * can not scroll.
         */
        if (list.size() >= mCycleScrollView.getMaxItemCount()) {
            mCycleScrollView.setCanScroll(true);
        } else {
            mCycleScrollView.setCanScroll(false);
        }

        /**
         * If list count more than MaxItemCount reBuild index.
         */
        mCycleScrollView.createIndex();
    }

    /**
     * Get list size.
     *
     * @return The list size.
     */
    public int getCount() {
        return list.size();
    }

    /**
     * Returns the element at the specified location in this
     *
     * @param index
     *            the index of the element to return.
     * @return the element at the specified location.
     */
    public T get(int index) {
        return list.get(index);
    }

    /**
     * Adds the specified object at the end of this and refresh view.
     *
     * @param t
     *            the object to add.
     */
    public void addItem(T t) {
        list.add(t);
        initView(list);
    }

    /**
     * Removes the first occurrence of the specified object from this and
     * refresh view.
     *
     * @param t
     *            the object to remove.
     */
    public void removeItem(T t) {
        list.remove(t);
        initView(list);
    }

    /**
     * clear all the itmes in the list and
     * refresh view.
     *
     *            the object to remove.
     */
    public void clear() {
        list.clear();
        initView(list);
    }

    /**
     * Add the specified view to the index.
     *
     * @param t
     *            The data to add.
     * @param index
     *            the index.
     */
    private void add(T t, int index) {
        View view = getView(t);
        ComputeItemSize(view);
        mCycleScrollView.addView(view);
        view.setTag(index);
    }

    /**
     * If item size is null compute item size.
     *
     * @param view
     *            the item view.
     */
    private void ComputeItemSize(View view) {
        if (mCycleScrollView.getItemWidth() == 0
                || mCycleScrollView.getItemHeight() == 0) {
            int w = View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED);
            int h = View.MeasureSpec.makeMeasureSpec(0,
                    View.MeasureSpec.UNSPECIFIED);
            view.measure(w, h);
            int height = view.getMeasuredHeight();
            int width = view.getMeasuredWidth();
            mCycleScrollView.setItemHeight(height);
            mCycleScrollView.setItemWidth(width);
        }
    }

    /**
     * Get item view.
     *
     * @param t
     *            the data need bind to view.
     * @return the view.
     */
    public abstract View getView(T t);

    /**
     * Bind the item to view.
     *
     * @param child
     *            the item view need bind.
     * @param t
     *            the item.
     */
    public abstract void bindView(View child, T t);
}
