/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.R;

import com.android.settings.SettingsPreferenceFragment;


/**
 * Activity to pick an application that will be used to display installation information and
 * options to uninstall/delete user data for system applications. This activity
 * can be launched through Settings or via the ACTION_MANAGE_PACKAGE_STORAGE
 * intent.
 */
public class DataUsageSummaryEx extends SettingsPreferenceFragment {

    static final String TAG = "DataUsageSummaryEx";
    static final boolean DEBUG = false;

    public static final String SUB_ID = "subId";
    static final String DATA_USAGE_SETTINGS_CATEGORY = "data_usage_settings_category";
    static final String KEY_DATA_USAGE_SETTINGS_SIM1 = "data_usage_settings_sim1";
    static final String KEY_DATA_USAGE_SETTINGS_SIM2 = "data_usage_settings_sim2";
    // SPRD: Add for bug675035.
    static final String KEY_DATA_USAGE_SETTINGS_WLAN = "data_usage_settings_wlan";

    private PreferenceCategory mDataUsageSettingsCategory;
    private PreferenceScreen mDataUsageSettingsSim1;
    private PreferenceScreen mDataUsageSettingsSim2;
    // SPRD: Add for bug675035.
    private PreferenceScreen mDataUsageSettingsWlan;

    private SimManager mSimManager;
    private Sim mSims[];
    private int SIM_CARD_1 = 0;
    private int SIM_CARD_2 = 1;
    // SPRD: Add for bug675035.
    private int WLAN = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.data_usage_summary_ex);
        mDataUsageSettingsCategory = (PreferenceCategory) findPreference(DATA_USAGE_SETTINGS_CATEGORY);
        mDataUsageSettingsSim1 = (PreferenceScreen) mDataUsageSettingsCategory.findPreference(KEY_DATA_USAGE_SETTINGS_SIM1);
        mDataUsageSettingsSim2 = (PreferenceScreen) mDataUsageSettingsCategory.findPreference(KEY_DATA_USAGE_SETTINGS_SIM2);
        // SPRD: Add for bug675035.
        mDataUsageSettingsWlan = (PreferenceScreen) mDataUsageSettingsCategory
                .findPreference(KEY_DATA_USAGE_SETTINGS_WLAN);

        mSimManager = SimManager.get(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        mDataUsageSettingsCategory.removeAll();
        if(null != mSimManager){
            mSims = mSimManager.getSims();
        }
        Log.d(TAG,"mSims.length:"+mSims.length);
        if(mSims.length > 0){
	    int mNumSlots = TelephonyManager.getPhoneCount();
	    Log.d(TAG,"mNumSlots:"+mNumSlots);
            boolean isSim1CardExist = TelephonyManager.getDefault(SIM_CARD_1).hasIccCard();
	    boolean isSim2CardExist = false;
	    if(mNumSlots > 1){
                isSim2CardExist = TelephonyManager.getDefault(SIM_CARD_2).hasIccCard();
	    }
            if(mSims.length == 1){
                if(isSim1CardExist || isSim2CardExist){
                    mDataUsageSettingsCategory.addPreference(mDataUsageSettingsSim1);
                    mDataUsageSettingsSim1.setTitle(mSims[SIM_CARD_1].getName());
                }
            } else if(mSims.length == 2){
                if(isSim1CardExist){
                    mDataUsageSettingsCategory.addPreference(mDataUsageSettingsSim1);
                    mDataUsageSettingsSim1.setTitle(mSims[SIM_CARD_1].getName());
                }
                if(isSim2CardExist){
                    mDataUsageSettingsCategory.addPreference(mDataUsageSettingsSim2);
                    mDataUsageSettingsSim2.setTitle(mSims[SIM_CARD_2].getName());
                }
            }
        } else {
            mDataUsageSettingsCategory.addPreference(mDataUsageSettingsSim1);
            mDataUsageSettingsSim1.setTitle(R.string.sim_slot_empty);
            mDataUsageSettingsSim1.setEnabled(false);
        }

        /* SPRD: Add for bug675035. @{ */
        if (isWifiSupported(getActivity())) {
            mDataUsageSettingsCategory.addPreference(mDataUsageSettingsWlan);
            mDataUsageSettingsWlan.setTitle(R.string.data_usage_tab_wifi);
        }
        /* @} */
    }

    /* SPRD: Add for bug675035. @{ */
    private boolean isWifiSupported(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
    }
    /* @} */

    private void startPreferencePanel(int subId) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.DATAUSAGE");
        intent.putExtra(SUB_ID, subId);
        Log.d(TAG, "startPreferencePanel: subId="+subId);
        startActivity(intent);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        Log.d(TAG, "onPreferenceTreeClick: preference="+preference.getKey());
        int subId = -1;
        if (null != mDataUsageSettingsSim1 && preference == mDataUsageSettingsSim1) {
            subId = mSims[SIM_CARD_1].getPhoneId();
        } else if (null != mDataUsageSettingsSim2 && preference == mDataUsageSettingsSim2) {
            subId = mSims[SIM_CARD_2].getPhoneId();
        /* SPRD: Add for bug675035. @{ */
        } else if (mDataUsageSettingsWlan != null && preference == mDataUsageSettingsWlan) {
            subId = WLAN;
        } else {
            Log.e("TAG", "This is an exception click!");
        }
        /* @} */

        startPreferencePanel(subId);

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

}
