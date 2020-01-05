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

package com.android.settings;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.preference.Preference;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

//SPRD: feature 810275
public class ApnPreference extends Preference implements
        CompoundButton.OnCheckedChangeListener/*, OnClickListener*/ {
    final static String TAG = "ApnPreference";

    public ApnPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ApnPreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.apnPreferenceStyle);
    }

    public ApnPreference(Context context) {
        this(context, null);
    }

    private static String mSelectedKey = null;
    private static CompoundButton mCurrentChecked = null;
    private boolean mProtectFromCheckedChange = false;
    private boolean mSelectable = true;
    private int mPhoneId; // SPRD : add for multi-sim
    /* SPRD: feature 810275  @{ */
    private int mId;
    private RadioButton mRadioButton;
    private boolean mIsSelected = false;
    /* @}*/

    @Override
    public View getView(View convertView, ViewGroup parent) {
        View view = super.getView(convertView, parent);

        View widget = view.findViewById(R.id.apn_radiobutton);
        if ((widget != null) && widget instanceof RadioButton) {
            RadioButton rb = (RadioButton) widget;
            //SPRD: feature 810275
            mRadioButton = rb;
            if (mSelectable) {
                rb.setOnCheckedChangeListener(this);
                // SPRD: add for <Bug#255818> According apn type display the button
                rb.setVisibility(View.VISIBLE);
                // SPRD: add for <Bug#255818> According apn type display the button

                boolean isChecked = getKey().equals(mSelectedKey);
                if (isChecked) {
                    mCurrentChecked = rb;
                    mSelectedKey = getKey();
                }

                mProtectFromCheckedChange = true;
                rb.setChecked(isChecked);
                // SPRD: Bug 646578 modify listview focus
                /* SPRD: feature 810275  @{ */
                mProtectFromCheckedChange = false;
            } else {
                rb.setVisibility(View.GONE);
            }
        }

        view.setTag(mId);
        /* @}*/

        return view;
    }

    public boolean isChecked() {
        return getKey().equals(mSelectedKey);
    }

    public void setChecked() {
        mSelectedKey = getKey();
    }

    /* SPRD: feature 810275  @{ */
    public void setChecked(boolean checked) {
        Log.i(TAG, "setChecked()");
        mSelectedKey = getKey();
        if (checked != mIsSelected) {
            mIsSelected = checked;
        }
        if (mRadioButton != null) {
            mRadioButton.setChecked(mIsSelected);
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.i(TAG, "ID: " + getKey() + " :" + isChecked);
        if (mProtectFromCheckedChange) {
            return;
        }

        if (isChecked) {
            mIsSelected = true;
            if (mCurrentChecked != null) {
                mCurrentChecked.setChecked(false);
            }
            mCurrentChecked = buttonView;
        } else {
            mIsSelected = false;
            mCurrentChecked = null;
            mSelectedKey = null;
        }
    }
    /* @}*/

    public void setSelectable(boolean selectable) {
        mSelectable = selectable;
    }

    public boolean getSelectable() {
        return mSelectable;
    }

    /* SPRD: feature 810275  @{ */
    public void setId(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }
    /* @}*/
}
