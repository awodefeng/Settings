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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.IActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Debug;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.hardware.display.WifiDisplayStatus;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;

import java.util.ArrayList;
/* SPRD:Modify Bug 318961, remove touch light @{ */
import java.io.File;
/* @} */

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    //SPRD:Modify Bug 213351, support change UI font
    private static final String KEY_FONT = "font";
    //private static final String KEY_WALLPAPER = "wallpaper";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private DisplayManager mDisplayManager;
    private WifiDisplayStatus mWifiDisplayStatus;
    private Preference mWifiDisplayPreference;
    //SPRD:Modify Bug 213351, support change UI font
    private PreferenceScreen mFont;
    /*
     * SPRD: add for press-brigntness
     */
    private ListPreference mTouchLightTimeoutPreference;
    private static final int VALUE_KEYLIGHT_1500 = 1500;
    private static final int VALUE_KEYLIGHT_6000 = 6000;
    private static final int VALUE_KEYLIGHT_1 = -1;
    private static final int VALUE_KEYLIGHT_2 = -2;
    private static final String BUTTON_TOUCH_LIGHT_TIMEOUT = "touch_light_timeout";
    private static final int FALLBACK_TOUCH_LIGHT_TIMEOUT_VALUE = 1500;

    private static final boolean WCN_DISABLED = SystemProperties.get("ro.wcn").equals("disabled");
    public static boolean PIKEL_UI_SUPPORT = SystemProperties.getBoolean("pikel_ui_support",true);

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (!RotationPolicy.isRotationSupported(getActivity())
                || RotationPolicy.isRotationLockToggleSupported(getActivity()) || PIKEL_UI_SUPPORT) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings,
            // if the device supports rotation.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false || PIKEL_UI_SUPPORT) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        /* SPRD:Modify Bug 331615, add for The default display settings sleep time was 2 minutes @{ */
        currentTimeout = initTimeoutPreferenceValue((int)currentTimeout);
        /* @} */
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        // SPRD: add for press-brightness.
        mTouchLightTimeoutPreference = (ListPreference) findPreference(BUTTON_TOUCH_LIGHT_TIMEOUT);
        /* SPRD:Modify Bug 318961, remove touch light @{ */
        if (!fileIsExists()) {
        	Log.d(TAG, "fileIsExists true--removePreference(mTouchLightTimeoutPreference)");
        	getPreferenceScreen().removePreference(mTouchLightTimeoutPreference);
        } else {
	        final int touchlightcurrentTimeout = Settings.System.getInt(resolver, Settings.System.BUTTON_LIGHT_OFF_TIMEOUT,
	                     FALLBACK_TOUCH_LIGHT_TIMEOUT_VALUE);
	        mTouchLightTimeoutPreference.setValue(String.valueOf(touchlightcurrentTimeout));
	        updateTouchLightPreferenceSummary(touchlightcurrentTimeout);
	        mTouchLightTimeoutPreference.setOnPreferenceChangeListener(this);
        }
        /* @} */
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
        /* SPRD: Modify for Bug819626, remove font size setting. @{ */
        // mFontSizePref.setOnPreferenceChangeListener(this);
        // mFontSizePref.setOnPreferenceClickListener(this);
        getPreferenceScreen().removePreference(mFontSizePref);
        /* @} */
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        mDisplayManager = (DisplayManager)getActivity().getSystemService(
                Context.DISPLAY_SERVICE);
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        mWifiDisplayPreference = (Preference)findPreference(KEY_WIFI_DISPLAY);
        if (mWifiDisplayStatus.getFeatureState()
                == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE || WCN_DISABLED) {
            getPreferenceScreen().removePreference(mWifiDisplayPreference);
            mWifiDisplayPreference = null;
        }
        /* SPRD:Modify Bug 213351, support change UI font @{ */
        mFont = (PreferenceScreen) findPreference(KEY_FONT);
        updateFontSummary();
        /* @} */
        if(PIKEL_UI_SUPPORT){
            //getPreferenceScreen().removePreference(findPreference(KEY_WALLPAPER));
            getPreferenceScreen().removePreference(mFont);
        }
    }


    /*
     * SPRD: add for press-brigntness
     */
    private void writeTouchLightPreference(Object objValue) {
        int value = Integer.parseInt(objValue.toString());
        Settings.System.putInt(getContentResolver(),
                Settings.System.BUTTON_LIGHT_OFF_TIMEOUT, value);
        updateTouchLightPreferenceSummary(value);
    }

    /*
     * SPRD: add for press-brigntness
     */
    private void updateTouchLightPreferenceSummary(int value) {
        if (value == VALUE_KEYLIGHT_1500) {
            mTouchLightTimeoutPreference
                    .setSummary(mTouchLightTimeoutPreference.getEntries()[0]);
        } else if (value == VALUE_KEYLIGHT_6000) {
            mTouchLightTimeoutPreference
                    .setSummary(mTouchLightTimeoutPreference.getEntries()[1]);
        } else if (value == VALUE_KEYLIGHT_1) {
            mTouchLightTimeoutPreference
                    .setSummary(mTouchLightTimeoutPreference.getEntries()[2]);
        } else if (value == VALUE_KEYLIGHT_2) {
            mTouchLightTimeoutPreference
                    .setSummary(mTouchLightTimeoutPreference.getEntries()[3]);
        }
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
                /* SPRD:Modify Bug 332651, GMS will remove defaults.xml @{ */
                if(currentTimeout != Long.parseLong(values[best].toString())){
                    mScreenTimeoutPreference.setValue(values[best].toString());
                }
                /* @} */
            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }

    /* SPRD：ADD for Settings porting from 4.1 to 4.3 @{ */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to update mCurConfig on screen switch");
        }
    }
    /* @} */

    @Override
    public void onResume() {
        super.onResume();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(
                    getActivity(), !mAccelerometer.isChecked());
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;

        /*
        * SPRD: add for press-brigntness
        */
        } else if (preference == mTouchLightTimeoutPreference) {
            try {
                int britnessmode = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE);
                if (britnessmode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    Toast.makeText(getActivity(),
                            getString(R.string.close_screen_bright_automode),
                            Toast.LENGTH_SHORT).show();
                    mTouchLightTimeoutPreference.getDialog().cancel();
                }
            } catch (Exception e) {
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        // SPRD: add for press-brigntness.
        if (BUTTON_TOUCH_LIGHT_TIMEOUT.equals(key)) {
            writeTouchLightPreference(objValue);
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        }
        return false;
    }
    /**
     * SPRD:Modify Bug 213351, support change UI font @{
     */
    private void updateFontSummary(){
        try {
            IActivityManager am = ActivityManagerNative.getDefault();
            Configuration config = am.getConfiguration();
            if (!config.bUserSetTypeface && config.sUserTypeface == null) {
                mFont.setSummary(R.string.font_setting_default_font);
            } else {
                int lastSplash = config.sUserTypeface.lastIndexOf("/");
                mFont.setSummary(config.sUserTypeface.substring(lastSplash+1, config.sUserTypeface.lastIndexOf(".")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /** @} */
    /* SPRD:Modify Bug 318961, remove touch light @{ */
    private boolean fileIsExists(){
        try{
            File file = new File("/sys/class/leds/keyboard-backlight/brightness");
            Log.d(TAG, " fileIsExists");
            if(!file.exists()){
            	Log.d(TAG, "fileIsExists false");
                return false;
            }               
        } catch (Exception e) {
                // TODO: handle exception
                return false;
        }
        Log.d(TAG, "fileIsExists true");
        return true;
    }
    /* @} */

    /*
     * SPRD: add for The default display settings sleep time was 2 minutes, but
     * the list of options the option is not the focus of two minutes after fall
     */
    private int initTimeoutPreferenceValue(int currentTimeout) {
        if (mScreenTimeoutPreference.findIndexOfValue(String.valueOf(currentTimeout)) == -1)
        {
            if (0 < currentTimeout && currentTimeout <= 20000) {
                currentTimeout = 15000;
            } else if (20000 < currentTimeout && currentTimeout <= 40000) {
                currentTimeout = 30000;
            } else if (40000 < currentTimeout && currentTimeout <= 80000) {
                currentTimeout = 60000;
            } else if (80000 < currentTimeout && currentTimeout <= 150000) {
                currentTimeout = 120000;
            } else if (150000 < currentTimeout && currentTimeout <= 400000) {
                currentTimeout = 300000;
            } else if (400000 < currentTimeout && currentTimeout <= 800000) {
                currentTimeout = 600000;
            } else if (800000 < currentTimeout && currentTimeout <= 2000000) {
                currentTimeout = 1800000;
            } else {
                currentTimeout = FALLBACK_SCREEN_TIMEOUT_VALUE;
            }
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT,
                        (int) currentTimeout);
                Log.d(TAG, "the 3rd app modified the database with our has deal with  ");
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        return currentTimeout;
    }

}
