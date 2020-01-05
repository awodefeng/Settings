/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class WallpaperTypeSettings extends SettingsPreferenceFragment {
    // devide launcher2 and launcher3 wallpaper
    private static final String WALLPAPER = "WallpaperPickerActivity";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wallpaper_settings);
        populateWallpaperTypes();
    }

    private void populateWallpaperTypes() {
        if (Utils.isMonkeyRunning()) {
            return;  // if Monkey test ，return
        }
        // Search for activities that satisfy the ACTION_SET_WALLPAPER action
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> rList;
        try {
            rList = pm.queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Exception e) {
            Log.e(WALLPAPER, "populateWallpaperTypes(): fail to call queryIntentActivities!");
            e.printStackTrace();
            return;
        }

        final PreferenceScreen parent = getPreferenceScreen();
        parent.setOrderingAsAdded(false);
        ArrayList<ResolveInfo> homeActivities = new ArrayList<ResolveInfo>();
        ComponentName currentDefaultHome  = pm.getHomeActivities(homeActivities);
        // Add Preference items for each of the matching activities
        for (ResolveInfo info : rList) {
            Preference pref = new Preference(getActivity());
            Intent prefIntent = new Intent(intent);
            prefIntent.setComponent(new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name));
            if (info.activityInfo.name.contains(WALLPAPER)) {
                if (currentDefaultHome != null) {
                    if (!info.activityInfo.packageName.equals(currentDefaultHome.getPackageName())) {
                        continue;
                    }
                }
            }
            pref.setIntent(prefIntent);
            CharSequence label = info.loadLabel(pm);
            if (label == null)
                label = info.activityInfo.packageName;
            pref.setTitle(label);
            parent.addPreference(pref);
        }
        homeActivities.clear();
    }
}
