/** Created by Spreadst */

package com.android.settings.wifi;

import java.util.concurrent.atomic.AtomicBoolean;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.WirelessSettings;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class HotspotEnabler implements CompoundButton.OnCheckedChangeListener,Preference.OnPreferenceClickListener {
    private final Context mContext;
    //private Switch mSwitch;
    private SwitchPreference mSwitch;
    private AtomicBoolean mAirplaneMode = new AtomicBoolean(false);
    private AtomicBoolean mRegister = new AtomicBoolean(false);

    private WifiManager mWifiManager;
    private boolean mStateMachineEvent;
    private final IntentFilter mIntentFilter;

    private boolean supportBtWifiSoftApCoexist = true;
    private BluetoothAdapter mBluetoothAdapter;

    // SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled
    private ConnectivityManager mConnMgr = null;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE,
                        WifiManager.WIFI_AP_STATE_FAILED));
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mAirplaneMode.set(isAirplaneModeOn());
                if (mAirplaneMode.get()) {
                    mSwitch.setEnabled(false);
                } else {
                    mSwitch.setEnabled(true);
                }
            }

        }
    };

    /*public HotspotEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        if (SystemProperties.get("ro.btwifisoftap.coexist", "true").equals(
                "false")) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            supportBtWifiSoftApCoexist = false;
        }

        /* SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled @{ */
        /*if (mConnMgr == null) {
            mConnMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        /* @} */
    //}

    public HotspotEnabler(Context context, SwitchPreference switchPreference) {
        //mContext = context;
        //mSwitch = switch_;

        mContext = context;
        mSwitch = switchPreference;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        if (SystemProperties.get("ro.btwifisoftap.coexist", "true").equals(
                "false")) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            supportBtWifiSoftApCoexist = false;
        }

        /* SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled @{ */
        if (mConnMgr == null) {
            mConnMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        /* @} */
    }
    public void resume() {
        // Wi-Fi state is sticky, so just let the receiver update UI
        if (!mRegister.get()) {
            mContext.registerReceiver(mReceiver, mIntentFilter);
            mRegister.set(true);
        }
        // add by spreadst_lc for cmcc wifi feature start
        mAirplaneMode.set(isAirplaneModeOn());
        if (WirelessSettings.isRadioAllowed(mContext,
                Settings.System.RADIO_WIFI) && !mAirplaneMode.get()) {
            handleWifiApStateChanged(mWifiManager.getWifiApState());
        } else {
            mSwitch.setChecked(false);
            mSwitch.setEnabled(false);
        }
        // add by spreadst_lc for cmcc wifi feature end
        mSwitch.setOnPreferenceClickListener(this);
    }

    public void pause() {
        if (mRegister.get()) {
            mContext.unregisterReceiver(mReceiver);
            mRegister.set(false);
        }
        mSwitch.setOnPreferenceClickListener(null);
    }

   /* public void setSwitch(Switch switch_) {
        if (mSwitch == switch_)
            return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(this);

        final int wifiState = mWifiManager.getWifiApState();
        boolean isEnabled = wifiState == WifiManager.WIFI_AP_STATE_ENABLED;
        boolean isDisabled = wifiState == WifiManager.WIFI_AP_STATE_DISABLED;
        mSwitch.setChecked(isEnabled);

        if (WirelessSettings.isRadioAllowed(mContext,
                Settings.System.RADIO_WIFI)) {
            mSwitch.setEnabled(isEnabled || isDisabled);
        } else {
            mSwitch.setEnabled(false);
        }
    }*/

    public void setSwitch(SwitchPreference switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnPreferenceClickListener(null);
        mSwitch = switch_;
        mSwitch.setOnPreferenceClickListener(this);

        final int wifiState = mWifiManager.getWifiApState();
        boolean isEnabled = wifiState == WifiManager.WIFI_AP_STATE_ENABLED;
        boolean isDisabled = wifiState == WifiManager.WIFI_AP_STATE_DISABLED;
        mSwitch.setChecked(isEnabled);

        if (WirelessSettings.isRadioAllowed(mContext,
                Settings.System.RADIO_WIFI)) {
            mSwitch.setEnabled(isEnabled || isDisabled);
        } else {
            mSwitch.setEnabled(false);
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
/*
        // Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return;
        }

        if (!supportBtWifiSoftApCoexist) {
            int btState = mBluetoothAdapter.getState();
            if(isChecked && ((btState == BluetoothAdapter.STATE_ON) || (btState == BluetoothAdapter.STATE_TURNING_ON))) {
                Toast.makeText(this.mContext, R.string.softap_bt_cannot_coexist, Toast.LENGTH_SHORT).show();
                mSwitch.setChecked(false);
                return;
            }
        }

        /* SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled @{ */
       /* if (!mConnMgr.getMobileDataEnabled() && isChecked) {
            showAlertForMobileDataNeedEnabled();
        }
        /* @} */

        /*final ContentResolver cr = mContext.getContentResolver();
        if (isChecked) {
            Settings.Global.putInt(cr, Settings.Global.SOFTAP_ENABLING_OR_ENABLED, 1);
        }
        /**
         * Disable Wifi if enabling tethering
         */
        /*int wifiState = mWifiManager.getWifiState();
        if (isChecked
                && ((wifiState == WifiManager.WIFI_STATE_ENABLING) || (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, isChecked)) {
            /* Disable here, enabled on receiving success broadcast */
            /*mSwitch.setEnabled(false);
        }

        /**
         * If needed, restore Wifi on tether disable
         */
       /* if (!isChecked) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Secure.getInt(cr,
                        Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                ;
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }*/
    }

    public boolean onPreferenceClick(Preference preference) {

        // Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return false;
        }
        boolean isChecked = mSwitch.isChecked();
        if (!supportBtWifiSoftApCoexist) {
            int btState = mBluetoothAdapter.getState();
            if(isChecked && ((btState == BluetoothAdapter.STATE_ON) || (btState == BluetoothAdapter.STATE_TURNING_ON))) {
                Toast.makeText(this.mContext, R.string.softap_bt_cannot_coexist, Toast.LENGTH_SHORT).show();
                mSwitch.setChecked(false);
                return false;
            }
        }

        /* SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled @{ */
        if (!mConnMgr.getMobileDataEnabled() && isChecked) {
            showAlertForMobileDataNeedEnabled();
        }
        /* @} */

        final ContentResolver cr = mContext.getContentResolver();
        if (isChecked) {
            Settings.Global.putInt(cr, Settings.Global.SOFTAP_ENABLING_OR_ENABLED, 1);
        }
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (isChecked
                && ((wifiState == WifiManager.WIFI_STATE_ENABLING) || (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, isChecked)) {
            /* Disable here, enabled on receiving success broadcast */
            mSwitch.setEnabled(false);
        }

        /**
         * If needed, restore Wifi on tether disable
         */
        if (!isChecked) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Secure.getInt(cr,
                        Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                ;
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
        return true;

    }


    private void handleWifiApStateChanged(int state) {
        switch (state) {
        case WifiManager.WIFI_AP_STATE_ENABLING:
            mSwitch.setEnabled(false);
            break;
        case WifiManager.WIFI_AP_STATE_ENABLED:
            setSwitchChecked(true);
            mSwitch.setEnabled(true);
            break;
        case WifiManager.WIFI_AP_STATE_DISABLING:
            mSwitch.setEnabled(false);
            break;
        case WifiManager.WIFI_AP_STATE_DISABLED:
            setSwitchChecked(false);
            if (!mAirplaneMode.get()) {
                mSwitch.setEnabled(true);
            }
            break;
        default:
            setSwitchChecked(false);
            mSwitch.setEnabled(true);
            break;
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mStateMachineEvent = true;
            mSwitch.setChecked(checked);
            mStateMachineEvent = false;
        }
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    /* SPRD: Modify Bug 316222 show tip for wifi hotstop by mobile data disabled @{ */
    private void showAlertForMobileDataNeedEnabled() {
        Toast.makeText(mContext, R.string.softap_need_mobile_data_enabled, Toast.LENGTH_LONG)
                .show();
    }
    /* @} */
}
