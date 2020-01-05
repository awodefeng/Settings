/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.util.Log;
import android.database.ContentObserver;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.sprd.internal.telephony.CpSupportUtils;
import com.android.ims.ImsManager;

import android.os.Handler;

import java.util.Collection;
import com.android.settings.WifiCallingSettings;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.SmsApplication.SmsApplicationData;

import android.preference.SwitchPreference;
import com.android.settings.wifi.WifiEnabler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class WirelessSettings extends RestrictedSettingsFragment
        implements OnPreferenceChangeListener {
    private static final String TAG = "WirelessSettings";

    private static final String KEY_TOGGLE_AIRPLANE = "toggle_airplane";
    private static final String KEY_WIMAX_SETTINGS = "wimax_settings";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_TETHER_SETTINGS = "tether_settings";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private static final String KEY_MOBILE_NETWORK_SETTINGS = "mobile_network_settings";
    private static final String KEY_MANAGE_MOBILE_PLAN = "manage_mobile_plan";
    private static final String KEY_SMS_APPLICATION = "sms_application";
    private static final String KEY_TOGGLE_NSD = "toggle_nsd"; //network service discovery
    private static final String KEY_CELL_BROADCAST_SETTINGS = "cell_broadcast_settings";
    private static final String KEY_WFC_SETTINGS = "wifi_calling_settings";

    public static final String EXIT_ECM_RESULT = "exit_ecm_result";
    public static final int REQUEST_CODE_EXIT_ECM = 1;
    public static final String SUB_ID = "sub_id"; // SPRD: add for multi-sim

    private AirplaneModeEnabler mAirplaneModeEnabler;
    private CheckBoxPreference mAirplaneModePreference;
    private NsdEnabler mNsdEnabler;
    private PreferenceScreen mMobileNetworkPreference;
    private PreferenceScreen mVPNSettingPreference;
    private PreferenceScreen mLteService;
    private ConnectivityManager mCm;
    private TelephonyManager mTm;

    private SwitchPreference mEnablerSwitchPreference;
    private WifiEnabler mWifiEnabler;

    private static final int MANAGE_MOBILE_PLAN_DIALOG_ID = 1;
    private static final String SAVED_MANAGE_MOBILE_PLAN_MSG = "mManageMobilePlanMessage";

    private SmsListPreference mSmsApplicationPreference;

    private static final boolean WCN_DISABLED = SystemProperties.get("ro.wcn").equals("disabled");
    public static boolean PIKEL_UI_SUPPORT = SystemProperties.getBoolean("pikel_ui_support",true);
    private PreferenceScreen mButtonWfc;

    public WirelessSettings() {
        super(null);
    }
    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (ensurePinRestrictedPreference(preference)) {
            return true;
        }
        log("onPreferenceTreeClick: preference=" + preference);

        ComponentName targetComponent = null; // SPRD: add multi-sim

        if (preference == mAirplaneModePreference && Boolean.parseBoolean(
                SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode launch ECM app dialog
            startActivityForResult(
                new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                REQUEST_CODE_EXIT_ECM);
            return true;
        } else if (preference == findPreference(KEY_MANAGE_MOBILE_PLAN)) {
            onManageMobilePlanClick();

        /* SPRD: add multi-sim @{ */
        } else if (KEY_MOBILE_NETWORK_SETTINGS.equals(preference.getKey())) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.android.phone",
                    "com.android.phone.MobileNetworkSettings"));
            startActivity(intent);
            return true;
        }
        /* @} */
        // Let the intents be launched by the Preference manager
       return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private String mManageMobilePlanMessage;
    private static final String CONNECTED_TO_PROVISIONING_NETWORK_ACTION
            = "com.android.server.connectivityservice.CONNECTED_TO_PROVISIONING_NETWORK_ACTION";
    public void onManageMobilePlanClick() {
        log("onManageMobilePlanClick:");
        mManageMobilePlanMessage = null;
        Resources resources = getActivity().getResources();

        NetworkInfo ni = mCm.getProvisioningOrActiveNetworkInfo();
        if (mTm.hasIccCard() && (ni != null)) {
            // Get provisioning URL
            String url = mCm.getMobileProvisioningUrl();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = new Intent(CONNECTED_TO_PROVISIONING_NETWORK_ACTION);
                intent.putExtra("EXTRA_URL", url);
                Context context = getActivity().getBaseContext();
                context.sendBroadcast(intent);
                mManageMobilePlanMessage = null;
            } else {
                // No provisioning URL
                String operatorName = mTm.getSimOperatorName();
                if (TextUtils.isEmpty(operatorName)) {
                    // Use NetworkOperatorName as second choice in case there is no
                    // SPN (Service Provider Name on the SIM). Such as with T-mobile.
                    operatorName = mTm.getNetworkOperatorName();
                    if (TextUtils.isEmpty(operatorName)) {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_unknown_sim_operator);
                    } else {
                        mManageMobilePlanMessage = resources.getString(
                                R.string.mobile_no_provisioning_url, operatorName);
                    }
                } else {
                    mManageMobilePlanMessage = resources.getString(
                            R.string.mobile_no_provisioning_url, operatorName);
                }
            }
        } else if (mTm.hasIccCard() == false) {
            // No sim card
            mManageMobilePlanMessage = resources.getString(R.string.mobile_insert_sim_card);
        } else {
            // NetworkInfo is null, there is no connection
            mManageMobilePlanMessage = resources.getString(R.string.mobile_connect_to_internet);
        }
        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            log("onManageMobilePlanClick: message=" + mManageMobilePlanMessage);
            showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
        }
    }

    private void updateSmsApplicationSetting() {
        log("updateSmsApplicationSetting:");
        ComponentName appName = SmsApplication.getDefaultSmsApplication(getActivity(), true);
        if (appName != null) {
            String packageName = appName.getPackageName();

            CharSequence[] values = mSmsApplicationPreference.getEntryValues();
            for (int i = 0; i < values.length; i++) {
                if (packageName.contentEquals(values[i])) {
                    mSmsApplicationPreference.setValueIndex(i);
                    mSmsApplicationPreference.setSummary(mSmsApplicationPreference.getEntries()[i]);
                    break;
                }
            }
        }
    }

    private void initSmsApplicationSetting() {
        log("initSmsApplicationSetting:");
        Collection<SmsApplicationData> smsApplications =
                SmsApplication.getApplicationCollection(getActivity());

        // If the list is empty the dialog will be empty, but we will not crash.
        int count = smsApplications.size();
        CharSequence[] entries = new CharSequence[count];
        CharSequence[] entryValues = new CharSequence[count];
        Drawable[] entryImages = new Drawable[count];

        PackageManager packageManager = getPackageManager();
        int i = 0;
        for (SmsApplicationData smsApplicationData : smsApplications) {
            entries[i] = smsApplicationData.mApplicationName;
            entryValues[i] = smsApplicationData.mPackageName;
            try {
                entryImages[i] = packageManager.getApplicationIcon(smsApplicationData.mPackageName);
            } catch (NameNotFoundException e) {
                entryImages[i] = packageManager.getDefaultActivityIcon();
            }
            i++;
        }
        mSmsApplicationPreference.setEntries(entries);
        mSmsApplicationPreference.setEntryValues(entryValues);
        mSmsApplicationPreference.setEntryDrawables(entryImages);
        updateSmsApplicationSetting();
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        log("onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                return new AlertDialog.Builder(getActivity())
                            .setMessage(mManageMobilePlanMessage)
                            .setCancelable(false)
                            .setPositiveButton(com.android.internal.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    log("MANAGE_MOBILE_PLAN_DIALOG.onClickListener id=" + id);
                                    mManageMobilePlanMessage = null;
                                }
                            })
                            .create();
        }
        return super.onCreateDialog(dialogId);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    public static boolean isRadioAllowed(Context context, String type) {
        if (!AirplaneModeEnabler.isAirplaneModeOn(context)) {
            return true;
        }
        // Here we use the same logic in onCreate().
        String toggleable = Settings.Global.getString(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        return toggleable != null && toggleable.contains(type);
    }

    private boolean isSmsSupported() {
        // Some tablet has sim card but could not do telephony operations. Skip those.
        return (mTm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mManageMobilePlanMessage = savedInstanceState.getString(SAVED_MANAGE_MOBILE_PLAN_MSG);
        }
        log("onCreate: mManageMobilePlanMessage=" + mManageMobilePlanMessage);

        mCm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mTm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        addPreferencesFromResource(R.xml.wireless_settings);

        final boolean isSecondaryUser = UserHandle.myUserId() != UserHandle.USER_OWNER;

        final Activity activity = getActivity();
        mAirplaneModePreference = (CheckBoxPreference) findPreference(KEY_TOGGLE_AIRPLANE);
        CheckBoxPreference nsd = (CheckBoxPreference) findPreference(KEY_TOGGLE_NSD);

        mAirplaneModeEnabler = new AirplaneModeEnabler(activity, mAirplaneModePreference);

        mSmsApplicationPreference = (SmsListPreference) findPreference(KEY_SMS_APPLICATION);
        mSmsApplicationPreference.setOnPreferenceChangeListener(this);
        if(PIKEL_UI_SUPPORT && null != mSmsApplicationPreference){
            getPreferenceScreen().removePreference(mSmsApplicationPreference);
        }
        mMobileNetworkPreference = (PreferenceScreen) findPreference(KEY_MOBILE_NETWORK_SETTINGS);
        mVPNSettingPreference = (PreferenceScreen) findPreference(KEY_VPN_SETTINGS);
        initSmsApplicationSetting();

        // Remove NSD checkbox by default
        getPreferenceScreen().removePreference(nsd);
        //mNsdEnabler = new NsdEnabler(activity, nsd);

        String toggleable = Settings.Global.getString(activity.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        //enable/disable wimax depending on the value in config.xml
        boolean isWimaxEnabled = !isSecondaryUser && this.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if (!isWimaxEnabled) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
            if (ps != null) root.removePreference(ps);
        } else {
            if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIMAX )
                    && isWimaxEnabled) {
                Preference ps = (Preference) findPreference(KEY_WIMAX_SETTINGS);
                ps.setDependency(KEY_TOGGLE_AIRPLANE);
            }
        }
        protectByRestrictions(KEY_WIMAX_SETTINGS);

        if (isSecondaryUser || PIKEL_UI_SUPPORT) { // Disable VPN
            removePreference(KEY_VPN_SETTINGS);
        }
        protectByRestrictions(KEY_VPN_SETTINGS);
        // Manually set dependencies for Bluetooth when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_BLUETOOTH)) {
            // No bluetooth-dependent items in the list. Code kept in case one is added later.
        }

        // Remove Mobile Network Settings and Manage Mobile Plan if it's a wifi-only device.
        if (isSecondaryUser || Utils.isWifiOnly(getActivity())) {
            removePreference(KEY_MOBILE_NETWORK_SETTINGS);
            removePreference(KEY_MANAGE_MOBILE_PLAN);
        }
        mButtonWfc = (PreferenceScreen) findPreference(KEY_WFC_SETTINGS);
        // Remove Mobile Network Settings and Manage Mobile Plan
        // if config_show_mobile_plan sets false.
        boolean isMobilePlanEnabled = this.getResources().getBoolean(
                R.bool.config_show_mobile_plan);
        if (!isMobilePlanEnabled || PIKEL_UI_SUPPORT) {
            Preference pref = findPreference(KEY_MANAGE_MOBILE_PLAN);
            if (pref != null) {
                removePreference(KEY_MANAGE_MOBILE_PLAN);
            }
        }
        protectByRestrictions(KEY_MOBILE_NETWORK_SETTINGS);
        protectByRestrictions(KEY_MANAGE_MOBILE_PLAN);

        // Remove SMS Application if the device does not support SMS
        if (!isSmsSupported()) {
            removePreference(KEY_SMS_APPLICATION);
        }

        // Remove Airplane Mode settings if it's a stationary device such as a TV.
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            removePreference(KEY_TOGGLE_AIRPLANE);
        }

        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        // Enable Proxy selector settings if allowed.
        Preference mGlobalProxy = findPreference(KEY_PROXY_SETTINGS);
        DevicePolicyManager mDPM = (DevicePolicyManager)
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        // proxy UI disabled until we have better app support
        getPreferenceScreen().removePreference(mGlobalProxy);
        mGlobalProxy.setEnabled(mDPM.getGlobalProxyAdmin() == null);

        // Disable Tethering if it's not allowed or if it's a wifi-only device
        ConnectivityManager cm =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        //SPRD: Bug #661533 Add softap UI for 9820w BEG-->
        if (isSecondaryUser || !cm.isTetheringSupported()
                || WCN_DISABLED /*|| PIKEL_UI_SUPPORT*/ || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
        //<-- Add softap UI for 9820w END
            getPreferenceScreen().removePreference(findPreference(KEY_TETHER_SETTINGS));
        } else {
            Preference p = findPreference(KEY_TETHER_SETTINGS);
            p.setTitle(Utils.getTetheringLabel(cm));
        }
        protectByRestrictions(KEY_TETHER_SETTINGS);

        // Enable link to CMAS app settings depending on the value in config.xml.
        boolean isCellBroadcastAppLinkEnabled = this.getResources().getBoolean(
                com.android.internal.R.bool.config_cellBroadcastAppLinks);
        try {
            if (isCellBroadcastAppLinkEnabled) {
                PackageManager pm = getPackageManager();
                if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                        == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                }
            }
        } catch (IllegalArgumentException ignored) {
            isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
        }
        if (isSecondaryUser || !isCellBroadcastAppLinkEnabled) {
            PreferenceScreen root = getPreferenceScreen();
            Preference ps = findPreference(KEY_CELL_BROADCAST_SETTINGS);
            if (ps != null) root.removePreference(ps);
        }
        protectByRestrictions(KEY_CELL_BROADCAST_SETTINGS);
        // SPRD: ADD remove the mobile_plan prefence.
        removePreference(KEY_MANAGE_MOBILE_PLAN);

        mLteService = (PreferenceScreen)findPreference("lte_service_settings");
        int phoneCount = TelephonyManager.getPhoneCount();
        boolean isLtePhone = false;
        for (int i = 0; i < phoneCount; i++) {
            if (CpSupportUtils.isLtePhone(i)){
                isLtePhone = true;
            }
        }
        if (!isLtePhone || PIKEL_UI_SUPPORT) {
            getPreferenceScreen().removePreference(mLteService);
        }
        
        if (ImsManager.isWfcEnabledByPlatform(getActivity())) {
        	mButtonWfc.setSummary(WifiCallingSettings.getWfcModeSummary(getActivity(), ImsManager.getWfcMode(getActivity())));
       } else {
    	   removePreference(KEY_WFC_SETTINGS);
       }

        mEnablerSwitchPreference = new WifiEnablerSwitchPreference(getActivity());
        mEnablerSwitchPreference.setEnabled(true);
        mEnablerSwitchPreference.setSwitchTextOff("");
        mEnablerSwitchPreference.setSwitchTextOn("");
        mEnablerSwitchPreference.setSummaryOn(R.string.accessibility_feature_state_on);
        mEnablerSwitchPreference.setSummaryOff(R.string.accessibility_feature_state_off);
        mWifiEnabler = new WifiEnabler(activity, mEnablerSwitchPreference);
    }
    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateMobileSettingsState();
            updateVPNSettingState();
        }
    };

    private boolean isAirplaneOff(){
        return Settings.Global.getInt(WirelessSettings.this.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 1;
    }

    @Override
    public void onStart() {
        super.onStart();

        initSmsApplicationSetting();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mWifiEnabler != null) {
            mWifiEnabler.resume(1);
        }

        mAirplaneModeEnabler.resume();
        if (mNsdEnabler != null) {
            mNsdEnabler.resume();
        }

        // SPRD: add for <Bug#255679> setenable false when no sim card  start
        updateMobileSettingsState();
        // SPRD: add for <Bug#255679> setenable false when no sim card end
        updateVPNSettingState();
        getActivity().registerReceiver(mReceiver,new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED));
        final Context context = getActivity();
        if (ImsManager.isWfcEnabledByPlatform(context)) {
        	getPreferenceScreen().addPreference(mButtonWfc);
        	mButtonWfc.setSummary(WifiCallingSettings.getWfcModeSummary(
        			context, ImsManager.getWfcMode(context)));
        } else {
        	removePreference(KEY_WFC_SETTINGS);
        }

        getPreferenceScreen().addPreference(mEnablerSwitchPreference);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!TextUtils.isEmpty(mManageMobilePlanMessage)) {
            outState.putString(SAVED_MANAGE_MOBILE_PLAN_MSG, mManageMobilePlanMessage);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mWifiEnabler != null) {
            mWifiEnabler.pause();
        }

        getActivity().unregisterReceiver(mReceiver);

        mAirplaneModeEnabler.pause();
        if (mNsdEnabler != null) {
            mNsdEnabler.pause();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EXIT_ECM) {
            Boolean isChoiceYes = data.getBooleanExtra(EXIT_ECM_RESULT, false);
            // Set Airplane mode based on the return value and checkbox state
            mAirplaneModeEnabler.setAirplaneModeInECM(isChoiceYes,
                    mAirplaneModePreference.isChecked());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_more_networks;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSmsApplicationPreference && newValue != null) {
            SmsApplication.setDefaultApplication(newValue.toString(), getActivity());
            updateSmsApplicationSetting();
            return true;
        }
        return false;
    }

    // SPRD: add for <Bug#255679> setenable false when no sim card start
    private void updateMobileSettingsState() {
        // SPRD: modify for bug306910
        Activity activity = getActivity();
        if (activity != null && !Utils.isWifiOnly(activity)) {
            boolean isSimReady = false;
            int phoneCount = TelephonyManager.getPhoneCount();
            for (int i = 0; i < phoneCount; i++) {
                TelephonyManager tm = (TelephonyManager)getSystemService(
                        TelephonyManager.getServiceName(Context.TELEPHONY_SERVICE, i));
                log("updateMobileSettingsState:phoneId = "+ i +",isStandby = "+isStandby(i)
                     +",hasIccCard = "+tm.hasIccCard()+",getSimState = "+tm.getSimState());
                // SPRD: modify for bug351122
                if (tm.hasIccCard() && isStandby(i)
                        && tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                    isSimReady = true;
                    break;
                }
            }
            if (mMobileNetworkPreference != null){
                log("updateMobileSettingsState:isSimReady = " + isSimReady + ",isAirplaneOff = " + isAirplaneOff());
                if (!isSimReady || !isAirplaneOff()) {
                    mMobileNetworkPreference.setEnabled(false);
                    mLteService.setEnabled(false);
                } else {
                    mMobileNetworkPreference.setEnabled(true);
                    mLteService.setEnabled(true);
                }
            }
        }
    }
    private void updateVPNSettingState(){
        String toggleable = Settings.Global.getString(WirelessSettings.this.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        // Manually set dependencies for Wifi when not toggleable.
        if (toggleable == null || !toggleable.contains(Settings.Global.RADIO_WIFI)) {
            if(mVPNSettingPreference != null){
                mVPNSettingPreference.setEnabled(isAirplaneOff());
            }
        }
    }

    private boolean isStandby(int phoneId) {
        String tmpStr = Settings.System.SIM_STANDBY + phoneId;
        return Settings.System.getInt(this.getContentResolver(), tmpStr, 1) == 1;
    }
    // SPRD: add for <Bug#255679> setenable false when no sim card end
    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.startsWith(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                String state = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (state.equals(IccCardConstants.INTENT_VALUE_ICC_ABSENT)
                        || state.equals(IccCardConstants.INTENT_VALUE_ICC_READY)) {
                    updateMobileSettingsState();
                }
                log("action:" + action + ", state:" + state);
            }
        }
    };


    public class WifiEnablerSwitchPreference extends SwitchPreference {

        private float fontScale=1;
        public WifiEnablerSwitchPreference(Context context) {
            super(context);
            fontScale=context.getResources().getDisplayMetrics().scaledDensity;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            ViewGroup preferenceLayout = (ViewGroup) view;
            view.setPadding(0,0,8,0);
            TextView title = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(0);
            TextView summary = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(1);
//            title.setTextSize(30);
            //20px to sp
//            summary.setTextSize(20 / fontScale);
            title.setPadding(8,0,0,0);
            summary.setPadding(8,0,0,0);
        }
    }

}
