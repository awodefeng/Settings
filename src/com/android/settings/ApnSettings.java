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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.sprd.android.support.featurebar.FeatureBarHelper;

import java.util.ArrayList;

public class ApnSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    static final String TAG = "ApnSettings";

    public static final String EXTRA_POSITION = "position";
    public static final String RESTORE_CARRIERS_URI =
        "content://telephony/carriers/restore";
    public static final String PREFERRED_APN_URI =
        "content://telephony/carriers/preferapn";

    public static final String APN_ID = "apn_id";
    /* SPRD: Bug 628333 modify for support virtual operator @{ */
    public static final String MVNO_TYPE = "mvno_type";
    public static final String MVNO_MATCH_DATA = "mvno_match_data";
    /* @} */
    // SPRD: for 640738 hide ims apn
    public static final String APN_TYPE_IMS = "ims";

    /* SPRD: Bug 655228 @{ */
    private static final String MVNO_TYPE_SPN = "spn";
    private static final String MVNO_TYPE_IMSI = "imsi";
    private static final String MVNO_TYPE_GID = "gid";
    private static final String MVNO_TYPE_PNN = "pnn";
    /* @} */
    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int TYPES_INDEX = 3;
    /* SPRD: Bug 628333 modify for support virtual operator @{ */
    private static final int MVNO_TYPE_INDEX = 4;
    private static final int MVNO_MATCH_DATA_INDEX = 5;
    /* @} */

    private static final int MENU_NEW = Menu.FIRST;
    private static final int MENU_RESTORE = Menu.FIRST + 1;

    private static final int EVENT_RESTORE_DEFAULTAPN_START = 1;
    private static final int EVENT_RESTORE_DEFAULTAPN_COMPLETE = 2;

    private static final int DIALOG_RESTORE_DEFAULTAPN = 1001;

    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);

    /* SPRD: add for bug716704 @{ */
    private static final String SPLIT = ",";
    private static final String CARRIER_CONFIG = "carrier_config";
    /* @} */

    private static boolean mRestoreDefaultApnMode;

    private RestoreApnUiHandler mRestoreApnUiHandler;
    private RestoreApnProcessHandler mRestoreApnProcessHandler;
    private HandlerThread mRestoreDefaultApnThread;

    private String mSelectedKey;

    public static int mPhoneId = 0; // SPRD: add for multi-sim

    /* SPRD: add for bug640798 @{ */
    private FeatureBarHelper mFeatureBarHelper;
    private TextView mLeftSkView;
    private TextView mCenterSkView;
    private TextView mRightSkView;
    /* @} */

    private IntentFilter mMobileStateFilter;

    /* SPRD: Bug 628333 modify for support virtual operator @{ */
    private String mMvnoType;
    private String mMvnoMatchData;
    /* @} */
    /* SPRD: add for bug723007 @{ */
    private static final String CURRENT_OPERATOR = "ro.operator";
    private static final String CUCC = "cucc";
    /* @} */
    /* SPRD: feature 810275  @{ */
    private static int mSelectedId;
    private int mApnPreferenceId;
    private static final int MENU_DIALOG = 1988;
    private ApnPreference mSelectedPreference;
    private String mDialogName;
    /* @} */
    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                PhoneConstants.DataState state = getMobileDataState(intent);
                switch (state) {
                case CONNECTED:
                    if (!mRestoreDefaultApnMode) {
                        fillList();
                    } else {
                        showDialog(DIALOG_RESTORE_DEFAULTAPN);
                    }
                    break;
                }
            }
        }
    };

    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPhoneId = getIntent().getIntExtra(WirelessSettings.SUB_ID,
                               TelephonyManager.getDefaultDataPhoneId(getApplicationContext())); // SPRD
                                                                                                 // :
                                                                                                 // add
                                                                                                 // by
                                                                                                 // spreadst
        Log.d(TAG, "onCreate phoneId = " + mPhoneId);
        addPreferencesFromResource(R.xml.apn_settings);
        getListView().setItemsCanFocus(true);
        /* SPRD: feature 810275  @{ */
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        /* @} */

        /* SPRD: add for bug640798 @{ */
        mFeatureBarHelper = new FeatureBarHelper(this);
        mLeftSkView = (TextView)mFeatureBarHelper.getOptionsKeyView();
        mCenterSkView =(TextView)mFeatureBarHelper.getCenterKeyView();
        mCenterSkView.setText(R.string.default_feature_bar_center);
        mRightSkView = (TextView)mFeatureBarHelper.getBackKeyView();
        /* @} */
        mMobileStateFilter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mMobileStateReceiver, mMobileStateFilter);

        /** SPRD: Bug 327811 title add phoneId @{ */
        if (TelephonyManager.isMultiSim()) {
            this.setTitle(getResources().getString(
                    R.string.apn_settings_ex, mPhoneId + 1));
        }
        /** @} */

        if (!mRestoreDefaultApnMode) {
            fillList();
        } else {
            showDialog(DIALOG_RESTORE_DEFAULTAPN);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mMobileStateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRestoreDefaultApnThread != null) {
            mRestoreDefaultApnThread.quit();
        }
    }

    private void fillList() {
        /* SPRD: for multi-sim @{ */
        // String where = "numeric=\""
        // +
        // android.os.SystemProperties.get(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC,
        // "")
        // + "\"";
        //
        // Cursor cursor =
        // getContentResolver().query(Telephony.Carriers.CONTENT_URI, new
        // String[] {
        // "_id", "name", "apn", "type"}, where, null,
        // Telephony.Carriers.DEFAULT_SORT_ORDER);

        // Uri contentUri = Telephony.Carriers.CONTENT_URI;
        String where;
        Uri contentUri = Telephony.Carriers.getContentUri(mPhoneId,null);

        /* SPRD: for 640738 hide ims apn @{*/
        String mccmnc;
        if (TelephonyManager.isMultiSim()) {
            mccmnc = android.os.SystemProperties.get(TelephonyManager.getProperty(
                    TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, mPhoneId), "");
            // APNs are listed based on the MCC+MNC.
            // Get the value from appropriate Telephony Property based on the
            // subscription.
            where = "numeric=\"" + mccmnc + "\"";

        } else {
            mccmnc = android.os.SystemProperties.get(
                    TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, "");
            where = "numeric=\"" + mccmnc + "\"";

        }
        where += " and name!='CMCC DM'";
        /* SPRD: Bug 628333 modify for support virtual operator @{ */
        Log.d(TAG,"where = " + where + ",mccmnc =" + mccmnc);
        Cursor cursor = getContentResolver().query(contentUri, new String[] {
                "_id", "name", "apn", "type", "mvno_type", "mvno_match_data" }, where, null,
                Telephony.Carriers.DEFAULT_SORT_ORDER);
        /* @} */
        /* @} */

        if (cursor != null) {
            PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
            apnList.removeAll();

            /* SPRD: Bug 628333 modify for support virtual operator @{ */
            ArrayList<ApnPreference> mnoApnList = new ArrayList<ApnPreference>();
            ArrayList<ApnPreference> mvnoApnList = new ArrayList<ApnPreference>();
            ArrayList<ApnPreference> mnoMmsApnList = new ArrayList<ApnPreference>();
            ArrayList<ApnPreference> mvnoMmsApnList = new ArrayList<ApnPreference>();
            /* @} */

            mSelectedKey = getSelectedApnKey();
            Log.d(TAG, "mSelectedKey = " + mSelectedKey);
            cursor.moveToFirst();
            // SPRD: feature 810275
            int id = 1;
            while (!cursor.isAfterLast()) {
                String name = cursor.getString(NAME_INDEX);
                String apn = cursor.getString(APN_INDEX);
                String key = cursor.getString(ID_INDEX);
                String type = cursor.getString(TYPES_INDEX);
                /* SPRD: Bug 628333 modify for support virtual operator @{ */
                String mvnoType = cursor.getString(MVNO_TYPE_INDEX);
                String mvnoMatchData = cursor.getString(MVNO_MATCH_DATA_INDEX);
                Log.d(TAG, "name = " + name + "apn = " + apn + "key = " + key + "type = " + type
                        + "mvnoType =" + mvnoType + "mvnoMatchData = " + mvnoMatchData);
                /* @} */
                /* SPRD: for 640738 hide ims apn @{*/
                boolean isApnVisible = apnSettingVisibility(mccmnc,type);
                Log.d(TAG, "isApnVisible = " + isApnVisible);
                if(isApnVisible) {
                    ApnPreference pref = new ApnPreference(this);

                    // SPRD: feature 810275
                    pref.setId(id);
                    pref.setKey(key);
                    pref.setTitle(name);
                    pref.setSummary(apn);
                    pref.setPersistent(false);
                    pref.setOnPreferenceChangeListener(this);

                    /* SPRD: for multi-sim @{*/
                    // boolean selectable = ((type == null) || !type.equals("mms"));
                    boolean selectable = ((type == null) || (type.indexOf("default") != -1)
                            || (type.equals("*")));
                    /* @} */
                    pref.setSelectable(selectable);
                    /* SPRD: Bug 628333 modify for support virtual operator @{ */
                    if (selectable) {
                        if ((mSelectedKey != null) && mSelectedKey.equals(key)) {
                            /* SPRD: feature 810275  @{ */
                            pref.setChecked(true);
                            mSelectedId = id;
                            /* @} */
                        }
                        addApnToList(pref, mnoApnList, mvnoApnList , mvnoType, mvnoMatchData);
                    } else {
                        addApnToList(pref, mnoMmsApnList, mvnoMmsApnList , mvnoType, mvnoMatchData);
                    }
                    /* @} */
                }
                /* @} */
                // SPRD: feature 810275
                id++;
                cursor.moveToNext();
            }
            cursor.close();

            /* SPRD: Bug 628333 modify for support virtual operator @{ */
            if (!mvnoApnList.isEmpty()) {
                Log.d(TAG,"mvnoApnList.isEmpty() = false");
                mnoApnList = mvnoApnList;
                mnoMmsApnList = mvnoMmsApnList;
            }

            for (Preference preference : mnoApnList) {
                apnList.addPreference(preference);
                ApnPreference apnPref = (ApnPreference) preference;

                // SPRD: add for bug671652
                if ((mSelectedKey == null)
                        && apnPref.getSelectable()
                        && isNeedSetDefault(mccmnc, apnPref.getSummary().toString())) {
                    Log.d(TAG,"apnPref setChecked = "  + apnPref);
                    // SPRD: feature 810275
                    apnPref.setChecked(true);
                    setSelectedApnKey(apnPref.getKey());
                }
            }

            for (Preference preference : mnoMmsApnList) {
                apnList.addPreference(preference);
            }
            /* @} */
        }
        /* @} */
    }

    /* SPRD: for  bug628333 support virtual operator @{*/
    private void addApnToList(ApnPreference pref, ArrayList<ApnPreference> mnoList,
                              ArrayList<ApnPreference> mvnoList , String mvnoType,
                              String mvnoMatchData) {
        if (!TextUtils.isEmpty(mvnoType) && !TextUtils.isEmpty(mvnoMatchData)) {
            if (mvnoMatches(mvnoType, mvnoMatchData)) {
                mvnoList.add(pref);
                // Since adding to mvno list, save mvno info
                mMvnoType = mvnoType;
                mMvnoMatchData = mvnoMatchData;
            }
        } else {
            mnoList.add(pref);
        }
    }
    /* @} */

    /* SPRD: for  bug628333 and 655228 support virtual operator @{*/
    private boolean mvnoMatches(String mvno_type, String mvno_match_data) {
        if (MVNO_TYPE_SPN.equalsIgnoreCase(mvno_type)) {
            String spnName = TelephonyManager.getDefault(mPhoneId).
                    getSpnFromSimRecords(getOpPackageName());
            Log.d(TAG, "mvnoMatches spnName =" + spnName);
            if ((spnName != null) &&
                    spnName.equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else if (MVNO_TYPE_IMSI.equalsIgnoreCase(mvno_type)) {
            String imsiSIM = TelephonyManager.getDefault(mPhoneId).getSubscriberId();
            Log.d(TAG, "mvnoMatches imsiSIM =" + imsiSIM);
            if ((imsiSIM != null) && imsiMatches(mvno_match_data, imsiSIM)) {
                return true;
            }
        } else if (MVNO_TYPE_GID.equalsIgnoreCase(mvno_type)) {
            String gid1 =TelephonyManager.getDefault(mPhoneId).getGroupIdLevel1();
            Log.d(TAG, "mvnoMatches gid1 =" + gid1);
            int mvno_match_data_length = mvno_match_data.length();
            if ((gid1 != null) && (gid1.length() >= mvno_match_data_length) &&
                    gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else if (MVNO_TYPE_PNN.equalsIgnoreCase(mvno_type)) {
            String pnn = TelephonyManager.getDefault(mPhoneId).getPnnHomeName(getOpPackageName());
            Log.d(TAG, "mvnoMatches pnn =" + pnn);
            if (pnn != null && pnn.equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else {
        }
        /* @} */
        return false;
    }
    /* @} */

    /* SPRD: for  bug628333 support virtual operator @{*/
    private boolean imsiMatches(String imsiDB, String imsiSIM) {
        // Note: imsiDB value has digit number or 'x' character for seperating USIM information
        // for MVNO operator. And then digit number is matched at same order and 'x' character
        // could replace by any digit number.
        // ex) if imsiDB inserted '310260x10xxxxxx' for GG Operator,
        //     that means first 6 digits, 8th and 9th digit
        //     should be set in USIM for GG Operator.
        int len = imsiDB.length();
        int idxCompare = 0;

        if (len <= 0) return false;
        if (len > imsiSIM.length()) return false;

        for (int idx = 0; idx < len; idx++) {
            char c = imsiDB.charAt(idx);
            if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }
    /* @} */

    /* SPRD: for 640738 hide ims apn @{*/
    public boolean apnSettingVisibility(String mccmnc, String apnType) {
        boolean isApnVisible = true;

        if (APN_TYPE_IMS.equals(apnType)) {
            /* SPRD: add for bug723007 @{ */
            if (isCUCCVersion()) {
                Log.d(TAG, "apnSettingVisibility is cucc version");
                return true;
            }
            /* @} */
            // SPRD: add for bug716704
            return isOperatorNeedVisible(mccmnc) ? true : false;
        }

        return isApnVisible;
    }
    /* @} */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_NEW, 0,
                getResources().getString(R.string.menu_new))
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, MENU_RESTORE, 0,
                getResources().getString(R.string.menu_restore))
                .setIcon(android.R.drawable.ic_menu_upload);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_NEW:
            addNewApn();
            return true;

        case MENU_RESTORE:
            /* SPRD: modify for bug 631492 add dialog for restoreDefaultApn @{*/
            Dialog dialog = new AlertDialog.Builder(ApnSettings.this)
                    .setMessage(R.string.dialog_restore_default_apn)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    dialog.dismiss();
                                    restoreDefaultApn();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .setOnDismissListener(
                            new DialogInterface.OnDismissListener() {
                                public void onDismiss(DialogInterface dialog) {
                                    return;
                                }
                            }).create();
            dialog.show();
            /* @} */
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addNewApn() {
        /* SPRD: for multi-sim @{ */
        // startActivity(new Intent(Intent.ACTION_INSERT,
        // Telephony.Carriers.CONTENT_URI));
        Uri uri;
        uri = Telephony.Carriers.getContentUri(mPhoneId,null);
        Intent intent = new Intent(Intent.ACTION_INSERT, uri);
        if (TelephonyManager.isMultiSim()) {
            intent.putExtra(WirelessSettings.SUB_ID, mPhoneId);
        }

        /* SPRD: for  bug628333 support virtual operator @{*/
        if (!TextUtils.isEmpty(mMvnoType) && !TextUtils.isEmpty(mMvnoMatchData)) {
            intent.putExtra(MVNO_TYPE, mMvnoType);
            intent.putExtra(MVNO_MATCH_DATA, mMvnoMatchData);
        }
        Log.d(TAG,"addNewApn mMvnoType =" + mMvnoType + ",mMvnoMatchData =" + mMvnoMatchData);
        /* @} */
        startActivity(intent);
        /* @} */
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        /* SPRD: feature 810275  @{ */
        /*
        int pos = Integer.parseInt(preference.getKey());
        // Uri url = ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI, pos);
        Uri url;
        url = ContentUris.withAppendedId(Telephony.Carriers.getContentUri(mPhoneId,null), pos);
        startActivity(new Intent(Intent.ACTION_EDIT, url));
        */
        mApnPreferenceId = ((ApnPreference)preference).getId();
        mSelectedPreference = (ApnPreference)preference;
        Log.d(TAG, "onPreferenceTreeClick mApnPreferenceId = " + mApnPreferenceId
                + " mSelectedId =" + mSelectedId);
        removeDialog(MENU_DIALOG);
        Log.i(TAG, "show menu dialog");
        mDialogName = (String) preference.getTitle();
        showDialog(MENU_DIALOG);
        /* @} */
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "onPreferenceChange(): Preference - " + preference
                + ", newValue - " + newValue + ", newValue type - "
                + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }

        return true;
    }

    /* SPRD: feature 810275  @{ */
    public void editApn(int id) {
        /* SPRD: bug 810771 NullPointerException when monkey test @{ */
        if (mSelectedPreference != null) {
            Context context = mSelectedPreference.getContext();
            if (context != null) {
                int pos = Integer.parseInt(mSelectedPreference.getKey());
                Log.d(TAG, "onClick: pos = " + pos);
                Uri url;
                url = ContentUris.withAppendedId(Telephony.Carriers.
                        getContentUri(mPhoneId,null), pos);
                Cursor cursor = context.getContentResolver().query(url, null, null, null, null);
                if ((cursor != null) && (cursor.getCount() > 0)) {
                    Intent intent = null;
                    intent = new Intent(Intent.ACTION_EDIT, url);
                    if (TelephonyManager.isMultiSim()) {
                        intent.putExtra(WirelessSettings.SUB_ID, mPhoneId);
                    }
                    context.startActivity(intent);
                }
            }
        }
        /* @} */
    }

    public void switchDefaultApn(int id) {
        mSelectedId = id;
        /* SPRD: bug 810771 NullPointerException when monkey test @{ */
        if (mSelectedPreference != null) {
            mSelectedPreference.setChecked(true);
            setSelectedApnKey((String) (mSelectedPreference.getKey()));
        }
        /* @} */
    }
    /* @} */

    private void setSelectedApnKey(String key) {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
        /* SPRD: for multi-sim @{ */
        // values.put(APN_ID, mSelectedKey);
        // resolver.update(PREFERAPN_URI, values, null, null);
        values.put(getApnIdByPhoneId(mPhoneId), mSelectedKey);
        resolver.update(
                Telephony.Carriers.getContentUri(mPhoneId, Telephony.Carriers.PATH_PREFERAPN),
                values, null, null);
        /* @} */
    }

    private String getSelectedApnKey() {
        String key = null;

        /* SPRD: for multi-sim @{ */
        // Cursor cursor = getContentResolver().query(PREFERAPN_URI, new
        // String[] {"_id"},
        // null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        Cursor cursor = getContentResolver().query(
                Telephony.Carriers.getContentUri(mPhoneId, Telephony.Carriers.PATH_PREFERAPN),
                new String[] {
                    "_id"
                },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        /* @} */
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(ID_INDEX);
        }
        cursor.close();
        return key;
    }

    private boolean restoreDefaultApn() {
        // SPRD: modified for bug270422 start
        mRestoreDefaultApnMode = true;
        showDialog(DIALOG_RESTORE_DEFAULTAPN);
        // SPRD: modified for bug270422 end

        if (mRestoreApnUiHandler == null) {
            mRestoreApnUiHandler = new RestoreApnUiHandler();
        }

        if (mRestoreApnProcessHandler == null ||
            mRestoreDefaultApnThread == null) {
            mRestoreDefaultApnThread = new HandlerThread(
                    "Restore default APN Handler: Process Thread");
            mRestoreDefaultApnThread.start();
            mRestoreApnProcessHandler = new RestoreApnProcessHandler(
                    mRestoreDefaultApnThread.getLooper(), mRestoreApnUiHandler);
        }

        mRestoreApnProcessHandler
                .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_START);
        return true;
    }

    private class RestoreApnUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_COMPLETE:
                    fillList();
                    getPreferenceScreen().setEnabled(true);
                    mRestoreDefaultApnMode = false;
                    dismissDialog(DIALOG_RESTORE_DEFAULTAPN);
                    Toast.makeText(
                        ApnSettings.this,
                        getResources().getString(
                                R.string.restore_default_apn_completed),
                        Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_START:
                    ContentResolver resolver = getContentResolver();
                    /* SPRD: for multi-sim @{ */
                    // resolver.delete(DEFAULTAPN_URI, null, null);
                    resolver.delete(Telephony.Carriers.getContentUri(mPhoneId,
                            Telephony.Carriers.PATH_RESTORE),null,null);
                    /* @} */
                    mRestoreApnUiHandler
                        .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_COMPLETE);
                    break;
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        /* SPRD: feature 810275  @{ */
        Log.i(TAG, "onCreateDialog id = " + id);
        final int EDIT = 0;
        final int USE = 1;
        if (id == MENU_DIALOG) {
            Dialog dialog = null;
            AlertDialog.Builder builder = null;
            builder = new AlertDialog.Builder(this);
            String[] menuItem;
            String[] editUseMenu = new String[] {
                    getResources().getString(R.string.edit_pref),
                    getResources().getString(R.string.use_pref)
            };
            String[] editMenu = new String[] {
                    getResources().getString(R.string.edit_pref)
            };
            // SPRD: bug 820294 & 838879
            if (mApnPreferenceId == mSelectedId ||
                    (mSelectedPreference != null && !mSelectedPreference.getSelectable())) {
                menuItem = editMenu;
            } else {
                menuItem = editUseMenu;
            }
            builder.setTitle(mDialogName);
            builder.setItems(menuItem, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // TODO Auto-generated method stub
                    switch (which) {
                        case EDIT:
                            //edit
                            editApn(mApnPreferenceId);
                            break;
                        case USE:
                            //switch apn
                            switchDefaultApn(mApnPreferenceId);
                            break;
                        default:
                            break;
                    }
                    removeDialog(MENU_DIALOG);
                }
            });
            dialog = builder.show();
            return dialog;
        }
        /* @} */
        // SPRD: modified for bug270422
        if (id == DIALOG_RESTORE_DEFAULTAPN && mRestoreDefaultApnMode) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage(getResources().getString(R.string.restore_default_apn));
            dialog.setCancelable(false);
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            getPreferenceScreen().setEnabled(false);
        }
    }

    /** SPRD: for multi-sim @{ */
    private String getApnIdByPhoneId(int phoneId) {
        switch (phoneId) {
            case 0:
                return APN_ID;
            default:
                return APN_ID + "_sim" + (phoneId + 1);
        }
    }
    /** @} */

    /** SPRD: Bug 327811 onNewIntent listen Home Button @{ */
    @Override
    protected void onNewIntent(Intent intent) {
        // TODO Auto-generated method stub
        super.onNewIntent(intent);
        mPhoneId = intent
                .getIntExtra(WirelessSettings.SUB_ID, TelephonyManager
                        .getDefaultDataPhoneId(getApplicationContext()));
        Log.i(TAG, "onNewIntent --> mPhoneId = " + mPhoneId);
    }
    /** @} */

    /* SPRD: add for bug671652 @{ */
    public boolean isNeedSetDefault(String mccmnc, String apn) {
        Log.i(TAG, "isNeedSetDefault mccmnc: " + mccmnc + " apn: " + apn);

        boolean isCUCCSimCard = "46001".equals(mccmnc)
                || "46006".equals(mccmnc)
                || "46009".equals(mccmnc);

        if (isCUCCSimCard) {
            if ("3gnet".equalsIgnoreCase(apn)) {
                return true;
            } else {
                return false;
            }
        }

        return true;
    }
    /* @} */

    /* SPRD: add for bug716704 @{ */
    private boolean isOperatorNeedVisible(String mccmnc) {
        if (TextUtils.isEmpty(mccmnc)) {
            return false;
        }

        CarrierConfigManager configManager =
                (CarrierConfigManager) this.getSystemService(CARRIER_CONFIG);
        if (configManager == null) {
            Log.d (TAG, "configManager is null");
            return false;
        }

        PersistableBundle persistableBundle = configManager.getConfigForDefaultPhone();
        if (persistableBundle == null) {
            Log.d(TAG, "persistableBundle is null ");
            return false;
        }

        String operatorList = persistableBundle
                .getString(CarrierConfigManager.KEY_OPERATOR_SHOW_IMS_APN, "");
        Log.d(TAG, "operatorList = " + operatorList);
        if (!operatorList.isEmpty()) {
            String[] strings = operatorList.split(SPLIT);
            for (String s : strings) {
                if (!s.isEmpty() && s.equals(mccmnc)) {
                    return true;
                }
            }
        }

        return false;
    }
    /* @} */

    /* SPRD: add for bug723007 @{ */
    private boolean isCUCCVersion() {
        return android.os.SystemProperties.get(CURRENT_OPERATOR, "")
                .equalsIgnoreCase(CUCC);
    }
    /* @} */
}
