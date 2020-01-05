package com.sprd.settings;

import java.util.ArrayList;
import java.util.List;
import com.android.settings.R;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.util.Log;

public class SecurityScreenOffReceiver extends BroadcastReceiver {
    private static final String TAG = "ScreenOffReceiver";
    private static final String ACTION_SCREEN_OFF_PROTECT = "android.intent.action.SCREEN_OFF_PROTECT_APP";
    private final long BYTES_IN_KILOBYTE = 1024;
    private ActivityManager mActivityManager;
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Receive action :" + action);
        if (ACTION_SCREEN_OFF_PROTECT.equals(action)) {
            mContext = context;
            mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            new SecurityThread().start();
            Log.d(TAG, "Receive action ok !");
        }
    }

    private float[] removeAllTasks(Context context, ArrayList<String> securityList) {
        Log.d(TAG, "removeAllTasks ");
        if (securityList == null) {
            return null;
        }
        float countApp = 0;
        float countMemory = 0;
        List<RunningAppProcessInfo> appProcesses = mActivityManager.getRunningAppProcesses();
        for (int i = 0; i < securityList.size(); i++) {
            Log.i(TAG, "getSecurity list = " + securityList.get(i));
            for (RunningAppProcessInfo appProcess : appProcesses) {
                if (securityList.get(i).toString().equals(appProcess.processName)
                        && appProcess.importance != RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    Log.i(TAG, "killed app = " + securityList.get(i).toString());
                    countApp++;
                    countMemory += getRunningAppProcessInfo(securityList.get(i).toString());
                    mActivityManager.killBackgroundProcesses(securityList.get(i).toString());
                }
            }
        }
        return new float[]{countApp, countMemory};
    }

    private float getRunningAppProcessInfo(String pkgName) {
        List<ActivityManager.RunningAppProcessInfo> appProcessList = mActivityManager.getRunningAppProcesses();
        float memSize = 0;
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessList) {
            int pid = appProcessInfo.pid;
            String processName = appProcessInfo.processName;
            if (pkgName.equalsIgnoreCase(processName)) {
                int[] myMempid = new int[]{pid};
                Debug.MemoryInfo[] memoryInfo = mActivityManager.getProcessMemoryInfo(myMempid);
                memSize = memoryInfo[0].dalvikPrivateDirty / BYTES_IN_KILOBYTE;
            }
        }
        Log.d(TAG, "memSize = " + memSize);
        return memSize;
    }

    private class SecurityThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "SecurityThread run");
            ArrayList<String> securityList = getPackageNameList();
            if (securityList == null || securityList.size() == 0) {
                return;
            }
            float[] obj = removeAllTasks(mContext, securityList);
        }
    }

    private ArrayList<String> getPackageNameList() {
        String[] mPackageNameList = mContext.getResources().getStringArray(R.array.reject_app_packagename_list);
        ArrayList<String> packageNameList = new ArrayList<String>();
        for (int i = 0; i < mPackageNameList.length; i++) {
            packageNameList.add(mPackageNameList[i]);
            Log.i(TAG, "packageNameList = " + packageNameList.get(i).toString());
        }
        return packageNameList;
    }

}
