/* Create by Spreadst
 *
 * File Name:  defaults.xml
 * Author:     zhangwen
 * Date:       2018-01-05
 * Copyright:  2001-2020 by Spreadtrum Communications, Inc.
 *            All rights reserved.
 *            This software is supplied under the terms of a license
 *            agreement or non-disclosure agreement with Spreadtrum.
 *            Passing on and copying of this document, and communication
 *            of its contents is not permitted without prior written
 *            authorization.
 */

package com.android.settings.location;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LocationSwitchPreference extends SwitchPreference {
    private Context context;

    public LocationSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        ViewGroup preferenceLayout = (ViewGroup) view;
        TextView summary = (TextView) ((ViewGroup) preferenceLayout
                .getChildAt(1)).getChildAt(1);
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        summary.setTextSize(20 / fontScale); // 20px to sp

    }
}