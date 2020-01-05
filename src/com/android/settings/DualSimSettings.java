/** SPRD： Created by Spreadst */
/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

import java.util.Timer;
import java.util.TimerTask;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.content.res.Configuration;
import android.sax.RootElement;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Message;
import android.os.Handler;
import android.os.AsyncResult;
import android.os.SystemProperties;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;

//cienet add liqiangwu 2011-6-13:
//import com.android.internal.telephony.ProxyManager;
//cienet end liqiangwu.
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;


import com.android.settings.R;

public class DualSimSettings extends PreferenceActivity implements
        DialogInterface.OnDismissListener, DialogInterface.OnClickListener,
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DualSimSettings";

    private static final boolean DEBUG = Debug.isDebug();

    private static final String KEY_DATA = "data";

    private static final String KEY_DUAL_VOICE_SETTING = "dual_voice_setting";
    private static final String KEY_DUAL_VIDEO_SETTING = "dual_video_setting";
    private static final String KEY_DUAL_MMS_SETTING = "dual_mms_setting";
    private static final String KEY_DUAL_PRIMARY_SETTING = "dual_primary_setting";
    
    // SPRD: add for bug338274 of replying msg setting
    private static final String KEY_REPLY_MSG = "reply_message_setting";

    private static final String KEY_CONFIG_SUB = "config_sub";

    private static final String CONFIG_SUB = "CONFIG_SUB";

    private static final String KEY_SIM_SLOT_CFG = "simslotcfg";
    private static final int DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS = 100;

    private static final int DIALOG_SET_DATA_SUBSCRIPTION_ERROR = 101;

    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 1;
    static final int EVENT_SET_SUBSCRIPTION_TIMEOUT = 2;
    static final int EVENT_ACTION_AIRPLANE_MODE_BROAD = 3;
    static final int DIALOG_WAIT_MAX_TIME = 5000;

    private static int isLeave = -1;

    private static int isShowDialog = -1;

    private ListPreference mData;
    //Added 2012/02/16 begin
    private ListPreference mDualVoiceSetting;

    private ListPreference mDualVideoSetting;

    private ListPreference mDualMmsSetting;
    private ListPreference mDualPrimarySetting;
    //Added 2012/02/16 end
    private PreferenceScreen mConfigSub;
    private PreferenceScreen mRoot;
    private PreferenceScreen mSimSlotCfg;
    // SPRD: add for bug338274 of replying msg setting
    private CheckBoxPreference mReplyMsgPrefence;
    ConnectivityManager cm;

    private static int mDefaultValue = 0;

    private static int mSeleteActiveSimMinCount = 2;

    private int setPhoneId = -1;

    private boolean mIsVTModem;

    private boolean dataEnabled = false;
    private int mPhoneId = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.dual_sim_settings);
        mRoot = getPreferenceScreen();
        mSimSlotCfg = (PreferenceScreen)findPreference(KEY_SIM_SLOT_CFG);
        mRoot.removePreference(mSimSlotCfg);
        mData = (ListPreference) findPreference(KEY_DATA);
        mData.setOnPreferenceChangeListener(this);
        mConfigSub = (PreferenceScreen) findPreference(KEY_CONFIG_SUB);
        mConfigSub.getIntent().putExtra(CONFIG_SUB, true);
        mConfigSub.getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        //Added 2012/02/16 begin
        mDualVoiceSetting = (ListPreference) findPreference(KEY_DUAL_VOICE_SETTING);
        mDualVideoSetting = (ListPreference) findPreference(KEY_DUAL_VIDEO_SETTING);
        mDualMmsSetting = (ListPreference) findPreference(KEY_DUAL_MMS_SETTING);
        mDualPrimarySetting=(ListPreference) findPreference(KEY_DUAL_PRIMARY_SETTING);
        mDualVideoSetting.setOnPreferenceChangeListener(this);
        mDualVoiceSetting.setOnPreferenceChangeListener(this);
        mDualMmsSetting.setOnPreferenceChangeListener(this);
        mDualPrimarySetting.setOnPreferenceChangeListener(this);
        //Added 2012/02/16 end
        /* SPRD: add for bug338274 of replying msg setting @{ */
        mReplyMsgPrefence = (CheckBoxPreference) findPreference(KEY_REPLY_MSG);
        mReplyMsgPrefence.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int replyMessage = 0;
                if (newValue.toString().equals("true")) {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_ISCHECKED;
                } else {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_UNCHECKED;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.MULTI_REPLY_MSG, replyMessage );
                return true;
            }
        });
        /* @} */

        mIsVTModem = ((TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                Context.TELEPHONY_SERVICE, 0))).getModemType() > 0;

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_DEFAULT_PHONE_CHANGE);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_MMS_REQUEST_DATA);
        /* SPRD: add for simhotswap @{ */
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
              intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED + i);
        }
        /* @} */
        registerReceiver(myReceiver, intentFilter);
        /** SPRD: modify bug 334495@{ */
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        /** @} */
        // SPRD: add for bug338274 of replying msg setting
        updateReplyMsgPrefence();
    }
    /** SPRD:
     * add for bug338274 of replying msg setting
     *
     */
    public void updateReplyMsgPrefence() {
        int replyMessage = TelephonyManager.getReplyMessage(DualSimSettings.this);
        mReplyMsgPrefence.setChecked(replyMessage == Settings.System.REPLY_MSG_PREFENCE_ISCHECKED);
    }
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_DEFAULT_PHONE_CHANGE)) {
                updateState();
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                updateState();
                if (Settings.System.getInt(getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) != 0) {
                    mHandler.sendEmptyMessage(EVENT_ACTION_AIRPLANE_MODE_BROAD);
                }
            }
            /* SPRD: add for simhotswap @{ */
            else if(action.startsWith(TelephonyIntents.ACTION_SIM_STATE_CHANGED)){
                updateState();
            /** SPRD: bug 331390,When sending MMS, the card cannot switch from the main card @{ */
            } else if (action.equals(TelephonyIntents.ACTION_MMS_REQUEST_DATA)) {
                Log.d(TAG, "receive action :ACTION_MMS_REQUEST_DATA");
                dataEnabled = intent.getBooleanExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY, false);
                mPhoneId = intent.getIntExtra(TelephonyIntents.EXTRA_PHONE_ID, 0);
                updateDataSummary();
            /** @} */
            }
            /* @} */
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        updateState();
        isLeave = 0;
    }

    /** SPRD: modify bug 334495@{ */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /** @} */

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isShowDialog == 1) {
            //removeDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isLeave = 1;
    }

    private void updateState() {
        updateDataSummary();
    }

    private void updateDataSummary() {

        int defaultPhoneId = TelephonyManager.getDefaultDataPhoneId(this);
        Log.d(TAG, "dataEnabled = " + dataEnabled + " mPhoneId = " + mPhoneId + " mDefaultPhoneId = " + defaultPhoneId);

        CharSequence[] summaries = getResources().getTextArray(R.array.dualsim_summaries);
        int phoneCount = TelephonyManager.getPhoneCount();
        TelephonyManager tm[] = new TelephonyManager[phoneCount];
        boolean hasCard[] = new boolean[phoneCount];
        boolean isCardReady[] = new boolean[phoneCount];
        boolean simLock[] = new boolean[phoneCount];
        boolean isEnabled = false;
        int activeSimCount = mDefaultValue;
        int activeSimNum = mDefaultValue;

        for(int i = 0; i < phoneCount; i++){
            hasCard[i] = PhoneFactory.isCardExist(i);
            isCardReady[i] = PhoneFactory.isCardReady(i);
            tm[i] = (TelephonyManager) this.getSystemService(TelephonyManager
                    .getServiceName(Context.TELEPHONY_SERVICE, i));
            simLock[i] = tm[i].checkSimLocked();
            if(hasCard[i]){
                isEnabled = true;
            }
            if (isCardReady[i] && !simLock[i]) {
                activeSimNum = i;
                activeSimCount++;
            }
        }

        if (isEnabled) {
            mConfigSub.setEnabled(true);
        }else{
            mConfigSub.setEnabled(false);
        }

        if (activeSimCount == mDefaultValue) {
            setDefaultEnabled(false);
            mData.setSummary(null);
            mDualVoiceSetting.setSummary(null);
            mDualVideoSetting.setSummary(null);
            mDualMmsSetting.setSummary(null);
            mDualPrimarySetting.setSummary(null);
            return;
        } else {
            if(activeSimCount >= mSeleteActiveSimMinCount){
                setDefaultEnabled(true);
                /** SPRD: bug 331390,When sending MMS, the card cannot switch from the main card @{ */
                if (dataEnabled && (defaultPhoneId != mPhoneId)) {
                    mData.setEnabled(false);
                }
                /** @} */
            } else {
                setDefaultEnabled(false);
                setTelephonyManagerDefaultValue(activeSimNum);
            }
            mData.setValue(String.valueOf(defaultPhoneId));
            mData.setSummary(summaries[defaultPhoneId]);

            setDualSettingDefaultValue();
        }
    }

    private void setDefaultEnabled(boolean isEnabled){
        mData.setEnabled(isEnabled);
        mDualVoiceSetting.setEnabled(isEnabled);
        if (mIsVTModem){
            mDualVideoSetting.setEnabled(false);
        } else{
            mDualVideoSetting.setEnabled(isEnabled);
        }
        mDualMmsSetting.setEnabled(isEnabled);
        mDualPrimarySetting.setEnabled(isEnabled);
    }
    private void setTelephonyManagerDefaultValue(int phoneId){
        TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VOICE, phoneId);
        TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VEDIO, phoneId);
        TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_MMS, phoneId);
    }

    private void setDualSettingDefaultValue(){
        int dualVoiceSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VOICE);
        int dualVideoSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VEDIO);
        int dualMmsSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_MMS);
        int dualPrimarySettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_PRIMARY);

        mDualVoiceSetting.setValue(String.valueOf(dualVoiceSettingValue));

        if (DEBUG) Log.d(TAG, "dualVideoSettingValue:"+dualVideoSettingValue +", mIsVTModem="+mIsVTModem);
        if (mIsVTModem) {
            mDualVideoSetting.setValue(String.valueOf(0));
        }else{
            mDualVideoSetting.setValue(String.valueOf(dualVideoSettingValue));
        }
        mDualMmsSetting.setValue(String.valueOf(dualMmsSettingValue));
        mDualPrimarySetting.setValue(String.valueOf(dualPrimarySettingValue));

        updateSettingSummary();
    }

    /*
     * <string-array name="dualsim_mms_setting_values">

        <item>0</item>  phone1

        <item>1</item>  phone2

        <item>-1</item> always

        <item>3</item>  auto
    </string-array>
     */
    private void updateSettingSummary(){
        CharSequence[] summaries = getResources().getTextArray(R.array.dualsim_summaries);
        CharSequence[] summaries_mms = getResources().getTextArray(R.array.dualsim_mms_summaries);
        CharSequence[] summaries_primary = getResources().getTextArray(R.array.dualsim_primary_summaries);

        int dualVoiceSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VOICE);
        int dualVideoSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VEDIO);
        int dualMmsSettingValue = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_MMS);
        int primaryCardSettingValue= TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_PRIMARY);

        mDualVoiceSetting.setSummary(dualVoiceSettingValue == -1 ? summaries[2] : summaries[dualVoiceSettingValue]);

        if (DEBUG) Log.d(TAG, "dualVideoSettingValue:"+dualVideoSettingValue + ", mIsVTModem="+mIsVTModem);
        if (mIsVTModem) {
            mDualVideoSetting.setSummary(summaries[0]);
        } else {
            mDualVideoSetting.setSummary(dualVideoSettingValue == -1 ? summaries[2] : summaries[dualVideoSettingValue]);
        }

        mDualMmsSetting.setSummary(dualMmsSettingValue == -1 ? summaries_mms[2] : summaries_mms[dualMmsSettingValue]);
        mDualPrimarySetting.setSummary(primaryCardSettingValue == -1 ? " " : summaries_primary[primaryCardSettingValue]);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        int phoneId = Integer.parseInt((String) objValue);
        if (KEY_DATA.equals(key)) {
            if (phoneId != TelephonyManager.getDefaultDataPhoneId(this))
                startUpdateDataSettings(phoneId);
        }else if(KEY_DUAL_VOICE_SETTING.equals(key)){
            TelephonyManager.setSubscriberDesiredSim(this, TelephonyManager.MODE_VOICE, phoneId);
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VOICE, phoneId);
            updateSettingSummary();
        }else if(KEY_DUAL_VIDEO_SETTING.equals(key)){
            TelephonyManager.setSubscriberDesiredSim(this, TelephonyManager.MODE_VEDIO, phoneId);
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VEDIO, phoneId);
            updateSettingSummary();
        }else if(KEY_DUAL_MMS_SETTING.equals(key)){
            TelephonyManager.setSubscriberDesiredSim(this, TelephonyManager.MODE_MMS, phoneId);
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_MMS, phoneId);
            updateSettingSummary();
        }else if(KEY_DUAL_PRIMARY_SETTING.equals(key)){
            Log.d(TAG, "Set Priamry Card phoneId"+phoneId);
            TelephonyManager.setSubscriberDesiredSim(this, TelephonyManager.MODE_PRIMARY, phoneId);
            ((TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                    Context.TELEPHONY_SERVICE, phoneId))).setPrimaryCard(phoneId);
            updateSettingSummary();
        }
        return true;
    }

    private void startUpdateDataSettings(int phoneId) {
        if (DEBUG) Log.d(TAG, "startUpdateDataSettings: " + phoneId);
        PhoneFactory.getPhone(TelephonyManager.getDefaultDataPhoneId(this)).registerForGprsDetached(mHandler, EVENT_SET_DATA_SUBSCRIPTION_DONE, null);
        setPhoneId = TelephonyManager.getDefaultDataPhoneId(this);//phoneId;
        restoreDataSettings(phoneId);
//        getPreferenceScreen().setEnabled(false);
        this.showDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
        //startTimer();
        isLeave = 0;
    }

    private void restoreDataSettings(int phoneId) {
        if (DEBUG) Log.d(TAG, "restoreDataSettings: " + phoneId);
        CharSequence[] summaries = getResources().getTextArray(R.array.dualsim_summaries);
        TelephonyManager.setAutoDefaultPhoneId(this, phoneId);
        PhoneFactory.updateDefaultPhoneId(phoneId);
        mData.setSummary(summaries[phoneId]);
    }

//    protected class DefaultSimThred extends Thread {
//        private int iSub;
//
//        public DefaultSimThred(int sub) {
//            iSub = sub;
//        }
//
//        public void run() {
//            PhoneFactory.updateDefaultPhoneId(iSub);
//        }
//    };
    private Timer timer;
    private TimerTask timerTask;

    private void startTimer() {
       closeTimer();
       timer = new Timer(true);
       timerTask = new TimerTask() {
           public void run() {
               //force execute busy act
               mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_TIMEOUT);
            }
        };
        if (DEBUG) Log.d(TAG, "startTimer,timer start");
        timer.schedule(timerTask, DIALOG_WAIT_MAX_TIME);
    }

    private void closeTimer() {
        if (DEBUG) Log.d(TAG, "closeTimer,timer end");
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private void finishSettingsWait() {
        if (DEBUG) Log.d(TAG, "Finish dual settings wait.");
//        if (setPhoneId>=0) {
//            PhoneFactory.getPhone(setPhoneId).unregisterForGprsAttach(mHandler);
//        }
        closeTimer();
        if (DEBUG) Log.d(TAG, "removeDialog start.");
        removeDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
        if (DEBUG) Log.d(TAG, "removeDialog done.");
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_TIMEOUT:
                    if (DEBUG) Log.d(TAG, "EVENT_SET_SUBSCRIPTION_TIMEOUT");
                    finishSettingsWait();
                    updateState();
                    if (isLeave == 0) {
                        Toast toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.set_dds_success),
                                Toast.LENGTH_LONG);
                        toast.show();
                    }
//                    if (isLeave == 0) {
//                        Toast toast = Toast.makeText(getApplicationContext(), R.string.set_data_subscription_timeout,
//                                Toast.LENGTH_LONG);
//                        toast.show();
//                    }
//                    showDialog(DIALOG_SET_DATA_SUBSCRIPTION_ERROR);
                    break;
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    if (DEBUG) Log.d(TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE");
                    if (setPhoneId>=0) {
                        PhoneFactory.getPhone(setPhoneId).unregisterForGprsDetached(mHandler);
                    }
                    ar = (AsyncResult) msg.obj;

                    String status;
                    if (ar.exception == null){
                        startTimer();
                    }
//                    if (ar.exception != null) {
//                        status = getResources().getString(R.string.set_dds_failed);
//                        displayAlertDialog(status);
//                        break;
//                    }

                    // cienet add liqiangwu 2011-6-13:
                    // final ProxyManager.SetDdsResult result =
                    // (ProxyManager.SetDdsResult) ar.result;

                    // Log.d(TAG, "SET_DATA_SUBSCRIPTION_DONE: result = "
                    // + result.toString());

                    // if (result.getState() ==
                    // ProxyManager.SetDdsResult.SUCCESS)
//                    if (isLeave == 0) {
//                        Toast toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.set_dds_success),
//                                Toast.LENGTH_LONG);
//                        toast.show();
//                    }
//                    else {
//                        status = getResources().getString(R.string.set_dds_failed);
//                        displayAlertDialog(status);
//                    }

                    break;
                case EVENT_ACTION_AIRPLANE_MODE_BROAD:
                    if (DEBUG) Log.d(TAG, "EVENT_ACTION_AIRPLANE_MODE_BROAD");
                    if (setPhoneId>=0) {
                        PhoneFactory.getPhone(setPhoneId).unregisterForGprsDetached(mHandler);
                    }
                    finishSettingsWait();
//                    if (isLeave == 0) {
//                        Toast toast = Toast.makeText(getApplicationContext(), getResources().getString(R.string.broad_airplane_mode_open),
//                                Toast.LENGTH_LONG);
//                        toast.show();
//                    }
                    if (mData.getDialog() != null) {
                        mData.getDialog().dismiss();
                    } else if (mDualVoiceSetting.getDialog() != null) {
                        mDualVoiceSetting.getDialog().dismiss();
                    } else if (mDualVideoSetting.getDialog() != null) {
                        mDualVideoSetting.getDialog().dismiss();
                    } else if (mDualMmsSetting.getDialog() != null) {
                        mDualMmsSetting.getDialog().dismiss();
                    }else if (mDualPrimarySetting.getDialog() != null) {
                        mDualPrimarySetting.getDialog().dismiss();
                    }
                    break;
            }
        }
    };

    @Override
    public void onDestroy() {
        unregisterReceiver(myReceiver);
        super.onDestroy();
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.dual_settings_title));
            dialog.setMessage(getResources().getString(R.string.set_data_subscription_progress));
            dialog.setCancelable(false);
            dialog.setIndeterminate(true);
            isShowDialog = 1;
            return dialog;
        }
        if (isLeave == 0 && id == DIALOG_SET_DATA_SUBSCRIPTION_ERROR) {
            Dialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.set_data_subscription_error_title)
                    .setMessage(R.string.set_data_subscription_timeout)
                    .setPositiveButton(R.string.set_data_subscription_error_ok,
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    updateState();
                                    dialog.dismiss();
//                                    getPreferenceScreen().setEnabled(true);
                                }
                            }).create();
            dialog.show();
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to disallow further input.
            // getPreferenceScreen().setEnabled(false);
        }
    }

    // This is a method implemented for DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        if (DEBUG) Log.d(TAG, "onDismiss!");
    }

    // This is a method implemented for DialogInterface.OnClickListener.
    public void onClick(DialogInterface dialog, int which) {
        if (DEBUG) Log.d(TAG, "onClick!");
    }

    void displayAlertDialog(String msg) {
        if (DEBUG) Log.d(TAG, "displayErrorDialog!" + msg);
        new AlertDialog.Builder(this).setMessage(msg).setTitle(android.R.string.dialog_alert_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, this).show().setOnDismissListener(this);
    }
}
