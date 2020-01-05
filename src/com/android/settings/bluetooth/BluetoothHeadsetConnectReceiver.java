package com.android.settings.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class BluetoothHeadsetConnectReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothHeadsetConnectReceiver";
    private static final String ACTION_CONNECT_HEADSET = "android.intent.action.connectheadset";
    private static final boolean IS_SUPPORT_AUTO_TEST =
            Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug");
    private static BluetoothAdapter mAdapter;

    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        String action = intent.getAction();
        Log.d(TAG, "Received: " + action);
        if (action.equals(ACTION_CONNECT_HEADSET) && IS_SUPPORT_AUTO_TEST) {
            String address = intent.getStringExtra("deviceAddress");
            mAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = mAdapter.getRemoteDevice(address);
            LocalBluetoothManager btMgr = LocalBluetoothManager.getInstance(context);
            CachedBluetoothDeviceManager mCachedDeviceManager = btMgr.getCachedDeviceManager();
            CachedBluetoothDevice mCachedDevice = mCachedDeviceManager.findDevice(device);
            if (mCachedDevice == null) {
                Log.e(TAG, "mCachedDevice is null, return");
                return;
            }
            int bondState = mCachedDevice.getBondState();
            if (mCachedDevice.isConnected()) {
                Log.d(TAG, "This device is already connected!");
            } else if (bondState == BluetoothDevice.BOND_BONDED) {
                mCachedDevice.connect(true);
            } else if (bondState == BluetoothDevice.BOND_NONE) {
                mCachedDevice.startPairing();
            }
        }
    }
}
