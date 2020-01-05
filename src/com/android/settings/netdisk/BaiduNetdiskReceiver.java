package com.android.settings.netdisk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONObject;

import com.xiaoxun.sdk.IResponseDataCallBack;
import com.xiaoxun.sdk.ResponseData;
import com.xiaoxun.sdk.XiaoXunNetworkManager;
import com.xiaoxun.sdk.utils.CloudBridgeUtil;
import com.xiaoxun.sdk.utils.Constant;

public class BaiduNetdiskReceiver extends BroadcastReceiver {
    public static final String ACTION_UNBIND_BAIDU_NETDISK = "com.xiaoxun.xunsettings.UNBIND_BAIDU_NETDISK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Constant.PROJECT_NAME.equals("SW760")) {
            return;
        }
        String action = intent.getAction();
        Log.d("BaiduNetdiskReceiver", "action: " + action);
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected() && Utils.isCharging(context)) {
                Utils.startUploadFileService(context);
            }
        } else if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            if (Utils.isWifiConnected(context)) {
                Utils.startUploadFileService(context);
            }
        } else if (action.equals(ACTION_UNBIND_BAIDU_NETDISK)) {
            String response = intent.getStringExtra("recvMsg");
            try {
                JSONObject responseJson = new JSONObject(response);
                String seid = responseJson.getString("SEID");
                int sn = responseJson.getInt("SN");
                String[] teid = {seid};
                XiaoXunNetworkManager networkManager = (XiaoXunNetworkManager) context.getSystemService("xun.network.Service");
                JSONObject pl = new JSONObject();
                pl.put(Constants.KEY_NAME_SUB_ACTION, CloudBridgeUtil.SUB_ACTION_BAIDU_NETDISK_UNBIND);
                networkManager.sendE2EMessageEX(teid, sn, 1, pl.toString(), new NetdiskResponseCallback());

                Utils.setValue(context, Constants.SHARE_PREF_NETDISK_ACCESS_TOKEN, "");
                Utils.setValue(context, Constants.SHARE_PREF_NETDISK_REFRESH_TOKEN, "");
            } catch (Exception e) {

            } 
        }
    }

    private class NetdiskResponseCallback extends IResponseDataCallBack.Stub{

        public void onSuccess(ResponseData responseData){

        }

        public void onError(int i, String s){

        }
    }
}
