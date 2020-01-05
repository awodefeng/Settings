/** Created by Spreadst */
package com.android.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class ApnEditorAdapter extends BaseAdapter{

    private LayoutInflater mInflater;
    private List<Map<String, Object>> mData;
    public Map<Integer, Boolean> isSelected;
    private Context mContext;
    private String[] mArray;
    private String[] mFullApn;

    public ApnEditorAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        mContext = context;
        init();
    }

    private void init() {
        mData = new ArrayList<Map<String, Object>>();
        mArray = mContext.getResources().getStringArray(R.array.apn_type_array);
        mFullApn = mContext.getResources().getStringArray(R.array.full_apn_type_array);
        isSelected = new HashMap<Integer, Boolean>();
        for (String apntype : mArray) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("title", apntype);
            mData.add(map);
        }
        for (int i=0; i<mData.size(); i++) {
            isSelected.put(i, false);
        }
    }

    public void setApnChecked(View view, int position) {
        ViewHolder holder = (ViewHolder)view.getTag();
        holder.cBox.toggle();
        /* SPRD: bug 854494 @{ */
        if (holder.title.isActivated()) {
            holder.title.setActivated(false);
        }
        /* @} */
        if (holder.cBox.isChecked()) {
            isSelected.put(position, true);
        } else {
            isSelected.put(position, false);
        }
    }

    public ArrayList<String> getOrigArray() {
        ArrayList<String> list = new ArrayList<String>();
        for (String apnType : mArray) {
            list.add(apnType);
        }
        return list;
    }

    public ArrayList<String> getFullArray() {
        ArrayList<String> list = new ArrayList<String>();
        for (String apnType : mFullApn) {
            list.add(apnType);
        }
        return list;
    }

    public int getPositionByText(String apnString) {
        String text = "";
        ArrayList<String> list = new ArrayList<String>();
        for (String apn : mArray) {
            list.add(apn);
        }
        if (list != null && !list.contains(apnString)) {
            return -1;
        }
        for (int i = 0; i < mArray.length; i++) {
            text = mData.get(i).get("title").toString();
            if (apnString.equals(text)) {
                return i;
            }
        }
        return -1;
    }

    public HashMap<String, Boolean> getEntries() {
        HashMap<String, Boolean> map = new HashMap<String, Boolean>();
        String text = "";
        for (int i=0; i<isSelected.size(); i ++) {
            text = mData.get(i).get("title").toString();
            map.put(text, isSelected.get(i));
        }
        return map;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.apn_type_list, null);
            holder.title = (TextView) convertView.findViewById(R.id.apn_type_list_item);
            holder.cBox = (CheckBox) convertView.findViewById(R.id.apn_check);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.title.setText(mData.get(position).get("title").toString());
        holder.cBox.setChecked(isSelected.get(position));
        return convertView;
    }

    public final class ViewHolder {
        public TextView title;
        public CheckBox cBox;
    }

}
