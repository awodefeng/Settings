package com.android.settings;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DevelopmentSwitchPreference extends SwitchPreference {
    private Context context;

    public DevelopmentSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        ViewGroup preferenceLayout = (ViewGroup) view;
        TextView summary = (TextView) ((ViewGroup) preferenceLayout.getChildAt(1)).getChildAt(1);
        summary.setTextColor(context.getResources().getColor(R.color.black));
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        summary.setTextSize(20 / fontScale);
    }
}

