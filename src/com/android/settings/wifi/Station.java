/** Created by Spreadst */

package com.android.settings.wifi;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.preference.Preference;
import android.app.AlertDialog;
import com.android.settings.R;
import android.content.DialogInterface;

class Station extends Preference{

    private WifiManager mWifiManager;
    private Context mContext;

    private String stationName;
    private boolean isConnected;

    public Station(Context context, String string, boolean connected) {
        super(context);

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        mContext = context;
        stationName = string;
        isConnected = connected;
        setTitle(stationName);
    }

    @Override
    protected void onClick() {
        askToAddBlockList();
    }

    void setBlockButton() {
        if (isConnected) {
            mWifiManager.softApBlockStation(stationName);
        } else {
            mWifiManager.softApUnblockStation(stationName);
        }
        mContext.sendBroadcast(new Intent(HotspotSettings.STATIONS_STATE_CHANGED_ACTION));
    }

    private void askToAddBlockList() {
        // TODO Auto-generated method stub
        String stationNameTemp = stationName;
        if (stationNameTemp == null) {
            stationNameTemp = "";
        }
        if (isConnected) {
            new AlertDialog.Builder(mContext).setTitle(stationNameTemp)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.block, controlBlockListener).show();
        } else {
            new AlertDialog.Builder(mContext).setTitle(stationNameTemp)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.unblock, controlBlockListener).show();
        }
    }

    android.content.DialogInterface.OnClickListener controlBlockListener = new android.content.DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            if (which == DialogInterface.BUTTON_POSITIVE) {
                setBlockButton();
            }
        }
    };
}
