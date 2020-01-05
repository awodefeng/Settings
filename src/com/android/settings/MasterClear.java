/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sprd.android.support.featurebar.FeatureBarHelper;

/**
 * Confirm and execute a reset of the device to a clean "just out of the box"
 * state.  Multiple confirmations are required: first, a general "are you sure
 * you want to do this?" prompt, followed by a keyguard pattern trace if the user
 * has defined one, followed by a final strongly-worded "THIS WILL ERASE EVERYTHING
 * ON THE PHONE" prompt.  If at any time the phone is allowed to go to sleep, is
 * locked, et cetera, then the confirmation sequence is abandoned.
 *
 * This is the initial screen.
 */
public class MasterClear extends Fragment {
    private static final String TAG = "MasterClear";

    private static final int KEYGUARD_REQUEST = 55;
    private static final int PIN_REQUEST = 56;
    private static final int DRAWABLE_WIDTH = 100;
    private static final int DRAWABLE_HEIGHT = 100;
    static final String ERASE_EXTERNAL_EXTRA = "erase_sd";
    private String mEnvironmentData = null;

    private View mContentView;
    private Button mInitiateButton;
    private View mExternalStorageContainer;
    private CheckBox mExternalStorage;
    private boolean mPinConfirmed;

    /* SPRD:add 20140516 of bug 3127474,No SD card, restore factory settings, "Format SD Card" is not ashing @{*/
    private TextView mEraseExternalStorage;
    private TextView mEraseExternalStorageDescription;
    /* @} */

    /* SPRD:fix bug 276465,reset factory when the power lower @{*/
    private int mLevelpower;
    private Context mContext;
    /* @} */
    /* SPRD: modify 20140206 Spreadtrum of 274789 Unplug the USB connection , format the SD card option is still not checked @{ */
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            mEnvironmentData = Environment.getExternalStoragePathState();
            establishInitialState();
        }
    };
    /* @} */
    /**
     * Keyguard validation is run using the standard {@link ConfirmLockPattern}
     * component as a subactivity
     * @param request the request code to be returned once confirmation finishes
     * @return true if confirmation launched
     */
    private boolean runKeyguardConfirmation(int request) {
        Resources res = getActivity().getResources();
        return new ChooseLockSettingsHelper(getActivity(), this)
                .launchConfirmationActivity(request,
                        res.getText(R.string.master_clear_gesture_prompt),
                        res.getText(R.string.master_clear_gesture_explanation));
    }

    private boolean runRestrictionsChallenge() {
        if (UserManager.get(getActivity()).hasRestrictionsChallenge()) {
            startActivityForResult(
                    new Intent(Intent.ACTION_RESTRICTIONS_CHALLENGE), PIN_REQUEST);
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PIN_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                mPinConfirmed = true;
            }
            return;
        } else if (requestCode != KEYGUARD_REQUEST) {
            return;
        }

        // If the user entered a valid keyguard trace, present the final
        // confirmation prompt; otherwise, go back to the initial state.
        if (resultCode == Activity.RESULT_OK) {
            showFinalConfirmation();
        } else {
            establishInitialState();
        }
    }

    private void showFinalConfirmation() {
        Preference preference = new Preference(getActivity());
        preference.setFragment(MasterClearConfirm.class.getName());
        preference.setTitle(R.string.master_clear_confirm_title);
        preference.getExtras().putBoolean(ERASE_EXTERNAL_EXTRA,mExternalStorage.isChecked());
        ((PreferenceActivity) getActivity()).onPreferenceStartFragment(null, preference);
    }

    /**
     * If the user clicks to begin the reset sequence, we next require a
     * keyguard confirmation if the user has currently enabled one.  If there
     * is no keyguard available, we simply go to the final confirmation prompt.
     */
    private final Button.OnClickListener mInitiateListener = new Button.OnClickListener() {

        public void onClick(View v) {
            // SPRD:Add 20131211 Spreadst of bug251129, set button disabled if it is already clicked
            //mInitiateButton.setEnabled(false);//SPRD:remove for bug629484
            /* SPRD:fix bug 276465,reset factory when the power lower @{
            mPinConfirmed = false;
            if (runRestrictionsChallenge()) {
                return;
            }
            if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                showFinalConfirmation();
            }*/
            Log.d(TAG,"onClick  mLevelpower is "+mLevelpower);
            if (mLevelpower < 30) {
                dialog();
            } else {
                mPinConfirmed = false;
                if (runRestrictionsChallenge()) {
                    return;
                }
                if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                    showFinalConfirmation();
                }
            }
            /* @} */
        }
    };

    /**
     * In its initial state, the activity presents a button for the user to
     * click in order to initiate a confirmation sequence.  This method is
     * called from various other points in the code to reset the activity to
     * this base state.
     *
     * <p>Reinflating views from resources is expensive and prevents us from
     * caching widget pointers, so we use a single-inflate pattern:  we lazy-
     * inflate each view, caching all of the widget pointers we'll need at the
     * time, then simply reuse the inflated views directly whenever we need
     * to change contents.
     */
    private void establishInitialState() {
        /*
         * If the external storage is emulated, it will be erased with a factory
         * reset at any rate. There is no need to have a separate option until
         * we have a factory reset that only erases some directories and not
         * others. Likewise, if it's non-removable storage, it could potentially have been
         * encrypted, and will also need to be wiped.
         */
        /* SPRD: REMOVE because of useless @{
        boolean isExtStorageEmulated = Environment.isExternalStorageEmulated();
        if (isExtStorageEmulated
                || (!Environment.isExternalStorageRemovable() && isExtStorageEncrypted())) {
            mExternalStorageContainer.setVisibility(View.GONE);

            final View externalOption = mContentView.findViewById(R.id.erase_external_option_text);
            externalOption.setVisibility(View.GONE);

            final View externalAlsoErased = mContentView.findViewById(R.id.also_erases_external);
            externalAlsoErased.setVisibility(View.VISIBLE);

            // If it's not emulated, it is on a separate partition but it means we're doing
            // a force wipe due to encryption.
            mExternalStorage.setChecked(!isExtStorageEmulated);
        } else {
            mExternalStorageContainer.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    mExternalStorage.toggle();
                }
            });
        }
        @} */

        /* SPRD：ADD for Settings porting from 4.1 to 4.3 @{ */
        if (mEnvironmentData != null && mEnvironmentData.equals(Environment.MEDIA_MOUNTED)){
            /* SPRD:add 20140516 of bug 3127474,No SD card, restore factory settings, "Format SD Card" is not ashing  @{ */
            mEraseExternalStorage.setEnabled(true);
            mEraseExternalStorageDescription.setEnabled(true);
            /* @} */
            mExternalStorageContainer
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mExternalStorage.toggle();
                        }
                    });
        } else {
            mExternalStorage.setEnabled(false);
            mExternalStorage.setChecked(false);
            /* SPRD:add 20140516 of bug 3127474,No SD card, restore factory settings, "Format SD Card" is not ashing  @{ */
            mEraseExternalStorage.setEnabled(false);
            mEraseExternalStorageDescription.setEnabled(false);
            /* @} */
            mExternalStorageContainer.setOnClickListener(null);

        }
        /* @} */

        loadAccountList();
    }

    private boolean isExtStorageEncrypted() {
        String state = SystemProperties.get("vold.decrypt");
        return !"".equals(state);
    }

    private void loadAccountList() {
        View accountsLabel = mContentView.findViewById(R.id.accounts_label);
        LinearLayout contents = (LinearLayout)mContentView.findViewById(R.id.accounts);
        contents.removeAllViews();

        Context context = getActivity();

        AccountManager mgr = AccountManager.get(context);
        Account[] accounts = mgr.getAccounts();
        final int N = accounts.length;
        if (N == 0) {
            accountsLabel.setVisibility(View.GONE);
            contents.setVisibility(View.GONE);
            return;
        }

        LayoutInflater inflater = (LayoutInflater)context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        AuthenticatorDescription[] descs = AccountManager.get(context).getAuthenticatorTypes();
        final int M = descs.length;

        for (int i=0; i<N; i++) {
            Account account = accounts[i];
            AuthenticatorDescription desc = null;
            for (int j=0; j<M; j++) {
                if (account.type.equals(descs[j].type)) {
                    desc = descs[j];
                    break;
                }
            }
            if (desc == null) {
                Log.w(TAG, "No descriptor for account name=" + account.name
                        + " type=" + account.type);
                continue;
            }
            Drawable icon = null;
            try {
                if (desc.iconId != 0) {
                    Context authContext = context.createPackageContext(desc.packageName, 0);
                    icon = authContext.getResources().getDrawable(desc.iconId);
                    icon = scaleDrawable(icon, DRAWABLE_WIDTH, DRAWABLE_HEIGHT);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "No icon for account type " + desc.type);
            }
            /* SPRD: modify bug 276452, account icon display inconsistent @{
            TextView child = (TextView)inflater.inflate(R.layout.master_clear_account,
                    contents, false);
            child.setText(account.name);
            if (icon != null) {
                child.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            }*/
            LinearLayout child = (LinearLayout)inflater.inflate(R.layout.master_clear_account,
                    contents, false);
            TextView accountTitle = (TextView)child.findViewById(R.id.account_title);
             /*SPRD:add for bug634119 @{*/
             if ("sprd.com.android.account.phone".equals(account.type)) {
                 accountTitle.setText(R.string.current_label_phone);
             } else {
                accountTitle.setText(account.name);
            }
            /*@}*/
            if (icon != null) {
                ImageView accountIcon  = (ImageView)child.findViewById(R.id.account_icon);
                accountIcon.setBackground(icon);
            }
            /* @} */
            contents.addView(child);
        }

        // SPRD: Modify for bug912293.
        accountsLabel.setVisibility(View.GONE);
        contents.setVisibility(View.GONE);
    }

    public Bitmap drawableToBitmap(Drawable drawable) {
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                : Bitmap.Config.RGB_565;
        Bitmap newBitMap = Bitmap.createBitmap(drawableWidth, drawableHeight, config);
        Canvas canvas = new Canvas(newBitMap);
        drawable.setBounds(0, 0, drawableWidth, drawableHeight);
        drawable.draw(canvas);
        return newBitMap;
    }

    public Drawable scaleDrawable(Drawable drawable, int width, int height) {
        int drawablewidth = drawable.getIntrinsicWidth();
        int drawableheight = drawable.getIntrinsicHeight();
        Bitmap oldbmp = drawableToBitmap(drawable);
        Matrix matrix = new Matrix();
        float scaleWidth = ((float) width / drawablewidth);
        float scaleHeight = ((float) height / drawableheight);
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, drawablewidth, drawableheight, matrix,
                true);
        return new BitmapDrawable(null,newbmp);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.master_clear, null);
        mContext = getActivity(); // SPRD:fix bug 276465,reset factory when the power lower
        /* SPRD: modify 20140206 Spreadtrum of 274789 Unplug the USB connection , format the SD card option is still not checked @{ */
        IntentFilter mountFilter = new IntentFilter();
        mountFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        mountFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mountFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        mountFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        mountFilter.addAction(Intent.ACTION_MEDIA_NOFS);
        mountFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        mountFilter.addDataScheme("file");
        mContext.registerReceiver(mMountReceiver, mountFilter);
        /* @} */
        mInitiateButton = (Button) mContentView.findViewById(R.id.initiate_master_clear);
        // mInitiateButton.setOnClickListener(mInitiateListener);
        mInitiateButton.setVisibility(View.GONE);
        mExternalStorageContainer = mContentView.findViewById(R.id.erase_external_container);
        mExternalStorage = (CheckBox) mContentView.findViewById(R.id.erase_external);
        mExternalStorageContainer.setVisibility(View.GONE);
        View externalOption = mContentView.findViewById(R.id.erase_external_option_text);
        externalOption.setVisibility(View.GONE);
        // SPRD:add 20140516 of bug 3127474,No SD card, restore factory settings, "Format SD Card" is not ashing @{*/
        mEraseExternalStorage=(TextView)mContentView.findViewById(R.id.erase_storage_text);
        mEraseExternalStorageDescription=(TextView)mContentView.findViewById(R.id.erase_storage_description_text);
        /* @} */
        setHasOptionsMenu(true);
        setSoftKey();
        return mContentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // SPRD:Add 20131211 Spreadst of 251129, reset button enabled when onResume
        mEnvironmentData = Environment.getExternalStoragePathState();
        establishInitialState();
        mInitiateButton.setEnabled(true);
        // If this is the second step after restrictions pin challenge
        if (mPinConfirmed) {
            mPinConfirmed = false;
            if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                showFinalConfirmation();
            }
        }
        /* SPRD:fix bug 276465,reset factory when the power lower @{*/
        mContext.registerReceiver(mBatteryInfoReceiver, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        /* @} */
    }

    /* SPRD:fix bug 276465,reset factory when the power lower @{*/
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int level = -1;
                if (rawlevel >= 0 && scale > 0) {
                    level = (rawlevel * 100) / scale;
                }
                mLevelpower = level;
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        mContext.unregisterReceiver(mBatteryInfoReceiver);
    }

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(R.string.master_clear_level);
        builder.setTitle(R.string.factory_dialog_title);
        builder.setPositiveButton(mContext.getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                            showFinalConfirmation();
                        }
                    }
                });
        builder.setNegativeButton(mContext.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.create().show();
    }
    /* @} */
    /* SPRD: modify 20140206 Spreadtrum of 274789 Unplug the USB connection , format the SD card option is still not checked @{ */
    @Override
    public void onDestroy() {
        mContext.unregisterReceiver(mMountReceiver);
        super.onDestroy();
    }
    /* @} */

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, 0, 1, R.string.master_clear_button_text)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            if (mLevelpower < 30) {
                dialog();
            } else {
                mPinConfirmed = false;
                if (runRestrictionsChallenge()) {
                    return true;
                }
                if (!runKeyguardConfirmation(KEYGUARD_REQUEST)) {
                    showFinalConfirmation();
                }
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
