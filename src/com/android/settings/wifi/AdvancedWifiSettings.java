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

package com.android.settings.wifi;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiWatchdogStateMachine;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.security.Credentials;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener,TimePickerDialog.OnTimeSetListener {

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_FREQUENCY_BAND = "frequency_band";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_POOR_NETWORK_DETECTION = "wifi_poor_network_detection";
    private static final String KEY_SCAN_ALWAYS_AVAILABLE = "wifi_scan_always_available";
    private static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String KEY_SUSPEND_OPTIMIZATIONS = "suspend_optimizations";
    private static final String KEY_RESET_WIFI_POLICY_DIALOG = "reset_wifi_policy_show_dialog";

    //add by spreadst_lc for cmcc wifi feature start
    private static final String KEY_MOBILE_TO_WLAN_PREFERENCE_CATEGORY = "mobile_to_wlan_preference_category";
    private static final String KEY_MOBILE_TO_WLAN_POLICY = "mobile_to_wlan_policy";
    private static final String KEK_MOBILE_TO_AUTO_ROAM = "mobile_to_auto_roam";
    private boolean supportCMCC = false;
    //add by spreadst_lc for cmcc wifi feature end
    private static final String KEY_DIALOG_CONNECT_TO_CMCC = "show_dialog_connect_to_cmcc";
    private static final int DEFAULT_CHECKED_VALUE = 1;

    private WifiManager mWifiManager;
    private Preference mResetPreference;

    private static final String KEY_WIFI_ALARM_CATEGORY = "wifi_alarm_category";
    private static final String KEY_WIFI_CONNECT_ALARM_CHECKBOX = "wifi_connect_alarm_checkbox";
    private static final String KEY_WIFI_CONNECT_ALARM_TIME = "wifi_connect_alarm_time";
    private static final String KEY_WIFI_DISCONNECT_ALARM_CHECKBOX = "wifi_disconnect_alarm_checkbox";
    private static final String KEY_WIFI_DISCONNECT_ALARM_TIME = "wifi_disconnect_alarm_time";

    private static final int DIALOG_WIFI_CONNECT_TIMEPICKER = 0;
    private static final int DIALOG_WIFI_DISCONNECT_TIMEPICKER = 1;

    private CheckBoxPreference mConnectCheckBox;
    private Preference mConnectTimePref;
    private CheckBoxPreference mDisconnectCheckBox;
    private Preference mDisconnectTimePref;

    AlarmManager mAlarmManager;
    private int whichTimepicker = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportCMCC = SystemProperties.get("ro.operator").equals("cmcc");
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mAlarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
        WifiConnectionPolicy.init(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        refreshWifiInfo();

        //add by spreadst_lc for cmcc wifi feature start
        if(supportCMCC) {
            initCellularWLANPreference();
        } else {
            PreferenceCategory wifiCellularPreferenceCategory = (PreferenceCategory)findPreference(KEY_MOBILE_TO_WLAN_PREFERENCE_CATEGORY);
            ListPreference pref = (ListPreference) findPreference(KEY_MOBILE_TO_WLAN_POLICY);
            if (pref != null) getPreferenceScreen().removePreference(pref);
            if (wifiCellularPreferenceCategory != null) getPreferenceScreen().removePreference(wifiCellularPreferenceCategory);
            getPreferenceScreen().removePreference(findPreference(KEY_DIALOG_CONNECT_TO_CMCC));
            getPreferenceScreen().removePreference(findPreference(KEY_RESET_WIFI_POLICY_DIALOG));
            getPreferenceScreen().removePreference(findPreference(KEY_WIFI_ALARM_CATEGORY));
            getPreferenceScreen().removePreference(findPreference(KEY_WIFI_CONNECT_ALARM_CHECKBOX));
            getPreferenceScreen().removePreference(findPreference(KEY_WIFI_CONNECT_ALARM_TIME));
            getPreferenceScreen().removePreference(findPreference(KEY_WIFI_DISCONNECT_ALARM_CHECKBOX));
            getPreferenceScreen().removePreference(findPreference(KEY_WIFI_DISCONNECT_ALARM_TIME));
        }
        //add by spreadst_lc for cmcc wifi feature end
    }

    private void initPreferences() {
        CheckBoxPreference notifyOpenNetworks =
            (CheckBoxPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());

        CheckBoxPreference poorNetworkDetection =
            (CheckBoxPreference) findPreference(KEY_POOR_NETWORK_DETECTION);
        if (poorNetworkDetection != null) {
            if (Utils.isWifiOnly(getActivity())) {
                getPreferenceScreen().removePreference(poorNetworkDetection);
            } else {
                poorNetworkDetection.setChecked(Settings.Global.getInt(getContentResolver(),
                        Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                        WifiWatchdogStateMachine.DEFAULT_POOR_NETWORK_AVOIDANCE_ENABLED ?
                        1 : 0) == 1);
            }
        }

        CheckBoxPreference scanAlwaysAvailable =
            (CheckBoxPreference) findPreference(KEY_SCAN_ALWAYS_AVAILABLE);
        scanAlwaysAvailable.setChecked(Settings.Global.getInt(getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1);

        Intent intent=new Intent(Credentials.INSTALL_AS_USER_ACTION);
        intent.setClassName("com.android.certinstaller",
                "com.android.certinstaller.CertInstallerMain");
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, android.os.Process.WIFI_UID);
        Preference pref = findPreference(KEY_INSTALL_CREDENTIALS);
        pref.setIntent(intent);
        // SPRD: fixed bug 257252
        //CheckBoxPreference suspendOptimizations =
        //    (CheckBoxPreference) findPreference(KEY_SUSPEND_OPTIMIZATIONS);
        //suspendOptimizations.setChecked(Settings.Global.getInt(getContentResolver(),
        //        Settings.Global.WIFI_SUSPEND_OPTIMIZATIONS_ENABLED, 1) == 1);

        ListPreference frequencyPref = (ListPreference) findPreference(KEY_FREQUENCY_BAND);

        if (mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
                updateFrequencyBandSummary(frequencyPref, value);
            } else {
                Log.e(TAG, "Failed to fetch frequency band");
            }
        } else {
            if (frequencyPref != null) {
                // null if it has already been removed before resume
                getPreferenceScreen().removePreference(frequencyPref);
            }
        }

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(getActivity())) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            int value = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.WIFI_SLEEP_POLICY,
                    Settings.Global.WIFI_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    private void updateFrequencyBandSummary(Preference frequencyBandPref, int index) {
        String[] summaries = getResources().getStringArray(R.array.wifi_frequency_band_entries);
        frequencyBandPref.setSummary(summaries[index]);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();

        if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_POOR_NETWORK_DETECTION.equals(key)) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        // SPRD: fixed bug 257252
        //} else if (KEY_SUSPEND_OPTIMIZATIONS.equals(key)) {
        //    Settings.Global.putInt(getContentResolver(),
        //            Settings.Global.WIFI_SUSPEND_OPTIMIZATIONS_ENABLED,
        //            ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_SCAN_ALWAYS_AVAILABLE.equals(key)) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_DIALOG_CONNECT_TO_CMCC.equals(key)) {
            Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.DIALOG_CONNECT_TO_CMCC,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_RESET_WIFI_POLICY_DIALOG.equals(key)) {
            mResetPreference.setEnabled(false);
            WifiConnectionPolicy.resetTimer();
            Settings.Global.putInt(getContentResolver(),
                    WifiConnectionPolicy.DIALOG_WLAN_TO_WLAN, 0);
            Settings.Global.putInt(getContentResolver(),
                    WifiConnectionPolicy.DIALOG_MOBILE_TO_WLAN_ALWAYS_ASK, 0);
            Settings.Global.putInt(getContentResolver(),
                    WifiConnectionPolicy.DIALOG_MOBILE_TO_WLAN_MANUAL, 0);
            Settings.Global.putInt(getContentResolver(),
                    WifiConnectionPolicy.DIALOG_WLAN_TO_MOBILE, 0);
            mResetPreference.setEnabled(true);
            Toast.makeText(getActivity(),
                    getResources().getString(R.string.reset_wifi_policy_show_dialog_toast_message),
                    Toast.LENGTH_SHORT).show();
        } else if (KEY_WIFI_CONNECT_ALARM_CHECKBOX.equals(key)) {
            boolean isChecked = ((CheckBoxPreference) preference).isChecked();
            if (isChecked) {
                int disconnFlag = Settings.Global.getInt(getContentResolver(),
                        WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_FLAG, 0);
                if (isSameTime() && disconnFlag == 1) {
                    ((CheckBoxPreference) preference).setChecked(false);
                    Toast.makeText(getActivity(), R.string.wifi_set_time_alarm_warning,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                setConnectWifiAlarm();
            } else {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                        WifiConnectionPolicy.ALARM_FOR_CONNECT_WIFI_ACTION), 0);
                mAlarmManager.cancel(pendingIntent);
            }
            Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_FLAG, isChecked ? 1 : 0);
        } else if (KEY_WIFI_CONNECT_ALARM_TIME.equals(key)) {
            removeDialog(DIALOG_WIFI_CONNECT_TIMEPICKER);
            showDialog(DIALOG_WIFI_CONNECT_TIMEPICKER);
        } else if (KEY_WIFI_DISCONNECT_ALARM_CHECKBOX.equals(key)) {
            boolean isChecked = ((CheckBoxPreference) preference).isChecked();
            if (isChecked) {
                int connFlag = Settings.Global.getInt(getContentResolver(),
                        WifiConnectionPolicy.WIFI_CONNECT_ALARM_FLAG, 0);
                if (isSameTime() && connFlag == 1) {
                    ((CheckBoxPreference) preference).setChecked(false);
                    Toast.makeText(getActivity(), R.string.wifi_set_time_alarm_warning,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                setDisonnectWifiAlarm();
            } else {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                        WifiConnectionPolicy.ALARM_FOR_DISCONNECT_WIFI_ACTION), 0);
                mAlarmManager.cancel(pendingIntent);
            }
            Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_FLAG, isChecked ? 1 : 0);
        } else if (KEY_WIFI_DISCONNECT_ALARM_TIME.equals(key)) {
            removeDialog(DIALOG_WIFI_DISCONNECT_TIMEPICKER);
            showDialog(DIALOG_WIFI_DISCONNECT_TIMEPICKER);
        } else if (KEK_MOBILE_TO_AUTO_ROAM.equals(key)) {
            // SPRD : Add for Bug 418034
            boolean isChecked = ((CheckBoxPreference) preference).isChecked();
            if (isChecked) {
                Settings.Global.putInt(getContentResolver(),Settings.Global.WIFI_AUTO_ROAM_SWITCH, 1);
            } else {
                Settings.Global.putInt(getContentResolver(),Settings.Global.WIFI_AUTO_ROAM_SWITCH, 0);
            }
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_FREQUENCY_BAND.equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                mWifiManager.setFrequencyBand(value, true);
                updateFrequencyBandSummary(preference, value);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_frequency_band_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        //add by spreadst_lc for cmcc wifi feature start
        if(KEY_MOBILE_TO_WLAN_POLICY.equals(key)){
            try{
                int value = Integer.parseInt(((String) newValue));
                WifiConnectionPolicy.setMobileToWlanPolicy(getActivity(), value);
                updateMobileToWlanSummary(preference, value);
            }catch(NumberFormatException e){
                Toast.makeText(getActivity(), R.string.mobile_to_wlan_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        return true;
    }

    private void refreshWifiInfo() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : getActivity().getString(R.string.status_unavailable));

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(getActivity());
        wifiIpAddressPref.setSummary(ipAddress == null ?
                getActivity().getString(R.string.status_unavailable) : ipAddress);
    }

    //add by spreadst_lc for cmcc wifi feature start
    private void initCellularWLANPreference() {
        boolean wifiEnabled = mWifiManager.isWifiEnabled();
        ListPreference pref = (ListPreference) findPreference(KEY_MOBILE_TO_WLAN_POLICY);
        pref.setEnabled(wifiEnabled);
        pref.setOnPreferenceChangeListener(this);
        int value = WifiConnectionPolicy.getMobileToWlanPolicy(getActivity());
        pref.setValue(String.valueOf(value));
        updateMobileToWlanSummary(pref, value);

        CheckBoxPreference ConnectToCmccCheckBox = (CheckBoxPreference) findPreference(KEY_DIALOG_CONNECT_TO_CMCC);
        setShowDialogCheckBoxStatus(ConnectToCmccCheckBox, WifiConnectionPolicy.DIALOG_CONNECT_TO_CMCC, wifiEnabled);
        mResetPreference = (Preference) findPreference(KEY_RESET_WIFI_POLICY_DIALOG);

        mConnectCheckBox = (CheckBoxPreference) findPreference(KEY_WIFI_CONNECT_ALARM_CHECKBOX);
        mConnectTimePref = (Preference) findPreference(KEY_WIFI_CONNECT_ALARM_TIME);
        mDisconnectCheckBox = (CheckBoxPreference) findPreference(KEY_WIFI_DISCONNECT_ALARM_CHECKBOX);
        mDisconnectTimePref = (Preference) findPreference(KEY_WIFI_DISCONNECT_ALARM_TIME);

        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        updateTimeDisplay(mConnectTimePref, calendar);
        hourOfDay = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_HOUR, 0);
        minute = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_MINUTE, 0);
        calendar = getCalendar(hourOfDay, minute);
        updateTimeDisplay(mDisconnectTimePref, calendar);
    }
    //add by spreadst_lc for cmcc wifi feature end

    private void setShowDialogCheckBoxStatus(CheckBoxPreference item, String showDialogFlag,
            boolean wifiEnabled) {
        item.setChecked(shouldShowDialog(showDialogFlag));
        item.setEnabled(wifiEnabled);
    }

    private boolean shouldShowDialog(String showDialogFlag) {
        return Settings.Global.getInt(getContentResolver(), showDialogFlag, DEFAULT_CHECKED_VALUE) == 1;
    }

    private void updateMobileToWlanSummary(Preference preference, int index) {
        String[] summaries = getResources().getStringArray(R.array.mobile_to_wlan);
        preference.setSummary(summaries[index]);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        final Calendar calendar = Calendar.getInstance();
        switch (dialogId) {
            case DIALOG_WIFI_CONNECT_TIMEPICKER:
                whichTimepicker = DIALOG_WIFI_CONNECT_TIMEPICKER;
                return new TimePickerDialog(
                        getActivity(),
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(getActivity()));
            case DIALOG_WIFI_DISCONNECT_TIMEPICKER:
                whichTimepicker = DIALOG_WIFI_DISCONNECT_TIMEPICKER;
                return new TimePickerDialog(
                        getActivity(),
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(getActivity()));
            default:
                break;
        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Log.d(TAG, "onTimeSet");

        Calendar calendar = getCalendar(hourOfDay, minute);
        if (whichTimepicker != -1) {
            switch (whichTimepicker) {
                case DIALOG_WIFI_CONNECT_TIMEPICKER:
                    int hourOfDayForDisc = Settings.Global.getInt(getContentResolver(),
                            WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_HOUR, 0);
                    int minuteForDisc = Settings.Global.getInt(getContentResolver(),
                            WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_MINUTE, 0);
                    if (hourOfDay == hourOfDayForDisc && minuteForDisc == minute) {
                        Toast.makeText(getActivity(), R.string.wifi_set_time_alarm_warning,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_HOUR, hourOfDay);
                        Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_MINUTE, minute);
                        if (mConnectCheckBox.isChecked()) {
                            setConnectWifiAlarm();
                        }
                        updateTimeDisplay(mConnectTimePref, calendar);
                    }
                    break;
                case DIALOG_WIFI_DISCONNECT_TIMEPICKER:
                    int hourOfDayForConn = Settings.Global.getInt(getContentResolver(),
                            WifiConnectionPolicy.WIFI_CONNECT_ALARM_HOUR, 0);
                    int minuteForConn = Settings.Global.getInt(getContentResolver(),
                            WifiConnectionPolicy.WIFI_CONNECT_ALARM_MINUTE, 0);
                    if (hourOfDay == hourOfDayForConn && minuteForConn == minute) {
                        Toast.makeText(getActivity(), R.string.wifi_set_time_alarm_warning,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_HOUR, hourOfDay);
                        Settings.Global.putInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_MINUTE, minute);
                        if (mDisconnectCheckBox.isChecked()) {
                            setDisonnectWifiAlarm();
                        }
                        updateTimeDisplay(mDisconnectTimePref, calendar);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private Calendar getCalendar(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 1);
        return calendar;
    }

    private void setConnectWifiAlarm() {
        Log.d(TAG, "setConnectWifiAlarm");
        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_CONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        long inMillis = calendar.getTimeInMillis();
        if (isDismissCalendar(hourOfDay, minute)) {
            inMillis += WifiConnectionPolicy.INTERVAL_MILLIS;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                WifiConnectionPolicy.ALARM_FOR_CONNECT_WIFI_ACTION), 0);
        mAlarmManager.cancel(pendingIntent);
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, inMillis, pendingIntent);
    }

    private void setDisonnectWifiAlarm() {
        Log.d(TAG, "setDisonnectWifiAlarm");
        int hourOfDay = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_HOUR, 0);
        int minute = Settings.Global.getInt(getContentResolver(), WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_MINUTE, 0);
        Calendar calendar = getCalendar(hourOfDay, minute);
        long inMillis = calendar.getTimeInMillis();
        if (isDismissCalendar(hourOfDay, minute)) {
            inMillis += WifiConnectionPolicy.INTERVAL_MILLIS;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(
                WifiConnectionPolicy.ALARM_FOR_DISCONNECT_WIFI_ACTION), 0);
        mAlarmManager.cancel(pendingIntent);
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, inMillis, pendingIntent);
    }

    private boolean isDismissCalendar(int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        if (calendar.get(Calendar.HOUR_OF_DAY) > hourOfDay) {
            return true;
        }else if (calendar.get(Calendar.HOUR_OF_DAY) == hourOfDay) {
            if (calendar.get(Calendar.MINUTE) >= minute) {
                return true;
            }
        }
        return false;
    }

    private void updateTimeDisplay(Preference preference, Calendar calendar) {
        preference.setSummary(DateFormat.getTimeFormat(getActivity()).format(
                calendar.getTime()));
    }

    private boolean isSameTime() {
        int hourOfDayForConn = Settings.Global.getInt(getContentResolver(),
                WifiConnectionPolicy.WIFI_CONNECT_ALARM_HOUR, 0);
        int hourOfDayForDisconn = Settings.Global.getInt(getContentResolver(),
                WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_HOUR, 0);
        if (hourOfDayForConn != hourOfDayForDisconn) {
            return false;
        }

        int minuteForConn = Settings.Global.getInt(getContentResolver(),
                WifiConnectionPolicy.WIFI_CONNECT_ALARM_MINUTE, 0);
        int minuteForDisconn = Settings.Global.getInt(getContentResolver(),
                WifiConnectionPolicy.WIFI_DISCONNECT_ALARM_MINUTE, 0);
        if (minuteForConn != minuteForDisconn) {
            return false;
        }
        return true;
    }
}
