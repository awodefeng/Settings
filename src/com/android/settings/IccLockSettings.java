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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.Toast;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.Button;
import android.provider.Settings;
import android.sim.SimManager;
import android.sim.Sim;
import android.view.View;
import android.widget.TextView;
import android.graphics.Color;
import com.sprd.android.support.featurebar.FeatureBarHelper;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.view.ViewGroup;
/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends PreferenceActivity
        implements EditPinPreference.OnPinEnteredListener,OnFocusChangeListener{
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = true;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";
    // SPRD: See bug #496369
    private static final String SLOT_ID = "slotId";
    // SPRD: save the cursorPosition for bug 496971
    private static final String CURSOR_POSITION = "cursorPosition";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    public static final String SUB_ID = "sub_id";

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // SPRD: save the cursorPosition for bug 496971
    private int mCursorPosition;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private Phone mPhone;
    private int mPhoneId = 0;

    private EditPinPreference mPinDialog;
    private CheckBoxPreference mPinToggle;

    private Resources mRes;
    private AlertDialog mDialog = null;

    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;
    private TelephonyManager tm;
    private int times = -1;
    private TabHost mTabHost;
    private TabWidget mTabWidget;
    private ListView mListView;

    private FeatureBarHelper mFeatureBarHelper;
    private TextView mLeftSkView;
    private TextView mCenterSkView;
    private TextView mRightSkView;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_ENABLE_ICC_PIN_COMPLETE:
                    iccLockChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_SIM_STATE_CHANGED:
                    updatePreferences();
                    break;
            }

            return;
        }
    };

    private final BroadcastReceiver mSimStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                /* SPRD: for multi-sim @{ */
                int phoneId = intent.getIntExtra(IccCardConstants.INTENT_KEY_PHONE_ID, 0);
                String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                String lockedReason = intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                if (phoneId==mPhoneId && IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(state) && IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    IccLockSettings.this.finish();
                }
                /* @} */
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
            }
        }
    };

    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }

    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "oncreate");
        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.sim_lock_settings);

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (CheckBoxPreference) findPreference(PIN_TOGGLE);
        int prevTabId = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);
            // SPRD: see bug #496369.
            prevTabId = savedInstanceState.getInt(SLOT_ID);
            // SPRD: get the cursorPosition for bug 496971
            mCursorPosition = savedInstanceState.getInt(CURSOR_POSITION);

            // Restore inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    break;

                case ICC_REENTER_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    mNewPin = savedInstanceState.getString(NEW_PINCODE);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        }

        mPinDialog.setOnPinEnteredListener(this);
        mPinDialog.getEditText().addTextChangedListener(mTextWatcher);
        //SPRD: 647146 add input limit
        mPinDialog.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);
        /* SPRD: for multi-sim @{ */
        mPhoneId = getIntent().getIntExtra(SUB_ID, 0);
        mPhone = (PhoneFactory.getPhones())[mPhoneId];
        // mPhone = PhoneFactory.getDefaultPhone();
        tm = TelephonyManager.getDefault(mPhoneId);
        if (TelephonyManager.isMultiSim()) {
            // SPRD: modify for bug322600
            //this.setTitle(getResources().getString(
                    //R.string.sim_lock_settings_ex, mPhoneId + 1));
            setContentView(R.layout.icc_lock_tabs);

            mTabHost = (TabHost) findViewById(android.R.id.tabhost);
            mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
            mListView = (ListView) findViewById(android.R.id.list);
            //SPRD:618177 Add softKey for settings start
            mListView.setOnFocusChangeListener(this);
            mListView.setOnItemSelectedListener(mItemSelectedListener);
            //SPRD:618177 Add softKey for settings end

            mTabHost.setup();
            mTabHost.setOnTabChangedListener(mTabListener);
            mTabHost.clearAllTabs();

            SimManager sm = SimManager.get(this);
            // SPRD: modify by BUG 637198
            Sim[] sims = sm.getSims();
            Log.i(TAG, "sims = "+sims+" length = "+sims.length);
            for (int i = 0; i < sims.length; ++i) {
                mTabHost.addTab(buildTabSpec(String.valueOf(i),
                        String.valueOf(sims[i] == null
                            ? getString(R.string.sim_editor_title, i + 1)
                            : sims[i].getName())));
            }
            TabWidget tabWidget = mTabHost.getTabWidget();
            for (int i = 0; i < tabWidget.getChildCount(); i++) {
                View view = tabWidget.getChildAt(i);
                TextView textView = (TextView) view.findViewById(android.R.id.title);
                textView.setTextColor(Color.parseColor("#000000"));
            }
            if(sims.length == 1){
                mPhone = (PhoneFactory.getPhones())[sims[0].getPhoneId()];
                // SPRD: modify for bug641393
                mPhoneId = sims[0].getPhoneId();
            }else{
                mPhone = (PhoneFactory.getPhones())[prevTabId];
            }
            /* SPRD: see bug #496369. {@ */
            if (prevTabId > 0) {
                //mTabWidget.setCurrentTab(prevTabId);
                mTabHost.setCurrentTab(prevTabId);
            }
            /* @} */
        }else {
            mPhone = PhoneFactory.getDefaultPhone();
        }
        /* @} */
        mRes = getResources();
        updatePreferences();

        /* SPRD: modify for bug276433 @{ */
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.RADIO_OPERATION), true,
                mRadioBusyObserver);
        /* @} */

        //SPRD:618177 Add softKey for settings start
        mFeatureBarHelper = new FeatureBarHelper(this);
	ViewGroup vg = mFeatureBarHelper.getFeatureBar();
        if (vg != null) {
            mLeftSkView = (TextView) mFeatureBarHelper.getOptionsKeyView();
            ((TextView) mLeftSkView).setText(R.string.default_feature_bar_options);
            mRightSkView = (TextView) mFeatureBarHelper.getBackKeyView();
            ((TextView) mRightSkView).setText(R.string.default_feature_bar_back);
            mCenterSkView = (TextView) mFeatureBarHelper.getCenterKeyView();
            ((TextView) mCenterSkView).setText(R.string.default_feature_bar_center);
            vg.removeView(mLeftSkView);
        }
        //SPRD:618177 Add softKey for settings end
    }

    private TextWatcher mTextWatcher = new TextWatcher(){

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if(mDialog!=null){
                Button btn = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (s == null || s.length() < MIN_PIN_LENGTH
                        || s.length() > MAX_PIN_LENGTH) {
                    btn.setEnabled(false);
                } else {
                    btn.setEnabled(true);
                }
                // SPRD: modify for bug312776
                mPin = s.toString();
            }
        }};

    private void updatePreferences() {
        mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
        /* SPRD: modify by BUG 637198 @{ */
        Log.d(TAG, "PhoneId : " + mPhoneId + "; isStandby : " + isStandby(mPhoneId));
        if (!isStandby(mPhoneId) || isAirplaneModeOn()) {
            mPinDialog.setEnabled(false);
            mPinToggle.setEnabled(false);
        } else {
            mPinDialog.setEnabled(true);
            mPinToggle.setEnabled(true);
        }
        /* @} */
    }

    /* SPRD: modify by BUG 637198 @{ */
    private boolean isStandby(int phoneId) {
        String tmpStr = Settings.System.SIM_STANDBY + phoneId;
        return Settings.System.getInt(getContentResolver(), tmpStr, 1) == 1;
    }
    /* @} */
    @Override
    protected void onResume() {
        super.onResume();

        // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
        // which will call updatePreferences().
        final IntentFilter filter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mSimStateReceiver, filter);

        if (mDialogState != OFF_MODE) {
            showPinDialog();
            // SPRD: set the cursorPosition for bug 496971
            if (mCursorPosition != 0) {
                mPinDialog.getEditText().setSelection(mCursorPosition);
                mCursorPosition = 0;
            }
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }

        /* SPRD: modify for bug281181 @{ */
        if (isAirplaneModeOn() || isRadioBusy()) {
            updatePreferences(false);
        }
        /* @} */
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mSimStateReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();

        //mPinDialog.showPinDialog();
        mDialog = (AlertDialog) (mPinDialog.showPinDialog());
        Button btn = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (mPin == null || mPin.length() < MIN_PIN_LENGTH
                || mPin.length() > MAX_PIN_LENGTH) {
            btn.setEnabled(false);
        } else {
            btn.setEnabled(true);
        }
    }

    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";

        //times = tm.getRemainTimes(TelephonyManager.UNLOCK_PIN); //SPRD: for multi-sim
        String showInputMsg = mRes.getString(R.string.input_pin_limit);
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                message = mRes.getString(R.string.sim_enter_pin) ;//+ "(" + mRes.getString(R.string.sim_retries_left) +
                //" " + times + " )";
                mPinDialog.setDialogTitle(mToState
                        ? mRes.getString(R.string.sim_enable_sim_lock)
                        : mRes.getString(R.string.sim_disable_sim_lock));
                break;
            case ICC_OLD_MODE:
                message = mRes.getString(R.string.sim_enter_old);// + "(" + mRes.getString(R.string.sim_retries_left) +
                //" " + times + " )";
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_NEW_MODE:
                message = mRes.getString(R.string.sim_enter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_REENTER_MODE:
                message = mRes.getString(R.string.sim_reenter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
        }
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        mPinDialog.setDialogMessage(message +"\n"+showInputMsg);
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.sim_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            mDialogState = ICC_LOCK_MODE;
            showPinDialog();
        } else if (preference == mPinDialog) {
            mDialogState = ICC_OLD_MODE;
            mDialog = (AlertDialog)(mPinDialog.showPinDialog());
            Button btn = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setEnabled(false);
            return false;
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
        // Disable the setting till the response is received.
        // SPRD: modify for bug300908
        mPinToggle.setEnabled(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            mPinToggle.setChecked(mToState);
            String msg = mToState ? mRes.getString(R.string.pin_enabled_success)
                    : mRes.getString(R.string.pin_disabled_success);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), Toast.LENGTH_LONG)
                    .show();
            /* SPRD: for multi-sim @{ */
            //if(tm.getRemainTimes(TelephonyManager.UNLOCK_PIN) == 0) {
               // mPhone.getIccCard().broadcastIccStateChangedIntent("LOCKED", "PUK");
               // mPinToggle.setChecked(true);
            //}
            /* @} */
        }
        // SPRD: modify for bug300908
        mPinToggle.setEnabled(true);
        resetDialogState();
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining),
                    Toast.LENGTH_LONG)
                    .show();
            /* SPRD: for multi-sim @{ */
            //if(tm.getRemainTimes(TelephonyManager.UNLOCK_PIN) == 0) {
               // mPhone.getIccCard().broadcastIccStateChangedIntent("LOCKED", "PUK");
                //mPinToggle.setChecked(true);
            //}
            /* @} */
        } else {
            Toast.makeText(this, mRes.getString(R.string.sim_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();

        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        if (DBG) Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    /* SPRD: modify for bug273705 @{ */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /* @} */

    /* SPRD: modify for bug276433 @{ */
    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (isAirplaneModeOn()) {
                updatePreferences(false);
            }
        }
    };

    private ContentObserver mRadioBusyObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if(!isAirplaneModeOn() && !isRadioBusy()){
                updatePreferences(true);
            }
        }
    };

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(this.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private boolean isRadioBusy() {
        return Settings.Secure.getInt(this.getContentResolver(),
                Settings.Secure.RADIO_OPERATION, 0) == 1;
    }

    private void updatePreferences(boolean enabled) {
        mPinToggle.setEnabled(enabled);
        mPinDialog.setEnabled(enabled);
        if (mPinDialog.isDialogOpen()) {
            mPinDialog.getDialog().dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
        getContentResolver().unregisterContentObserver(mRadioBusyObserver);
    }
    /* @} */

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            mPhoneId = Integer.parseInt(tabId);

            mPhone = PhoneFactory.getPhone(mPhoneId);

            // SPRD: add for bug 472978
            mPinDialog.dismissDialog();

            // The User has changed tab; update the body.
            updatePreferences();
        }
    };

    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    private TabSpec buildTabSpec(String tag, String title) {
        return mTabHost.newTabSpec(tag).setIndicator(title).setContent(
                mEmptyTabContent);
    }

    //SPRD:618177 Add softKey for settings start
    OnItemSelectedListener mItemSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            Object selectedItem = mListView.getSelectedItem();
            if (selectedItem != null) {
                if (selectedItem instanceof EditPinPreference) {
                    mCenterSkView.setVisibility(mPinDialog.isEnabled() ? View.VISIBLE : View.GONE);
                } else if (selectedItem instanceof CheckBoxPreference) {
                    mCenterSkView.setVisibility(mPinToggle.isEnabled() ? View.VISIBLE : View.GONE);
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // TODO Auto-generated method stub
        if (hasFocus) {
            Object selectedItem = mListView.getSelectedItem();
            if (selectedItem instanceof EditPinPreference) {
                mCenterSkView.setVisibility(mPinDialog.isEnabled() ? View.VISIBLE : View.GONE);
            } else if (selectedItem instanceof CheckBoxPreference) {
                mCenterSkView.setVisibility(mPinToggle.isEnabled() ? View.VISIBLE : View.GONE);
            }
        } else {
            mCenterSkView.setVisibility(View.VISIBLE);//SPRD:add for bug641497
        }
    }
    //SPRD:618177 Add softKey for settings end

}
