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

package com.android.settings.applications;

import java.util.ArrayList;

import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.settings.R;

import com.android.settings.SettingsPreferenceFragment;


/**
 * Activity to pick an application that will be used to display installation information and
 * options to uninstall/delete user data for system applications. This activity
 * can be launched through Settings or via the ACTION_MANAGE_PACKAGE_STORAGE
 * intent.
 */
public class ManageAppSettings extends SettingsPreferenceFragment {

    static final String TAG = "ManageAppSettings";
    static final boolean DEBUG = false;

    static final String KEY_MANAGE_APP_CATEGORY = "manage_app_settings_category";
    static final String KEY_FILTER_APPS_THIRD_PARTY = "filter_apps_third_party";
    static final String KEY_FILTER_APPS_ONSDCARD = "filter_apps_onsdcard";
    static final String KEY_FILTER_APPS_RUNNING = "filter_apps_running";
    static final String KEY_FILTER_APPS_ALL = "filter_apps_all";
    static final String KEY_FILTER_APPS_DISABLED = "filter_apps_disabled";
    private PreferenceCategory mManageAppCategory;
    private PreferenceScreen mFilterAppsThirdParty;
    private PreferenceScreen mFilterAppsOnsdcard;
    private PreferenceScreen mFilterAppsRunning;
    private PreferenceScreen mFilterAppsAll;
    private PreferenceScreen mFilterAppsDisabled;

    private ApplicationsState mApplicationsState;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.manage_app_settings);
        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mApplicationsState.doResumeIfNeededLocked();

        mManageAppCategory = (PreferenceCategory) findPreference(KEY_MANAGE_APP_CATEGORY);
        mFilterAppsThirdParty = (PreferenceScreen) mManageAppCategory.findPreference(KEY_FILTER_APPS_THIRD_PARTY);
        mFilterAppsOnsdcard = (PreferenceScreen) mManageAppCategory.findPreference(KEY_FILTER_APPS_ONSDCARD);
        mFilterAppsRunning = (PreferenceScreen) mManageAppCategory.findPreference(KEY_FILTER_APPS_RUNNING);
        mFilterAppsAll = (PreferenceScreen) mManageAppCategory.findPreference(KEY_FILTER_APPS_ALL);
        mFilterAppsDisabled = (PreferenceScreen) mManageAppCategory.findPreference(KEY_FILTER_APPS_DISABLED);

    }

    @Override
    public void onResume() {
        super.onResume();
        
        if(mApplicationsState.haveDisabledApps()){
            mManageAppCategory.addPreference(mFilterAppsDisabled);
        } else {
            mManageAppCategory.removePreference(mFilterAppsDisabled);
        }
        if (Environment.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
            mManageAppCategory.addPreference(mFilterAppsOnsdcard);
        } else {
            mManageAppCategory.removePreference(mFilterAppsOnsdcard);
        }
    }

    private void startPreferencePanel(int resid, String filterAppItem) {
        int resId = resid;
        Bundle args = new Bundle();
        args.putString(ManageApplications.FILTER_APP_ITEM, filterAppItem);

        PreferenceActivity pa = (PreferenceActivity)getActivity();
        pa.startPreferencePanel(ManageApplications.class.getName(), args,
                resId, null, this, -1);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        int resid = R.string.filter_apps_all;
        String filterAppItem = KEY_FILTER_APPS_ALL;

        if(preference == mFilterAppsThirdParty){
            resid = R.string.filter_apps_third_party;
            filterAppItem = KEY_FILTER_APPS_THIRD_PARTY;
        } else if(preference == mFilterAppsOnsdcard){
            resid = R.string.filter_apps_onsdcard;
            filterAppItem = KEY_FILTER_APPS_ONSDCARD;
        } else if(preference == mFilterAppsRunning){
            resid = R.string.filter_apps_running;
            filterAppItem = KEY_FILTER_APPS_RUNNING;
        } else if(preference == mFilterAppsAll){
            resid = R.string.filter_apps_all;
            filterAppItem = KEY_FILTER_APPS_ALL;
        } else if(preference == mFilterAppsDisabled){
            resid = R.string.filter_apps_disabled;
            filterAppItem = KEY_FILTER_APPS_DISABLED;
        }

        startPreferencePanel(resid, filterAppItem);

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    
}
