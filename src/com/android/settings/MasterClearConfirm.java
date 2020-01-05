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

package com.android.settings;

import com.android.internal.os.storage.ExternalStorageFormatter;
import com.android.internal.widget.LockPatternUtils;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.util.Log;

import com.sprd.android.support.featurebar.FeatureBarHelper;

/**
 * Confirm and execute a reset of the device to a clean "just out of the box"
 * state.  Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL ERASE EVERYTHING
 * ON THE PHONE" prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 *
 * This is the confirmation screen.
 */
public class MasterClearConfirm extends Fragment {

    // SPRD：ADD for Settings porting from 4.1 to 4.3
    private static final String TAG = "MasterClearConfirm";

    private View mContentView;
    private boolean mEraseSdCard;
    private Button mFinalButton;

    /**
     * The user has gone through the multiple confirmation, so now we go ahead
     * and invoke the Checkin Service to reset the device to its factory-default
     * state (rebooting in the process).
     */
    private Button.OnClickListener mFinalClickListener = new Button.OnClickListener() {

        public void onClick(View v) {
            if (Utils.isMonkeyRunning()) {
                return;
            }

            if (mEraseSdCard) {
                Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                /* SPRD：ADD for Settings 327753 @{ */
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_INTERNAL, true);
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_EXTERNAL, true);
                /* @} */
                getActivity().startService(intent);
            } else {
                // SPRD: REMOVE because of useless
                //getActivity().sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
                // Intent handling is asynchronous -- assume it will happen soon.
                /* SPRD：ADD for Settings 327753 @{ */
                Log.i(TAG, "MasterClearConfirm not Erase ExtSdCard");
                Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_INTERNAL, true);
                getActivity().startService(intent);
                /* @} */                
            }
        }
    };

    /**
     * Configure the UI for the final confirmation interaction
     */
    private void establishFinalConfirmationState() {
        mFinalButton = (Button) mContentView.findViewById(R.id.execute_master_clear);
        mFinalButton.setOnClickListener(mFinalClickListener);
        mFinalButton.setVisibility(View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.master_clear_confirm, null);
        establishFinalConfirmationState();
        setHasOptionsMenu(true);
        setSoftKey();
        return mContentView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mEraseSdCard = args != null ? args.getBoolean(MasterClear.ERASE_EXTERNAL_EXTRA) : false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 0, 1, R.string.master_clear_final_button_text)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            if (Utils.isMonkeyRunning()) {
                return true;
            }

            if (mEraseSdCard) {
                Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_INTERNAL, true);
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_EXTERNAL, true);
                getActivity().startService(intent);
            } else {
                Log.i(TAG, "MasterClearConfirm not Erase ExtSdCard");
                Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
                intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
                intent.putExtra(ExternalStorageFormatter.EXTRA_FORMAT_INTERNAL, true);
                getActivity().startService(intent);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void setSoftKey() {
        FeatureBarHelper featureBar = new FeatureBarHelper(getActivity());
        ViewGroup vg = featureBar.getFeatureBar();
        if (vg != null) {
            TextView center = (TextView) featureBar.getCenterKeyView();
            vg.removeView(center);
        }
    }
}
