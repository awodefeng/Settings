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

package com.sprd.settings.sim;

import java.util.ArrayList;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Debug;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.System;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.app.ActionBar;
import android.view.MenuItem;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelephonyIntents;
import com.sprd.internal.telephony.CpSupportUtils;
import com.sprd.settings.sim.DataListAdapter;
import com.android.settings.R;
import android.net.ConnectivityManager;
import com.android.internal.telephony.dataconnection.MsmsDcTrackerProxy;

public class SimManagerActivity extends PreferenceActivity implements
        DialogInterface.OnDismissListener, DialogInterface.OnClickListener,
        Preference.OnPreferenceClickListener {
    private static final String TAG = "SimManagerActivity";
    private static final String KEY_DATA = "data_setting";
    private static final String KEY_VOICE = "voice_setting";
    private static final String KEY_VIDEO = "video_setting";
    private static final String KEY_MMS = "mms_setting";
    private static final String KEY_REPLY_MSG = "reply_message_setting";
    private static final String KEY_CONFIG_SUB = "standby_setting";
    private static final String KEY_SIM_SLOT_CFG = "simslotcfg";
    private static final String CONFIG_SUB = "CONFIG_SUB";
    private static final int DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS = 100;
    private static final int DIALOG_SET_DATA_SUBSCRIPTION_ERROR = 101;
    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 1;
    static final int EVENT_SET_SUBSCRIPTION_TIMEOUT = 2;
    static final int EVENT_ACTION_AIRPLANE_MODE_BROAD = 3;
    static final int DIALOG_WAIT_MAX_TIME = 5000;
    static final int BAN_CARD_DIALOG_WAIT_MAX_TIME = 30000;
    static final int SUBSCRIPTION_ID_INVALID = -1;
    static final int DUAL_SET_ALWAYS_PROMPT = -1;
    static final int DATA_CLOSED = -1;
    static final int MMS_SET_AUTO = 3;
    private static int isLeave = -1;
    private boolean airplane;
    // SPRD: modify for bug256948
    private int isShowDialog = -1;
    private int mPhoneCount = 1;
    private int mSimCounts = 0;
    private boolean isVTCall[];
    private PreferenceScreen mStandbyPreference;
    private ListPreference mDataPreference;
    private ListPreference mVoicePreference;
    private ListPreference mVideoPreference;
    private ListPreference mMmsPreference;
    private CheckBoxPreference mReplyMsgPrefence;
    private PreferenceScreen mRoot;
    private PreferenceScreen mSimSlotCfg;
    private TelephonyManager telephonyManager[];
    private boolean isVideoEnable;
    private int setPhoneId = -1;
    private int oldSetPhoneId = -1;
    private SimManager mSimManager;
    private Sim mSims[], mSimPhone[], mSimMms[], mSimData[];
    private SimListAdapter mSimPhoneAdapter, mSimMmsAdapter;
    private DataListAdapter mSimDataAdapter;
    private String preferencekey = null;
    private byte[] lock = new byte[0];
    private ConnectivityManager mConManager;
    private ConnectivityManager mSimConnManager[];
    private Context mContext;
    private static final boolean DEBUG = Debug.isDebug();
    private boolean dataEnabled = false;
    private int mPhoneId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ActionBar actionBar =  getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                ,ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setDisplayHomeAsUpEnabled(true);
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.dual_sim_settings_uui);

        mRoot = getPreferenceScreen();
        mSimSlotCfg = (PreferenceScreen)findPreference(KEY_SIM_SLOT_CFG);
        mRoot.removePreference(mSimSlotCfg);

        isVideoEnable= SystemProperties.getBoolean("persist.sys.support.vt", true);
        mPhoneCount = TelephonyManager.getPhoneCount();
        isVTCall = new boolean[mPhoneCount];

        mDataPreference = (ListPreference) findPreference(KEY_DATA);
        mDataPreference.setOnPreferenceClickListener(this);
        mDataPreference.onCreateDialogView();

        mVoicePreference = (ListPreference) findPreference(KEY_VOICE);
        mVoicePreference.setOnPreferenceClickListener(this);
        mVoicePreference.onCreateDialogView();
        mVideoPreference = (ListPreference) findPreference(KEY_VIDEO);
        mVideoPreference.setOnPreferenceClickListener(this);
        mVideoPreference.onCreateDialogView();
        if(!isVideoEnable) {
            this.getPreferenceScreen().removePreference(mVideoPreference);
        }
        mMmsPreference = (ListPreference) findPreference(KEY_MMS);
        mMmsPreference.setOnPreferenceClickListener(this);
        mMmsPreference.onCreateDialogView();
        /* SPRD: add for reply Mms CheckBoxPreference @{ */
        mReplyMsgPrefence = (CheckBoxPreference) findPreference(KEY_REPLY_MSG);
        mReplyMsgPrefence.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                /* SPRD: 0 UNCHECKED, 1 CHECKED @{ */
                int replyMessage = 0;
                if (newValue.toString().equals("true")) {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_ISCHECKED;
                } else {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_UNCHECKED;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.MULTI_REPLY_MSG, replyMessage );
                /* @} */
                return true;
            }
        });
        /* @} */
        mStandbyPreference = (PreferenceScreen) findPreference(KEY_CONFIG_SUB);
        mStandbyPreference.getIntent().putExtra(CONFIG_SUB, true);
        // SPRD: modify for bug 254499
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_DEFAULT_PHONE_CHANGE);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_MMS_REQUEST_DATA);
        registerReceiver(myReceiver, intentFilter);

        telephonyManager = new TelephonyManager[mPhoneCount];
        mContext = this;
        mConManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        updateReplyMsgPrefence();
        }
    /* SPRD: add for updating of the checkbox of mReplyMsgPrefence @{ */
    public void updateReplyMsgPrefence() {
        int replyMessage = TelephonyManager.getReplyMessage(SimManagerActivity.this);
        mReplyMsgPrefence.setChecked(replyMessage == Settings.System.REPLY_MSG_PREFENCE_ISCHECKED);
    }
    /* @} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        if(item.getItemId() == android.R.id.home){
            finish();
            return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }
    private void initSim(){
        mSimManager = SimManager.get(this);
        if(mSimManager == null){
            if (DEBUG) Log.d(TAG,"simManager = "+null);
            return;
        }
        mSims = mSimManager.getSims();
        mSimCounts = mSims.length;
        if (DEBUG) Log.d(TAG, " length " + mSimCounts);
    }
    public void prepareForDataAdapter() {
        boolean isCloseData = false;
        final int dataPhoneId = TelephonyManager.getDefaultDataPhoneId(this);
        boolean mDataDefaultNetworkOn = mConManager.getMobileDataEnabledByPhoneId(dataPhoneId);
        isCloseData = mDataDefaultNetworkOn ? false : true;

        if (mSimCounts >= 1) {
            mSimData = new Sim[mSimCounts + 1];
            mSimData[mSimCounts] = new Sim(DATA_CLOSED, null, this.getResources().getString(
                    R.string.closeData), 0 , "",0);
        } else {
            mSimData = mSims;
            return;
        }
        for (int i = 0; i < mSimCounts; i++) {
            mSimData[i] = mSims[i];
        }

        mSimDataAdapter = new DataListAdapter(this, mSimData, new View.OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub
                mDataPreference.getDialog().dismiss();
                int clickViewId = mSimData[v.getId()].getPhoneId();
                if (clickViewId != -1) {
                    mConManager.setMobileDataEnabledByPhoneId(clickViewId, true);
                    startUpdateDataSettings(clickViewId);
                } else {
                    /* SPRD: modify for bug 251090 (clickViewId:items for users to choose 0:sim1 1:sim2 -1:close;
                     setPhoneId:clickViewId of the last operation; dataPhoneId: the default data phone id 0:sim1 1:sim2) */
                    if(clickViewId != setPhoneId){
                        startCloseDataSettings(dataPhoneId);
                    }
                }
            }
        }, com.android.internal.R.layout.select_sim_singlechoice,isCloseData);
    }
    public void prepareForAdapter() {
        if (mSimCounts > 1) {
            mSimPhone = new Sim[mSimCounts + 1];
            mSimMms = new Sim[mSimCounts + 1];
            mSimPhone[mSimCounts] = new Sim(DUAL_SET_ALWAYS_PROMPT, null, getResources().getString(
                    R.string.dual_settings_always_prompt), 0 , "",0);
            mSimMms[mSimCounts] = new Sim(DUAL_SET_ALWAYS_PROMPT, null, getResources().getString(
                    R.string.dual_settings_always_prompt), 0 , "",0);
        } else {
            mSimPhone = mSims;
            mSimMms = mSims;
        }
        for (int i = 0; i < mSimCounts; i++) {
            if (DEBUG) Log.d(TAG, mSims[i].toString());
            mSimPhone[i] = mSims[i];
            mSimMms[i] = mSims[i];
            //SPRD:fix bug255597
            if(TelephonyManager.getDefault().getModemType() == TelephonyManager.MODEM_TYPE_WCDMA){
                isVTCall[mSims[i].getPhoneId()] = (mSims[i].getPhoneId() == 0) ? true
                        : false;
            }else{
                isVTCall[mSims[i].getPhoneId()] = (telephonyManager[mSims[i].getPhoneId()].getModemType() > 0) ? true
                      : false;
            }
        }
        mSimPhoneAdapter = new SimListAdapter(this, mSimPhone, new View.OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub
                Log.w(TAG, "simAdapter default-- position  = " + v.getId());
                if (preferencekey.equals(KEY_VIDEO)) {
                    mVideoPreference.getDialog().dismiss();
                    preferenceChanged(KEY_VIDEO, mSimPhone[v.getId()].getPhoneId());
                } else if (preferencekey.equals(KEY_VOICE)) {
                    mVoicePreference.getDialog().dismiss();
                    preferenceChanged(KEY_VOICE, mSimPhone[v.getId()].getPhoneId());
                }
            }
        }, com.android.internal.R.layout.select_sim_singlechoice);

        mSimMmsAdapter = new SimListAdapter(this, mSimMms, new View.OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub
                Log.w(TAG, "simMmsAdapter default-- position  = " + v.getId());
                mMmsPreference.getDialog().dismiss();
                preferenceChanged(KEY_MMS, mSimMms[v.getId()].getPhoneId());
            }
        }, com.android.internal.R.layout.select_sim_singlechoice);

    }

    public void updateStandbyPreference() {
        if (0 == mSimCounts || airplane) {
            mStandbyPreference.setEnabled(false);
        } else {
            mStandbyPreference.setEnabled(true);
        }
    }

    private void updateSimSlotCfgPreference(){
        if(SystemProperties.getBoolean("wcdma.sim.slot.cfg",false) && mSimSlotCfg != null){
            if(0 == mSimCounts){
                mSimSlotCfg.setEnabled(false);
            } else {
                mSimSlotCfg.setEnabled(true);
            }
        }
    }

    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /* SPRD: modify for bug254499 @{ */
            if (action.equals(TelephonyIntents.ACTION_DEFAULT_PHONE_CHANGE)) {
                updateDataSummary();
            }else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                airplane = Settings.System.getInt(getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                if (airplane) {
                    mHandler.sendEmptyMessage(EVENT_ACTION_AIRPLANE_MODE_BROAD);
                /* SPRD: modify for bug249764 @{ */
                } else {
                    if (!getPreferenceScreen().isEnabled()) {
                        getPreferenceScreen().setEnabled(true);
                    }
                }
                updateState();
                /* @} */
            /* SPRD: bug 304316,When sending MMS, the card cannot switch from the main card @{ */
            } else if (action.equals(TelephonyIntents.ACTION_MMS_REQUEST_DATA)) {
                Log.d(TAG, "receive action :ACTION_MMS_REQUEST_DATA");
                dataEnabled = intent.getBooleanExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY, false);
                mPhoneId = intent.getIntExtra(TelephonyIntents.EXTRA_PHONE_ID, 0);
                updateDataSummary();
            /* @} */
            }
            /* @} */
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        for (int i = 0; i < mPhoneCount; i++) {
            telephonyManager[i] = (TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                    Context.TELEPHONY_SERVICE, i));
            isVTCall[i] = false;
        }
        initSim();
        prepareForDataAdapter();
        prepareForAdapter();
        airplane = Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        if (DEBUG) Log.d(TAG, "onResume: airplane="+airplane);
        updateState();
        isLeave = 0;
        /* SPRD: modify for bug 254499 @{ */
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                true, mMobileDataObserver);
        /* @} */
    }

    /* SPRD: modify for bug 254499 @{ */
    private ContentObserver mMobileDataObserver = new ContentObserver(
            new Handler()) {

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "mMobileDataObserver");
            updateDataSummary();
        }
    };
    /* @} */

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isLeave = 1;
        // SPRD: modify for bug 254499
        mContext.getContentResolver().unregisterContentObserver(mMobileDataObserver);
    }

    private void updateState() {
        updateStandbyPreference();
        updateDataSummary();
        updateVoiceSummary();
        if(isVideoEnable) {
            updateVideoSummary();
        }
        updateMmsSummary();
    }

    private void updateVoiceSummary() {
        setSummary(mVoicePreference, TelephonyManager.MODE_VOICE);
    }

    private void updateVideoSummary() {
        setSummary(mVideoPreference, TelephonyManager.MODE_VEDIO);
    }

    private void updateMmsSummary() {
        setSummary(mMmsPreference, TelephonyManager.MODE_MMS);
    }

    private void setSummary(ListPreference pref, int mode) {
        int phoneId = 0;
        if (airplane) {
            pref.setEnabled(false);
            pref.setSummary(null);
            return;
        }
        if (mMmsPreference.getKey().equals(pref.getKey())) {
            pref.getBuilder().setAdapter(mSimMmsAdapter, null);
        } else {
            pref.getBuilder().setAdapter(mSimPhoneAdapter, null);
        }
        if (mVoicePreference.getKey().equals(pref.getKey())) {
            phoneId = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VOICE);
        } else if (mVideoPreference.getKey().equals(pref.getKey())) {
            phoneId = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_VEDIO);
        } else if (mMmsPreference.getKey().equals(pref.getKey())) {
            phoneId = TelephonyManager.getDefaultSim(this, TelephonyManager.MODE_MMS);
        }

        if (DEBUG) Log.d(TAG, "setSummary:mode=" + mode + " phoneId=" + phoneId);
        if (mSimCounts >= 2) {
            if (DEBUG) Log.d(TAG, "setSummary: active Counts  = " + mSimCounts);
            if ((TelephonyManager.MODE_VEDIO == mode) && (supportMulticard(isVTCall) < 2)) {
                pref.setEnabled(false);
                if (supportMulticard(isVTCall) == 0) {
                    pref.setSummary(null);
                    return;
                }
            } else
                pref.setEnabled(true);
            if (DEBUG) Log.d(TAG, "standbycount = " + standbyCount());
            if ( standbyCount() < 2) {
                pref.setEnabled(false);
            }
        } else {
           pref.setEnabled(false);
            //SPRD:fix bug255597
            if (mSimCounts == 0 || ((TelephonyManager.MODE_VEDIO == mode) && (supportMulticard(isVTCall) == 0))) {
                pref.setSummary(null);
                return;
            }
        }
        pref.setValue(Integer.toString(phoneId));
        if (mMmsPreference.getKey().equals(pref.getKey())) {
            for (int i = 0; i < mSimMms.length; i++) {
                if (mSimMms[i].getPhoneId() == phoneId) {
                    pref.setValue(Integer.toString(i));
                    pref.setSummary(mSimMms[i].getName());
                }
            }
        } else {
            for (int i = 0; i < mSimPhone.length; i++) {
                if (mSimPhone[i].getPhoneId() == phoneId) {
                    pref.setValue(Integer.toString(i));
                    pref.setSummary(mSimPhone[i].getName());
                }
            }
        }
    }
    public int supportMulticard(boolean[] bl) {
        int count = 0;
        for(int i = 0; i < bl.length; i++) {
            if(bl[i]) {
                count++;
            }
        }
        return count;
    }

    public int standbyCount() {
        int standbyCount = 0;
        for (int i = 0;i <mSimCounts;i++ ) {
            boolean isStandby = System.getInt(mContext.getContentResolver(),
                     TelephonyManager.getSetting(System.SIM_STANDBY, i), 1) == 1;
            if (isStandby) {
                standbyCount ++;
            }
         }
        return standbyCount;
    }

    private void updateDataSummary() {
        int Data_val = TelephonyManager.getDefaultDataPhoneId(mContext);
        Log.d(TAG, "dataEnabled = " + dataEnabled + " mPhoneId = " + mPhoneId + " mDefaultPhoneId = " + Data_val);
        if (airplane || 0 == mSimCounts) {
            mDataPreference.setEnabled(false);
            mDataPreference.setSummary(null);
            return;
        /* SPRD: bug 304316,When sending MMS, the card cannot switch from the main card @{ */
        } else if (dataEnabled && (Data_val != mPhoneId)) {
            mDataPreference.setEnabled(false);
        /* @} */
        } else {
            mDataPreference.setEnabled(true);
        }
        prepareForDataAdapter();
        mDataPreference.getBuilder().setAdapter(mSimDataAdapter, null);
        boolean dataDefaultNetworkOn = mConManager.getMobileDataEnabledByPhoneId(Data_val);
        setPhoneId = Data_val;
        if (DEBUG)
            Log.d(TAG, "updateDataSummary:defaultPhoneId=" + Data_val + "dataDefaultNetworkOn = "
                    + dataDefaultNetworkOn);
        if (!dataDefaultNetworkOn) {
            setPhoneId = -1;
            mDataPreference.setValue(Integer.toString(mSimCounts));
            mDataPreference.setSummary(mSimData[mSimCounts].getName());
        } else {
            mDataPreference.setValue(Integer.toString(Data_val));
            mDataPreference.setSummary(mSimManager.getName(Data_val));
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        preferencekey = ((ListPreference) preference).getKey();
        Log.w(TAG, " preferencekey = " + preferencekey);
        if (mDataPreference == (ListPreference) preference) {
            mSimDataAdapter.setMode(-1);
        } else {
            if (mVoicePreference == (ListPreference) preference) {
                mSimPhoneAdapter.setMode(TelephonyManager.MODE_VOICE);
            } else if (mMmsPreference == (ListPreference) preference) {
                mSimMmsAdapter.setMode(TelephonyManager.MODE_MMS);
            } else if (mVideoPreference == (ListPreference) preference) {
                mSimPhoneAdapter.setMode(TelephonyManager.MODE_VEDIO);
            }
        }

        return false;
    }

    public void preferenceChanged(String key,int phoneId) {
        int mode =-1;
        Log.w(TAG, "key = " + key);
        if (KEY_DATA.equals(key)) {
            startUpdateDataSettings(phoneId);
        } else if (KEY_VOICE.equals(key)) {
            mode = TelephonyManager.MODE_VOICE;
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VOICE, phoneId);
            setSummary(mVoicePreference, mode);
        } else if (KEY_VIDEO.equals(key)) {
            mode = TelephonyManager.MODE_VEDIO;
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_VEDIO, phoneId);
            setSummary(mVideoPreference, mode);
        } else if (KEY_MMS.equals(key)) {
            mode = TelephonyManager.MODE_MMS;
            TelephonyManager.setDefaultSim(this, TelephonyManager.MODE_MMS, phoneId);
            setSummary(mMmsPreference, mode);
        }
    }

    private void startUpdateDataSettings(int phoneId) {
        if (setPhoneId >= 0 && setPhoneId < TelephonyManager.getPhoneCount()) {
            oldSetPhoneId = setPhoneId;
        } else {
            oldSetPhoneId = TelephonyManager.getDefaultDataPhoneId(this.getApplicationContext());
        }
        if (DEBUG)
            Log.i(TAG, "startUpdateDataSettings: " + phoneId + " setPhoneId=" + setPhoneId
                    + " old=" + oldSetPhoneId);
        if (setPhoneId == phoneId) {
            Toast toast = Toast.makeText(getApplicationContext(),R.string.no_need_set_data_subscription,
                    Toast.LENGTH_LONG);
            toast.show();
            return;
        }
        setPhoneId = phoneId;
        /* SPRD: modify for bug255102 @{ */
        getPreferenceScreen().setEnabled(false);
        this.showDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
        isLeave = 0;
        if (MsmsDcTrackerProxy.isActivePhoneId(setPhoneId)) {
            Log.w(TAG, "[" + setPhoneId + "]" + "already active phone, just start timer");
            startTimer(DIALOG_WAIT_MAX_TIME);
            return;
        } else {
            int activePhoneId = MsmsDcTrackerProxy.getActivePhoneId();
            Log.w(TAG, "[" + activePhoneId + "]" + "is active phone, register for GprsDetached");
            oldSetPhoneId = (activePhoneId < 0 ? oldSetPhoneId : activePhoneId);
            PhoneFactory.getPhone(oldSetPhoneId).registerForGprsDetached(mHandler, EVENT_SET_DATA_SUBSCRIPTION_DONE, null);
        }
        restoreDataSettings(phoneId);
        startTimer(BAN_CARD_DIALOG_WAIT_MAX_TIME);
        /* @} */
    }
    private void startCloseDataSettings(int phoneId) {
        mConManager.setMobileDataEnabledByPhoneId(phoneId, false);
        setPhoneId = -1;
        this.showDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
        isLeave = 0;
        startTimer(DIALOG_WAIT_MAX_TIME);
    }

    private void restoreDataSettings(int index) {
        boolean updateResult;
        if (DEBUG) Log.d(TAG, "restoreDataSettings: " + index);
        TelephonyManager.setAutoDefaultPhoneId(this, index);
        PhoneFactory.updateDefaultPhoneId(index);
    }

    private ScheduledThreadPoolExecutor timer = null;

    private TimerTask timerTask = null;

    private void startTimer(int time) {
        closeTimer();
        timer = new ScheduledThreadPoolExecutor(1);
        timerTask = new TimerTask() {
            public void run() {
                mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_TIMEOUT);
            }
        };
        if (DEBUG) Log.d(TAG, "startTimer,timer start");
        timer.schedule(timerTask, time , TimeUnit.MILLISECONDS);
    }

    private void closeTimer() {
        if (DEBUG) Log.d(TAG, "closeTimer,timer end");
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }

    private void finishSettingsWait() {
        if (DEBUG) Log.d(TAG, "Finish dual settings wait.");
        closeTimer();
        if(isShowDialog == 1) {
            removeDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
            isShowDialog = -1;
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_TIMEOUT:
                    Log.i(TAG, "EVENT_SET_SUBSCRIPTION_TIMEOUT");
                    finishSettingsWait();
                    // SPRD: modify for bug249764
                    getPreferenceScreen().setEnabled(true);
                    updateDataSummary();
                    if (isLeave == 0) {
                    // SPRD: add for bug362780
                    int ltePhoneId = CpSupportUtils.getLTEPhoneId();
                       if (ltePhoneId != -1 ) {
                          if (setPhoneId != ltePhoneId && setPhoneId != -1) {
                              Toast.makeText(getApplicationContext(), getResources()
                              .getString(R.string.set_dds_success_gsm), Toast.LENGTH_LONG).show();
                          } else {
                              Toast.makeText(getApplicationContext(), getResources()
                              .getString(R.string.set_dds_success), Toast.LENGTH_LONG).show();
                          }
                       }
                    }
                    break;
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    Log.i(TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE: oldSetPhoneId = " + oldSetPhoneId);
                    if (oldSetPhoneId >= 0) {
                        PhoneFactory.getPhone(oldSetPhoneId).unregisterForGprsDetached(mHandler);
                    }
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        startTimer(DIALOG_WAIT_MAX_TIME);
                    }
                    break;
                case EVENT_ACTION_AIRPLANE_MODE_BROAD:
                    Log.i(TAG, "EVENT_ACTION_AIRPLANE_MODE_BROAD: oldSetPhoneId = " + oldSetPhoneId);
                    if (oldSetPhoneId >= 0) {
                        PhoneFactory.getPhone(oldSetPhoneId).unregisterForGprsDetached(mHandler);
                    }
                    finishSettingsWait();
                    break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        unregisterReceiver(myReceiver);
        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(getText(R.string.dual_settings_title_uui));
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
                                    // TODO Auto-generated method stub
                                    updateState();
                                    dialog.dismiss();
                                    // getPreferenceScreen().setEnabled(true);
                                }
                            }).create();
            dialog.show();
            return null;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to disallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    // This is a method implemented for DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        Log.i(TAG, "onDismiss!");
    }

    // This is a method implemented for DialogInterface.OnClickListener.
    public void onClick(DialogInterface dialog, int which) {
        Log.i(TAG, "onClick!");
    }

    void displayAlertDialog(String msg) {
        Log.i(TAG, "displayErrorDialog!" + msg);
        new AlertDialog.Builder(this).setMessage(msg).setTitle(android.R.string.dialog_alert_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, this).show().setOnDismissListener(this);
    }
}
