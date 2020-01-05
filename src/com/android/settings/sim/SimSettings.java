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

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.DialogInterface;
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
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.sim.Sim;
import android.sim.SimManager;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.settings.DualSimSettings;
import com.android.settings.R;
import android.os.SystemProperties;
import com.sprd.android.support.featurebar.FeatureBarHelper;

import com.android.internal.telephony.TelephonyProperties;
import com.sprd.internal.telephony.TeleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class SimSettings extends RestrictedSettingsFragment {
    private static final String TAG = "SimSettings";
    private static final boolean DBG = Debug.isDebug();

    private static final String DISALLOW_CONFIG_SIM = "no_config_sim";
    private static final String SIM_CARD_CATEGORY = "sim_cards";
    private static final String KEY_CELLULAR_DATA = "sim_cellular_data";
    private static final String KEY_CALLS = "sim_calls";
    private static final String KEY_SMS = "sim_sms";
    private static final String STANDBY_DIALOG_TAG = "standby_dialog";
    private static final String DATA_DIALOG_TAG = "data_dialog";
    private static final String PROGRESS_DIALOG_TAG = "progress_dialog";
    private static final String KEY_PRIMARY_CARD = "sim_primary_card";
    // SPRD: add new feature for data switch on/off
    private static final String KEY_ACTIVITIES = "sim_activities";
    public static final String EXTRA_SLOT_ID = "slot_id";
    // SPRD: add for bug338274 of replying msg setting
    private static final String KEY_REPLY_MSG = "reply_message_setting";

    /**
     * By UX design we use only one Subscription Information(SubInfo) record per SIM slot.
     * mAvalableSubInfos is the list of SubInfos we present to the user.
     * mSubInfoArray is the list of all SubInfos.
     * mSelectableSubInfos is the list of SubInfos that a user can select for data, calls, and SMS.
     */
    //SPRD: modify for bug497338
    //private List<SubscriptionInfo> mAvailableSubInfos = null;
    private Sim[] mSubInfoArray = null;
    //SPRD: modify for bug497338
    private Sim[] mAvailableSubInfoArray = null;
    private PreferenceScreen mSimCards = null;
    // SPRD: add new feature for data switch on/off
    private DataPreference mDataPreference;
    private SimManager mSimManager;
    // SPRD: add option to enable/disable sim card
    private TelephonyManager mTelephonyManager[];
    private int mNumSlots;
    private Context mContext;

    /* SPRD: add option to enable/disable sim card @{ */
//    private boolean mNeedPromptDataChange = false;
    private PorgressDialogFragment mProgressDialogFragment = null;
    private DialogFragment mAlertDialogFragment = null;
    private FragmentManager mFragmentManager;
    /* @} */
    private static final int SIM_INFO_CHANGED = 1;
    SimInfoChanged mSimInfoChanged;
    private static final int TEXT_SIZE = 16;
    private boolean mIsVTModem;

    private static TextView mOptionView;
    private static TextView mCenterView;
    private TextView mBackView;
    private ListView mSimSettingList;
    private FeatureBarHelper mHelperBar;
    // SPRD: add for bug338274 of replying msg setting
    private CheckBoxPreference mReplyMsgPrefence;
    private ConnectivityManager mConnService;
    private Boolean mMobileDataEnabled;

    public SimSettings() {
        super(DISALLOW_CONFIG_SIM);
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        mContext = getActivity();
        getActivity().getActionBar().setHomeButtonEnabled(false);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);

        mSimManager = SimManager.get(getActivity());
        /* SPRD: add option to enable/disable sim card @{ */
        mFragmentManager = getFragmentManager();
        mNumSlots = TelephonyManager.getPhoneCount();
        mTelephonyManager = new TelephonyManager[mNumSlots];
        for (int i = 0; i < mNumSlots; i++) {
            mTelephonyManager[i] = (TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                    Context.TELEPHONY_SERVICE, i));
        }
        mConnService = ConnectivityManager.from(mContext);
        addPreferencesFromResource(R.xml.sim_settings);

        mSimCards = (PreferenceScreen)findPreference(SIM_CARD_CATEGORY);
        mAvailableSubInfoArray = getActiveSubInfoList();
        /* SPRD: add new feature for data switch on/off @{ */
        PreferenceCategory simPreferenceCatergory = (PreferenceCategory )findPreference(KEY_ACTIVITIES);
        mDataPreference = (DataPreference)new DataPreference(getActivity());
        mDataPreference.setOrder(0);
        mDataPreference.setKey(KEY_CELLULAR_DATA);
        simPreferenceCatergory.addPreference(mDataPreference);
        if (TelephonyManager.isDualLteModem()) {
            Preference mSimPrimaryCardPreference = (Preference) findPreference(KEY_PRIMARY_CARD);
            simPreferenceCatergory.removePreference(mSimPrimaryCardPreference);
        }
        for (int i = 0; i < mNumSlots; ++i) {
            final Sim sir = mSimManager
                    .getSimById(i);
            SimPreference simPreference = new SimPreference(mContext, sir, i);
            simPreference.setOrder(i-mNumSlots);
            mSimCards.addPreference(simPreference);
        }

        mIsVTModem = ((TelephonyManager) getSystemService(TelephonyManager.getServiceName(
                Context.TELEPHONY_SERVICE, 0))).getModemType() > 0;
        /* SPRD: add for bug338274 of replying msg setting @{ */
        mReplyMsgPrefence = (CheckBoxPreference) findPreference(KEY_REPLY_MSG);
        mReplyMsgPrefence.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int replyMessage = 0;
                if (newValue.toString().equals("true")) {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_ISCHECKED;
                } else {
                    replyMessage = Settings.System.REPLY_MSG_PREFENCE_UNCHECKED;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.MULTI_REPLY_MSG, replyMessage );
                return true;
            }
        });
        /* @} */
        /* @} */
        //SPRD: modify for bug648570
        ListView list = (ListView)(getActivity().getWindow().findViewById(com.android.internal.R.id.list));
        if(list == null){
            finish();
        }
        /* SPRD: add option remember default SMS/Voice sub id @{ */
        //SPRD: modify for bug497338
        try {
            mHelperBar = new FeatureBarHelper(getActivity());
            if (mHelperBar != null) {
                mOptionView = (TextView) mHelperBar.getOptionsKeyView();
                mCenterView = (TextView) mHelperBar.getCenterKeyView();
                mBackView = (TextView) mHelperBar.getBackKeyView();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "RuntimeException:"+e);
        }
        if (mAvailableSubInfoArray.length > 1) {
            initSimManagerSharedPreferences();
        }
        /* @} */
        // SPRD: add for bug338274 of replying msg setting
        updateReplyMsgPrefence();
    }

    public void updateReplyMsgPrefence() {
        int replyMessage = TelephonyManager.getReplyMessage(mContext);
        mReplyMsgPrefence.setChecked(replyMessage == Settings.System.REPLY_MSG_PREFENCE_ISCHECKED);
    }

    private void updateSubscriptions() {
        mSubInfoArray = mSimManager.getSims();
        for (int i = 0; i < mNumSlots; ++i) {
            Preference pref = mSimCards.findPreference("sim" + i);
            if (pref instanceof SimPreference) {
                mSimCards.removePreference(pref);
            }
        }

        for (int i = 0; i < mNumSlots; ++i) {
            final Sim sir = mSimManager
                    .getSimById(i);
            SimPreference simPreference = new SimPreference(mContext, sir, i);
            simPreference.setOrder(i-mNumSlots);
            mSimCards.addPreference(simPreference);
            //SPRD: modify for bug497338
            //mAvailableSubInfos.add(sir);
        }
        updateAllOptions();
    }

    private void updateAllOptions() {
        updateSimSlotValues();
        updateActivitesCategory();
    }

    private void updateSimSlotValues() {
        final int prefSize = mSimCards.getPreferenceCount();
        for (int i = 0; i < prefSize; ++i) {
            Preference pref = mSimCards.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference)pref).update();
            }
        }
    }

    private void updateActivitesCategory() {
        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
        if (!TelephonyManager.isDualLteModem()) {
            updatePrimaryCardValues();
        }
    }

    private void updateSmsValues() {
        final Preference simPref = findPreference(KEY_SMS);
        int dualVoiceSettingValue = TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_MMS);
        simPref.setTitle(R.string.dual_mms_setting_title);
        final Sim sir = mSimManager.getSimById(dualVoiceSettingValue);
        if (DBG) log("[updateSmsValues] mSubInfoArray=" + mSubInfoArray);

        if (sir != null) {
            simPref.setSummary(sir.getName());
        } else if (sir == null) {
            simPref.setSummary(R.string.sim_calls_ask_first_prefs_title);
        }
        /* SPRD: add option to enable/disable sim card @{ */
        //simPref.setEnabled(mSelectableSubInfos.size() >= 1);
        //SPRD: modify for bug497338
        simPref.setEnabled(mAvailableSubInfoArray.length > 1);
        /* @} */
    }

    /* SPRD: add new feature for data switch on/off @{ */
    private void updateCellularDataValues() {
        mDataPreference.update();
        //RPRD:910638 update center view when data selected.
        Object selectedItem = mSimSettingList.getSelectedItem();
        if(selectedItem instanceof DataPreference){
            mMobileDataEnabled = mConnService.getMobileDataEnabled();
            updateFeatureBarForData(mDataPreference);
        }
    }
    /* @} */

    private void updateCallValues() {
        final Preference simPref = findPreference(KEY_CALLS);
        int dualVoiceSettingValue = TelephonyManager.getDefaultSim(mContext, TelephonyManager.MODE_VOICE);
        simPref.setTitle(R.string.dual_voice_setting_title);
        final Sim sir = mSimManager.getSimById(dualVoiceSettingValue);
        if (sir != null) {
            simPref.setSummary(sir.getName().toString().trim().isEmpty()?
                    "SIM"+(sir.getPhoneId()+1):sir.getName());
        } else if (sir == null) {
            simPref.setSummary(R.string.sim_calls_ask_first_prefs_title);
        }
        /* @} */
        /* SPRD: add option to enable/disable sim card @{ */
        //simPref.setEnabled(allPhoneAccounts.size() > 1);
        //SPRD: modify for bug497338
        simPref.setEnabled(mAvailableSubInfoArray.length > 1);
        /* @} */
    }

    /* SPRD: add option of selecting primary card @{ */
    private void updatePrimaryCardValues() {
        final Preference simPref = findPreference(KEY_PRIMARY_CARD);
        final int primaryCard = mTelephonyManager[0].getPrimaryCard();
        final Sim sir = mSimManager.getSimById(primaryCard);
        simPref.setTitle(R.string.select_primary_card);
        if (DBG) log("[updatePrimaryCardValues] mSubInfoArray=" + mSubInfoArray);
        if (sir != null) {
            simPref.setSummary(sir.getName().toString().trim().isEmpty()?
                    "SIM"+(sir.getPhoneId()+1):sir.getName());
        } else if (sir == null) {
            simPref.setSummary(R.string.sim_selection_required_pref);
        }
        /* SPRD: modify by bug459921@{ */
        if (mSimManager.getActiveSims().length <= 1
                || (TelephonyManager.from(mContext).isCmccPriority() && isSingleCmcc())){
            simPref.setEnabled(false);
            simPref.setShouldDisableView(true);
        } else {
            // SPRD: [Lastest:bug484116 History:bug429579] Not allowed to switch primary card if any sim card have been disabled.
            //SPRD: modify for bug497338
            simPref.setEnabled(mAvailableSubInfoArray.length > 1);
        }
    }
    /* @} */

    // SPRD：Modified for bug 667242
    private boolean isSingleCmcc() {
        int cmccCard = 0;
        for (int i = 0; i < mNumSlots; i++) {
            String numeric = SystemProperties
                    .get(TelephonyManager.getProperty(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC, i));
            if (!TextUtils.isEmpty(numeric)) {
                String mcc = numeric.substring(0, 3);
                String mnc = numeric.substring(3);
                String tmpMccMnc = mcc + Integer.parseInt(mnc);
                String operatorName = TeleUtils.updateOperator(tmpMccMnc, "numeric_to_operator");
                if ("China Mobile".equals(operatorName)) {
                    cmccCard++;
                }
            }
        }
        return cmccCard == 1 ? true : false;
    }

    @Override
    public void onResume() {
        super.onResume();
        /* SPRD:  modify for bug 493220 @{ */
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver, UserHandle.USER_OWNER);   // SPRD:  modify for bug 508104
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.MOBILE_DATA), true,
                mMobileDataObserver,UserHandle.USER_OWNER);   // SPRD:  modify for bug 508104
        /*}@*/

        // SPRD: add option to enable/disable sim card
//        mNeedPromptDataChange = false;
        updatePreferencesState();
        mSimInfoChanged = new SimInfoChanged();
        mSimManager.addOnSimsUpdatedListener(mSimInfoChanged, null, true);
        final TelephonyManager tm =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        //SPRD: modify for bug497338
        //updateSubscriptions();
        mSimSettingList = this.getListView();
        if (mSimSettingList != null) {
            mSimSettingList.setOnKeyListener(mSimlistListener);
            mSimSettingList.setOnItemSelectedListener(mItemSelectedListener);
        }
    }

    OnItemSelectedListener mItemSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            Object selectedItem = mSimSettingList.getSelectedItem();
            if (selectedItem != null) {
                if (selectedItem instanceof DataPreference) {
                    updateFeatureBarForData((DataPreference) selectedItem);
                } else if (selectedItem instanceof SimPreference) {
                    int slotId = ((SimPreference) selectedItem).getSlotId();
                    Switch simEnable = ((SimPreference) selectedItem).mSwitch;
                    if (simEnable == null) return;
                    mOptionView.setVisibility(((SimPreference) selectedItem).isEnabled()?View.VISIBLE:View.GONE);
                    mCenterView.setVisibility(simEnable.isEnabled() ? View.VISIBLE:View.GONE);
                    mOptionView.setText(mContext.getResources().getString(R.string.feature_bar_softkey_edit));
                    mCenterView.setText(isStandby(slotId)?mContext.getResources().getString(R.string.feature_bar_softkey_off):
                        mContext.getResources().getString(R.string.feature_bar_softkey_on));
                } else {
                    mOptionView.setVisibility(View.GONE);
                    mCenterView.setVisibility(((Preference) selectedItem).isEnabled()?View.VISIBLE:View.GONE);
                    mCenterView.setText(mContext.getResources().getString(R.string.feature_bar_softkey_options));
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    OnKeyListener mSimlistListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            Preference selectedItem = (Preference) mSimSettingList.getSelectedItem();
                if (KeyEvent.ACTION_UP == event.getAction()) {
                    if (selectedItem != null) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_MENU:
                        if (selectedItem.isEnabled()) {
                            if (selectedItem instanceof DataPreference) {
                                Intent intent = new Intent(mContext,SimDialogActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY,SimDialogActivity.DATA_PICK);
                                mContext.startActivity(intent);
                                return true;
                            } else if (selectedItem instanceof SimPreference) {
                                SimFragmentDialog.show(SimSettings.this,((SimPreference) selectedItem).getSlotId());
                                return true;
                            }
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        if (selectedItem instanceof DataPreference) {
                            Switch dataSwitch = ((DataPreference) selectedItem).mDataSwitch;
                            if (dataSwitch != null && dataSwitch.isEnabled()) {
                                int dataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
                                boolean isDataEnable = isMobileDataEnabled(dataPhoneId);
                                setMobileDataEnabled(!isDataEnable);
                                ((DataPreference) selectedItem).updateDataSwitch(dataPhoneId);
                                updateFeatureBarForData((DataPreference) selectedItem);
                                return true;
                            }
                        } else if (selectedItem instanceof SimPreference) {
                            Switch simSwitch = ((SimPreference) selectedItem).mSwitch;
                            if (simSwitch != null && simSwitch.isEnabled()) {
                                int slotId = ((SimPreference) selectedItem).getSlotId();
                                Log.d(TAG, "[KEYCODE_DPAD_CENTER] slotId ="+ slotId);
                                boolean isSimStandBy = isStandby(slotId);
                                showStandbyAlertDialog(slotId,!isSimStandBy);
                            }
                            return true;
                        }
                        break;
                    }
                }
            }
            return false;
        }
    };

    private void updateFeatureBarForData(DataPreference dataPreference){
        Switch dataSwitch = dataPreference.mDataSwitch;
        if (dataSwitch == null) return;
        if(mSimManager.getActiveSims().length > 1){
            mOptionView.setVisibility(View.VISIBLE);
        }else{
            mOptionView.setVisibility(View.GONE);
        }
        mCenterView.setVisibility(dataSwitch.isEnabled()?View.VISIBLE:View.GONE);
        mOptionView.setText(mContext.getResources().getString(R.string.feature_bar_softkey_options));
        mCenterView.setText(isMobileDataEnabled() ?
                mContext.getResources().getString(R.string.feature_bar_softkey_off):
            mContext.getResources().getString(R.string.feature_bar_softkey_on));
    }

    class SimInfoChanged implements SimManager.OnSimsUpdateListener{

        @Override
        public void onSimUpdated(Sim[] sims) {
            mAvailableSubInfoArray = getActiveSubInfoList();
            updateSubscriptions();
        }
    }

    /* SPRD: add option to enable/disable sim card @{ */
    private void showStandbyAlertDialog(final int phoneId, final boolean onOff) {
        StandbyAlertDialogFragment.show(SimSettings.this, phoneId, onOff);
    }

    private synchronized void showProgressDialog() {
        if(mProgressDialogFragment != null){
            return;
        }
        Log.d(TAG, "show progressing dialog...");
        //FragmentManager fm = getFragmentManager();
        if (getActivity() != null && getActivity().isResumed()) {
            FragmentTransaction transaction = mFragmentManager.beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            mProgressDialogFragment = new PorgressDialogFragment();
            mProgressDialogFragment.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
            mProgressDialogFragment.setCancelable(false);
            /* SPRD: modify for bug493042 @{ */
            mProgressDialogFragment.setTargetFragment(this, 0);
            /* @} */
            mProgressDialogFragment.show(
                    transaction, PROGRESS_DIALOG_TAG);
        }
    }

    /* SPRD: modify for bug493042 @{ */
    private void resetProgressDialogFragment(PorgressDialogFragment dialogFragment) {
        mProgressDialogFragment = dialogFragment;
    }
    /* @} */
    /* SPRD: modify for bug492873 @{ */
    private void resetAlertDialogFragment(DialogFragment dialogFragment) {
        mAlertDialogFragment = dialogFragment;
    }
    /* @} */

//    private void showDataAlertDialog(String msg) {
//        DataAlertDialogFragment.show(SimSettings.this, msg);
//    }
    /* @} */

    @Override
    public void onPause() {
        super.onPause();
        /* SPRD:  modify for bug 493220 @{ */
        getContentResolver().unregisterContentObserver(mRadioBusyObserver);
        // SPRD: add new feature for data switch on/off
        getContentResolver().unregisterContentObserver(mMobileDataObserver);
        /*}@*/
        // SPRD: modify for bug 648572
        if (mSimInfoChanged != null) {
            mSimManager.removeOnSimsUpdatedListener(mSimInfoChanged);
            mSimInfoChanged = null;
        }
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        // Disable Sim selection for Data when voice call is going on as changing the default data
        // sim causes a modem reset currently and call gets disconnected
        // ToDo : Add subtext on disabled preference to let user know that default data sim cannot
        // be changed while call is going on
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DBG) log("PhoneStateListener.onCallStateChanged: state=" + state);
             final Preference pref = findPreference(KEY_CELLULAR_DATA);
            if (pref != null) {
                final boolean ecbMode = SystemProperties.getBoolean(
                        TelephonyProperties.PROPERTY_INECM_MODE, false);
                /* SPRD: Modify the Bug 492127 @{ */
                if (getActiveSubInfoList().length <= 0) {
                    pref.setEnabled(false);
                } else {
                    //SPRD: modify for bug497338
                    pref.setEnabled((state == TelephonyManager.CALL_STATE_IDLE) && !ecbMode && getActiveSubInfoList().length > 0);
                }
                /* @} */
            }
        }
    };

    @Override
    public void onDestroy() {// SPRD: add option to enable/disable sim card
        super.onDestroy();
        //updatePreferencesState();
    };

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen,
            final Preference preference) {
        final Context context = mContext;
        Intent intent = new Intent(context, SimDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (preference instanceof SimPreference) {
            // SPRD: modify for bug500268
            SimFragmentDialog.show(SimSettings.this, ((SimPreference) preference).getSlotId());
        } else if (preference instanceof DataPreference) {
            // SPRD: add new feature for data switch on/off
            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.DATA_PICK);
            context.startActivity(intent);
        } else if (findPreference(KEY_CALLS) == preference) {
            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.CALLS_PICK);
            context.startActivity(intent);
        } else if (findPreference(KEY_SMS) == preference) {
            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.SMS_PICK);
            context.startActivity(intent);
        }
        /* SPRD: add option of selecting primary card @{ */
        else if (findPreference(KEY_PRIMARY_CARD) == preference) {
            intent.putExtra(SimDialogActivity.DIALOG_TYPE_KEY, SimDialogActivity.PRIMARY_PICK);
            context.startActivity(intent);
        }
        /* @} */

        return true;
    }

    /* SPRD: add option to enable/disable sim card @{ */
    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (getActivity() != null) {
                mAvailableSubInfoArray = getActiveSubInfoList();
                updateSubscriptions();
                updatePreferencesState();
//                if (!TelephonyManager.isRadioBusy(mContext)  && mNeedPromptDataChange) {
//                    mNeedPromptDataChange = false;
//                    /* SPRD: modify for bug497338 @{ */
//                    if (mAvailableSubInfoArray.length == 1) {
//                        int availablePhoneId = mAvailableSubInfoArray[0].getPhoneId();
//                    /*}@*/
//                        String msg = getString(R.string.toggle_data_change_message,
//                                availablePhoneId + 1);
//                        showDataAlertDialog(msg);
//                    }
//                }
            }
        }
    };

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void updatePreferencesState() {
        if (!TelephonyManager.isRadioBusy(mContext) ) {
            if (mProgressDialogFragment != null) {
                mProgressDialogFragment.dismissAllowingStateLoss(); // SPRD: modify for bug500791
                mProgressDialogFragment = null;
            }
        }

        if (isAirplaneModeOn()) {
            if(mAlertDialogFragment != null) {
                //getFragmentManager().get
                mAlertDialogFragment.dismissAllowingStateLoss(); // SPRD: modify for bug500791
                mAlertDialogFragment = null;
            }
            if (mProgressDialogFragment != null) {
                mProgressDialogFragment.dismissAllowingStateLoss(); // SPRD: modify for bug500791
                mProgressDialogFragment = null;
            }
        }
        getPreferenceScreen().setEnabled(
                !TelephonyManager.isRadioBusy(mContext)  && !isAirplaneModeOn());
    }

    /* SPRD: set current default voice/sms phone id */
    /* as multi sim active default voice/sms phone id  @{*/
    private void initSimManagerSharedPreferences() {
        long smsPhoneId = TelephonyManager.getSubscriberDesiredSim(mContext, TelephonyManager.MODE_MMS);
        long voicePhoneId = TelephonyManager.getSubscriberDesiredSim(mContext, TelephonyManager.MODE_VOICE);
        Log.d(TAG, "initSimManagerSharedPreferences, smsPhoneId: " + smsPhoneId + ",  voicePhoneId: "
                + voicePhoneId);
        if (smsPhoneId == TelephonyManager.PHONE_ID_INVALID) {
            TelephonyManager.setSubscriberDesiredSim(mContext, TelephonyManager.MODE_MMS, TelephonyManager
                    .getDefaultSim(mContext,TelephonyManager.MODE_MMS));
        }
        if (voicePhoneId == TelephonyManager.PHONE_ID_INVALID) {
            TelephonyManager.setSubscriberDesiredSim(mContext, TelephonyManager.MODE_VOICE, TelephonyManager
                    .getDefaultSim(mContext,TelephonyManager.MODE_VOICE));
        }
    }
    /* @} */

    private class SimPreference extends Preference {
        private Sim mSubInfoRecord;
        private int mSlotId;
        Context mContext;
        // SPRD: add option to enable/disable sim card
        private Switch mSwitch;

        public SimPreference(Context context, Sim subInfoRecord, int slotId) {
            super(context);
            // SPRD: use custom layout: add Switch to enable/disable sim
            setLayoutResource(R.layout.sim_preference_ex);

            mContext = context;
            mSubInfoRecord = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            update();
        }

        /* SPRD: add option to enable/disable sim card @{ */
        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mSwitch = (Switch) view.findViewById(R.id.universal_switch);
            mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    boolean standby = isStandby(mSlotId);
                    if (standby != isChecked) {
                        showStandbyAlertDialog(mSlotId, isChecked);
                    }
                }
            });
            updateStandbyState();
        }

        private void updateStandbyState() {
            if (mSwitch != null) {
                if (mSubInfoRecord != null) {
                    boolean standby = isStandby(mSlotId);
                    mSwitch.setChecked(standby);
                    boolean canSetSimStandby = (mTelephonyManager[mSlotId].getSimState() 
                            == TelephonyManager.SIM_STATE_READY || !standby)
                            && !TelephonyManager.isRadioBusy(mContext)
                            && !isAirplaneModeOn();
                    mSwitch.setEnabled(canSetSimStandby);
                    mSwitch.setVisibility(View.VISIBLE);
                } else {
                    mSwitch.setVisibility(View.GONE);
                }
            }
        }
        /* @} */


        public void update() {
            //SPRD: modify for Bug494140
            if (!isAdded()) {
                return;
            }

            final Resources res = mContext.getResources();

            setTitle(String.format(mContext.getResources()
                    .getString(R.string.sim_editor_title), (mSlotId + 1)));
            if (mSubInfoRecord != null) {// SPRD: add option to enable/disable sim card
                if (!isStandby(mSlotId)) {
                    setSummary(R.string.not_stand_by);
                    setFragment(null);
                    setEnabled(false);
                } else {
                    /* SPRD: for bug628333 and 650528 @{ */
                    /* SPRD: remove and add to show primary card for bug541380 @{ */
                    String displayName = String.valueOf(mSubInfoRecord.getName().toString()
                            .isEmpty() ?
                            "SIM" + (mSubInfoRecord.getPhoneId() + 1) : mSubInfoRecord.getName());
                    /* @} */
                    /* @} */
                    String phoneNumber = TextUtils.isEmpty(
                            mTelephonyManager[mSubInfoRecord.getPhoneId()].getLine1Number()) ? " "
                            : " - " + mTelephonyManager[mSubInfoRecord.getPhoneId()]
                            .getLine1Number();
                    if (mSlotId == mTelephonyManager[0].getPrimaryCard()) {
                        setSummary(getString(R.string.main_card_slot) + " " + displayName
                                + phoneNumber);
                    } else {
                        setSummary(getString(R.string.gsm_card_slot) + " " + displayName
                                + phoneNumber);
                    }
                    /* @} */
                    setEnabled(!TelephonyManager.isRadioBusy(mContext)
                            && !isAirplaneModeOn()
                            // SPRD: modify the bug494142
                            && mTelephonyManager[mSubInfoRecord.getPhoneId()].getSimState()
                            == TelephonyManager.SIM_STATE_READY);
                }
                //setIcon(new BitmapDrawable(res, (createIconBitmap(mContext))));
            } else {
                setSummary(R.string.sim_slot_empty);
                setFragment(null);
                setEnabled(false);
            }
            // SPRD: add option to enable/disable sim card
            updateStandbyState();
        }

        private Bitmap createIconBitmap(Context context) {
            // TODO Auto-generated method stub
            Bitmap iconBitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_sim_card_multi_24px_clr);
            int width = iconBitmap.getWidth();
            int height = iconBitmap.getHeight();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            // Create a new bitmap of the same size because it will be modified.
            Bitmap workingBitmap = Bitmap.createBitmap(metrics, width, height, iconBitmap.getConfig());

            Canvas canvas = new Canvas(workingBitmap);
            Paint paint = new Paint();

            // Tint the icon with the color.
            paint.setColorFilter(new PorterDuffColorFilter(mSimManager.getColor(mSlotId), PorterDuff.Mode.SRC_ATOP));

            canvas.drawBitmap(iconBitmap, 0, 0, paint);
            paint.setColorFilter(null);

            // Write the sim slot index.
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setColor(Color.WHITE);
            // Set text size scaled by density
            paint.setTextSize(TEXT_SIZE * metrics.density);
            // Convert sim slot index to localized string
            final String index = String.format("%d", mSlotId + 1);
            final Rect textBound = new Rect();
            paint.getTextBounds(index, 0, 1, textBound);
            final float xOffset = (width / 2.f) - textBound.centerX();
            final float yOffset = (height / 2.f) - textBound.centerY();
            canvas.drawText(index, xOffset, yOffset, paint);

            return workingBitmap;
        }

        private int getSlotId() {
            return mSlotId;
        }
    }

    /* SPRD: add new feature for data switch on/off @{ */
    public class DataPreference extends Preference {
        Context mContext;
        Switch mDataSwitch;
        TextView mTitle;
        TextView mSummary;
        public DataPreference(Context context) {
            super(context);
            mContext = context;
            setLayoutResource(R.layout.sim_preference_ex);
            update();
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mDataSwitch = (Switch) view.findViewById(R.id.universal_switch);
            /* SPRD modify for bug608361 {*/
            mTitle = (TextView) view.findViewById(android.R.id.title);
            mSummary = (TextView) view.findViewById(android.R.id.summary);
/*            if (mAvailableSubInfoArray.length > 0){
                mTitle.setTextColor(getResources().getColor(R.color.primary_text_color));
                mSummary.setTextColor(getResources().getColor(R.color.secondary_text_color));
            }*/
            /*}*/
            mDataSwitch.setVisibility(View.VISIBLE);
            final int dataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
//            mDataSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//                @Override
//                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                    boolean isDataEnable = getDataEnabled();
//                    if (isDataEnable != isChecked) {
//                        setDataEnabled(isChecked);
//                    }
//                }
//            });
            updateDataSwitch(dataPhoneId);
        }

        public void updateDataSwitch(int phoneId) {
            Log.d(TAG,"mDataSwitch updateDataSwitch phoneId" + phoneId);
            if (mDataSwitch != null) {
                boolean isDataEnable = isMobileDataEnabled(phoneId);
                mDataSwitch.setChecked(isDataEnable);
                boolean canSetDataEnable = (mTelephonyManager[phoneId].getSimState() == TelephonyManager.SIM_STATE_READY)
                        && !TelephonyManager.isRadioBusy(mContext)
                        && !isAirplaneModeOn()
                        && isStandby(phoneId);
                mDataSwitch.setEnabled(canSetDataEnable);
            }
        }

        public void update() {
            int phoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
            final Sim sir = mSimManager.getSimById(phoneId);
            setTitle(R.string.cellular_data_title);
            if (DBG) log("[update DataPreference] mSubInfoArray=" + mSubInfoArray);

            if (sir != null) {
                /* SPRD: for  bug650528 and 628333 @{ */
                setSummary(sir.getName().toString().trim().isEmpty() ?
                        "SIM" + (sir.getPhoneId() + 1) : sir.getName());
                /* @} */
                /* @} */
                updateDataSwitch(sir.getPhoneId());
            } else if (sir == null) {
                setSummary(R.string.sim_selection_required_pref);
            }
            if (mSimManager.getActiveSims().length <= 0) {
                setEnabled(false);
            } else {
                //SPRD: modify for bug497338
                setEnabled(mAvailableSubInfoArray.length > 0);
            }
        }

    }

    private boolean isMobileDataEnabled() {
        if (mMobileDataEnabled != null) {
            // TODO: deprecate and remove this once enabled flag is on policy
            return mMobileDataEnabled;
        } else {
            return mConnService.getMobileDataEnabled();
        }
    }

    private void setMobileDataEnabled(boolean enabled) {
        Log.d(TAG, "setMobileDataEnabled()");
        mConnService.setMobileDataEnabled(enabled);
        mMobileDataEnabled = enabled;
    }

    private boolean isMobileDataEnabled(int phoneId) {
        Log.d(TAG, "isMobileDataEnabled()--isMultiSim:"+TelephonyManager.isMultiSim());
        boolean isDataEnable;
        if(TelephonyManager.isMultiSim()){
            int dataPhoneId = TelephonyManager.getDefaultDataPhoneId(mContext);
            isDataEnable = (mConnService.getMobileDataEnabledByPhoneId(dataPhoneId) && phoneId == dataPhoneId);
        } else {
            isDataEnable = mConnService.getMobileDataEnabled();
        }
        return isDataEnable;
    }

    private ContentObserver mMobileDataObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateCellularDataValues();
        }

    };
    /* @} */

    private void log(String s) {
        Log.d(TAG, s);
    }

    /* SPRD: add option to enable/disable sim card @{ */
    private Sim[] getActiveSubInfoList() {
        /* SPRD: modify for avoid null point exception @{ */
        if (mSimManager == null) {
            return new Sim[0];
        }
        /* @} */
        int activeCount = 0;
        Sim[] availableSubInfoList = mSimManager
                .getActiveSims();
        if (availableSubInfoList == null) {
            return new Sim[0];
        }
        Sim[] sims = new Sim[availableSubInfoList.length];
        for(int i = 0;i < availableSubInfoList.length;i++){
            Sim subInfo = availableSubInfoList[i];
            int phoneId = subInfo.getPhoneId();
            boolean isSimReady = mTelephonyManager[phoneId].getSimState() == TelephonyManager.SIM_STATE_READY;
            if (isSimReady) {
                sims[activeCount++] = subInfo;
            }
        }
        Sim[] retSims = new Sim[activeCount];
        for (int i = 0; i < activeCount; i++) {
            retSims[i] = sims[i];
        }
        return retSims;
    }

    public static class PorgressDialogFragment extends DialogFragment {
        View v;
        TextView mMessageView;
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            // TODO Auto-generated method stub
            //return super.onCreateView(inflater, container, savedInstanceState);
            v = inflater.inflate(R.layout.progress_dialog_fragment_ex, container, false);
            ProgressBar mProgress = (ProgressBar) v.findViewById(com.android.internal.R.id.progress);
            mMessageView = (TextView) v.findViewById(R.id.message);
            mMessageView.setText(getResources().getString(R.string.primary_card_switching));
            //setView(view);
            mProgress.setVisibility(View.GONE);
            return v;
        }

        /* SPRD: modify for bug493042 @{ */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (getTargetFragment() != null) {
                ((SimSettings) getTargetFragment()).resetProgressDialogFragment(this);
            }
            return super.onCreateDialog(savedInstanceState);
        }
        /* @} */

    }

    public static class StandbyAlertDialogFragment extends DialogFragment {
        private static final String SAVE_PHONE_ID = "phoneId";
        private static final String SAVE_ON_OFF = "onOff";
        private int mPhoneId;
        private boolean mOnOff;
        private static StandbyAlertDialogFragment mDialog;

        public static synchronized void show(SimSettings parent, int phoneId, boolean onOff) {
            if (!parent.isAdded()) return;
            if(mDialog != null){
                return;
            }
            mDialog = new StandbyAlertDialogFragment();
            mDialog.mPhoneId = phoneId;
            mDialog.mOnOff = onOff;
            mDialog.setTargetFragment(parent, 0);
            mDialog.show(parent.getFragmentManager(), STANDBY_DIALOG_TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                mPhoneId = savedInstanceState.getInt(SAVE_PHONE_ID);
                mOnOff = savedInstanceState.getBoolean(SAVE_ON_OFF);
            }
            final SimSettings sft = (SimSettings) getTargetFragment();
            /* SPRD: modify for bug492873 @{ */
            if (sft == null) {
                Log.d(TAG, "StandbyAlertDialogFragment getTargetFragment failure!!!");
                return super.onCreateDialog(savedInstanceState);
            }
            sft.resetAlertDialogFragment(this);
            /* @} */
            final TelephonyManager telephonyManager = TelephonyManager.from(getActivity());
            final SimManager simManager = SimManager.get(getActivity());
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.proxy_error);
            builder.setMessage(R.string.stand_by_set_changed_prompt);
            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    telephonyManager.setSimStandby(mPhoneId, mOnOff);
//                    if ((!mOnOff && mPhoneId == TelephonyManager.getDefaultDataPhoneId(getActivity())
//                            || mOnOff && !telephonyManager
//                            .isSimStandby(TelephonyManager.getDefaultDataPhoneId(getActivity())))
//                            && sft.getDataEnabled()) {
//                        sft.mNeedPromptDataChange = true;
//                    } else {
//                        sft.mNeedPromptDataChange = false;
//                    }
                    sft.showProgressDialog();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            mDialog = null;
            /* SPRD: modify for bug492873 @{ */
            if (getTargetFragment() != null) {
                ((SimSettings) getTargetFragment()).updateSimSlotValues();
            }
            /* @} */
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(SAVE_PHONE_ID, mPhoneId);
            outState.putBoolean(SAVE_ON_OFF, mOnOff);
        }
    }

    private boolean isStandby(int phoneId) {
        String tmpStr = Settings.System.SIM_STANDBY + phoneId;
        return Settings.System.getInt(this.getContentResolver(), tmpStr, 1) == 1;
    }
//    public static class DataAlertDialogFragment extends DialogFragment {
//        private static final String SAVE_MSG = "msg";
//        private String mMsg;
//
//        public static void show(SimSettings parent, String msg) {
//            if (!parent.isAdded()) return;
//            //FragmentTransaction transaction = getFragmentManager().beginTransaction();
//            DataAlertDialogFragment dialog = new DataAlertDialogFragment();
//            dialog.mMsg = msg;
//            dialog.setTargetFragment(parent, 0);
//            dialog.showAllowingStateLoss(parent.getFragmentManager(), DATA_DIALOG_TAG);//SPRD: modify for bug544907
//        }
//
//        @Override
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            if (savedInstanceState != null) {
//                mMsg = savedInstanceState.getCharSequence(SAVE_MSG).toString();
//            }
//            /* SPRD: modify for bug492873 @{ */
//            if (getTargetFragment() != null) {
//                ((SimSettings) getTargetFragment()).resetAlertDialogFragment(this);
//                ((SimSettings) getTargetFragment()).mNeedPromptDataChange = false;
//            }
//            /* @} */
//            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//            builder.setTitle(R.string.proxy_error);
//            builder.setMessage(mMsg);
//            builder.setNegativeButton(R.string.okay, null);
//            return builder.create();
//        }
//
//        @Override
//        public void onSaveInstanceState(Bundle outState) {
//            super.onSaveInstanceState(outState);
//            outState.putCharSequence(SAVE_MSG, (CharSequence)mMsg);
//        }
//    }
//    /* @} */

}
