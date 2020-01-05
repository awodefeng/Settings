/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcel;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.internal.os.BatteryStatsImpl;
import com.android.settings.R;

import com.sprd.android.support.featurebar.FeatureBarHelper;

public class BatteryHistoryDetail extends Fragment {
    public static final String EXTRA_STATS = "stats";

    /* SPRD: Add for bug673073. @{ */
    private static TextView mOptionView;
    private static TextView mCenterView;
    private static TextView mBackView;
    private ViewGroup mViewGroup;
    /* @} */
    private BatteryStatsImpl mStats;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        byte[] data = getArguments().getByteArray(EXTRA_STATS);
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(data, 0, data.length);
        parcel.setDataPosition(0);
        mStats = com.android.internal.os.BatteryStatsImpl.CREATOR
                .createFromParcel(parcel);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.preference_batteryhistory, null);
        // SPRD: Add for bug673073.
        setSoftKey();
        BatteryHistoryChart chart = (BatteryHistoryChart)view.findViewById(
                R.id.battery_history_chart);
        chart.setStats(mStats);
        // SPRD: add ScrollView for display battery history chart
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        scrollView.addView(view);
        // SPRD: Add for bug673073 & bug821131.
        // scrollView.setOnFocusChangeListener(mFocusChangeListener);

        view.setMinimumHeight(getScreenMaxSize());

        return scrollView;
    }

    /**
     * SPRD: get Display size @{
     */
    private int getScreenMaxSize() {

        // get display metrics
        Activity activity = getActivity();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int dispHeight = dm.heightPixels;
        int dispWidth = dm.widthPixels;

        // get max both of them
        if (dispHeight < dispWidth) {
            dispHeight = dispWidth;
        }

        // Get action bar height like that the FW setContentHeight
        TypedValue heightValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, heightValue, true);
        final int actionBarHeight = TypedValue.complexToDimensionPixelSize(heightValue.data,
                activity.getResources().getDisplayMetrics());

        // get status bar height
        int statusBarHeight = activity.getResources().getDimensionPixelSize(
                    com.android.internal.R.dimen.status_bar_height);

        return dispHeight - (actionBarHeight + statusBarHeight);
    }
    /** SPRD: get Display size @} */

    /* SPRD: Add for bug673073. @{ */
    private void setSoftKey() {
        FeatureBarHelper helperBar = new FeatureBarHelper(getActivity());
        mViewGroup = helperBar.getFeatureBar();
        if (mViewGroup != null) {
            mOptionView = (TextView) helperBar.getOptionsKeyView();
            mOptionView.setText(R.string.default_feature_bar_options);
            mCenterView = (TextView) helperBar.getCenterKeyView();
            mCenterView.setText(R.string.default_feature_bar_center);
            mBackView = (TextView) helperBar.getBackKeyView();
            mBackView.setText(R.string.default_feature_bar_back);
            mViewGroup.removeView(mOptionView);
            // SPRD: Modify for bug821131.
            mViewGroup.removeView(mCenterView);
        }
    }

    OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener() {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            mViewGroup.removeView(mCenterView);
            if (!hasFocus) {
                mViewGroup.addView(mCenterView);
            }
        }

    };
    /* @} */
}
