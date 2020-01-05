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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing storage usage on disk for known {@link StorageVolume} returned
 * by {@link StorageManager}. Calculates and displays usage of data types.
 */
public class PhoneMemory extends SettingsPreferenceFragment {
    private static final String TAG = "PhoneMemory";

    private static final String TAG_CONFIRM_CLEAR_CACHE = "confirmClearCache";

    private StorageManager mStorageManager;
    private UsbManager mUsbManager;

    private ArrayList<StorageVolumePreferenceCategory> mCategories = Lists.newArrayList();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // SPRD: Modify for bug896047.
        mStorageManager = StorageManager.from(getActivity().getApplicationContext());
        mStorageManager.registerListener(mStorageListener);

        addPreferencesFromResource(R.xml.phone_info_memory);

        addCategory(StorageVolumePreferenceCategory.buildForInternal(context));

        /* SPRD: Modify for bug661530. @{
        final StorageVolume[] storageVolumes = mStorageManager.getVolumeList();
        for (StorageVolume volume : storageVolumes) {
            if (!volume.isEmulated() && !isSdCardPath(volume)) {
                addCategory(StorageVolumePreferenceCategory.buildForPhysical(context, volume));
            }
        }
        @}*/

        setHasOptionsMenu(true);
    }

    private void addCategory(StorageVolumePreferenceCategory category) {
        mCategories.add(category);
        getPreferenceScreen().addPreference(category);
       // category.init();
    }

    private boolean isSdCardPath(StorageVolume volume){
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);
        boolean isSdCardPath = false;
        String sdDir = null;
        sdDir = (Environment.getExternalStoragePath()).toString();//SPRD: add for bug628933
        if(sdDir != null){
            isSdCardPath = sdDir.equals(volume.getPath());
        }
        return isSdCardPath;
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        // SPRD:ADD add action for receiver.
        intentFilter.addAction(UsbManager.ACTION_USB_STATE);
        intentFilter.addDataScheme("file");
        getActivity().registerReceiver(mMediaScannerReceiver, intentFilter);

        for (StorageVolumePreferenceCategory category : mCategories) {
            category.init();
            category.onResume();
        }
    }

    StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            Log.i(TAG, "Received storage state changed notification that " + path +
                    " changed state from " + oldState + " to " + newState);
            for (StorageVolumePreferenceCategory category : mCategories) {
                final StorageVolume volume = category.getStorageVolume();
                if (volume != null && path.equals(volume.getPath())) {
                    category.onStorageStateChanged();
                    break;
                }
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mMediaScannerReceiver);
        for (StorageVolumePreferenceCategory category : mCategories) {
            category.onPause();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() --- start");
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (StorageVolumePreferenceCategory.KEY_CACHE.equals(preference.getKey())) {
            ConfirmClearCacheFragment.show(this);
            return true;
        }

        for (StorageVolumePreferenceCategory category : mCategories) {
            Intent intent = category.intentForClick(preference);
            if (intent != null) {
                // Don't go across app boundary if monkey is running
                if (!Utils.isMonkeyRunning()) {
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException anfe) {
                        Log.w(TAG, "No activity found for intent " + intent);
                    }
                }
                return true;
            }
        }

        return false;
    }

    private final BroadcastReceiver mMediaScannerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UsbManager.ACTION_USB_STATE)) {
               boolean isUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
               String usbFunction = mUsbManager.getDefaultFunction();
               for (StorageVolumePreferenceCategory category : mCategories) {
                   category.onUsbStateChanged(isUsbConnected, usbFunction);
               }
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                for (StorageVolumePreferenceCategory category : mCategories) {
                    category.onMediaScannerFinished();
                }
            }
        }
    };

    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    private void onCacheCleared() {
        for (StorageVolumePreferenceCategory category : mCategories) {
            category.onCacheCleared();
        }
    }

    private static class ClearCacheObserver extends IPackageDataObserver.Stub {
        private final PhoneMemory mTarget;
        private int mRemaining;

        public ClearCacheObserver(PhoneMemory target, int remaining) {
            mTarget = target;
            mRemaining = remaining;
        }

        @Override
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
            synchronized (this) {
                if (--mRemaining == 0) {
                    mTarget.onCacheCleared();
                }
            }
        }
    }

    /**
     * Dialog to request user confirmation before clearing all cache data.
     */
    public static class ConfirmClearCacheFragment extends DialogFragment {
        public static void show(PhoneMemory parent) {
            if (!parent.isAdded()) return;

            final ConfirmClearCacheFragment dialog = new ConfirmClearCacheFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_CLEAR_CACHE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.memory_clear_cache_title);
            builder.setMessage(getString(R.string.memory_clear_cache_message));

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final PhoneMemory target = (PhoneMemory) getTargetFragment();
                    final PackageManager pm = context.getPackageManager();
                    final List<PackageInfo> infos = pm.getInstalledPackages(0);
                    final ClearCacheObserver observer = new ClearCacheObserver(
                            target, infos.size());
                    for (PackageInfo info : infos) {
                        pm.deleteApplicationCacheFiles(info.packageName, observer);
                    }
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            return builder.create();
        }
    }
}
