/*
 * Copyright (C) 2015 The Android Open Source Project
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
//import android.telephony.CarrierConfigManagerEx;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Switch;
import android.widget.TextView;

import com.android.ims.ImsConfig;
import com.android.ims.ImsManager;
//import com.android.internal.logging.MetricsLogger;
//import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.telephony.Phone;
import android.preference.PreferenceActivity;
import android.view.Gravity;
import android.app.ActionBar;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import com.android.ims.ImsManager;
import android.preference.SwitchPreference;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Toast;

/**
 * "Wi-Fi Calling settings" screen.  This preference screen lets you
 * enable/disable Wi-Fi Calling and change Wi-Fi Calling mode.
 */
public class WifiCallingSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "WifiCallingSettings";

    //String keys for preference lookup
    private static final String BUTTON_WFC_MODE = "wifi_calling_mode";

    //UI objects
    private ListPreference mButtonWfcMode;
    private TextView mEmptyView;

    private boolean mValidListener = false;
    private boolean mEditableWfcMode = true;
    private Switch actionBarSwitch;
    private SwitchPreference mVowifiCallingSettingsPreference;
    private Preference mEmptyPreference;

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        /*
         * Enable/disable controls when in/out of a call and depending on
         * TTY mode and TTY support over VoLTE.
         * @see android.telephony.PhoneStateListener#onCallStateChanged(int,
         * java.lang.String)
         */
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            final Settings activity = (Settings) getActivity();
            //boolean isNonTtyOrTtyOnVolteEnabled = ImsManager.isNonTtyOrTtyOnVolteEnabled(activity);
             boolean isWfcEnabled = mVowifiCallingSettingsPreference.isChecked();
                   // && isNonTtyOrTtyOnVolteEnabled;

            mVowifiCallingSettingsPreference.setEnabled(state == TelephonyManager.CALL_STATE_IDLE);
                   // && isNonTtyOrTtyOnVolteEnabled);
            Log.d(TAG, "onCallStateChanged isWfcEnabled:"+isWfcEnabled+" state:"+state);

            Preference pref = getPreferenceScreen().findPreference(BUTTON_WFC_MODE);
            if (pref != null) {
                pref.setEnabled(isWfcEnabled
                        && (state == TelephonyManager.CALL_STATE_IDLE));
            }
        }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        boolean isNonTtyOrTtyOnVolteEnabled = true;
//        if (activity instanceof PreferenceActivity) {
//            PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
//            if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
//                final int padding = activity.getResources().getDimensionPixelSize(
//                        R.dimen.action_bar_switch_padding);
//                actionBarSwitch.setPaddingRelative(0, 0, padding, 0);
//                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
//                        ActionBar.DISPLAY_SHOW_CUSTOM);
//                activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
//                        ActionBar.LayoutParams.WRAP_CONTENT,
//                        ActionBar.LayoutParams.WRAP_CONTENT,
//                        Gravity.CENTER_VERTICAL | Gravity.END));
//                actionBarSwitch.requestFocus();
//            }
//        }
//        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
//        getListView().setEmptyView(mEmptyView);
//        mEmptyView.setText(R.string.wifi_calling_off_explanation);
        //837401 Change switch button from action bar to list
        mVowifiCallingSettingsPreference = new VowifiCallingSettingsPreference(getActivity());
        mVowifiCallingSettingsPreference.setEnabled(true);
        mVowifiCallingSettingsPreference.setSwitchTextOff("");
        mVowifiCallingSettingsPreference.setSwitchTextOn("");
        mVowifiCallingSettingsPreference.setSummaryOn(R.string.accessibility_feature_state_on);
        mVowifiCallingSettingsPreference.setSummaryOff(R.string.accessibility_feature_state_off);
        mEmptyPreference  = new Preference(getActivity());
        getPreferenceScreen().addPreference(mVowifiCallingSettingsPreference);
        mEmptyPreference.setSelectable(false);
        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        mVowifiCallingSettingsPreference.setOrder(0);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        actionBarSwitch.hide();
    }

    private void showAlert(Intent intent) {
        Context context = getActivity();

//        CharSequence title = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_TITLE);
//        CharSequence message = intent.getCharSequenceExtra(Phone.EXTRA_KEY_ALERT_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private IntentFilter mIntentFilter;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ImsManager.ACTION_IMS_REGISTRATION_ERROR)) {
                // If this fragment is active then we are immediately
                // showing alert on screen. There is no need to add
                // notification in this case.
                //
                // In order to communicate to ImsPhone that it should
                // not show notification, we are changing result code here.
                setResultCode(Activity.RESULT_CANCELED);

                // UX requirement is to disable WFC in case of "permanent" registration failures.
                mVowifiCallingSettingsPreference.setChecked(false);

                showAlert(intent);
            }
        }
    };

//    @Override
//    protected int getMetricsCategory() {
//        return MetricsEvent.WIFI_CALLING;
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_calling_settings);
        mButtonWfcMode = (ListPreference) findPreference(BUTTON_WFC_MODE);
        mButtonWfcMode.setOnPreferenceChangeListener(this);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ImsManager.ACTION_IMS_REGISTRATION_ERROR);

        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        boolean isWifiOnlySupported = true;     
        boolean isLteOnlySupported = true;
        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForDefaultPhone();
            if (b != null) {
                //mEditableWfcMode = b.getBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL);
                isWifiOnlySupported = b.getBoolean(
                        CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
                isLteOnlySupported = b.getBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_LTE_ONLY_BOOL);
            }
        }
        Log.d("WifiCallingSettings", "iswifionlysupported:"+isWifiOnlySupported);
        if (!isWifiOnlySupported) {
            mButtonWfcMode.setEntries(R.array.wifi_calling_mode_choices_without_wifi_only);
            mButtonWfcMode.setEntryValues(R.array.wifi_calling_mode_values_without_wifi_only);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final Context context = getActivity();
        if (ImsManager.isWfcEnabledByPlatform(context)) {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
             mValidListener = true;
        }
        // NOTE: Buttons will be enabled/disabled in mPhoneStateListener
        boolean wfcEnabled = ImsManager.isWfcEnabledByUser(context);
               // && ImsManager.isNonTtyOrTtyOnVolteEnabled(context);
                mVowifiCallingSettingsPreference.setChecked(wfcEnabled);
        Log.d(TAG, "onResume wfcEnabled:"+wfcEnabled);
        int wfcMode = ImsManager.getWfcMode(context);
        mButtonWfcMode.setValue(Integer.toString(wfcMode));
        updateButtonWfcMode(context, wfcEnabled, wfcMode);
        context.registerReceiver(mIntentReceiver, mIntentFilter);
//        Intent intent = getActivity().getIntent();
    }

    @Override
    public void onPause() {
        super.onPause();

        final Context context = getActivity();

        if (mValidListener) {
            mValidListener = false;

            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

//            mSwitchBar.removeOnSwitchChangeListener(this);
        }

        context.unregisterReceiver(mIntentReceiver);
    }

    /**
     * Listens to the state change of the switch.
     */


    private void updateButtonWfcMode(Context context, boolean wfcEnabled, int wfcMode) {
        mButtonWfcMode.setSummary(getWfcModeSummary(context, wfcMode));
        mButtonWfcMode.setEnabled(wfcEnabled);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (wfcEnabled) {
            getPreferenceScreen().removePreference(mEmptyPreference);
            preferenceScreen.addPreference(mButtonWfcMode);
            mButtonWfcMode.setOrder(1);
        } else {
            preferenceScreen.removePreference(mButtonWfcMode);
            mEmptyPreference.setSummary(R.string.wifi_calling_off_explanation);
            getPreferenceScreen().addPreference(mEmptyPreference);
        }
        preferenceScreen.setEnabled(mEditableWfcMode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
       if (preference == mButtonWfcMode) {
            mButtonWfcMode.setValue((String) newValue);
            int buttonMode = Integer.valueOf((String) newValue);
            int currentMode = ImsManager.getWfcMode(context);
            if (buttonMode != currentMode) {
                ImsManager.setWfcMode(context, buttonMode);
                mButtonWfcMode.setSummary(getWfcModeSummary(context, buttonMode));
                //MetricsLogger.action(getActivity(), getMetricsCategory(), buttonMode);
            }
        }
        return true;
    }

    static int getWfcModeSummary(Context context, int wfcMode) {
        Log.d("WifiCallingSettings", "getWfcSummary wfcMode:"+wfcMode);
        int resId = R.string.wifi_calling_off_summary;
        if (ImsManager.isWfcEnabledByUser(context)) {
            switch (wfcMode) {
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_ONLY:
                    resId = R.string.wfc_mode_wifi_only_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.CELLULAR_PREFERRED:
                    resId = R.string.wfc_mode_cellular_preferred_summary;
                    break;
                case ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED:
                    resId = R.string.wfc_mode_wifi_preferred_summary;
                    break;
                default:
                    Log.e(TAG, "Unexpected WFC mode value: " + wfcMode);
            }
        }
        return resId;
    }
    public class VowifiCallingSettingsPreference extends SwitchPreference {

        private float fontScale=1;
        public VowifiCallingSettingsPreference(Context context) {
            super(context);
            fontScale=context.getResources().getDisplayMetrics().scaledDensity;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            ViewGroup preferenceLayout = (ViewGroup) view;
            TextView summary = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(1);
            //SPRD: remove for bug840976
            //summary.setTextColor(getContext().getResources().getColor(R.color.black));
            float fontScale = getContext().getResources().getDisplayMetrics().scaledDensity;
            summary.setTextSize(20 / fontScale);
        }
    }
    //add for 837401 Change switch button from action bar to list
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        final Context context = getActivity();
        final Activity activity = getActivity();
        if (preference == mVowifiCallingSettingsPreference) {
            boolean isChecked = mVowifiCallingSettingsPreference.isChecked();
            mVowifiCallingSettingsPreference.setOrder(0);
            Log.d("WifiCallingSettings", "onCheckedChange isChecked:"+isChecked);
            if(getActivity() != null){
                ImsManager.setWfcSetting(getActivity(),isChecked);
                int wfcMode = ImsManager.getWfcMode(getActivity());
                updateButtonWfcMode(getActivity(),isChecked,wfcMode);
                //SPRD: Add for bug 884368
                if (relatedVolteVowifi()){
                    /* SPRD: Add for bug837696. @{ */
                    if (isChecked) {
                        if (ImsManager.isVolteEnabledByPlatform(getActivity())
                            && !ImsManager.isEnhanced4gLteModeSettingEnabledByUser(getActivity())) {
                            ImsManager.setEnhanced4gLteModeSetting(getActivity(), true);
                            Toast.makeText(activity, R.string.vowifi_service_volte_open_synchronously, Toast.LENGTH_LONG).show();
                        }
                    }
             /* @} */
                }
            }
        }
        return true;
    }
    /* SPRD: Add for bug 884368. @{ */
    private boolean relatedVolteVowifi() {
        boolean isRelated = true;
        int phoneId = TelephonyManager.getDefaultDataPhoneId(getActivity());
        CarrierConfigManager configManager = (CarrierConfigManager)
                getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle config = configManager.getConfigForPhoneId(phoneId);
            if (config != null) {
                isRelated = config.getBoolean(CarrierConfigManager.KEY_RELATED_VOWIFI_AND_VOLTE);
            }
        }
        Log.d(TAG, "isRelated = "+ isRelated);
        return isRelated;
    }
    /* @} */
}
