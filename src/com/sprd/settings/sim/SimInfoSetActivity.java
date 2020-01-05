
package com.sprd.settings.sim;


import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.sim.Sim;
import android.sim.SimManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;

import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.WirelessSettings;

public class SimInfoSetActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    private EditTextPreference mName;

    private EditTextPreference mNumber;

    private Preference mOperator;

    private Context mContext;

    private int mPhoneId = -1;

    private static final String KEY_NAME = "name_setting";

    private static final String KEY_OPERATOR = "operator_setting";

    private static final String KEY_NUMBER = "number_setting";

    private static final int INPUT_MAX_LENGTH = 7;

    private static final int INPUT_MAX_LENGTH_NUMBER = 10;

    public void onCreate(Bundle savedInstanceState) {
        ActionBar actionBar =  getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP,ActionBar.DISPLAY_HOME_AS_UP);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        super.onCreate(savedInstanceState);

        mContext = this;
        mPhoneId = this.getIntent().getIntExtra("phoneId", 0);
        SimManager simManager = SimManager.get(mContext);
        String simName = simManager.getName(mPhoneId);
        Sim[] sims = simManager.getSims();
        if (sims == null || sims.length == 0) {
            return;
        }
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        mName = new EditTextPreference(this);
        mName.setPersistent(false);
        mName.setDialogTitle(R.string.sim_name_setting_title);
        mName.setKey(KEY_NAME + mPhoneId);
        mName.setTitle(R.string.sim_name_setting_title);
        mName.setText(simName);
        mName.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        /* SPRD: modify for the limit of Chinese or English character and cursor position @{ */
        mName.getEditText().addTextChangedListener(new TextWatcher() {
            private int editStart;
            private EditText mEditText = mName.getEditText();

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                editStart = mEditText.getSelectionStart();
                mEditText.removeTextChangedListener(this);
                while (calculateLength(s.toString()) > INPUT_MAX_LENGTH) {
                    s.delete(editStart - 1, editStart);
                    editStart--;
                }
                mEditText.setText(s);
                mEditText.setSelection(editStart);
                mEditText.addTextChangedListener(this);
                Dialog dialog = mName.getDialog();
                if (dialog instanceof AlertDialog) {
                    Button btn = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                    btn.setEnabled(s.toString().trim().length() > 0);
                }
            }

            private int calculateLength(CharSequence c) {
                double len = 0;
                for (int i = 0; i < c.length(); i++) {
                    int tmp = (int) c.charAt(i);
                    if (tmp > 0 && tmp < 127) {
                        len += 0.5;
                    } else {
                        len++;
                    }
                }
                return (int) Math.round(len);
            }
        });
        /* @} */
        mName.setOnPreferenceChangeListener(this);
        root.addPreference(mName);

        /* SPRD: modify for new feature of telephone number display in the StandBySet @{ */
        String PhoneNumber = simManager.getMsisdn(mPhoneId);
        mNumber = new EditTextPreference(this);
        mNumber.setPersistent(false);
        mNumber.setDialogTitle(R.string.sim_number_setting_title);
        mNumber.setKey(KEY_NUMBER + mPhoneId);
        mNumber.setTitle(R.string.sim_number_setting_title);
        mNumber.setText(PhoneNumber);
        mNumber.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        mNumber.getEditText().addTextChangedListener(new TextWatcher() {
            private int editStart;
            private EditText mEditText = mNumber.getEditText();

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                editStart = mEditText.getSelectionStart();
                mEditText.removeTextChangedListener(this);
                while (calculateLength(s.toString()) > INPUT_MAX_LENGTH_NUMBER) {
                    s.delete(editStart - 1, editStart);
                    editStart--;
                }
                mEditText.setText(s);
                mEditText.setSelection(editStart);
                mEditText.addTextChangedListener(this);
            }

            private int calculateLength(CharSequence c) {
                double len = 0;
                for (int i = 0; i < c.length(); i++) {
                    int tmp = (int) c.charAt(i);
                    if (tmp > 0 && tmp < 127) {
                        len += 0.5;
                    } else {
                        len++;
                    }
                }
                return (int) Math.round(len);
            }
        });
        mNumber.setOnPreferenceChangeListener(this);
        root.addPreference(mNumber);
        /* @} */

        if (Settings.CU_SUPPORT) {
            mOperator = new Preference(mContext, null, 0);
            mOperator.setKey(KEY_OPERATOR + mPhoneId);
            mOperator.setTitle(R.string.device_status);
            mOperator.setSummary(R.string.device_status_summary);
            mOperator.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent();
                    intent.setClassName("com.android.settings",
                            "com.android.settings.deviceinfo.StatusSim");
                    intent.putExtra(WirelessSettings.SUB_ID, mPhoneId);
                    SimInfoSetActivity.this.startActivity(intent);
                    return true;
                }
            });
            root.addPreference(mOperator);
        }

        setPreferenceScreen(root);

        refreshSimInfo();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        if (item.getItemId() == android.R.id.home) {
            finish();
            return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    void refreshSimInfo() {
        SimManager simManager = SimManager.get(mContext);
        String name = simManager.getName(mPhoneId);
        String number = simManager.getMsisdn(mPhoneId);
        if (number.isEmpty()){
            mNumber.setSummary(R.string.sim_number_setting_null);
        }else {
            mNumber.setSummary(number);
        }
        mName.setSummary(name);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SimManager simManager = SimManager.get(mContext);
        if (preference == mName) {
            simManager.setName(mPhoneId, (String) newValue);
            refreshSimInfo();
        } if (preference == mNumber) {
            String PhoneNumber = (String) newValue;
            if (PhoneNumber.isEmpty()) {
                PhoneNumber ="";
            }
            simManager.setMsisdn("MSISDN", PhoneNumber, mPhoneId);
            refreshSimInfo();
        }
        return true;
    }
}