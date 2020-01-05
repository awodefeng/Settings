package com.android.settings.netdisk;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.xiaoxun.sdk.IResponseDataCallBack;
import com.xiaoxun.sdk.ResponseData;
import com.xiaoxun.sdk.XiaoXunNetworkManager;
import com.xiaoxun.sdk.utils.CloudBridgeUtil;
import com.xiaoxun.sdk.utils.Constant;
import com.android.settings.R;

public class UploadFileService extends IntentService {

    private static final String TAG = UploadFileService.class.getSimpleName();
    private static final String XUN_703_ONLINE_ID_ONE= "SWX003";
    private static final String XUN_703_ONLINE_ID_TWO= "SWX004";
    private static final String XUN_703_OFFLINE_ID_ONE= "SWF005";
    private static final String XUN_703_OFFLINE_ID_TWO= "SWF006";

    List<MediaBean> mediaList = new ArrayList<MediaBean>();
    HashMap<String, String> spMediaData = new HashMap<String, String>();
    NetdiskResponseCallback netdiskResponseCallback = new NetdiskResponseCallback();

    private static final String UPLOAD_FILE_ACCESS_TOKEN_INVALID = "access_token_invalid";
    private String watchName = null;
    private boolean isUploadingFile = false;
    private String imei = null;

    public UploadFileService() {
        super("UploadFileService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (isUploadingFile) {
            return;
        }
        isUploadingFile = true;
        File file = new File("/storage/emulated/0/DCIM/Camera/");
        sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_DIR", Uri.fromFile(file)));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String accessToken = Utils.getValue(UploadFileService.this, Constants.SHARE_PREF_NETDISK_ACCESS_TOKEN, "");
        if (TextUtils.isEmpty(accessToken)) {
            getAccessTokenFromCloud();
        } else {
            initMediaData();
            uploadAllMediaFile(accessToken);
        }
    }

    private String getWatchName() {
        
        if (TextUtils.isEmpty(watchName)) {
            String xunSn = SystemProperties.get("persist.sys.xxun.sn");
            if (Constant.PROJECT_NAME.equals("SW760")) {
                watchName = getResources().getString(R.string.xun_product_name_3c);
            } else {
                if(xunSn.contains(XUN_703_OFFLINE_ID_ONE) || xunSn.contains(XUN_703_OFFLINE_ID_TWO)){
                    watchName = getResources().getString(R.string.xun_703_product_name_offline);
                } else {
                    watchName = getResources().getString(R.string.xun_703_product_name_online);
                }
            }
            String subImei = getImei();
            if (!TextUtils.isEmpty(subImei) && subImei.length() >= 5) {
                subImei = subImei.substring(subImei.length() - 5);
            }
            watchName = watchName + "_" + subImei ;
        }

        return watchName;
    }

    private String getImei() {
        if (TextUtils.isEmpty(imei)) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            imei = telephonyManager.getDeviceId();
        }
        return imei;

    }

    private void initMediaData() {
        MediaDataUtil.getMediaData(UploadFileService.this, mediaList);
        spMediaData = ListDataSaveUitl.getMapData(UploadFileService.this, Constants.SHARE_PREF_NAME, Constants.SHARE_PREF_MEDIA_DATA, String.class);
    }

    private void uploadAllMediaFile(String accessToken) {
        if (!TextUtils.isEmpty(accessToken)) {
            Log.e(TAG, "mediaList: " + mediaList.size());
            for (MediaBean bean : mediaList) {
                if (!spMediaData.containsKey(bean.id)) {
                    String result = uploadFileToNetdisk(accessToken, bean.path);
                    if (!TextUtils.isEmpty(result)) {
                        if (UPLOAD_FILE_ACCESS_TOKEN_INVALID.equals(result)) {
                            updateAccessTokenFromCloud();
                            break;
                        } else {
                            spMediaData.put(bean.id, result);
                            ListDataSaveUitl.saveMapData(UploadFileService.this, Constants.SHARE_PREF_NAME, Constants.SHARE_PREF_MEDIA_DATA, spMediaData);
                        }
                    }
                }
            }
        }
    }

    private String uploadFileToNetdisk(String accessToken, String filePath) {
        if (!(Utils.isWifiConnected(UploadFileService.this) && Utils.isCharging(UploadFileService.this))) {
            return null;
        }
        try {
            //预创建
            String preCreateResult = preCreate(accessToken, filePath);
            if (TextUtils.isEmpty(preCreateResult)) {
                return null;
            }

            JSONObject preResult = new JSONObject(preCreateResult);

            int errno = preResult.getInt("errno");
            //access_token失效
            if (errno == -6) {
                return UPLOAD_FILE_ACCESS_TOKEN_INVALID;
            } else if (errno != 0) {
                return null;
            }

            int returnType = preResult.getInt("return_type");
            //文件在云端已存在
            if (returnType == 2) {
                return null;
            }
            File uploadFile = new File(filePath);
            String path = preResult.getString("path");
            String uploadid = preResult.getString("uploadid");

            String uploadResult = uploadFile(uploadFile, accessToken, path, uploadid);
            if (TextUtils.isEmpty(uploadResult)) {
                return null;
            }

            JSONObject uploadJson = new JSONObject(uploadResult);
            //数据上传失败
            if (!uploadJson.has("md5")) {
                return null;
            }
            String md5 = uploadJson.getString("md5");
            String createResult = create(accessToken, md5, path, String.valueOf(uploadFile.length()), uploadid);
            if (TextUtils.isEmpty(createResult)) {
                return null;
            }
            JSONObject createJson = new JSONObject(createResult);
            //文件创建失败
            if (!createJson.has("fs_id")) {
                return null;
            }

            String fsId = createJson.getString("fs_id");
            return fsId;

        } catch (Exception e) {
            return null;
        }
    }

    private String preCreate(String accessToken, String filePath) {
        String url = "https://pan.baidu.com/rest/2.0/xpan/file?method=precreate&access_token=" + accessToken;

        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        try {
            
            JSONArray array = new JSONArray();
            array.put(MD5.getFileMD5(filePath));
            String brandName = getResources().getString(R.string.xun_watch);
            String body = "path=/﻿﻿" + brandName + "/" + getWatchName() + "/"+ file.getName() +
                    "&size=" + file.length() +
                    "&isdir=0&autoinit=1&rtype=3" +
                    "&block_list=" + array.toString();

            String result = HttpUtils.httpPost(url, body.replace("\ufeff", "").replace("\uFEFF", ""), "application/x-www-from-urlencoded");
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String uploadFile(File file, String accessToken, String path, String uploadId) {
        String url = "https://pcs.baidu.com/rest/2.0/pcs/superfile2?" +
                "access_token=" + accessToken +
                "&method=upload&type=tmpfile" +
                "&path=" + path + "&uploadid=" + uploadId +
                "&partseq=0";
        return HttpUtils.httpPostFile(UploadFileService.this, url, file);
    }

    private String create(String accessToken, String md5, String path, String size, String uploadId) {
        String url = "https://pan.baidu.com/rest/2.0/xpan/file?method=create&" + "access_token=" + accessToken;

        try {
            JSONArray array = new JSONArray();
            array.put(md5);
            String body = "path=" + path + "&size=" + size + "&isdir=0&autoinit=1&rtype=3" +
                    "uploadid" + uploadId + "&block_list=" + array.toString();

            String result = HttpUtils.httpPost(url, body, "application/x-www-from-urlencoded");
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private void getAccessTokenFromCloud() {
        try {
            XiaoXunNetworkManager networkManager = (XiaoXunNetworkManager) getSystemService("xun.network.Service");
            if (!networkManager.isWebSocketOK()) {
                Thread.sleep(5* 000);
            }
            JSONObject pl = new JSONObject();
            pl.put(Constants.KEY_NAME_EID, networkManager.getWatchEid());
            JSONObject getAccessToken = new JSONObject();
            getAccessToken.put(Constants.KEY_NAME_CID, Constants.CID_GET_BAIDU_NETDISK_ACCESS_TOKEN);
            getAccessToken.put(Constants.KEY_NAME_PL, pl);
            sendNetMessage(networkManager, getAccessToken);
        } catch (Exception e) {

        }

    }

    private void updateAccessTokenFromCloud() {
        try {
            XiaoXunNetworkManager networkManager = (XiaoXunNetworkManager) getSystemService("xun.network.Service");
            String refreshToken = Utils.getValue(UploadFileService.this, Constants.SHARE_PREF_NETDISK_REFRESH_TOKEN, "");
            JSONObject pl = new JSONObject();
            pl.put(Constants.KEY_NAME_REFRESH_TOKEN, refreshToken);
            pl.put(Constants.KEY_NAME_EID, networkManager.getWatchEid());
            JSONObject updateAccessToken = new JSONObject();
            updateAccessToken.put(Constants.KEY_NAME_CID, Constants.CID_UPDATE_BAIDU_NETDISK_ACCESS_TOKEN);
            updateAccessToken.put(Constants.KEY_NAME_PL, pl);
            sendNetMessage(networkManager, updateAccessToken);
        } catch (Exception e) {

        }
    }

    private void sendNetMessage(XiaoXunNetworkManager networkManager, JSONObject data) throws JSONException {
        data.put(Constants.KEY_NAME_SN, networkManager.getMsgSN());
        data.put(Constants.KEY_NAME_VERSION, CloudBridgeUtil.PROTOCOL_VERSION);
        data.put(Constants.KEY_NAME_SID,  networkManager.getSID());
        Log.e(TAG, "sendNetMessage: " + data.toString());
        networkManager.sendJsonMessage(data.toString(), netdiskResponseCallback);
    }

    private class NetdiskResponseCallback extends IResponseDataCallBack.Stub{

        public void onSuccess(ResponseData responseData){
            try {
                String response = responseData.getResponseData();
                Log.e(TAG, "NetdiskResponseCallback: " + response);
                JSONObject responseJson = new JSONObject(response);
                handleGetAccessTokenResponse(responseJson);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void onError(int i, String s){
            Log.d(TAG, "NetdiskResponseCallback fail errorMessage:" + s);
        }
    }

    private void handleGetAccessTokenResponse(JSONObject response) throws JSONException {
        int rc = response.getInt(Constants.KEY_NAME_RC);
        if (rc == 1) {
            if (!response.isNull(Constants.KEY_NAME_PL)) {
                JSONObject pl = response.getJSONObject(Constants.KEY_NAME_PL);
                if (pl.has(Constants.KEY_NAME_ACCESS_TOKEN)) {
                    String accessToken = pl.getString(Constants.KEY_NAME_ACCESS_TOKEN);
                    String refreshToken = pl.getString(Constants.KEY_NAME_REFRESH_TOKEN);
                    Utils.setValue(UploadFileService.this, Constants.SHARE_PREF_NETDISK_ACCESS_TOKEN, accessToken);
                    Utils.setValue(UploadFileService.this, Constants.SHARE_PREF_NETDISK_REFRESH_TOKEN, refreshToken);

                    int cid = response.getInt(Constants.KEY_NAME_CID);
                    if (cid == Constants.CID_GET_BAIDU_NETDISK_ACCESS_TOKEN_RESP) {
                        initMediaData();
                        uploadAllMediaFile(accessToken);
                    } else if (cid == Constants.CID_UPDATE_BAIDU_NETDISK_ACCESS_TOKEN_RESP) {
                        uploadAllMediaFile(accessToken);
                    }
                }
            }
        }

    }

}
