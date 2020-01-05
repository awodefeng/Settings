/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
//yanbing add WifiSetings 20190308 start
import android.app.Dialog;
import android.view.Window;
import android.widget.LinearLayout;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import android.widget.TextView;
//yanbing add WifiSetings 20190308 end


class WifiDialog extends Dialog implements WifiConfigUiBase, DialogInterface.OnClickListener{

    //yanbing add WifiSetings 20190308 satrt
    public interface WifiDialogListener {
        void onForget(WifiDialog dialog);
        void onSubmit(WifiDialog dialog);
    }
    //yanbing add WifiSetings 20190308 end

    //add by spreadst_lc for cmcc wifi feature start
    static int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;
    static final int BUTTON_FORGET = DialogInterface.BUTTON_NEUTRAL;
    static int BUTTON_DISCONNECT = DialogInterface.BUTTON_POSITIVE;
    //add by spreadst_lc for cmcc wifi feature end
    private final boolean mEdit;
    //yanbing add WifiSetings 20190308 start
    private final WifiDialogListener mListener;
    //yanbing add WifiSetings 20190308 end
    private final AccessPoint mAccessPoint;

    private View mView;
    private WifiConfigController mController;
    //yanbing add WifiSetings 20190308 start
    private Context mContext;
    private Button mCancelBtn;
    private Button mOKButton;
    private TextView mPasswordView;
    //yanbing add WifiSetings 20190308 end

    public WifiDialog(Context context, WifiDialogListener listener,
            AccessPoint accessPoint, boolean edit) {
        super(context);
        mContext = context;
        mEdit = edit;
        mListener = listener;
        mAccessPoint = accessPoint;
    }

    public WifiDialog(Context context,int style, WifiDialogListener listener,
                      AccessPoint accessPoint, boolean edit) {
        super(context,style);
        mContext = context;
        mEdit = edit;
        mListener = listener;
        mAccessPoint = accessPoint;
    }

    @Override
    public WifiConfigController getController() {
        return mController;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_BACK;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mView = getLayoutInflater().inflate(R.layout.wifi_dialog, null);
        //yanbing add XUN_SW703_A01-316 20180328 start
        mPasswordView = (TextView) mView.findViewById(R.id.password);
        //yanbing add XUN_SW703_A01-316 20180328 end
        setContentView(mView);

        mController = new WifiConfigController(this, mView, mAccessPoint, mEdit);
        super.onCreate(savedInstanceState);
        /* During creation, the submit button can be unavailable to determine
         * visibility. Right after creation, update button visibility */
        mController.enableSubmitIfAppropriate();

        //yanbing add WifiSetings 20190308 start
        mCancelBtn = (Button)mView.findViewById(R.id.cancel_button);
        mOKButton =  (Button)mView.findViewById(R.id.ok_button);

        mCancelBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                clickForget();
                dismiss();
            }
        });

        mOKButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                //yanbing add XUN_SW703_A01-316 20180328 start
                if (mPasswordView == null) {
                    mPasswordView = (TextView) mView.findViewById(R.id.password);
                }

                if(mPasswordView != null && mPasswordView.length() >= 8){
                    clickSubmit();
                    dismiss();
                }else {
                    Toast.makeText(mContext,"密码长度不足8位", Toast.LENGTH_SHORT).show();
                }
                //yanbing add XUN_SW703_A01-316 20180328 end
            }
        });
        //yanbing add WifiSetings 20190308 end
    }

    //yanbing add WifiSetings 20190308 start
    private void clickSubmit(){
        mListener.onSubmit(this);
    }

    private void clickForget(){
        mListener.onForget(this);
    }
    //yanbing add WifiSetings 20190308 end

    @Override
    public boolean isEdit() {
        return mEdit;
    }

    @Override
    public Button getSubmitButton() {
        return null;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int id) {
    }

    @Override
    public Button getForgetButton() {
        return null;
    }

    @Override
    public Button getCancelButton() {
        return null;
    }

    @Override
    public void setSubmitButton(CharSequence text) {
        //add by spreadst_lc for cmcc wifi feature start
        BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;
        BUTTON_DISCONNECT = 0;
        //add by spreadst_lc for cmcc wifi feature end
    }

    @Override
    public void setForgetButton(CharSequence text) {
    }

    @Override
    public void setCancelButton(CharSequence text) {
    }

    //add by spreadst_lc for cmcc wifi feature start
    public void setDisconnectButton(CharSequence text) {
        BUTTON_DISCONNECT = DialogInterface.BUTTON_POSITIVE;
        BUTTON_SUBMIT = 0;
    }

    public Button getDisconnectButton() {
        return null;
    }
    //add by spreadst_lc for cmcc wifi feature end

    //yanbing add WifiSetings 20190308 start
    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.width= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        layoutParams.height= (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN| View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
    }
    //yanbing add WifiSetings 20190308 end
}
