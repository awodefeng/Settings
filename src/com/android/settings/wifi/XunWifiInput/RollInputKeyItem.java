package com.android.settings.wifi.XunWifiInput;

/**
 * Created by mayanjun on 2017/11/4.
 */
import android.util.Log;

import com.android.settings.R;


public class RollInputKeyItem {
    private String mKeyName;
    private int mNormalIconID;
    private int mPressedIconID;

    public RollInputKeyItem(char keyname, int nomalIconId, int pressIconId){
        this.mKeyName = String.valueOf(keyname);
        this.mNormalIconID = nomalIconId;
        this.mPressedIconID = pressIconId;
    }

    public RollInputKeyItem(char keyname){
        this.mKeyName = String.valueOf(keyname);
        this.mNormalIconID = R.drawable.key_icon_normal;
        this.mPressedIconID = R.drawable.key_icon_press;
        Log.i(CycleScrollView.TAG, "keyname = " + keyname);
    }

    public String getKeyName(){
        return mKeyName;
    }
    public int getNormalIconID(){return mNormalIconID;}
    public int getPressedIconID(){return  mPressedIconID;}
}
