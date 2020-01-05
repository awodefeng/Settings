package com.android.settings.wifi;

import com.android.settings.R;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import com.android.settings.R;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.util.Log;
/**
 * Created by dengyanbing on 2019/3/28.
 */

class WifiOptionDialog extends Dialog{

    public interface WifiOptionDialogListener {
        void connect_to_network(AccessPoint accessPoint,WifiOptionDialog dialog);
        void cancel_save_network(AccessPoint accessPoint,WifiOptionDialog dialog);
        void disconnect_network(AccessPoint accessPoint,WifiOptionDialog dialog);
    }


    private View mView;
    private final AccessPoint mAccessPoint;
//    private TextView mSsidView;

    private TextView mCloseView;
    private LinearLayout connect_to_network,disconnect_network,cancel_save_network;
    private final WifiOptionDialogListener mListener;

    public WifiOptionDialog(Context context,WifiOptionDialogListener listener,AccessPoint accessPoint) {
        super(context);
        mListener = listener;
        mAccessPoint = accessPoint;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mView = getLayoutInflater().inflate(R.layout.wifi_option_dialog, null);
        setContentView(mView);
        mCloseView = (TextView) mView.findViewById(R.id.close);
//        mSsidView = (TextView) mView.findViewById(R.id.wifi_ssid);
//        mSsidView.setSelected(true);
//        mSsidView.setText(mAccessPoint.ssid);

        connect_to_network =(LinearLayout)mView.findViewById(R.id.connect_to_network);
        disconnect_network =(LinearLayout)mView.findViewById(R.id.disconnect_network);
        cancel_save_network=(LinearLayout)mView.findViewById(R.id.cancel_save_network);

        if(mAccessPoint!=null && mAccessPoint.getLevel() != -1 && mAccessPoint.getState() == null){
            connect_to_network.setVisibility(View.VISIBLE);
        }

        if(mAccessPoint!=null && (mAccessPoint.getState()!=null && mAccessPoint.getState().ordinal() ==5)){
            disconnect_network.setVisibility(View.VISIBLE);
        }

        mCloseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        connect_to_network.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect_to_network();
                dismiss();
            }
        });

        cancel_save_network.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("yanbing","cancel_save_network");
               cancel_save_network();
                dismiss();
            }
        });

        disconnect_network.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect_network();
                dismiss();
            }
        });
    }

    private void connect_to_network(){
        mListener.connect_to_network(mAccessPoint,this);
    }

    private void cancel_save_network(){
        mListener.cancel_save_network(mAccessPoint,this);
    }

    private void disconnect_network(){
        mListener.disconnect_network(mAccessPoint,this);
    }

    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.width= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        layoutParams.height= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
    }
}
