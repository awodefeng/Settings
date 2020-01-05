package com.android.settings.wifi.XunWifiInput;

/**
 * Created by mayanjun on 2017/11/4.
 */
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


import com.android.settings.R;

import java.util.List;


public class RollInputKeyAdpter extends CycleScrollAdapter<RollInputKeyItem>  {

    public RollInputKeyAdpter(List<RollInputKeyItem> list,
                              CycleScrollView<RollInputKeyItem> cycleScrollView, Context context) {
        super(list, cycleScrollView, context);
    }

    @Override
    public View getView(RollInputKeyItem item) {
        View view = View.inflate(mContext, R.layout.view_item, null);
        ImageView image = (ImageView)view.findViewById(R.id.item_image);
        TextView text = (TextView)view.findViewById(R.id.item_text);

        image.setImageResource(item.getNormalIconID());
        text.setText(""+ item.getKeyName());
        return view;
    }

    @Override
    public void bindView(View child, RollInputKeyItem item) {
        ImageView image = (ImageView)child.findViewById(R.id.item_image);
        TextView text = (TextView)child.findViewById(R.id.item_text);

        image.setImageResource(item.getNormalIconID());
        text.setText(""+ item.getKeyName());
    }
}
