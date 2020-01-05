/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.WirelessSettings;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BluetoothEnabler is a helper to manage the Bluetooth on/off checkbox
 * preference. It turns on/off Bluetooth and ensures the summary of the
 * preference reflects the current state.
 */
public final class BluetoothEnabler implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    private final Context mContext;
    private SwitchPreference mSwitchPreference;
    private boolean mValidListener;
    private final LocalBluetoothAdapter mLocalAdapter;
    private AtomicBoolean mRegister = new AtomicBoolean(false);
    private final IntentFilter mIntentFilter;
    private boolean supportBtWifiSoftApCoexit = true;
    private static String TAG = "BluetoothEnabler";
    private SwitchUiHandler mSwitchHandler;
    private final int MSG_RECOVER_STATE = 1;
    private WifiManager mWifiManager;

    //TelephonyManager used to TELL LTE the BT status
    private TelephonyManager mTelephonyManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Broadcast receiver is always running on the UI thread here,
            // so we don't need consider thread synchronization.
           //   int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
          //    handleStateChanged(state);

            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                handleStateChanged(state);
            }
        }
    };

    public BluetoothEnabler(Context context,SwitchPreference switchPreference) {
        mContext = context;
        mSwitchPreference = switchPreference;
        mValidListener = false;
        mSwitchPreference.setPersistent(false);

        LocalBluetoothManager manager = LocalBluetoothManager.getInstance(context);
        if (manager == null) {
            // Bluetooth is not supported
            mLocalAdapter = null;
            mSwitchPreference.setEnabled(false);
        } else {
            mLocalAdapter = manager.getBluetoothAdapter();
        }
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (SystemProperties.get("ro.btwifisoftap.coexist", "true").equals(
                "false")) {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext
                        .getSystemService(Context.WIFI_SERVICE);
            }
            mSwitchHandler = new SwitchUiHandler();
            supportBtWifiSoftApCoexit = false;
        }

        //TelephonyManager used to TELL LTE the BT status
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }

    }

    public void resume() {
        if (mLocalAdapter == null) {
            mSwitchPreference.setEnabled(false);
            return;
        }

        // Bluetooth state is not sticky, so set it manually
        handleStateChanged(mLocalAdapter.getBluetoothState());

        if (!mRegister.get()) {
            mContext.registerReceiver(mReceiver, mIntentFilter);
            mRegister.set(true);
        }
        //mSwitch.setOnCheckedChangeListener(this);
        //mSwitchPreference.setOnPreferenceClickListener(this);
        mSwitchPreference.setOnPreferenceChangeListener(this);
        mValidListener = true;
    }

    public void pause() {
        if (mLocalAdapter == null) {
            return;
        }

        if (mRegister.get()) {
            mContext.unregisterReceiver(mReceiver);
            mRegister.set(false);
        }
        //mSwitchPreference.setOnPreferenceClickListener(null);
        mSwitchPreference.setOnPreferenceChangeListener(null);
        mValidListener = false;
    }

    public void setSwitch(SwitchPreference switch_) {
        if (mSwitchPreference == switch_) return;
        //mSwitchPreference.setOnPreferenceClickListener(null);
        mSwitchPreference.setOnPreferenceChangeListener(null);
        mSwitchPreference = switch_;
        //mSwitchPreference.setOnPreferenceClickListener(mValidListener ? this : null);
        mSwitchPreference.setOnPreferenceChangeListener(mValidListener ? this : null);

        int bluetoothState = BluetoothAdapter.STATE_OFF;
        if (mLocalAdapter != null) bluetoothState = mLocalAdapter.getBluetoothState();
        boolean isOn = bluetoothState == BluetoothAdapter.STATE_ON;
        boolean isOff = bluetoothState == BluetoothAdapter.STATE_OFF;
        setChecked(isOn);
        mSwitchPreference.setEnabled(isOn || isOff);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // Show toast message if Bluetooth is not allowed in airplane mode
        boolean isChecked = mSwitchPreference.isChecked();
        if (isChecked &&
                !WirelessSettings.isRadioAllowed(mContext, Settings.Global.RADIO_BLUETOOTH)) {
            Toast.makeText(mContext, R.string.wifi_in_airplane_mode, Toast.LENGTH_SHORT).show();
            // Reset switch to off
            //buttonView.setChecked(false);
        }

        if (!supportBtWifiSoftApCoexit) {
            if (isChecked && mWifiManager.isSoftapEnablingOrEnabled()) {
                mSwitchHandler.sendEmptyMessage(MSG_RECOVER_STATE);
                Toast.makeText(this.mContext,
                        R.string.bt_softap_cannot_coexist, Toast.LENGTH_SHORT)
                        .show();
                return true;
            } else if (isChecked == false && mWifiManager.isSoftapEnablingOrEnabled()){
                return true;
            }
        }
        Log.d(TAG,"Check bluetooth enable change");
        if (mLocalAdapter != null) {

            try {
                //TelephonyManager used to TELL LTE the BT status
                if (isChecked && (mTelephonyManager != null)) {
                    Log.d(TAG,"Tell LTE BT opened!");
                    mTelephonyManager.switchBT(true);
                    Log.d(TAG,"Tell LTE BT opened END!");
                }
            } catch (Exception e) {}

            Log.d(TAG,"onPreferenceChange isChecked : " + isChecked);
            mLocalAdapter.setBluetoothEnabled(isChecked);

            try {
                //TelephonyManager used to TELL LTE the BT status
                if (!isChecked && (mTelephonyManager != null)) {
                    Log.d(TAG,"Tell LTE BT closed!");
                    mTelephonyManager.switchBT(false);
                    Log.d(TAG,"Tell LTE BT closed END!");
                }
            } catch (Exception e) {}

        }
        mSwitchPreference.setEnabled(false);
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        //Just update switch if not user click.
        if ((Boolean)newValue == mSwitchPreference.isChecked()) {
            return true;
        }

        boolean isChecked = (Boolean)newValue;//mSwitch.isChecked();
        // Show toast message if Bluetooth is not allowed in airplane mode
        if (isChecked &&
                !WirelessSettings.isRadioAllowed(mContext, Settings.Global.RADIO_BLUETOOTH)) {
            Toast.makeText(mContext, R.string.wifi_in_airplane_mode, Toast.LENGTH_SHORT).show();
            // Reset switch to off
            //buttonView.setChecked(false);
        }

        if (!supportBtWifiSoftApCoexit) {
            if (isChecked && mWifiManager.isSoftapEnablingOrEnabled()) {
                mSwitchHandler.sendEmptyMessage(MSG_RECOVER_STATE);
                Toast.makeText(this.mContext,
                        R.string.bt_softap_cannot_coexist, Toast.LENGTH_SHORT)
                        .show();
                return true;
            } else if (isChecked == false && mWifiManager.isSoftapEnablingOrEnabled()){
                return true;
            }
        }
        Log.d(TAG,"Check bluetooth enable change");
        if (mLocalAdapter != null) {

            try {
                //TelephonyManager used to TELL LTE the BT status
                if (isChecked && (mTelephonyManager != null)) {
                    Log.d(TAG,"Tell LTE BT opened!");
                    mTelephonyManager.switchBT(true);
                    Log.d(TAG,"Tell LTE BT opened END!");
                }
            } catch (Exception e) {}

            Log.d(TAG,"onPreferenceChange isChecked : " + isChecked);
            mLocalAdapter.setBluetoothEnabled(isChecked);

            try {
                //TelephonyManager used to TELL LTE the BT status
                if (!isChecked && (mTelephonyManager != null)) {
                    Log.d(TAG,"Tell LTE BT closed!");
                    mTelephonyManager.switchBT(false);
                    Log.d(TAG,"Tell LTE BT closed END!");
                }
            } catch (Exception e) {}

        }
        mSwitchPreference.setEnabled(false);
        return true;
    }

    class SwitchUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            mSwitchPreference.setChecked(false);
        }
    }

    void handleStateChanged(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_TURNING_ON:
                mSwitchPreference.setEnabled(false);
                break;
            case BluetoothAdapter.STATE_ON:
                setChecked(true);
                mSwitchPreference.setEnabled(true);
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                mSwitchPreference.setEnabled(false);
                break;
            case BluetoothAdapter.STATE_OFF:
                setChecked(false);
                mSwitchPreference.setEnabled(true);
                break;
            default:
                setChecked(false);
                mSwitchPreference.setEnabled(true);
        }
    }

    private void setChecked(boolean isChecked) {
        if (isChecked != mSwitchPreference.isChecked()) {
            // set listener to null, so onCheckedChanged won't be called
            // if the checked status on Switch isn't changed by user click
            if (mValidListener) {
                mSwitchPreference.setOnPreferenceClickListener(null);
            }
            mSwitchPreference.setChecked(isChecked);
            if (mValidListener) {
                mSwitchPreference.setOnPreferenceClickListener(this);
            }
        }
    }
}
