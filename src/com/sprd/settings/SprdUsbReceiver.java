/** Created by Spreadst */
package com.sprd.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

public class SprdUsbReceiver extends BroadcastReceiver {
    private final String LOG_TAG = "SprdUsbReceiver";

    private Context context;
    private UsbManager mUsbManager = null;
    private static boolean powerOff =false;
    private boolean mConnected = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        // TODO Auto-generated method stub
        mUsbManager = (UsbManager) context
                .getSystemService(Context.USB_SERVICE);

        String action = intent.getAction();
        Log.i(LOG_TAG, "action = " + action);
        if (action.equals(UsbManager.ACTION_USB_STATE)) {
            if (intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)) {
                mConnected = true;
                powerOff=false;

                if (SystemProperties.get("persist.sys.sprd.mtbf", "1").equals("0")) {
                    return;
                }
                recoverFunction();
            }
        } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            if(mConnected){
                powerOff=true;
                mConnected = false;
            }
            String mCurrentfunction = getCurrentFunction();

            /* @ { added by xubinbin  20180816 start  @ } */
            if (UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)
                    || UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction)) {
                return;
            }
             /* @ { added by xubinbin  20180816 end  @ } */

            mUsbManager.setCurrentFunction(
                    UsbManager.USB_FUNCTION_MASS_STORAGE, false);

            if (Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.USB_REMEMBER_CHOICE, 0) == 0) {
                Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.USB_CURRENT_FUNCTION, 0);
            }
        }
    }

    private void recoverFunction() {
         /* @ { added by xubinbin  20180816 start  @ } */
        String mCurrentfunction = getCurrentFunction();
        if (UsbManager.USB_FUNCTION_MTP.equals(mCurrentfunction)
                || UsbManager.USB_FUNCTION_PTP.equals(mCurrentfunction)) {
            return;
        }
         /* @ { added by xubinbin  20180816 end  @ } */

        /* @ { removed by xubinbin  20180816 start  @ }
        Intent sprdIntent = new Intent(
                "com.sprd.settings.APPLICATION_SPRD_USB_SETTINGS");
        sprdIntent.setClass(context, SprdUsbSettings.class);
        sprdIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(sprdIntent);
        @ { removed by xubinbin  20180816 end  @ } */

    }

    public String getCurrentFunction() {
        String functions = SystemProperties.get("sys.usb.config", "");
        int commaIndex = functions.indexOf(',');
        if (commaIndex > 0) {
            return functions.substring(0, commaIndex);
        } else {
            return functions;
        }
    }
    public static boolean isPowerOff() {
        return powerOff;
    }
}
