package com.android.settings.applications;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.applications.ApplicationsState;

public class MouseControlViewHolder {
    public ApplicationsState.AppEntry mEntry;
    public View mRootView;
    public TextView mAppName;
    public ImageView mAppIcon;
    public CheckBox mCheckBox;

    static public MouseControlViewHolder createOrRecycle(LayoutInflater inflater,
            View convertView) {
        if (convertView == null) {
            convertView = inflater
                    .inflate(R.layout.autorun_filelist_item, null);
            MouseControlViewHolder holder = new MouseControlViewHolder();
            holder.mRootView = convertView;
            holder.mAppName = (TextView) convertView.findViewById(R.id.app_name);
            holder.mAppIcon = (ImageView) convertView
                    .findViewById(R.id.app_icon);
            holder.mCheckBox = (CheckBox) convertView
                    .findViewById(R.id.select_checkbox);
            holder.mAppIcon.setVisibility(View.GONE);
            convertView.setTag(holder);
            return holder;
        } else {
            return (MouseControlViewHolder) convertView.getTag();
        }
    }
}
