/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.os.storage.IMountService;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing storage usage on disk for known {@link StorageVolume} returned
 * by {@link StorageManager}. Calculates and displays usage of data types.
 */
public class Memory extends SettingsPreferenceFragment {
    private static final String TAG = "MemorySettings";

    private static final String TAG_CONFIRM_CLEAR_CACHE = "confirmClearCache";

    /* SPRD: Modify Bug 209961, add install location @{ */
    private static final String KEY_APP_INSTALL_LOCATION = "app_install_location";
    private static final int APP_INSTALL_AUTO = 0;
    private static final int APP_INSTALL_DEVICE = 1;
    private static final int APP_INSTALL_SDCARD = 2;
    private static final int APP_INSTALL_EXT_DEVICE = 3;

    private static final String APP_INSTALL_DEVICE_ID = "device";
    private static final String APP_INSTALL_SDCARD_ID = "sdcard";
    private static final String APP_INSTALL_AUTO_ID = "auto";
    private static final String APP_INSTALL_EXT_DEVICE_ID = "internal sdcard";
    private ListPreference mInstallLocation;
    /* @} */

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();

        addPreferencesFromResource(R.xml.device_info_memory);

        setHasOptionsMenu(true);
        /* SPRD: Modify Bug 209961, add install location @{ */
        mInstallLocation = (ListPreference) findPreference(KEY_APP_INSTALL_LOCATION);
        mInstallLocation.setValue(getAppInstallLocation());
        mInstallLocation.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String value = (String) newValue;
                /* SPRD：SPRD: ADD to toast message when sdcard unmounted @{ */
                if (value.equals(APP_INSTALL_SDCARD_ID)) {
                    if (!Environment.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
                        Toast.makeText(getActivity(), getString(R.string.sdcard_not_available), Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
                /* @} */
                handleUpdateAppInstallLocation(value);
                return false;
            }
        });
        /* @} */
        /**
         * SPRD: remove install prefence,when internal card is primary.
         */
        if (Environment.STORAGE_TYPE_EMMC_INTERNAL == Environment.getStorageType() && Environment.internalIsEmulated()) {
            removePreference(KEY_APP_INSTALL_LOCATION);
        }
        //remove app install location
        removePreference(KEY_APP_INSTALL_LOCATION);
        /* SPRD: Bug #313604 UMS Settings modify sprd USB storage@{ */
        if (Environment.getStorageType() == Environment.STORAGE_TYPE_EMMC_INTERNAL && !Environment.internalIsEmulated()) {
        	mInstallLocation.setEntries(getResources().getStringArray(R.array.app_ums_install_location_entries));
        	mInstallLocation.setEntryValues(getResources().getStringArray(R.array.app_ums_install_location_values));
        }
        /* @} */
    }

    /* SPRD: Modify Bug 209961, add install location @{ */
    /* SPRD：MODIFY to use Globla instead of System */
    protected void handleUpdateAppInstallLocation(final String value) {
        if(APP_INSTALL_DEVICE_ID.equals(value)) {
    		Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_DEVICE);
            
        } else if (APP_INSTALL_SDCARD_ID.equals(value)) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_SDCARD);
        } else if (APP_INSTALL_EXT_DEVICE_ID.equals(value)) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_EXT_DEVICE);            
        } else {
            // Should not happen, default to prompt...
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO);
        }
        mInstallLocation.setValue(value);
    }

    private String getAppInstallLocation() {
        int selectedLocation = Settings.Global.getInt(getContentResolver(),
                Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO);
        if (selectedLocation == APP_INSTALL_DEVICE) {
            return APP_INSTALL_DEVICE_ID;
        } else if (selectedLocation == APP_INSTALL_SDCARD) {
            /* SPRD: for Bug264513,the install location is not changed when the phone is shut down and remove sdcard @{ */
            if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStoragePathState())){
        		Settings.Global.putInt(getContentResolver(),
                        Settings.Global.DEFAULT_INSTALL_LOCATION, APP_INSTALL_DEVICE);
            
                return APP_INSTALL_DEVICE_ID;
            }
            /* @} */
            return APP_INSTALL_SDCARD_ID;
        } else  if (selectedLocation == APP_INSTALL_AUTO) {
            return APP_INSTALL_AUTO_ID;
        } else  if (selectedLocation == APP_INSTALL_EXT_DEVICE) {
            return APP_INSTALL_EXT_DEVICE_ID;
        } else {
            // Default value, should not happen.
            return APP_INSTALL_AUTO_ID;
        }
    }
    /* @} */
}
