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

package com.android.settings.bluetooth;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import com.android.settings.R;

import com.android.settings.Settings;
import java.util.Collection;

import java.util.concurrent.CopyOnWriteArrayList;

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

/* @} */
/**
 * BluetoothSettings is the Settings screen for Bluetooth configuration and
 * connection management.
 */
public final class BluetoothSettings extends DeviceListPreferenceFragment {
    private static final String TAG = "BluetoothSettings";

    private static final int MENU_ID_SCAN = Menu.FIRST;
    private static final int MENU_ID_RENAME_DEVICE = Menu.FIRST + 1;
    private static final int MENU_ID_VISIBILITY_TIMEOUT = Menu.FIRST + 2;
    private static final int MENU_ID_SHOW_RECEIVED = Menu.FIRST + 3;
    private static final int MENU_ID_FTP_SWITCH = Menu.FIRST + 4;
    // Bluetooth Ftp Profile not support on sphone
    private static boolean mIsSupportBtFtp = false;

    /* Private intent to show the list of received files */
    private static final String BTOPP_ACTION_OPEN_RECEIVED_FILES =
            "android.btopp.intent.action.OPEN_RECEIVED_FILES";

    /* Get/Set the ftp settings from ftp service */	
    private static final String BTFTP_ACTION_FTP_GET_SWITCH_STATUS =
            "android.btftp.intent.action.FTP_GET_SWITCH_STATUS";
    private static final String BTFTP_ACTION_FTP_SET_SWITCH_STATUS =
            "android.btftp.intent.action.FTP_SET_SWITCH_STATUS";

    /* FTP service will broadcase status change intent when receiving get /set ftp setting intent */
    private static final String BTFTP_ACTION_FTP_SWITCH_STATUS_CHANGE =
            "android.btftp.intent.action.FTP_SWITCH_STATUS_CHANGE";
    private static final String BTFTP_EXTRA = "ftp_switch_status";
	
    private BluetoothEnabler mBluetoothEnabler;

    private BluetoothDiscoverableEnabler mDiscoverableEnabler;

    private PreferenceGroup mPairedDevicesCategory;

    private PreferenceGroup mAvailableDevicesCategory;
    private boolean mAvailableDevicesCategoryIsPresent;
    private boolean mActivityStarted;

    private TextView mEmptyView;
    private Button mButton;

    private final IntentFilter mIntentFilter;
    private Handler mUiHandler;
    private Boolean shouldScanDisabled = false;

    private Object mObject = new Object();
    // accessed from inner class (not private to avoid thunks)
    Preference mMyDevicePreference;

    private boolean ftpSwitchStatus = false;
    private boolean isFtpSetSwitchOngoing = false;
	
    /* SPRD: modified for UUI empty view text color and size @{ */
    private static boolean UNIVERSEUI_SUPPORT = SystemProperties.getBoolean("universe_ui_support",false);
    private static final float DEFAULT_FONT_SIZE = 20f;
    
    private SwitchPreference mSwitchPreference;
    private Preference mEmptyPreference;
    /* @} */

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
                updateDeviceName();
            }

            if (mIsSupportBtFtp && action.equals(BTFTP_ACTION_FTP_SWITCH_STATUS_CHANGE)) {
                
                ftpSwitchStatus = intent.getBooleanExtra(BTFTP_EXTRA, false);
                isFtpSetSwitchOngoing = false;

                Log.d(TAG, "Received BluetoothFtp switch status change intent: " + ftpSwitchStatus);
                getActivity().invalidateOptionsMenu();
            }
        }

        private void updateDeviceName() {
            if (mLocalAdapter.isEnabled() && mMyDevicePreference != null) {
                mMyDevicePreference.setTitle(mLocalAdapter.getName());
            }
        }
    };

    public class BluetoothSwitchPreference extends SwitchPreference {
        private Context context;
        public BluetoothSwitchPreference(Context context) {
            super(context);
            this.context = context;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);

            ViewGroup preferenceLayout = (ViewGroup) view;
            TextView summary = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(1);
            float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            summary.setTextSize(20 / fontScale);   //20px to sp
        }
    }

    public BluetoothSettings() {
        super(DISALLOW_CONFIG_BLUETOOTH);
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        mIntentFilter.addAction(BTFTP_ACTION_FTP_SWITCH_STATUS_CHANGE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivityStarted = (savedInstanceState == null);    // don't auto start scan after rotation
        Log.d(TAG, "onActivityCreated()");
        addPreferencesFromResource(R.xml.bluetooth_settings);
        
        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);
        Activity activity = getActivity();
        mSwitchPreference = new BluetoothSwitchPreference(activity);
        mSwitchPreference.setOrder(0);
        mSwitchPreference.setEnabled(true);
        mSwitchPreference.setSwitchTextOff("");
        mSwitchPreference.setSwitchTextOn("");
        mSwitchPreference.setSummaryOn(R.string.accessibility_feature_state_on);
        mSwitchPreference.setSummaryOff(R.string.accessibility_feature_state_off);
        mBluetoothEnabler = new BluetoothEnabler(activity, mSwitchPreference);
        setHasOptionsMenu(true);
        mEmptyPreference = new Preference(getActivity());
        mEmptyPreference.setEnabled(false);
        mEmptyPreference.setOrder(1);
        mEmptyPreference.setSelectable(false);
        if (mIsSupportBtFtp) {
            Intent I = new Intent(BTFTP_ACTION_FTP_GET_SWITCH_STATUS);
            getActivity().sendBroadcast(I);
        }

        HandlerThread mThread = new HandlerThread(TAG);
        mThread.start();
        mUiHandler = new Handler(mThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mUiHandler != null) {
            mUiHandler.removeCallbacksAndMessages(null);
            Looper looper = mUiHandler.getLooper();
            if (looper != null) {
                looper.quit();
            }
            mUiHandler = null;
        }
    }

    @Override
    void addPreferencesForActivity() {
        /*addPreferencesFromResource(R.xml.bluetooth_settings);
        Activity activity = getActivity();
        Switch actionBarSwitch = new Switch(activity);

        /*if (activity instanceof PreferenceActivity) {
            PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
            if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                final int padding = activity.getResources().getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding);
                actionBarSwitch.setPaddingRelative(0, 0, padding, 0);
                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.END));
                actionBarSwitch.requestFocus();
            }
        }

        mBluetoothEnabler = new BluetoothEnabler(activity, actionBarSwitch);
        */

        //setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        // resume BluetoothEnabler before calling super.onResume() so we don't get
        // any onDeviceAdded() callbacks before setting up view in updateContent()
        //shouldScanDisabled = false;
        if (mBluetoothEnabler != null) {
            mBluetoothEnabler.resume();
        }

        if (mDiscoverableEnabler != null) {
            mDiscoverableEnabler.resume();
        }
        getActivity().registerReceiver(mReceiver, mIntentFilter);
        if (mLocalAdapter != null) {
            updateContent(mLocalAdapter.getBluetoothState(), mActivityStarted);
        }
        mUiHandler.post(mUpdateScanButton);
        super.onResume();

        Intent I = new Intent(BTFTP_ACTION_FTP_GET_SWITCH_STATUS);
        getActivity().sendBroadcast(I);
                
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBluetoothEnabler != null) {
            mBluetoothEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
        if (mDiscoverableEnabler != null) {
            mDiscoverableEnabler.pause();
        }
        mUiHandler.removeCallbacks(mUpdateScanButton);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mLocalAdapter == null) return;
        // If the user is not allowed to configure bluetooth, do not show the menu.
        if (isRestrictedAndNotPinProtected()) return;

        int ftpTestId = ftpSwitchStatus ? R.string.bluetooth_ftp_switch_off : R.string.bluetooth_ftp_switch_on;
        Log.d(TAG, "BluetoothFtp onCreateOptionsMenu: " + ftpSwitchStatus);
        boolean bluetoothIsEnabled = mLocalAdapter.getBluetoothState() == BluetoothAdapter.STATE_ON;
        boolean isDiscovering = mLocalAdapter.isDiscovering();
        int textId = isDiscovering ? R.string.bluetooth_searching_for_devices :
            R.string.bluetooth_search_for_devices;

        synchronized (mObject) {
            menu.add(Menu.NONE, MENU_ID_SCAN, 0, textId)
                    .setEnabled(bluetoothIsEnabled && !isDiscovering&& !shouldScanDisabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        menu.add(Menu.NONE, MENU_ID_RENAME_DEVICE, 0, R.string.bluetooth_rename_device)
                .setEnabled(bluetoothIsEnabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_VISIBILITY_TIMEOUT, 0, R.string.bluetooth_visibility_timeout)
                .setEnabled(bluetoothIsEnabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        if (mIsSupportBtFtp) {
            menu.add(Menu.NONE, MENU_ID_FTP_SWITCH, 0, ftpTestId)
                   .setEnabled(bluetoothIsEnabled && !isFtpSetSwitchOngoing)
                   .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        menu.add(Menu.NONE, MENU_ID_SHOW_RECEIVED, 0, R.string.bluetooth_show_received_files)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_SCAN:
                if (mLocalAdapter.getBluetoothState() == BluetoothAdapter.STATE_ON) {
                    synchronized (mObject) {
                        if(!shouldScanDisabled){
                            startScanning();
                        }
                    }
                }
                return true;

            case MENU_ID_RENAME_DEVICE:
                new BluetoothNameDialogFragment().show(
                        getFragmentManager(), "rename device");
                return true;

            case MENU_ID_VISIBILITY_TIMEOUT:
                new BluetoothVisibilityTimeoutFragment().show(
                        getFragmentManager(), "visibility timeout");
                return true;

            case MENU_ID_SHOW_RECEIVED:
                Intent intent = new Intent(BTOPP_ACTION_OPEN_RECEIVED_FILES);
                getActivity().sendBroadcast(intent);
                return true;
				
            case MENU_ID_FTP_SWITCH:

                Intent I = new Intent(BTFTP_ACTION_FTP_SET_SWITCH_STATUS);
                I.putExtra(BTFTP_EXTRA, !ftpSwitchStatus);
                getActivity().sendBroadcast(I);
                
                isFtpSetSwitchOngoing =  true;
                
                item.setEnabled(false);
                return true;
				
        }
        return super.onOptionsItemSelected(item);
    }

    private void startScanning() {
        Log.d(TAG, "startScanning");
        if (isRestrictedAndNotPinProtected()) return;
        if (!mAvailableDevicesCategoryIsPresent && mAvailableDevicesCategory != null) {
            ((BluetoothProgressCategory)mAvailableDevicesCategory).setProgress(true);
            getPreferenceScreen().addPreference(mAvailableDevicesCategory);
        }
        mLocalAdapter.startScanning(true);
    }

    @Override
    void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        mLocalAdapter.stopScanning();
        super.onDevicePreferenceClick(btPreference);
    }

    private void addDeviceCategory(PreferenceGroup preferenceGroup, int titleId,
            BluetoothDeviceFilter.Filter filter) {
        preferenceGroup.setTitle(titleId);
        getPreferenceScreen().addPreference(preferenceGroup);
        setFilter(filter);
        setDeviceListGroup(preferenceGroup);
        addCachedDevices();
        preferenceGroup.setEnabled(true);
    }

    private void updateContent(int bluetoothState, boolean scanState) {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        int messageId = 0;

        switch (bluetoothState) {
            case BluetoothAdapter.STATE_ON:
                preferenceScreen.removeAll();
                preferenceScreen.setOrderingAsAdded(true);
                mDevicePreferenceMap.clear();
                preferenceScreen.addPreference(mSwitchPreference);
                // This device
                if (mMyDevicePreference == null) {
                    mMyDevicePreference = new Preference(getActivity());
                }
                mMyDevicePreference.setTitle(mLocalAdapter.getName());
                if (getResources().getBoolean(com.android.internal.R.bool.config_voice_capable)) {
                   // mMyDevicePreference.setIcon(R.drawable.ic_bt_cellphone);    // for phones
                } else {
                   // mMyDevicePreference.setIcon(R.drawable.ic_bt_laptop);   // for tablets, etc.
                }
                mMyDevicePreference.setOrder(1);
                mMyDevicePreference.setPersistent(false);
                mMyDevicePreference.setEnabled(true);
                preferenceScreen.addPreference(mMyDevicePreference);

                if (!isRestrictedAndNotPinProtected()) {
                    if (mDiscoverableEnabler == null) {
                        mDiscoverableEnabler = new BluetoothDiscoverableEnabler(getActivity(),
                                mLocalAdapter, mMyDevicePreference);
                        mDiscoverableEnabler.resume();
                        LocalBluetoothManager.getInstance(getActivity()).setDiscoverableEnabler(
                                mDiscoverableEnabler);
                    }
                }

                // Paired devices category
                if (mPairedDevicesCategory == null) {
                    mPairedDevicesCategory = new PreferenceCategory(getActivity());
                } else {
                    mPairedDevicesCategory.removeAll();
                }
                mPairedDevicesCategory.setOrder(2);
                addDeviceCategory(mPairedDevicesCategory,
                        R.string.bluetooth_preference_paired_devices,
                        BluetoothDeviceFilter.BONDED_DEVICE_FILTER);
                int numberOfPairedDevices = mPairedDevicesCategory.getPreferenceCount();

                if (mDiscoverableEnabler != null) {
                    mDiscoverableEnabler.setNumberOfPairedDevices(numberOfPairedDevices);
                }

                // Available devices category
                if (mAvailableDevicesCategory == null) {
                    mAvailableDevicesCategory = new BluetoothProgressCategory(getActivity(), null);
                } else {
                    mAvailableDevicesCategory.removeAll();
                }
                mAvailableDevicesCategory.setOrder(3);
                if (!isRestrictedAndNotPinProtected()) {
                    addDeviceCategory(mAvailableDevicesCategory,
                            R.string.bluetooth_preference_found_devices,
                            BluetoothDeviceFilter.UNBONDED_DEVICE_FILTER);
                }
                int numberOfAvailableDevices = mAvailableDevicesCategory.getPreferenceCount();
                mAvailableDevicesCategoryIsPresent = true;

                if (numberOfAvailableDevices == 0 && !mLocalAdapter.isDiscovering()) {
                    preferenceScreen.removePreference(mAvailableDevicesCategory);
                    mAvailableDevicesCategoryIsPresent = false;
                }

                if (numberOfPairedDevices == 0) {
                    preferenceScreen.removePreference(mPairedDevicesCategory);
                    if (scanState == true) {
                        mActivityStarted = false;
                        startScanning();
                    } else {
                        if (!mAvailableDevicesCategoryIsPresent) {
                            preferenceScreen.addPreference(mAvailableDevicesCategory);
                        }
                    }
                }
                getActivity().invalidateOptionsMenu();
                return; // not break

            case BluetoothAdapter.STATE_TURNING_OFF:
                messageId = R.string.bluetooth_turning_off;
                break;

            case BluetoothAdapter.STATE_OFF:
                messageId = R.string.bluetooth_empty_list_bluetooth_off;
                setDeviceListGroup(mAvailableDevicesCategory);
                updateProgressUi(false);
                break;

            case BluetoothAdapter.STATE_TURNING_ON:
                messageId = R.string.bluetooth_turning_on;
                break;
        }

        setDeviceListGroup(preferenceScreen);
        removeAllDevices();
        //mEmptyView.setText(messageId);
        preferenceScreen.addPreference(mSwitchPreference);
        preferenceScreen.addPreference(mEmptyPreference);
        mEmptyPreference.setSummary(messageId);
        /* SPRD: modified for UUI empty view text color and size @{ */
        if (UNIVERSEUI_SUPPORT) {
            Resources res = getResources();
            mEmptyView.setTextColor(res.getColor(R.color.text_Empty_color_newui));
            mEmptyView.setTextSize(DEFAULT_FONT_SIZE);
        }
        /* @} */
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        updateContent(bluetoothState, true);
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        super.onScanningStateChanged(started);
        // Update options' enabled state
        getActivity().invalidateOptionsMenu();
    }

    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        setDeviceListGroup(getPreferenceScreen());
        removeAllDevices();
        updateContent(mLocalAdapter.getBluetoothState(), false);
    }

    private final View.OnClickListener mDeviceProfilesListener = new View.OnClickListener() {
        public void onClick(View v) {
            // User clicked on advanced options icon for a device in the list
            if (v.getTag() instanceof CachedBluetoothDevice) {
                if (isRestrictedAndNotPinProtected()) return;

                CachedBluetoothDevice device = (CachedBluetoothDevice) v.getTag();

                Bundle args = new Bundle(1);
                args.putParcelable(DeviceProfilesSettings.EXTRA_DEVICE, device.getDevice());

                ((PreferenceActivity) getActivity()).startPreferencePanel(
                        DeviceProfilesSettings.class.getName(), args,
                        R.string.bluetooth_device_advanced_title, null, null, 0);
            } else {
                Log.w(TAG, "onClick() called for other View: " + v); // TODO remove
            }
        }
    };

    /**
     * Add a listener, which enables the advanced settings icon.
     * @param preference the newly added preference
     */
    @Override
    void initDevicePreference(BluetoothDevicePreference preference) {
        CachedBluetoothDevice cachedDevice = preference.getCachedDevice();
        preference.setOnPreferenceChangeListener(mDevicePrefChangeListener);
        if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            // Only paired device have an associated advanced settings screen
            preference.setOnSettingsClickListener(mDeviceProfilesListener);
        }
    }

    @Override
    protected int getHelpResource() {
          // SPRD: no need to show 'Help' Option item
          //return R.string.help_url_bluetooth;
          return 0;
    }

    public final Runnable mUpdateScanButton = new Runnable() {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            synchronized (mObject) {
                shouldScanDisabled = false;
                Collection<CachedBluetoothDevice> cachedDevices = mLocalManager
                        .getCachedDeviceManager().getCachedDevicesCopy();
                for (CachedBluetoothDevice cachedDevice : cachedDevices) {
                    if (cachedDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                        shouldScanDisabled = true;
                        break;
                    }
                    CopyOnWriteArrayList<LocalBluetoothProfile> safeList = new CopyOnWriteArrayList<LocalBluetoothProfile>(cachedDevice.getProfiles());
                    for (LocalBluetoothProfile profile : safeList) {
                        int connectionStatus = cachedDevice
                                .getProfileConnectionState(profile);
                        if (connectionStatus == BluetoothProfile.STATE_CONNECTING) {
                            shouldScanDisabled = true;
                            break;
                        }
                    }
                }
            }
            try {
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }
            } catch (NullPointerException e) {
                Log.e(TAG, "getActivity() is null.");
            }
        }
    };

    private Preference.OnPreferenceChangeListener mDevicePrefChangeListener = new Preference.OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // TODO Auto-generated method stub
            if (getActivity() == null || getActivity().isFinishing()) {
                return false;
            }
            mUiHandler.post(mUpdateScanButton);
            return false;
        }
    };
}
