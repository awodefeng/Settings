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

package com.android.settings;

import android.content.ComponentName;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;
import android.telephony.TelephonyManager;

import com.sprd.settings.RecoverySystemUpdatePreference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import android.text.TextUtils;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Handler;
import android.os.Message;
import android.content.Context;
import android.os.SystemProperties;
//fota start
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
//fota end

public class DeviceInfoSettings extends RestrictedSettingsFragment {

    private static final String LOG_TAG = "DeviceInfoSettings";

    private static final String FILENAME_PROC_VERSION = "/proc/version";
    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_TEAM = "team";
    private static final String KEY_CONTRIBUTORS = "contributors";
    private static final String KEY_REGULATORY_INFO = "regulatory_info";
    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String PROPERTY_URL_SAFETYLEGAL = "ro.url.safetylegal";
    private static final String PROPERTY_SELINUX_STATUS = "ro.build.selinux";
    private static final String KEY_KERNEL_VERSION = "kernel_version";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_SELINUX_STATUS = "selinux_status";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_FIRMWARE_VERSION = "firmware_version";
    private static final String KEY_UPDATE_SETTING = "additional_system_update_settings";
    private static final String KEY_EQUIPMENT_ID = "fcc_equipment_id";
    private static final String PROPERTY_EQUIPMENT_ID = "ro.ril.fccid";
    //SPRD: Modify Bug 210894,add form cmcc Detailed hardware version
    private static final String KEY_HARDWARE_VERSION = "hardware_version";
    //SPRD: added for multiSimChoose
    private static final String KEY_STATUS_INFO = "status_info";
    // SPRD: Modify for bug912326.
    private static final String DEF_FIRMWARE_VERSION = "Mocor 1.0";
    private static final String VALUE_MATCH_VERSION = "4.4.4";
    public static boolean PIKEL_UI_SUPPORT = SystemProperties.getBoolean("pikel_ui_support",true);

    static final int TAPS_TO_BE_A_DEVELOPER = 7;

    long[] mHits = new long[3];
    int mDevHitCountdown;
    Toast mDevHitToast;

    /* SPRD: add CP2 version for bug 268191 @{ */
    private Runnable mBasedVerRunnable;
    private static final int MSG_UPDATE_BASED_VERSION_SUMMARY = 1;

    /* @} */

    public DeviceInfoSettings() {
        super(null /* Don't PIN protect the entire screen */);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.device_info_settings);

        // We only call ensurePinRestrictedPreference() when mDevHitCountdown == 0.
        // This will keep us from entering developer mode without a PIN.
        protectByRestrictions(KEY_BUILD_NUMBER);
        // SPRD: SPRD: for Bug271433 when BroadcastReceiver is already unregistered mustn't unregister it again
        initRecoverySystemUpdatePreference();

        // SPRD: Modify for bug912326.
        setStringSummary(KEY_FIRMWARE_VERSION, DEF_FIRMWARE_VERSION);
        findPreference(KEY_FIRMWARE_VERSION).setEnabled(true);
        /* SPRD: add CP2 version for bug 268191 @{ */
        setValueSummary(KEY_BASEBAND_VERSION, "gsm.version.baseband");
        /* @} */
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL + getMsvSuffix());
        setValueSummary(KEY_EQUIPMENT_ID, PROPERTY_EQUIPMENT_ID);
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL);
        /* SPRD: change version for bug 367494 @{ */
        boolean wcnVersion = SystemProperties.get("ro.wcn").equals("disabled");
        Log.d(LOG_TAG, " wcnVersion = " + wcnVersion);
        if (wcnVersion) {
            setStringSummary(KEY_BUILD_NUMBER, "SharkL_V1.0");
        } else {
            /* SPRD: Modify for bug912326. @{ */
            String buildNumber = Build.DISPLAY;
            if (!TextUtils.isEmpty(buildNumber) && buildNumber.contains(VALUE_MATCH_VERSION)) {
                buildNumber = buildNumber.replace(VALUE_MATCH_VERSION, DEF_FIRMWARE_VERSION);
            }
            setStringSummary(KEY_BUILD_NUMBER, buildNumber);
            /* @} */
        }
        /* @} */
        findPreference(KEY_BUILD_NUMBER).setEnabled(true);
        findPreference(KEY_KERNEL_VERSION).setSummary(getFormattedKernelVersion());

        /* SPRD: change version for bug 367494 @{ */
        if (wcnVersion) {
            setStringSummary(KEY_HARDWARE_VERSION, "SPRD_SHARKL_DS");
        }
        //SPRD: Modify Bug 210894,add form cmcc Detailed hardware version
        else if(SystemProperties.get("ro.product.board.customer", "none").equalsIgnoreCase("cgmobile")){
            //cg modify by xuyouqin start
            setStringSummary(KEY_HARDWARE_VERSION, SystemProperties.get("ro.product.hardware", "P1"));
            //cg modify by xuyouqin end
        }else{
            setStringSummary(KEY_HARDWARE_VERSION, SystemProperties.get("ro.product.hardware", "SPREADTRUM"));
        }
        getPreferenceScreen().removePreference(findPreference(KEY_HARDWARE_VERSION));

        if (!SELinux.isSELinuxEnabled()) {
            String status = getResources().getString(R.string.selinux_status_disabled);
            setStringSummary(KEY_SELINUX_STATUS, status);
        } else if (!SELinux.isSELinuxEnforced()) {
            String status = getResources().getString(R.string.selinux_status_permissive);
            setStringSummary(KEY_SELINUX_STATUS, status);
        }

        // Remove selinux information if property is not present
        removePreferenceIfPropertyMissing(getPreferenceScreen(), KEY_SELINUX_STATUS,
                PROPERTY_SELINUX_STATUS);

        // Remove Safety information preference if PROPERTY_URL_SAFETYLEGAL is not set
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "safetylegal",
                PROPERTY_URL_SAFETYLEGAL);

        // Remove Equipment id preference if FCC ID is not set by RIL
        removePreferenceIfPropertyMissing(getPreferenceScreen(), KEY_EQUIPMENT_ID,
                PROPERTY_EQUIPMENT_ID);

        // Remove Baseband version if wifi-only device
        if(SystemProperties.get("ro.product.board.customer", "none").equalsIgnoreCase("cgmobile")){
            //cg modify by xuyouqin Remove Baseband version start
            /*if (Utils.isWifiOnly(getActivity()))*/ {
                getPreferenceScreen().removePreference(findPreference(KEY_BASEBAND_VERSION));
            }
            //cg modify by xuyouqin Remove Baseband version end
        }else{
            if (Utils.isWifiOnly(getActivity())) {
                getPreferenceScreen().removePreference(findPreference(KEY_BASEBAND_VERSION));
            }
        }

        /*
         * Settings is a generic app and should not contain any device-specific
         * info.
         */
        final Activity act = getActivity();
        // These are contained in the "container" preference group
        PreferenceGroup parentPreference = (PreferenceGroup) findPreference(KEY_CONTAINER);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TERMS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_LICENSE,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_COPYRIGHT,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TEAM,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        // SPRD: Add for bug695734.
        getPreferenceScreen().removePreference(parentPreference);
        // These are contained by the root preference screen
        parentPreference = getPreferenceScreen();
        if (UserHandle.myUserId() == UserHandle.USER_OWNER) {
            Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference,
                    KEY_SYSTEM_UPDATE_SETTINGS,
                    Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        } else {
            // Remove for secondary users
            removePreference(KEY_SYSTEM_UPDATE_SETTINGS);
        }
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_CONTRIBUTORS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);

        // Read platform settings for additional system update setting
        removePreferenceIfBoolFalse(KEY_UPDATE_SETTING,
                R.bool.config_additional_system_update_setting_enable);

        // Remove regulatory information if not enabled.
        removePreferenceIfBoolFalse(KEY_REGULATORY_INFO,
                R.bool.config_show_regulatory_info);
	/*redstone fota*/
	if(isApkExist(act,"com.redstone.ota.ui") == false){
		if(findPreference("rsfota_update_settings") != null){
			getPreferenceScreen().removePreference(findPreference("rsfota_update_settings"));
		}
	}else{
		Preference preference = findPreference("rsfota_update_settings");
		if(preference != null){
			preference.setTitle(getAppName(act,"com.redstone.ota.ui"));
		}
	}
	/*redstone end*/
 
	//fota start
        if(isApkExist(act, "com.adups.fota") == false){
            if(findPreference("fota_update_settings") != null){
        	    getPreferenceScreen().removePreference(findPreference("fota_update_settings"));
            }
		} else {
		    Preference preference = findPreference("fota_update_settings");
			if (preference != null) {
		        preference.setTitle(getAppName(act, "com.adups.fota"));
			}
		}
        //fota end
        /* SPRD: add CP2 version for bug 268191 @{ */
        mBasedVerRunnable = new Runnable() {
            public void run() {
                getBasedSummary();
            }
        };
        new Thread(mBasedVerRunnable).start();
        /* @} */
    }

    @Override
    public void onResume() {
        super.onResume();
        mDevHitCountdown = getActivity().getSharedPreferences(DevelopmentSettings.PREF_FILE,
                Context.MODE_PRIVATE).getBoolean(DevelopmentSettings.PREF_SHOW,
                        android.os.Build.TYPE.equals("eng")) ? -1 : TAPS_TO_BE_A_DEVELOPER;
        mDevHitToast = null;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(KEY_FIRMWARE_VERSION)) {
            System.arraycopy(mHits, 1, mHits, 0, mHits.length-1);
            mHits[mHits.length-1] = SystemClock.uptimeMillis();
            if (mHits[0] >= (SystemClock.uptimeMillis()-500)) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("android",
                        com.android.internal.app.PlatLogoActivity.class.getName());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unable to start activity " + intent.toString());
                }
            }
        } else if (preference.getKey().equals(KEY_BUILD_NUMBER)) {
            // Don't enable developer options for secondary users.
            if (UserHandle.myUserId() != UserHandle.USER_OWNER) return true;

            if (mDevHitCountdown > 0) {
                if (mDevHitCountdown == 1) {
                    if (super.ensurePinRestrictedPreference(preference)) {
                        return true;
                    }
                }
                mDevHitCountdown--;
                if (mDevHitCountdown == 0) {
                    getActivity().getSharedPreferences(DevelopmentSettings.PREF_FILE,
                            Context.MODE_PRIVATE).edit().putBoolean(
                                    DevelopmentSettings.PREF_SHOW, true).apply();
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_on,
                            Toast.LENGTH_LONG);
                    mDevHitToast.show();
                } else if (mDevHitCountdown > 0
                        && mDevHitCountdown < (TAPS_TO_BE_A_DEVELOPER-2)) {
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(getActivity(), getResources().getQuantityString(
                            R.plurals.show_dev_countdown, mDevHitCountdown, mDevHitCountdown),
                            Toast.LENGTH_SHORT);
                    mDevHitToast.show();
                }
            } else if (mDevHitCountdown < 0) {
                if (mDevHitToast != null) {
                    mDevHitToast.cancel();
                }
                mDevHitToast = Toast.makeText(getActivity(), R.string.show_dev_already,
                        Toast.LENGTH_LONG);
                mDevHitToast.show();
            }
        } else if (KEY_STATUS_INFO.equals(preference.getKey())) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            ComponentName targetComponent = null;
            if(PIKEL_UI_SUPPORT){
                intent.setComponent(new ComponentName("com.android.settings","com.android.settings.deviceinfo.StatusOther"));
            } else {
                if (TelephonyManager.isMultiSim()) {
                    if(com.android.settings.Settings.UNIVERSEUI_SUPPORT){
                        targetComponent = new ComponentName("com.android.settings","com.sprd.settings.sim.MobileSimChooseUUI");
                    }else{
                        targetComponent = new ComponentName("com.android.settings","com.android.settings.MobileSimChoose");
                    }
                    intent.setComponent(targetComponent);
                    intent.putExtra("package_name", "com.android.settings");
                    intent.putExtra("class_name", "com.android.settings.deviceinfo.StatusSim");
                    intent.putExtra("class_name_other", "com.android.settings.deviceinfo.StatusOther");
                    intent.putExtra("title_name", R.string.device_status_ex);
                } else {
                    intent.setComponent(new ComponentName("com.android.settings","com.android.settings.deviceinfo.Status"));
                }
            }
            startActivity(intent);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void removePreferenceIfPropertyMissing(PreferenceGroup preferenceGroup,
            String preference, String property ) {
        if (SystemProperties.get(property).equals("")) {
            // Property is missing so remove preference from group
            try {
                preferenceGroup.removePreference(findPreference(preference));
            } catch (RuntimeException e) {
                Log.d(LOG_TAG, "Property '" + property + "' missing and no '"
                        + preference + "' preference");
            }
        }
    }

    private void removePreferenceIfBoolFalse(String preference, int resId) {
        if (!getResources().getBoolean(resId)) {
            Preference pref = findPreference(preference);
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }
    }

    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary(
                getResources().getString(R.string.device_info_default));
        }
    }

    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property,
                            getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
            // No recovery
        }
    }
    /* SPRD: add CP2 version for bug 268191 @{ */
    private void getBasedSummary() {
        try {
            String cp2 = "";
            String temp;
            String summary = findPreference(KEY_BASEBAND_VERSION).getSummary().toString();
            temp = getCp2Version();
            if (temp != null) {
                Log.d(LOG_TAG, " temp = " + temp);
                temp = temp.replaceAll("\\s+", "");
                if (temp.startsWith("Platform")) {
                    final String PROC_VERSION_REGEX =
                            "PlatformVersion:(\\S+)" + "ProjectVersion:(\\S+)" + "HWVersion:(\\S+)";
                    Matcher m = Pattern.compile(PROC_VERSION_REGEX).matcher(temp);
                    if (!m.matches()) {
                        Log.e(LOG_TAG, "Regex did not match on cp2 version: ");
                    } else {
                        String dateTime = m.group(3);
                        String modem = "modem";
                        int endIndex = dateTime.indexOf(modem) + modem.length();
                        String subString1 = dateTime.substring(0, endIndex);
                        String subString2 = dateTime.substring(endIndex);
                        String time = subString2.substring(10);
                        String date = subString2.substring(0, 10);
                        cp2 = m.group(1) + "|" + m.group(2) + "|" + subString1 + "|" + date + " "
                                + time;
                    }
                } else {
                    Log.e(LOG_TAG, "cp2 version is error");
                }
            }
            if (!TextUtils.isEmpty(cp2)) {
                summary += "\n" + cp2;
            }
            Log.d(LOG_TAG, " cp2 = " + cp2);
            Message msg = mHandler.obtainMessage();
            msg.what = MSG_UPDATE_BASED_VERSION_SUMMARY;
            msg.obj = summary;
            mHandler.sendMessage(msg);

        } catch (RuntimeException e) {
            // No recovery
        }
    }
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_BASED_VERSION_SUMMARY:
                    if(SystemProperties.get("ro.product.board.customer", "none").equalsIgnoreCase("cgmobile")){
                        //cg modify by xuyouqin start
                        //findPreference(KEY_BASEBAND_VERSION).setSummary((CharSequence) msg.obj);
                        //cg modify by xuyouqin end
                    }else{
                        findPreference(KEY_BASEBAND_VERSION).setSummary((CharSequence) msg.obj);
                    }
                    break;
            }
        }
    };
    /* @} */ 
    /**
     * Reads a line from the specified file.
     * @param filename the file to read from
     * @return the first line, if any.
     * @throws IOException if the file couldn't be read
     */
    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion() {
        try {
            return formatKernelVersion(readLine(FILENAME_PROC_VERSION));

        } catch (IOException e) {
            Log.e(LOG_TAG,
                "IO Exception when getting kernel version for Device Info screen",
                e);

            return "Unavailable";
        }
    }
    /* SPRD: add CP2 version for bug 268191 @{ */
    public static String getCp2Version() {
        LocalSocket socket = null;
        final String socketName = "wcnd";
        String result = null;
        byte[] buf = new byte[255];
        OutputStream outputStream = null;
        InputStream inputStream = null;
        
        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(socketName,
                    LocalSocketAddress.Namespace.ABSTRACT);
            socket.connect(address);
            outputStream = socket.getOutputStream();
            if (outputStream != null) {
                String strcmd = "wcn at+spatgetcp2info";
                StringBuilder cmdBuilder = new StringBuilder(strcmd).append('\0');
                String cmd = cmdBuilder.toString(); /* Cmd + \0 */
                try {
                    outputStream.write(cmd.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed wrirting output stream: " + e);
                }
            }
            
            inputStream = socket.getInputStream();
            int count = inputStream.read(buf, 0, 255);
            result = new String(buf, "utf-8");
            Log.d(LOG_TAG,"count = "+count);
            if (result.startsWith("Fail")) {
                Log.d(LOG_TAG,"cp2 no data available");
                return null;
            } 
        } catch (Exception e) {  
            Log.i(LOG_TAG, " get socket info fail about：" + e.toString());  
        } finally {  
            try {
                buf = null;
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                socket.close();
            } catch (Exception e) {  
                Log.i(LOG_TAG, "socket fail about：" + e.toString());
            }  
        }   
        return result;
    }   
    /* @} */
    public static String formatKernelVersion(String rawKernelVersion) {
        // Example (see tests for more):
        // Linux version 3.0.31-g6fb96c9 (android-build@xxx.xxx.xxx.xxx.com) \
        //     (gcc version 4.6.x-xxx 20120106 (prerelease) (GCC) ) #1 SMP PREEMPT \
        //     Thu Jun 28 11:02:39 PDT 2012
        final String PROC_VERSION_REGEX =
            "Linux version (\\S+) " + /* group 1: "3.0.31-g6fb96c9" */
            "\\((\\S+?)\\) " +        /* group 2: "x@y.com" (kernel builder) */
            "(?:\\(gcc.+? \\)) " +    /* ignore: GCC version information */
            "(#\\d+) " +              /* group 3: "#1" */
            "(?:.*?)?" +              /* ignore: optional SMP, PREEMPT, and any CONFIG_FLAGS */
            "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)"; /* group 4: "Thu Jun 28 11:02:39 PDT 2012" */

        Matcher m = Pattern.compile(PROC_VERSION_REGEX).matcher(rawKernelVersion);
        if (!m.matches()) {
            Log.e(LOG_TAG, "Regex did not match on /proc/version: " + rawKernelVersion);
            return "Unavailable";
        } else if (m.groupCount() < 4) {
            Log.e(LOG_TAG, "Regex match on /proc/version only returned " + m.groupCount()
                    + " groups");
            return "Unavailable";
        }
            if(SystemProperties.get("ro.product.board.customer", "none").equalsIgnoreCase("cgmobile")){
                //cg modify by xuyouqin start
                return m.group(1) + "\n" + // 3.0.31-g6fb96c9
                        m.group(3);// x@y.com #1

                //cg modify by xuyouqin end              
            }else{
                return m.group(1) + "\n" + // 3.0.31-g6fb96c9
                        m.group(2) + " " + m.group(3) + "\n" + // x@y.com #1
                        m.group(4); // Thu Jun 28 11:02:39 PDT 2012
            }
                    
    }

    /**
     * Returns " (ENGINEERING)" if the msv file has a zero value, else returns "".
     * @return a string to append to the model number description.
     */
    private String getMsvSuffix() {
        // Production devices should have a non-zero value. If we can't read it, assume it's a
        // production device so that we don't accidentally show that it's an ENGINEERING device.
        try {
            String msv = readLine(FILENAME_MSV);
            // Parse as a hex number. If it evaluates to a zero, then it's an engineering build.
            if (Long.parseLong(msv, 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException ioe) {
            // Fail quietly, as the file may not exist on some devices.
        } catch (NumberFormatException nfe) {
            // Fail quietly, returning empty string should be sufficient
        }
        return "";
    }

    /* SPRD: for Bug271433，when BroadcastReceiver is already unregistered mustn't unregister it again @{ */
    private BroadcastReceiver mBatteryLevelRcvr;
    private IntentFilter mBatteryLevelFilter;
    private int mLevelPower;
    private static String KEY_RECOVERY_SYSTEM_UPDATE = "RecoverySystemUpdate";
    private void monitorBatteryState() {
        mBatteryLevelRcvr = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int level = -1; // percentage, or -1 for unknown
                if (rawlevel >= 0 && scale > 0) {
                    level = (rawlevel * 100) / scale;
                }
                mLevelPower = level;
            }

        };
        mBatteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        getActivity().registerReceiver(mBatteryLevelRcvr, mBatteryLevelFilter);
    }
    private void unregisterBatteryReceiver(){
        getActivity().unregisterReceiver(mBatteryLevelRcvr);
    }
    private void initRecoverySystemUpdatePreference(){
        monitorBatteryState();
        RecoverySystemUpdatePreference rsup = (RecoverySystemUpdatePreference) findPreference(KEY_RECOVERY_SYSTEM_UPDATE);
        rsup.setBatteryCallBack(new RecoverySystemUpdatePreference.BatteryCallBack() {
            @Override
            public int getBatteryLevel() {
                return mLevelPower;
            }
        });
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBatteryReceiver();
    }
    /* @} */
	
    //fota start
    private boolean isApkExist(Context ctx, String packageName){
        PackageManager pm = ctx.getPackageManager();
        PackageInfo packageInfo = null;
        String versionName = null;
        try {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("FotaUpdate", "isApkExist not found");
            return false;
        }
		
        if (versionName != null) {
            String[] names = versionName.split("\\.");
            if (names.length >= 4 && "9".equals(names[3])) {
                return false;
            }
        }
        Log.i("FotaUpdate", "isApkExist = true");
        return true;
    }

    public String getAppName(Context ctx, String packageName) {
        PackageManager pm = ctx.getPackageManager();
        ApplicationInfo appInfo = null;
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            appInfo = null;
        }
		
        return (String) pm.getApplicationLabel(appInfo);
    }
    //fota end
}
