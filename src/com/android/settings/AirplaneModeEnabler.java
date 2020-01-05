/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;

import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.TelephonyProperties;

public class AirplaneModeEnabler implements Preference.OnPreferenceChangeListener {

    private final Context mContext;

    private final CheckBoxPreference mCheckBoxPref;

    private AirplanModeChange mAirplanModeChange;

    public AirplaneModeEnabler(Context context, CheckBoxPreference airplaneModeCheckBoxPreference) {
        mContext = context;
        mCheckBoxPref = airplaneModeCheckBoxPreference;
        
        airplaneModeCheckBoxPreference.setPersistent(false);
    
        mAirplanModeChange = new AirplanModeChange();
    }

    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            boolean isRadioBusy = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.RADIO_OPERATION, 0) == 1;
            mCheckBoxPref.setEnabled(!isRadioBusy);
            Log.d("riq", "radioBusyChanged: isRadioBusy " + isRadioBusy);
        }
    };

    /* @} */
    public void resume() {
        mCheckBoxPref.setOnPreferenceChangeListener(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED_DONE);
        mContext.registerReceiver(mBroadcastReceiver, filter);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver);
        onAirplaneModeChanged();
    }
    
    public void pause() {
        mCheckBoxPref.setOnPreferenceChangeListener(null);
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.getContentResolver().unregisterContentObserver(mRadioBusyObserver);
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void setAirplaneModeOn(boolean enabling) {
        Log.d("riq", "setAirplaneModeOn: " + enabling);
        // Update the UI to reflect system setting
        mCheckBoxPref.setChecked(enabling);
        Message msg = Message.obtain();
        msg.obj = enabling;
        mAirplanModeChange.sendMessage(msg);
    }

    /**
     * Called when we've received confirmation that the airplane mode was set.
     * TODO: We update the checkbox summary when we get notified
     * that mobile radio is powered up/down. We should not have dependency
     * on one radio alone. We need to do the following:
     * - handle the case of wifi/bluetooth failures
     * - mobile does not send failure notification, fail on timeout.
     */
    private void onAirplaneModeChanged() {
        // SPRD: bug 330615,when all the card were forbidden, flight mode can not change immediately
        boolean isRadioBusy = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.RADIO_OPERATION, 0) == 1;
        Log.d("AirplaneModeEnabler", "onAirplaneModeChanged:isRadioBusy->"+isRadioBusy);
        mCheckBoxPref.setChecked(isAirplaneModeOn(mContext));
        mCheckBoxPref.setEnabled(!isRadioBusy);
    }
    
    /**
     * Called when someone clicks on the checkbox preference.
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode, do not update database at this point
        } else {
            setAirplaneModeOn((Boolean) newValue);
        }
        return true;
    }

    public void setAirplaneModeInECM(boolean isECMExit, boolean isAirplaneModeOn) {
        if (isECMExit) {
            // update database based on the current checkbox state
            setAirplaneModeOn(isAirplaneModeOn);
        } else {
            // update summary
            onAirplaneModeChanged();
        }
    }

    /* SPRD: for airplanemode optimization @{ */
    /*
     * delay 2 sec to handle airplane mode change to fix bug about phone crash
     * when we enable and disable among wifi,wifi direct and airplane mode
     */
    private class AirplanModeChange extends Handler {
        @Override
        public void handleMessage(Message message) {
            Log.d("riq", "AirplanModeChange: " + (Boolean) message.obj);
            // Change the system setting
            TelephonyManager.setRadioBusy(mContext, true);
            Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                                    (Boolean) message.obj ? 1 : 0);
            mCheckBoxPref.setEnabled(false);
            // Post the intent
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", (Boolean) message.obj);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }
    /* @} */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            onAirplaneModeChanged();
        }
    };
}
