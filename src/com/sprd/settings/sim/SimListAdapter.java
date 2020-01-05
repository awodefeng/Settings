
package com.sprd.settings.sim;

import com.android.internal.telephony.PhoneFactory;

import android.provider.Settings.System;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sprd.internal.telephony.CpSupportUtils;
import com.sprd.settings.sim.SimManagerActivity;

public class SimListAdapter extends BaseAdapter {

    public final class ViewHolder {

        public RelativeLayout colorImage;

        public TextView name;

        public RadioButton viewBtn;

        public TextView number;
    }

    private LayoutInflater mInflater;
    private Sim[] mData;
    private Context mContext;
    private OnClickListener mListener;
    private int mLayoutId;
    private int mode = -1;

    public SimListAdapter(Context context, Sim[] data, OnClickListener listener, int layoutId) {
        this.mContext = context;
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.mListener = listener;
        this.mLayoutId = layoutId;
    }

    public int getCount() {

        return mData.length;
    }

    public Object getItem(int position) {
       
        return mData[position];
    }
    public int getMode() {
        return mode;
    }
    public void setMode(int values) {
        mode = values;
    }
    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        Sim sim = (Sim) mData[position];       
        int val = -1;
        if (convertView == null) {

            holder = new ViewHolder();

            convertView = mInflater.inflate(mLayoutId, null);           
            holder.colorImage = (RelativeLayout) convertView.findViewById(com.android.internal.R.id.sim_color);
            holder.name = (TextView) convertView.findViewById(com.android.internal.R.id.sim_name);
            holder.number = (TextView) convertView.findViewById(com.android.internal.R.id.sim_number);
            holder.viewBtn = (RadioButton) convertView.findViewById(com.android.internal.R.id.btn);
            if (holder.viewBtn != null)
                holder.viewBtn.setId(position);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        if (sim == null) {
            return convertView;
        }
        boolean isPhoneEnabled = System.getInt(mContext.getContentResolver(),
                TelephonyManager.getSetting(System.SIM_STANDBY, mData[position].getPhoneId()), 1) == 1;       
        if (!isPhoneEnabled) {
            holder.name.setTextColor(Color.GRAY);
            holder.number.setTextColor(Color.GRAY);
            if (holder.viewBtn != null) {
                holder.viewBtn.setEnabled(false);
            }
        }else{
            holder.name.setTextColor(Color.BLACK);
            holder.number.setTextColor(Color.BLACK);
            if (holder.viewBtn != null) {
                holder.viewBtn.setEnabled(true);
            }
        }
        if (sim.getPhoneId() == SimManagerActivity.DUAL_SET_ALWAYS_PROMPT
                || sim.getPhoneId() == SimManagerActivity.MMS_SET_AUTO) {
            holder.colorImage.setVisibility(View.GONE);
        } else {
            holder.colorImage.setVisibility(View.VISIBLE);
            holder.colorImage.setBackgroundResource(SimManager.COLORS_IMAGES[sim.getColorIndex()]);
        }
        holder.name.setText(mData[position].getName());
        if (holder.viewBtn != null && mListener != null) {
            if (getMode() == -1) {
                val = TelephonyManager.getDefaultDataPhoneId(mContext);
            } else if (getMode() == TelephonyManager.MODE_VOICE) {
                val = TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_VOICE);
            } else if (getMode() == TelephonyManager.MODE_VEDIO) {
                val = TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_VEDIO);
            } else if (getMode() == TelephonyManager.MODE_MMS) {
                val = TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_MMS);
            }
            if (mData[position].getPhoneId() == val) {
                holder.viewBtn.setChecked(true);
            } else
                holder.viewBtn.setChecked(false);
            holder.viewBtn.setOnClickListener(mListener);
        }
        if (holder.number != null) {
            int supportMainSolt = CpSupportUtils.getLTEPhoneId();
            if (supportMainSolt != -1 && sim.getPhoneId() != -1) {
                holder.number.setVisibility(View.VISIBLE);
                if (supportMainSolt == sim.getPhoneId()) {
                    holder.number.setText(mContext.getString(com.android.internal.R.string.main_card_slot) + mData[position].getNumber());
                } else {
                    String string = mContext.getString(com.android.internal.R.string.gsm_card_slot) + mData[position].getNumber();
                    holder.number.setText(string);
                }
            } else {
                holder.number.setVisibility(View.GONE);
            }
        }
        return convertView;
    }
}
