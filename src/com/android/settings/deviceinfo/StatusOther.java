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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.sim.Sim;
import android.sim.SimManager;
import android.text.TextUtils;
import android.util.Log;
import android.content.ActivityNotFoundException;

import com.android.settings.R;
import com.android.settings.Utils;

import java.lang.ref.WeakReference;
import com.sprd.android.support.featurebar.FeatureBarHelper;
import android.widget.TextView;
import android.view.ViewGroup;
import android.telephony.TelephonyManager;
import android.telephony.VoLteServiceState;
import android.telephony.PhoneStateListener;
import android.telephony.CarrierConfigManager;
import android.os.SystemProperties;
import android.os.PersistableBundle;


/**
 * Display the following information
 * # Phone Number
 * # Network
 * # Roaming
 * # Device Id (IMEI in GSM and MEID in CDMA)
 * # Network type
 * # Signal Strength
 * # Battery Strength  : TODO
 * # Uptime
 * # Awake Time
 * # XMPP/buzz/tickle status : TODO
 *
 */
public class StatusOther extends PreferenceActivity {
    private static final String KEY_BATTERY_STATUS = "battery_status";
    private static final String KEY_BATTERY_LEVEL = "battery_level";
    private static final String KEY_IP_ADDRESS = "wifi_ip_address";
    private static final String KEY_WIFI_MAC_ADDRESS = "wifi_mac_address";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_SERIAL_NUMBER = "serial_number";
    private static final String KEY_WIMAX_MAC_ADDRESS = "wimax_mac_address";
    private static final String KEY_SIM_STATUS = "sim_status";
    private static final String KEY_IMS_REGISTRATION = "ims_registration";

    private static final int EVENT_UPDATE_STATS = 500;

    private Resources mRes;
    private Preference mUptime;

    private String sUnknown;

    private Preference mBatteryStatus;
    private Preference mBatteryLevel;
    private Preference mSerialPref;
    private PreferenceScreen mSimStatus;

    private Handler mHandler;
    private Preference mImsRegisteredStatus;

    private PhoneStateListener mLtePhoneStateListener;
    private boolean isVolteEnable = SystemProperties.getBoolean("persist.sys.volte.enable", false);
    /*SPRD:add for bug 648423@{*/
    private FeatureBarHelper mFeatureBarHelper;
    /*@}*/

    private static class MyHandler extends Handler {
        private WeakReference<StatusOther> mStatus;

        public MyHandler(StatusOther activity) {
            mStatus = new WeakReference<StatusOther>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            StatusOther status = mStatus.get();
            if (status == null) {
                return;
            }

            switch (msg.what) {
                case EVENT_UPDATE_STATS:
                    status.updateTimes();
                    sendEmptyMessageDelayed(EVENT_UPDATE_STATS, 1000);
                    break;
            }
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel.setSummary(Utils.getBatteryPercentage(intent));
                mBatteryStatus.setSummary(Utils.getBatteryStatus(getResources(), intent));
            }
            // SPRD:ADD for Bug307553,Update bluetooth address info when bluetooth status changed
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                setBtStatus();
            }
        }
    };

    /* SPRD: Add for bug 945629. @{ */
    public void createPhoneStateListener() {
        mLtePhoneStateListener = new PhoneStateListener() {
            @Override
            public void onVoLteServiceStateChanged(VoLteServiceState serviceState) {
                boolean mRegisteVolte  = (serviceState.getSrvccState() != VoLteServiceState.HANDOVER_STARTED
                            && (serviceState.getImsState() == 1));
                Log.e("StatusOther","mRegisteVolte : "+ mRegisteVolte);
                String registered = mRes.getString(R.string.status_ims_not_registered);
                if (mRegisteVolte) {
                    registered = mRes.getString(R.string.status_ims_registered);
                }
                mImsRegisteredStatus.setSummary(registered);
            }
        };
    }
    public void startMonitor() {
        if (isVolteEnable) {
            TelephonyManager.getDefault().listen(mLtePhoneStateListener,
                    PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_VOLTE_STATE);
        }
    }

    public void stopMonitor() {
        if (isVolteEnable) {
            TelephonyManager.getDefault().listen(mLtePhoneStateListener,
                    PhoneStateListener.LISTEN_NONE);
        }
        mLtePhoneStateListener = null;
    }
    /*@}*/

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mHandler = new MyHandler(this);

        /*SPRD:add for bug 648423@{*/
        mFeatureBarHelper = new FeatureBarHelper(this);
        ViewGroup vg = mFeatureBarHelper.getFeatureBar();
        if (vg != null) {
            TextView option = (TextView)mFeatureBarHelper.getOptionsKeyView();
            vg.removeView(option);
        }
        /*@}*/
        addPreferencesFromResource(R.xml.device_info_status_other);
        mBatteryLevel = findPreference(KEY_BATTERY_LEVEL);
        mBatteryStatus = findPreference(KEY_BATTERY_STATUS);
        mImsRegisteredStatus = findPreference(KEY_IMS_REGISTRATION);
        if (!showImsRegistedInStatus()) {
            removePreferenceFromScreen(KEY_IMS_REGISTRATION);
        }
        PreferenceScreen prefSet = getPreferenceScreen();
        mSerialPref = prefSet.findPreference(KEY_SERIAL_NUMBER);
        mSimStatus = (PreferenceScreen) prefSet.findPreference(KEY_SIM_STATUS);

        mRes = getResources();
        if (sUnknown == null) {
            sUnknown = mRes.getString(R.string.device_info_default);
        }

        mUptime = findPreference("up_time");

        setWimaxStatus();
        setWifiStatus();
        setBtStatus();
        setIpAddressStatus();

//        String serial = Build.SERIAL;
//        if (serial != null && !serial.equals("")) {
//            setSummaryText(KEY_SERIAL_NUMBER, serial);
//        } else {
//            removePreferenceFromScreen(KEY_SERIAL_NUMBER);
//        }
        //SPRD: add add show ro.serialno SystemProperty feature for bug 284215
        setSummaryText(KEY_SERIAL_NUMBER, SystemProperties.get("ro.serialno",
                getResources().getString(R.string.device_info_default)));
        mSerialPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                String packageName = "com.spreadtrum.android.eng";
                String className = "com.spreadtrum.android.eng.PhaseCheck";
                Intent intent = new Intent();
                intent.setAction("com.spreadtrum.android.eng.PhaseCheck");
                intent.putExtra("textFilter","filter");//filter sn1/sn2
                intent.setClassName(packageName,className);
                try{
                    startActivity(intent);
                }catch(ActivityNotFoundException e){
                    Log.e("AA", "Not found Activity !");
                }
                return true;
            }
        });
        SimManager sm = SimManager.get(this);
        Sim[] sims = sm.getActiveSims();
        if(sims.length == 0){
            mSimStatus.setEnabled(false);
        } else {
            mSimStatus.setEnabled(true);
        }
        mSimStatus.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                ComponentName targetComponent = new ComponentName("com.android.settings","com.sprd.settings.sim.MobileSimChooseUUI");
                intent.setComponent(targetComponent);
                intent.putExtra("package_name", "com.android.settings");
                intent.putExtra("class_name", "com.android.settings.deviceinfo.StatusSim");
                intent.putExtra("title_name", R.string.device_status_ex);
                try{
                    startActivity(intent);
                }catch(ActivityNotFoundException e){
                    Log.e("StatusOther", "Not found Activity !");
                }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // SPRD:ADD for Bug307553,Add a change event registered to the Receiver
        registerReceiver(mBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(mBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        mHandler.sendEmptyMessage(EVENT_UPDATE_STATS);
        /* SPRD: Add for bug 945629. @{ */
        if (showImsRegistedInStatus()) {
            createPhoneStateListener();
            startMonitor();
        }
        /* @} */
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(mBroadcastReceiver);
        mHandler.removeMessages(EVENT_UPDATE_STATS);
        /* SPRD: Add for bug 945629. @{ */
        if (showImsRegistedInStatus()) {
            stopMonitor();
        }
        /* @} */
    }

    /**
     * Removes the specified preference, if it exists.
     * @param key the key for the Preference item
     */
    private void removePreferenceFromScreen(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            getPreferenceScreen().removePreference(pref);
        }
    }

    private void setSummaryText(String preference, String text) {
            if (TextUtils.isEmpty(text)) {
               text = sUnknown;
             }
             // some preferences may be missing
             if (findPreference(preference) != null) {
                 findPreference(preference).setSummary(text);
             }
    }

    private void setWimaxStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIMAX);

        if (ni == null) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = (Preference) findPreference(KEY_WIMAX_MAC_ADDRESS);
            if (ps != null) root.removePreference(ps);
        } else {
            Preference wimaxMacAddressPref = findPreference(KEY_WIMAX_MAC_ADDRESS);
            String macAddress = SystemProperties.get("net.wimax.mac.address",
                    getString(R.string.status_unavailable));
            wimaxMacAddressPref.setSummary(macAddress);
        }
    }
    private void setWifiStatus() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_WIFI_MAC_ADDRESS);

        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : getString(R.string.status_unavailable));
         getPreferenceScreen().removePreference(wifiMacAddressPref);//SPRD:add for bug640330
    }

    private void setIpAddressStatus() {
        Preference ipAddressPref = findPreference(KEY_IP_ADDRESS);
        String ipAddress = Utils.getDefaultIpAddresses(this);
        if (ipAddress != null) {
            ipAddressPref.setSummary(ipAddress);
        } else {
            ipAddressPref.setSummary(getString(R.string.status_unavailable));
        }
    }

    private void setBtStatus() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        Preference btAddressPref = findPreference(KEY_BT_ADDRESS);

        if (bluetooth == null) {
            // device not BT capable
            getPreferenceScreen().removePreference(btAddressPref);
        } else {
            String address = bluetooth.isEnabled() ? bluetooth.getAddress() : null;
            btAddressPref.setSummary(!TextUtils.isEmpty(address) ? address
                    : getString(R.string.status_unavailable));
        }
    }

    void updateTimes() {
        long at = SystemClock.uptimeMillis() / 1000;
        long ut = SystemClock.elapsedRealtime() / 1000;

        if (ut == 0) {
            ut = 1;
        }

        mUptime.setSummary(convert(ut));
    }

    private String pad(int n) {
        if (n >= 10) {
            return String.valueOf(n);
        } else {
            return "0" + String.valueOf(n);
        }
    }

    private String convert(long t) {
        int s = (int)(t % 60);
        int m = (int)((t / 60) % 60);
        int h = (int)((t / 3600));

        return h + ":" + pad(m) + ":" + pad(s);
    }
    /* SPRD: Add for bug 945629. @{ */
    private boolean showImsRegistedInStatus() {
        boolean isShowRegistered = false;
        int phoneId = TelephonyManager.getDefaultDataPhoneId(getApplicationContext());
        CarrierConfigManager configManager = (CarrierConfigManager)
           getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle config = configManager.getConfigForPhoneId(phoneId);
            if (config != null) {
                isShowRegistered = config.getBoolean(
                    CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_IN_STATUS);
            }
        }
        Log.d("StatusOther", "isShowRegistered = "+ isShowRegistered);
        return isShowRegistered;
    }
    /* @} */
}
