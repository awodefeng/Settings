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
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.WirelessSettings;

import java.lang.ref.WeakReference;

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
public class StatusSim extends PreferenceActivity {
    private static final String TAG = "StatusSim";
    private static final String KEY_DATA_STATE = "data_state";
    private static final String KEY_SERVICE_STATE = "service_state";
    private static final String KEY_OPERATOR_NAME = "operator_name";
    private static final String KEY_ROAMING_STATE = "roaming_state";
    private static final String KEY_NETWORK_TYPE = "network_type";
    private static final String KEY_PHONE_NUMBER = "number";
    private static final String KEY_IMEI_SV = "imei_sv";
    private static final String KEY_IMEI = "imei";
    private static final String KEY_PRL_VERSION = "prl_version";
    private static final String KEY_MIN_NUMBER = "min_number";
    private static final String KEY_MEID_NUMBER = "meid_number";
    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_ICC_ID = "icc_id";
    private static final String[] PHONE_RELATED_ENTRIES = {
        KEY_DATA_STATE,
        KEY_SERVICE_STATE,
        KEY_OPERATOR_NAME,
        KEY_ROAMING_STATE,
        KEY_NETWORK_TYPE,
        KEY_PHONE_NUMBER,
        KEY_IMEI,
        KEY_IMEI_SV,
        KEY_PRL_VERSION,
        KEY_MIN_NUMBER,
        KEY_MEID_NUMBER,
        KEY_SIGNAL_STRENGTH,
        KEY_ICC_ID
    };

    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 200;
    private static final int EVENT_SERVICE_STATE_CHANGED = 300;

    private TelephonyManager mTelephonyManager;
    private Phone mPhone = null;
    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private Resources mRes;
    private Preference mSignalStrength;

    private String sUnknown;

    private Handler mHandler;
    public static int mPhoneId = 0;

    private static final String NAME_CMCC = "cmcc";
    private static final String PROPERTY_NAME = "ro.operator";
    private static final String PROPERTY_VALUE = SystemProperties.get(PROPERTY_NAME, "");

    private static class MyHandler extends Handler {
        private WeakReference<StatusSim> mStatus;

        public MyHandler(StatusSim activity) {
            mStatus = new WeakReference<StatusSim>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            StatusSim status = mStatus.get();
            if (status == null) {
                return;
            }

            switch (msg.what) {
                case EVENT_SIGNAL_STRENGTH_CHANGED:
                    status.updateSignalStrength();
                    break;

                case EVENT_SERVICE_STATE_CHANGED:
		    status.updateSignalStrength();//SPRD: Add for Bug# 409290
                    ServiceState serviceState = status.mPhone.getServiceState();
                    status.updateServiceState(serviceState);
                    break;
            }
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onDataConnectionStateChanged(int state) {
            updateDataState();
            updateNetworkType();
        }

        /* SPRD: listen voice_service_state for device info of network :PhoneStateListener.LISTEN_SERVICE_STATE @{ */
        @Override
        public void onServiceStateChanged(ServiceState state) {
            updateNetworkType();
        }
        /* @} */
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Preference removablePref;

        mHandler = new MyHandler(this);
        mPhoneId = getIntent().getIntExtra(WirelessSettings.SUB_ID, 0);
        mTelephonyManager = (TelephonyManager)getSystemService(TelephonyManager.getServiceName(
                Context.TELEPHONY_SERVICE, mPhoneId));

        addPreferencesFromResource(R.xml.device_info_status_sim);

        mRes = getResources();
        if (sUnknown == null) {
            sUnknown = mRes.getString(R.string.device_info_default);
        }

        mPhone = PhoneFactory.getPhone(mPhoneId);
        // Note - missing in zaku build, be careful later...
        mSignalStrength = findPreference(KEY_SIGNAL_STRENGTH);

        if (Utils.isWifiOnly(getApplicationContext())) {
            for (String key : PHONE_RELATED_ENTRIES) {
                removePreferenceFromScreen(key);
            }
        } else {
            // NOTE "imei" is the "Device ID" since it represents
            //  the IMEI in GSM and the MEID in CDMA
            if (mPhone.getPhoneName().equals("CDMA")) {
                setSummaryText(KEY_MEID_NUMBER, mPhone.getMeid());
                setSummaryText(KEY_MIN_NUMBER, mPhone.getCdmaMin());
                if (getResources().getBoolean(R.bool.config_msid_enable)) {
                    findPreference(KEY_MIN_NUMBER).setTitle(R.string.status_msid_number);
                }
                setSummaryText(KEY_PRL_VERSION, mPhone.getCdmaPrlVersion());
                removePreferenceFromScreen(KEY_IMEI_SV);

                if (mPhone.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                    // Show ICC ID and IMEI for LTE device
                    setSummaryText(KEY_ICC_ID, mPhone.getIccSerialNumber());
                    setSummaryText(KEY_IMEI, mPhone.getImei());
                } else {
                    // device is not GSM/UMTS, do not display GSM/UMTS features
                    // check Null in case no specified preference in overlay xml
                    removePreferenceFromScreen(KEY_IMEI);
                    removePreferenceFromScreen(KEY_ICC_ID);
                }
            } else {
                if (NAME_CMCC.equals(PROPERTY_VALUE)) {
                    setSummaryText(KEY_IMEI, TelephonyManager.getDefault(0).getDeviceId());
                }else {
                    setSummaryText(KEY_IMEI, mPhone.getDeviceId());
                }
                setSummaryText(KEY_IMEI_SV,
                        ((TelephonyManager) getSystemService(TELEPHONY_SERVICE))
                            .getDeviceSoftwareVersion());

                // device is not CDMA, do not display CDMA features
                // check Null in case no specified preference in overlay xml
                removePreferenceFromScreen(KEY_PRL_VERSION);
                removePreferenceFromScreen(KEY_MEID_NUMBER);
                removePreferenceFromScreen(KEY_MIN_NUMBER);
                removePreferenceFromScreen(KEY_ICC_ID);
            }

            String rawNumber = mPhone.getLine1Number();  // may be null or empty
            String formattedNumber = null;
            if (!TextUtils.isEmpty(rawNumber)) {
                formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
            }
            // If formattedNumber is null or empty, it'll display as "Unknown".
            setSummaryText(KEY_PHONE_NUMBER, formattedNumber);

            mPhoneStateReceiver = new PhoneStateIntentReceiver(this, mHandler, mPhoneId);
            mPhoneStateReceiver.notifySignalStrength(EVENT_SIGNAL_STRENGTH_CHANGED);
            mPhoneStateReceiver.notifyServiceState(EVENT_SERVICE_STATE_CHANGED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!Utils.isWifiOnly(getApplicationContext())) {
            mPhoneStateReceiver.registerIntent();

            updateSignalStrength();
            updateServiceState(mPhone.getServiceState());
            updateDataState();

            // SPRD: listen voice_service_state for device info of network :PhoneStateListener.LISTEN_SERVICE_STATE
            mTelephonyManager.listen(mPhoneStateListener,
                      PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                      |PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!Utils.isWifiOnly(getApplicationContext())) {
            mPhoneStateReceiver.unregisterIntent();
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
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
            if (TextUtils.isEmpty(text) || "UNKNOWN".equals(text)) {
               text = sUnknown;
             }
             // some preferences may be missing
             if (findPreference(preference) != null) {
                 findPreference(preference).setSummary(text);
             }
    }

    private void updateNetworkType() {
        // Whether EDGE, UMTS, etc...
        // SPRD: modify for bug266170 to use voice network type on device info
        int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        int voiceType = mTelephonyManager.getVoiceNetworkType();
        int dataType = mTelephonyManager.getDataNetworkType();
        Log.d(TAG,"voiceType ="+voiceType);
        Log.d(TAG,"dataType ="+dataType);
        if(voiceType != TelephonyManager.NETWORK_TYPE_UNKNOWN){
            networkType = voiceType;
        }else{
            if(dataType != TelephonyManager.NETWORK_TYPE_UNKNOWN){
                networkType = dataType;
            }
        }
        setSummaryText(KEY_NETWORK_TYPE, mTelephonyManager.getNetworkTypeName(networkType));
        Log.d(TAG,"network type ="+mTelephonyManager.getNetworkTypeName(networkType));
    }

    private void updateDataState() {
        int state = mTelephonyManager.getDataState();
        String display = mRes.getString(R.string.radioInfo_unknown);

        switch (state) {
            case TelephonyManager.DATA_CONNECTED:
                display = mRes.getString(R.string.radioInfo_data_connected);
                break;
            case TelephonyManager.DATA_SUSPENDED:
                display = mRes.getString(R.string.radioInfo_data_suspended);
                break;
            case TelephonyManager.DATA_CONNECTING:
                display = mRes.getString(R.string.radioInfo_data_connecting);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                display = mRes.getString(R.string.radioInfo_data_disconnected);
                break;
        }

        setSummaryText(KEY_DATA_STATE, display);
    }

    private void updateServiceState(ServiceState serviceState) {
        int state = serviceState.getState();
        String display = mRes.getString(R.string.radioInfo_unknown);

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                display = mRes.getString(R.string.radioInfo_service_in);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
            case ServiceState.STATE_EMERGENCY_ONLY:
                display = mRes.getString(R.string.radioInfo_service_out);
                break;
            case ServiceState.STATE_POWER_OFF:
                display = mRes.getString(R.string.radioInfo_service_off);
                break;
        }

        setSummaryText(KEY_SERVICE_STATE, display);

        if (serviceState.getRoaming()) {
            setSummaryText(KEY_ROAMING_STATE, mRes.getString(R.string.radioInfo_roaming_in));
        } else {
            setSummaryText(KEY_ROAMING_STATE, mRes.getString(R.string.radioInfo_roaming_not));
        }
        //SPRD:modify for bug 307641
        setSummaryText(KEY_OPERATOR_NAME, serviceState.getOperatorAlphaLong());
    }

    void updateSignalStrength() {
        // TODO PhoneStateIntentReceiver is deprecated and PhoneStateListener
        // should probably used instead.

        // not loaded in some versions of the code (e.g., zaku)
        if (mSignalStrength != null) {
            /* SPRD: modify for bug 266825 @{ */
            // int state =
            // mPhone.getServiceState().getState();
            Resources r = getResources();
            //
            // if ((ServiceState.STATE_OUT_OF_SERVICE == state) ||
            // (ServiceState.STATE_POWER_OFF == state)) {
            // mSignalStrength.setSummary("0");
            // }
            // int signalDbm = mPhone.getSignalStrength().getDbm();
            int signalDbm = mPhoneStateReceiver.getSignalStrengthDbm();
            Log.d(TAG, "signalDbm="+signalDbm);

            if (-1 == signalDbm) signalDbm = 0;

            // int signalAsu = mPhone.getSignalStrength().getAsuLevel();
            int signalAsu = mPhoneStateReceiver.getSignalStrengthLevelAsu();
            Log.d(TAG, "signalAsu="+signalAsu);
            /* @{ */

            if (-1 == signalAsu) signalAsu = 0;

            mSignalStrength.setSummary(String.valueOf(signalDbm) + " "
                        + r.getString(R.string.radioInfo_display_dbm) + "   "
                        + String.valueOf(signalAsu) + " "
                        + r.getString(R.string.radioInfo_display_asu));
        }
    }
}
