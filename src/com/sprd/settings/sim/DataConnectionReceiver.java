
package com.sprd.settings.sim;

import java.util.TimerTask;

import android.net.ConnectivityManager;
import android.os.Bundle;

import android.content.res.Configuration;
import android.telephony.TelephonyManager;

import android.util.Log;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Message;
import android.os.Handler;
import android.os.AsyncResult;
import android.os.Debug;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.android.internal.telephony.PhoneFactory;
import com.android.settings.R;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import android.app.ProgressDialog;
import android.view.WindowManager;
import com.android.internal.telephony.dataconnection.MsmsDcTrackerProxy;

public class DataConnectionReceiver extends BroadcastReceiver {
    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 1;
    static final int EVENT_SET_SUBSCRIPTION_TIMEOUT = 2;
    static final int EVENT_TARGET_GPRS_ATTACH_DONE = 4;
    static final int DIALOG_WAIT_MAX_TIME = 5000;
    static final int BAN_CARD_DIALOG_WAIT_MAX_TIME = 30000;

    private static final String TAG = "DataConnectionReceiver";
    private static final boolean DEBUG = Debug.isDebug();
    private int setPhoneId = -1;
    private int oldSetPhoneId = -1;
    private byte[] lock = new byte[0];
    int defaultPhoneId;
    Context mContext;
    private static int isShowDialog = -1;
    private ProgressDialog dialog;

    private void startUpdateDataSettings(int phoneId) {
        if (setPhoneId >= 0 && setPhoneId < TelephonyManager.getPhoneCount()) {
            oldSetPhoneId = setPhoneId;
        } else {
            oldSetPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
        }
        if (DEBUG) Log.d(TAG, "startUpdateDataSettings: " + phoneId + " setPhoneId=" + setPhoneId + " old="
                + oldSetPhoneId);
        setPhoneId = phoneId;

        if (MsmsDcTrackerProxy.isActivePhoneId(setPhoneId)) {
            if (DEBUG) Log.d(TAG, "[" + setPhoneId + "]" + "already active phone, just start timer");
            startTimer(DIALOG_WAIT_MAX_TIME);
            return;
        } else {
            int activePhoneId = MsmsDcTrackerProxy.getActivePhoneId();
            if (DEBUG) Log.d(TAG, "[" + activePhoneId + "]" + "is active phone, register for GprsDetached");
            oldSetPhoneId = (activePhoneId < 0 ? setPhoneId : activePhoneId);
            PhoneFactory.getPhone(oldSetPhoneId).registerForGprsDetached(mHandler,
                    EVENT_SET_DATA_SUBSCRIPTION_DONE, null);
        }
        restoreDataSettings(phoneId);
        startTimer(BAN_CARD_DIALOG_WAIT_MAX_TIME);
    }

    private void restoreDataSettings(int phoneId) {
        if (DEBUG) Log.d(TAG, "restoreDataSettings: " + phoneId);
        TelephonyManager.setAutoDefaultPhoneId(mContext, phoneId);
        PhoneFactory.updateDefaultPhoneId(phoneId);
    }

    private ScheduledThreadPoolExecutor timer = null;
    private TimerTask timerTask = null;

    private void startTimer(int time) {
        closeTimer();
        timer = new ScheduledThreadPoolExecutor(1);
        timerTask = new TimerTask() {
            public void run() {
                // force execute busy act
                mHandler.sendEmptyMessage(EVENT_SET_SUBSCRIPTION_TIMEOUT);
            }
        };
        if (DEBUG) Log.d(TAG, "startTimer,timer start");
        timer.schedule(timerTask, time, TimeUnit.MILLISECONDS);
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
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_TIMEOUT:
                    if (DEBUG) Log.d(TAG, "EVENT_SET_SUBSCRIPTION_TIMEOUT");
                    Intent intentDettach = new Intent();
                    intentDettach.setAction(Intent.ACTION_DATA_CONNECTION_DETTACH);
                    mContext.sendBroadcast(intentDettach);
                    finishSettingsWait();
                    break;
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    if (DEBUG) Log.d(TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE");
                    ar = (AsyncResult) msg.obj;
                    if (setPhoneId >= 0) {
                        PhoneFactory.getPhone(setPhoneId).unregisterForGprsDetached(mHandler);
                    }
                    if (ar.exception == null) {
                        startTimer(DIALOG_WAIT_MAX_TIME);
                    }
                    break;
                case EVENT_TARGET_GPRS_ATTACH_DONE:
                    synchronized (lock) {
                        if (DEBUG) Log.d(TAG, "EVENT_TARGET_GPRS_ATTACH_DONE oldSetPhoneId=" + oldSetPhoneId
                                + " setPhoneId=" + setPhoneId);
                        if (oldSetPhoneId >= 0) {
                            PhoneFactory.getPhone(oldSetPhoneId).unregisterForGprsDetached(
                                    mHandler);
                            if (setPhoneId >= 0) {
                                PhoneFactory.getPhone(setPhoneId).unregisterForGprsAttached(
                                        mHandler);
                            }
                            oldSetPhoneId = -1;
                            ar = (AsyncResult) msg.obj;
                            if (ar.exception == null) {
                                startTimer(DIALOG_WAIT_MAX_TIME);
                            }
                        }
                    }
            }
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        /* SPRD: bug 807273 NullPointerException @{ */
        if (intent == null || intent.getAction() == null) {
            return;
        }
        /* @}*/
        mContext = context;
        String action = intent.getAction();
        int sim_id = intent.getIntExtra("SIM_ID", 0);
        setPhoneId = intent.getIntExtra("SETTING_ID", 0);
        Log.i(TAG, "sim_id " + sim_id);
        Log.i(TAG, "action " + action);
        if (action.equals(Intent.ACTION_DATA_CONNECTION_CHANGED)) {
            startUpdateDataSettings(sim_id);
        }
    }
}
