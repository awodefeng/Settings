package com.android.settings;

import com.android.settings.R;
import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Switch;

public class NoTextSwitchPreference extends SwitchPreference {

    public NoTextSwitchPreference(Context context, AttributeSet attrs){
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view){
        super.onBindView(view);
        Switch switchView = (Switch)view.findViewById(com.android.internal.R.id.switchWidget);
        switchView.setTextOn("");
        switchView.setTextOff("");
    }
}