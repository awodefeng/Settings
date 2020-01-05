/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.sim;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;
import android.text.TextUtils;
import android.sim.SimManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.sim.SimSettings.SimInfoChanged;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.util.DisplayMetrics;
import android.util.Log;

public class SimDialogActivity extends Activity {
    private static String TAG = "SimDialogActivity";

//    public static String PREFERRED_SIM = "preferred_sim";
    public static String DIALOG_TYPE_KEY = "dialog_type";
    public static final int INVALID_PICK = -1;
    public static final int DATA_PICK = 0;
    public static final int CALLS_PICK = 1;
    public static final int SMS_PICK = 2;
//    public static final int PREFERRED_PICK = 3;
    /* SPRD: add option for selecting primary card @{ */
    public static final int PRIMARY_PICK = 4;
    public static final int SHOW_APN_DIALOG = 6;
    private int mDialogType = INVALID_PICK;
    public static final String PRIMARYCARD_PICK_CANCELABLE = "show_after_boot";
    private boolean mIsForeground = false;
    private boolean mIsPrimaryCardCancelable = false;
    private PorgressDialogFragment mProgerssDialogFragment = null;
    private TelephonyManager mTelephonyManager[];
    private Dialog mSimChooseDialog = null;
    private SimManager mSimManager;
    private Context mContext;
    SimInfoChanged mSimInfoChanged;
    private ConnectivityManager mConnService;
    private static final int TEXT_SIZE = 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        int mNumSlots = TelephonyManager.getPhoneCount();
        mTelephonyManager = new TelephonyManager[mNumSlots];
        for (int i = 0; i < mNumSlots; i++) {
            mTelephonyManager[i] = (TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                    Context.TELEPHONY_SERVICE, i));
        }
        /* SPRD: add option for selecting primary card @{ */
        mSimManager = SimManager.get(this);
        mConnService = ConnectivityManager.from(mContext);
        processIntent();
        /* SPRD: modify for bug508651 @{ */
        final IntentFilter intentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
        /* @} */
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver);
        /* @} */
    }

    /* SPRD: modify for bug526139 @{ */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle extras = getIntent().getExtras();
        int OldDialogType = extras.getInt(DIALOG_TYPE_KEY, INVALID_PICK);
        /* SPRD:modify for Bug555948,the primary card dialog was dismissed after SMS dialog create.@{ */
        mIsPrimaryCardCancelable = extras.getBoolean(PRIMARYCARD_PICK_CANCELABLE);
        setIntent(intent);
        extras = getIntent().getExtras();
        int dialogType = extras.getInt(DIALOG_TYPE_KEY, INVALID_PICK);
        if (dialogType != OldDialogType && mSimChooseDialog != null && !mIsPrimaryCardCancelable) {
            /* @} */
            mSimChooseDialog.dismiss();
            processIntent();
        }
    }

    private void processIntent() {
        final Bundle extras = getIntent().getExtras();
        /*SPRD: add for 492893, fuzz test @{*/
        if (extras == null) {
            Log.e(TAG, "invalid extras null");
            finish();
            return;
        }
        /*@}*/

        final int dialogType = extras.getInt(DIALOG_TYPE_KEY, INVALID_PICK);
        mIsPrimaryCardCancelable = extras.getBoolean(PRIMARYCARD_PICK_CANCELABLE);
        switch (dialogType) {
            case DATA_PICK:
            case CALLS_PICK:
            case SMS_PICK:
            /* SPRD: add option for selecting primary card @{ */
            case PRIMARY_PICK:
                mDialogType = dialogType;
                mSimChooseDialog = createDialog(this, mDialogType);
                mSimChooseDialog.show();
                break;
            /* @} */
//            case PREFERRED_PICK:
//                displayPreferredDialog(extras.getInt(PREFERRED_SIM));
//                break;
            default:
                throw new IllegalArgumentException("Invalid dialog type " + dialogType + " sent.");
        }
    }
    /* @} */

//    private void displayPreferredDialog(final int slotId) {
//        final Resources res = getResources();
//        final Context context = getApplicationContext();
//        final Sim sir = SimManager
//                .get(this).getSimById(slotId);
//
//        if (sir != null) {
//            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
//            alertDialogBuilder.setTitle(R.string.sim_preferred_title);
//            alertDialogBuilder.setMessage(res.getString(
//                        R.string.sim_preferred_message, sir.getName()));
//
//            alertDialogBuilder.setPositiveButton(R.string.yes, new
//                    DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int id) {
//                    final int subId = sir.getSubscriptionId();
//                    PhoneAccountHandle phoneAccountHandle =
//                            subscriptionIdToPhoneAccountHandle(subId);
//                    setDefaultDataSubId(context, subId);
//                    setDefaultSmsSubId(context, subId);
//                    setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
//                    finish();
//                }
//            });
//            alertDialogBuilder.setNegativeButton(R.string.no, new
//                    DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog,int id) {
//                    finish();
//                }
//            });
//            alertDialogBuilder.create().show();
//        } else {
//            finish();
//        }
//    }

    private void setDefaultDataSubId(final Context context, final int phoneId) {
        TelephonyManager.setDefaultDataPhoneId(context, phoneId);
        /* SPRD: Bug 702718 create PDP connection take 2-3 minutes after change sim data
         * from main sim card to second card @{
         * */
        disableDataForOtherSubscriptions(phoneId);
        mConnService.setMobileDataEnabledByPhoneId(phoneId, true);
        /* @}*/
        Toast.makeText(context, R.string.data_switch_started, Toast.LENGTH_LONG).show();
    }

    private void disableDataForOtherSubscriptions(int defaultDataPhoneId) {
       Sim[] subInfoList = mSimManager.getActiveSims();
       if (subInfoList != null) {
           for (Sim subInfo : subInfoList) {
               if (subInfo.getPhoneId() != defaultDataPhoneId) {
                   mConnService.setMobileDataEnabledByPhoneId(subInfo.getPhoneId(), false);
               }
           }
       }
   }

//    private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(final int subId) {
//        final TelecomManager telecomManager = TelecomManager.from(this);
//        final TelephonyManager telephonyManager = TelephonyManager.from(this);
//        final Iterator<PhoneAccountHandle> phoneAccounts =
//                telecomManager.getCallCapablePhoneAccounts().listIterator();
//
//        while (phoneAccounts.hasNext()) {
//            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
//            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
//            if (subId == telephonyManager.getSubIdForPhoneAccount(phoneAccount)) {
//                return phoneAccountHandle;
//            }
//        }
//
//        return null;
//    }

    public Dialog createDialog(final Context context, final int id) {
        dismissSimChooseDialog();
        final ArrayList<String> list = new ArrayList<String>();
        final Sim[] subInfoList = mSimManager.getActiveSims();
        final int selectableSubInfoLength = subInfoList == null ? 0 : subInfoList.length;
        final StatusBarManager statusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        final Sim[] subInfoListForCallAndSms = new Sim[selectableSubInfoLength + 1];
        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int value) {
                        // SPRD: modify by add radioButton on set defult sub id
                        setDefaltSubIdByDialogId(context, id, value, subInfoList);
                    }
                };

                Dialog.OnKeyListener keyListener = new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                            KeyEvent event) {
                        /* SPRD: add option for selecting primary card @{ */
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && !mIsPrimaryCardCancelable) {
                            finish();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_BACK
                                && mIsPrimaryCardCancelable) {
                            return true;
                        } else {
                            return false;
                        }
                        /* @} */
                    }
                };

        if (id == CALLS_PICK || id == SMS_PICK) {
            list.add(getResources().getString(R.string.sim_calls_ask_first_prefs_title));
            for (int i = 1; i < subInfoListForCallAndSms.length; i++) {
                    subInfoListForCallAndSms[i] = subInfoList[i-1];
            }
        }
        for (int i = 0; i < selectableSubInfoLength; ++i) {
            final Sim sir = subInfoList[i];
            CharSequence displayName = sir.getName();
            if (displayName == null) {
                displayName = "";
            }
            list.add(displayName.toString());
        }
        String[] arr = list.toArray(new String[0]);

        // SPRD: modify for bug459003
        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                AlertDialog.THEME_HOLO_LIGHT);

        ListAdapter adapter = new SelectAccountListAdapter(
                (id == CALLS_PICK || id == SMS_PICK)? subInfoListForCallAndSms: subInfoList,
                builder.getContext(),
                R.layout.select_account_list_item,
                arr, id);

        switch (id) {
            case DATA_PICK:
                builder.setTitle(R.string.select_sim_for_data);
                break;
            case CALLS_PICK:
                builder.setTitle(R.string.select_sim_for_calls);
                break;
            case SMS_PICK:
                builder.setTitle(R.string.sim_card_select_title);
                break;
            /* SPRD: add option of selecting primary card @{ */
            case PRIMARY_PICK:
                /* SPRD: add for bug 543820 @{ */
                View titleView = LayoutInflater.from(this).inflate(
                        R.layout.select_primary_card_title, null);
                TextView textview = (TextView) titleView
                        .findViewById(R.id.multi_mode_slot_introduce);
                if (TelephonyManager.isDeviceSupportLte()) {
                    if (TelephonyManager.RadioCapbility.CSFB == TelephonyManager.getRadioCapbility()) {
                        textview.setText(getString(R.string.select_primary_slot_description_4g));
                    }else {
                        textview.setText(getString(R.string.select_primary_slot_description_4g_3g_2g));
                    }
                }
                textview.setTextColor(Color.BLACK);
                builder.setCustomTitle(titleView);
                /* @} */
                break;
            /* @} */
            default:
                throw new IllegalArgumentException("Invalid dialog type "
                        + id + " in SIM dialog.");
        }

        Dialog dialog = builder.setAdapter(adapter, selectionListener).create();
        dialog.setOnKeyListener(keyListener);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                finish();
            }
        });

        /* SPRD: add option of selecting primary card @{ */
        if (mIsPrimaryCardCancelable) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    statusBarManager.disable(StatusBarManager.DISABLE_NONE);
                }
            });
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            dialog.setCanceledOnTouchOutside(false);
            statusBarManager.disable(StatusBarManager.DISABLE_EXPAND);
        }
        /* @} */

        return dialog;

    }

    /* SPRD: add option of selecting primary card @{ */
    private void showAlertDialog(final int phoneId) {
        dismissSimChooseDialog();
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lte_service_attention)
                .setMessage(R.string.whether_switch_primary_card)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                mTelephonyManager[0].setPrimaryCard(phoneId);
                                showProgressingDialog();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                }).create();
         /* SPRD: modify for bug503957 @{ */
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                finish();
            }
        });
        /* }@ */
        alertDialog.show();
    }

    private void showProgressingDialog() {
        Log.d(TAG, "show progressing dialog...");
        dismissProgressDialog();
        FragmentTransaction tr = getFragmentManager().beginTransaction();
        tr.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        mProgerssDialogFragment = new PorgressDialogFragment();
        mProgerssDialogFragment.setStyle(DialogFragment.STYLE_NORMAL, 0);
        mProgerssDialogFragment.setCancelable(false);
        mProgerssDialogFragment.show(tr, "progress_dialog");
    }

    private void dismissProgressDialog() {
        if (mProgerssDialogFragment != null && mProgerssDialogFragment.isVisible() && mIsForeground) {
            Log.d(TAG, "dismiss progressing dialog...");
            mProgerssDialogFragment.dismiss();
            finish();
        }
    }

    /**
    * SPRD: add for set default SMS/Voice/Data sub id by dialog id
    */
    private void setDefaltSubIdByDialogId(
            final Context context, int dialogId, int chooseId, Sim[] subInfoList){
        final Sim sir;
        mDialogType = INVALID_PICK;

        switch (dialogId) {
            case DATA_PICK:
                sir = subInfoList[chooseId];
                 /* SPRD: add new feature for data switch on/off @{ */
                int currentDataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
                 Log.d(TAG,"phoneId = " + sir.getPhoneId()
                        + ",currentDataPhoneId = " + currentDataPhoneId);
                 if ( sir.getPhoneId() != currentDataPhoneId ) {
                     Log.d(TAG,"set defalut data connection");
                     setDefaultDataSubId(context, sir.getPhoneId());
                 }
                 /* @} */
                break;
            case CALLS_PICK:
                Log.d(TAG, "getSubscriberDesiredSim" + TelephonyManager
                        .getSubscriberDesiredSim(mContext, TelephonyManager.MODE_VOICE));
                TelephonyManager.setSubscriberDesiredSim(mContext, TelephonyManager.MODE_VOICE,
                        chooseId > 0 ? chooseId - 1 : TelephonyManager.PHONE_ID_INVALID);
                TelephonyManager.setDefaultSim(mContext, TelephonyManager.MODE_VOICE,
                        chooseId > 0 ? chooseId - 1 : TelephonyManager.PHONE_ID_INVALID);
                break;
            case SMS_PICK:
                TelephonyManager.setSubscriberDesiredSim(mContext, TelephonyManager.MODE_MMS,
                        chooseId > 0 ? chooseId - 1 : TelephonyManager.PHONE_ID_INVALID);
                TelephonyManager.setDefaultSim(mContext, TelephonyManager.MODE_MMS,
                        chooseId > 0 ? chooseId - 1 : TelephonyManager.PHONE_ID_INVALID);
                break;
            /* SPRD: add option of selecting primary card @{ */
            case PRIMARY_PICK:
                sir = subInfoList[chooseId];
                int selectPrimaryCard = sir.getPhoneId();
                TelephonyManager tm = TelephonyManager.from(SimDialogActivity.this);
                Log.d(TAG, "PRIMARY_PICK lastPrimaryCard = " + tm.getPrimaryCard()
                        + " selectPrimaryCard = " + selectPrimaryCard);

                if (mIsPrimaryCardCancelable) {
                        mTelephonyManager[0].setPrimaryCard(selectPrimaryCard);
                } else if (selectPrimaryCard != tm.getPrimaryCard()) {
                    Log.d(TAG, "selectPrimaryCard != tm.getPrimaryCard()");
                    showAlertDialog(selectPrimaryCard);
                    return;
                }
            break;
            /* @} */
            default:
                throw new IllegalArgumentException("Invalid dialog type "
                        + dialogId + " in SIM dialog.");
        }

        finish();

    }

    private void dismissSimChooseDialog() {
        if (mSimChooseDialog != null && mSimChooseDialog.isShowing() && mIsForeground) {
            mSimChooseDialog.dismiss();
        }
    }

    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (!TelephonyManager.isRadioBusy(mContext)) {
                dismissProgressDialog();
            } else if (isAirplaneModeOn() && !mIsPrimaryCardCancelable) {
                finish();
            }
        }
    };

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSimInfoChanged = new SimInfoChanged();
        mSimManager.addOnSimsUpdatedListener(mSimInfoChanged, null, true);

        /* SPRD: modify for Bug613854 @{ */
        Sim[] availableSubInfoList= mSimManager.getActiveSims();
        if(availableSubInfoList == null || availableSubInfoList.length<2)
        {
            finish();
        }
        /* @} */

        mIsForeground = true;
        if (mDialogType != INVALID_PICK) {
            if (mIsPrimaryCardCancelable) {
                mSimChooseDialog = createDialog(this.getApplicationContext(), mDialogType);
            } else {
                mSimChooseDialog = createDialog(this, mDialogType);
            }
            mSimChooseDialog.show();
        }

        if (!TelephonyManager.isRadioBusy(mContext)) {
            dismissProgressDialog();
        }
    }

    class SimInfoChanged implements SimManager.OnSimsUpdateListener{

        @Override
        public void onSimUpdated(Sim[] sims) {
            if(sims == null || sims.length <2){
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        mIsForeground = false;
        super.onPause();
        mSimManager.removeOnSimsUpdatedListener(mSimInfoChanged);
    }

    private boolean isStandby(int phoneId) {
        String tmpStr = Settings.System.SIM_STANDBY + phoneId;
        return Settings.System.getInt(getContentResolver(), tmpStr, 1) == 1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            getContentResolver().unregisterContentObserver(mRadioBusyObserver);
            //SPRD: modify for bug508651
            unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Log.d(TAG, "onDestroy,Exception = " + e);
        }
    };

    private class SelectAccountListAdapter extends ArrayAdapter<String> {
        private Context mContext;
        private int mResId;
        private int mDialogId;
        private final float OPACITY = 0.54f;
        private Sim[] mSubInfoList;

        public SelectAccountListAdapter(Sim[] subInfoList,
                Context context, int resource, String[] arr, int dialogId) {
            super(context, resource, arr);
            mContext = context;
            mResId = resource;
            mDialogId = dialogId;
            mSubInfoList = subInfoList;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.title = (TextView) rowView.findViewById(R.id.title);
                holder.summary = (TextView) rowView.findViewById(R.id.summary);
                holder.icon = (ImageView) rowView.findViewById(R.id.icon);
                holder.defaultSubscription =
                        (RadioButton) rowView.findViewById(R.id.default_subscription_off);
                rowView.setTag(holder);
            } else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            final Sim sir = mSubInfoList[position];
            // SPRD: add option to enable/disable sim card
            String summary = "";
            if (sir == null) {
                holder.title.setText(getItem(position));
                // SPRD: add option to enable/disable sim card
                //holder.summary.setText("");
                holder.icon.setImageDrawable(getResources()
                        .getDrawable(R.drawable.ic_list_sim_always_ask));
                holder.icon.setAlpha(OPACITY);
                /* SPRD: modify by add radioButton on set defult sub id @{ */
                holder.defaultSubscription.setChecked(false);
                switch (mDialogId) {
                    case DATA_PICK:
                        break;
                    case CALLS_PICK:
                        holder.defaultSubscription.setChecked(0 == position
                                &&  TelephonyManager.getDefaultSim(mContext,
                                        TelephonyManager.MODE_VOICE) == TelephonyManager.PHONE_ID_INVALID);
                        break;
                    case SMS_PICK:
                        holder.defaultSubscription.setChecked(0 == position
                                && TelephonyManager.getDefaultSim(mContext,
                                        TelephonyManager.MODE_MMS) == TelephonyManager.PHONE_ID_INVALID);
                        break;
                    case PRIMARY_PICK:
                        break;
                    /* @} */
                    default:
                        throw new IllegalArgumentException("Invalid dialog type "
                        + mDialogId + " in SIM dialog.");
                        }
                        /* @} */

            } else {
                holder.title.setText(sir.getName());
                /* SPRD: add option to enable/disable sim card @{ */
                //holder.summary.setText(sir.getNumber());
                summary = sir.getNumber();
                /* @} */
                holder.icon.setImageBitmap(createIconBitmap(mContext,sir.getPhoneId()));
                 /* SPRD: modify by add radioButton on set defult sub id @{ */
                switch (mDialogId) {
                    case DATA_PICK:
                        holder.defaultSubscription.setChecked(
                                TelephonyManager.getDefaultDataPhoneId(mContext) == sir.getPhoneId());
                        break;
                    case CALLS_PICK:
                        holder.defaultSubscription.setChecked(
                                TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_VOICE) == sir.getPhoneId());
                        break;
                    case SMS_PICK:
                        holder.defaultSubscription.setChecked(
                                TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_MMS) == sir.getPhoneId());
                        break;
                    case PRIMARY_PICK:
                        holder.defaultSubscription.setChecked(
                                mTelephonyManager[0].getPrimaryCard() == sir.getPhoneId());
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid dialog type " + mDialogId + " in SIM dialog.");
                }
            }
            /* SPRD: add option to enable/disable sim card @{ */
            /* SPRD: modify for Bug625823 */
            String operatorName = SystemProperties.get("ro.operator", "");
            if ("reliance".equals(operatorName)) {
                holder.defaultSubscription.setVisibility(View.VISIBLE);
            /* @} */
            } else {
                holder.defaultSubscription
                        .setVisibility(mIsPrimaryCardCancelable ? View.GONE : View.VISIBLE);
            }

            /* SPRD: modify for bug494887 @{ */
            if (mDialogId == DATA_PICK) {
                holder.defaultSubscription.setEnabled(isEnabled(position));
            }
            /* @} */

            holder.summary.setText(summary);
            if (TextUtils.isEmpty(summary)) {
                holder.summary.setVisibility(View.GONE);
            } else {
                holder.summary.setVisibility(View.VISIBLE);
            }
            /* @} */

            final boolean isSubIdChecked = holder.defaultSubscription.isChecked();
            holder.defaultSubscription.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Log.d(TAG, "onCheckedChanged isSubIdChecked = " + isSubIdChecked);
                    if (!isSubIdChecked) {
                        setDefaltSubIdByDialogId(mContext, mDialogId, position, mSubInfoList);
                    } else {
                        finish();
                    }
                }
            });
            /* @} */
            return rowView;
        }

        /* SPRD: modify for bug494887 @{ */
        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            if (mDialogId == DATA_PICK) {
                final Sim sir = mSubInfoList[position];
                boolean isSimReady = mTelephonyManager[sir.getPhoneId()]
                        .getSimState() == TelephonyManager.SIM_STATE_READY;
                boolean isSimStandby = isStandby(sir.getPhoneId());
                if (!isSimStandby || !isSimReady) {
                    return false;
                }
            }
            return true;
        }
        /* @} */

        private class ViewHolder {
            TextView title;
            TextView summary;
            ImageView icon;
            // SPRD: modify by add radioButton on set defult sub id
            RadioButton defaultSubscription;
        }
        private Bitmap createIconBitmap(Context context,int phoneId) {
            // TODO Auto-generated method stub
            Bitmap iconBitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_list_sim);
            int width = iconBitmap.getWidth();
            int height = iconBitmap.getHeight();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            // Create a new bitmap of the same size because it will be modified.
            Bitmap workingBitmap = Bitmap.createBitmap(metrics, width, height, iconBitmap.getConfig());

            Canvas canvas = new Canvas(workingBitmap);
            Paint paint = new Paint();

            // Tint the icon with the color.
            //paint.setColorFilter(new PorterDuffColorFilter(mSimManager.getColor(phoneId), PorterDuff.Mode.SRC_ATOP));
            canvas.drawBitmap(iconBitmap, 0, 0, null);
            paint.setColorFilter(null);

            // Write the sim slot index.
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setColor(Color.WHITE);
            // Set text size scaled by density
            paint.setTextSize(TEXT_SIZE * metrics.density);
            // Convert sim slot index to localized string
            final String index = String.format("%d", phoneId + 1);
            final Rect textBound = new Rect();
            paint.getTextBounds(index, 0, 1, textBound);
            final float xOffset = (width / 2.f) - textBound.centerX();
            final float yOffset = (height / 2.f) - textBound.centerY();
            canvas.drawText(index, xOffset, yOffset, paint);

            return workingBitmap;
        }
    }

    public static class PorgressDialogFragment extends DialogFragment {
        View v;
        TextView mMessageView;
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            v = inflater.inflate(R.layout.progress_dialog_fragment_ex, container, false);
            mMessageView = (TextView) v.findViewById(R.id.message);
            mMessageView.setText(getResources().getString(R.string.primary_card_switching));
            //setView(view);
            return v;
        }
    }

    /* SPRD: modify for bug508651 @{ */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (stateExtra != null && IccCardConstants.INTENT_VALUE_ICC_ABSENT .equals(stateExtra)) {
                    dismissSimChooseDialog();
                    finish();
                }
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                /* SPRD: [bug522030] If call incoming while primary card selection dialog is showing. Just
                   dismiss the dialog and make sure all radios powered on if allowed @{ */
                if (mIsPrimaryCardCancelable) {
                    int phoneId = intent.getIntExtra(TelephonyIntents.EXTRA_PHONE_ID, 0);
                    mTelephonyManager[0].setPrimaryCard(phoneId);
                    if (mSimChooseDialog != null) {
                        mSimChooseDialog.dismiss();
                    }
                    finish();
                }
                /* @} */
            }
        }
    };
    /* @} */
}
