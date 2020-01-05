package com.android.settings.netdisk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.util.Log;

public class Utils {

    public static void setValue(Context context, String key, String value) {
        final SharedPreferences preferences = context.getSharedPreferences(Constants.SHARE_PREF_NAME, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public static String getValue(Context context, String key, String def) {
            return context.getSharedPreferences(Constants.SHARE_PREF_NAME, Context.MODE_PRIVATE).getString(key, def);
    }

    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifiInfo.isConnected();
    }

    public static boolean isCharging(Context context) {
        boolean usbStatus = false;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = context.registerReceiver(null, filter);
        int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        Log.d("Utils", "checkUsbStatus,plug=" + plug);
        if (plug == 1 || plug == 2 || plug == 4) {
            usbStatus = true;
        }
        return usbStatus;
    }

    public static void startUploadFileService(Context context) {
        Intent intent = new Intent(context, UploadFileService.class);
        ComponentName componentName = new ComponentName("com.android.settings", "com.android.settings.netdisk.UploadFileService");
        intent.setComponent(componentName);
        context.startService(intent);
    }
}
