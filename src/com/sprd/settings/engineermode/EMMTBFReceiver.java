
package com.sprd.settings.engineermode;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.IPowerManager;
import android.preference.PreferenceManager;
import com.android.internal.view.RotationPolicy;
import android.provider.Settings;
import android.provider.Settings.System;
import android.content.ContentResolver;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.net.Uri;
import android.os.Bundle;
import com.android.internal.telephony.PhoneFactory;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import com.sprd.settings.SprdUsbSettings;
import android.os.PowerManager;

public class EMMTBFReceiver extends BroadcastReceiver {
    private static final String TAG = "EngineerModeReceiver";
    final String HOURS_12 = "12";

    final String KEY_PACKAGE_NAME = "PACKAGE NAME";
    final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    final String KEY_SETITEM = "SETITEM";
    final String KEY_RESULT = "RESULT";
    private static final String MTBFSP = "com.sprd.engineermode.action.MTBFRSP";
    private static final String SET_RESULT_OK = "Ok";
    private static final String SET_RESULT_FAIL = "Fail";

    private static final int SETTING_CONNSET = 0;
    private static final int SETTING_TIMESET = 1;
    private static final int SETTING_USBSET = 2;
    private static final int SETTING_IMSET = 3;
    private static final int SETTING_SCREENSET = 4;

    private static final String CMWAP_SETTING = "8";
    private static final int SCREEN_OFF_TIME = 1800000;
    public static final String APN_ID = "apn_id";

    public void onReceive(final Context context, final Intent intent) {
        final ContentResolver resolver = context.getContentResolver();
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (intent.getAction().equals("com.sprd.engineermode.action.MTBF")) {
            Log.d(TAG, "receive engapp MTBF action");
            setDefaultIme(context, resolver);
            setScreenItems(context, resolver);
            setSystemTime(context, resolver);
            setUSBItems(context, connectivityManager, resolver);
        } else if (intent.getAction().equals(MTBFSP)) {
            Bundle extras = intent.getExtras();
            String packageName = (String) extras.get(KEY_PACKAGE_NAME);
            if (packageName.equals("com.android.email")) {
                Log.d(TAG, "receive email MTBF complete action");
                setGprsData(context, connectivityManager, resolver);
            }
        }
    }

    private void timeUpdated(Context context) {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        context.sendBroadcast(timeChanged);
    }

    private void setScreenItems(Context context, ContentResolver resolver) {
        Intent intentResult = new Intent();
        intentResult.setAction(MTBFSP);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, SETTINGS_PACKAGE_NAME);
        String result = SET_RESULT_OK;
        /* SPRD: set brightness to min * */

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        int minimumBacklight = pm.getMinimumScreenBrightnessSetting();
        Settings.System.putInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        Settings.System.putInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS, minimumBacklight);

        Settings.System.putInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        int brightness = 0 + context.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessDim);
        /* @} */

        /* SPRD: cancle Auto-rotate screen * */
        RotationPolicy.setRotationLockForAccessibility(context, true);
        /* @} */

        /* SPRD: set Screen Sleep to max * */
        try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_OFF_TIME);
        } catch (NumberFormatException e) {
            Log.e(TAG, "could not persist screen timeout setting", e);
            result = SET_RESULT_FAIL;
        }
        /* @} */
        bundle.putInt(KEY_SETITEM, SETTING_SCREENSET);
        bundle.putString(KEY_RESULT, result);
        intentResult.putExtras(bundle);
        context.sendBroadcast(intentResult);

    }

    private void setDefaultIme(Context context, ContentResolver resolver) {
        Intent intentResult = new Intent();
        intentResult.setAction(MTBFSP);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, SETTINGS_PACKAGE_NAME);
        /* SPRD: set pinyinIME to default * */
        final String imeId = "com.android.inputmethod.latin/.LatinIME";
        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, imeId);
        /* @} */
        bundle.putInt(KEY_SETITEM, SETTING_IMSET);
        bundle.putString(KEY_RESULT, SET_RESULT_OK);
        intentResult.putExtras(bundle);
        context.sendBroadcast(intentResult);
    }

    private void setUSBItems(Context context, ConnectivityManager cnManager,
            ContentResolver resolver) {
        Intent intentResult = new Intent();
        intentResult.setAction(MTBFSP);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, SETTINGS_PACKAGE_NAME);
        /* SPRD: set Screen stay awake & debug mode* */
        Settings.Global.putInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB));
        Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED,1);
        /* @} */
        bundle.putInt(KEY_SETITEM, SETTING_USBSET);
        bundle.putString(KEY_RESULT, SET_RESULT_OK);
        intentResult.putExtras(bundle);
        context.sendBroadcast(intentResult);
    }

    private void setSystemTime(Context context, ContentResolver resolver) {
        Intent intentResult = new Intent();
        intentResult.setAction(MTBFSP);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, SETTINGS_PACKAGE_NAME);
        /** SPRD: set Time Format **/
        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, 1);
        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE,1);
        Settings.System.putString(resolver, Settings.System.TIME_12_24,HOURS_12);
        Settings.System.putString(resolver, Settings.System.DATE_FORMAT,"yyyy-MM-dd");
        timeUpdated(context);
        /* @} */
        bundle.putInt(KEY_SETITEM, SETTING_TIMESET);
        bundle.putString(KEY_RESULT, SET_RESULT_OK);
        intentResult.putExtras(bundle);
        context.sendBroadcast(intentResult);
    }

    private void setGprsData(Context context, ConnectivityManager cnManager,
            ContentResolver resolver) {
        Intent intentResult = new Intent();
        intentResult.setAction(MTBFSP);
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, SETTINGS_PACKAGE_NAME);
        /** SPRD: set data enable & select CMWAP apn **/
        if (TelephonyManager.isMultiSim()) {
            for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
                if (!cnManager.getMobileDataEnabledByPhoneId(i)) {
                    cnManager.setMobileDataEnabledByPhoneId(i, true);
                }
            }

        } else {
            cnManager.setMobileDataEnabled(true);
        }
        int phoneCount = TelephonyManager.getPhoneCount();

        ContentValues values = new ContentValues();
        for (int i = 0; i < phoneCount; i++) {
            values.put(getApnIdByPhoneId(i), CMWAP_SETTING);
            resolver.update(
                    Telephony.Carriers.getContentUri(i, Telephony.Carriers.PATH_PREFERAPN),
                    values, null, null);
        }
        /* @} */
        bundle.putInt(KEY_SETITEM, SETTING_CONNSET);
        bundle.putString(KEY_RESULT, SET_RESULT_OK);
        intentResult.putExtras(bundle);
        context.sendBroadcast(intentResult);
    }
    
    private String getApnIdByPhoneId(int phoneId) {
        switch (phoneId) {
            case 0:
                return APN_ID;
            default:
                return APN_ID + "_sim" + (phoneId + 1);
        }
    }
}
