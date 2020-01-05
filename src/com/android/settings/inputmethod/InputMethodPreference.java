/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.inputmethod;

import com.android.internal.inputmethod.InputMethodUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.List;

public class InputMethodPreference extends CheckBoxPreference implements OnPreferenceClickListener,
    OnPreferenceChangeListener{
    private static final String TAG = InputMethodPreference.class.getSimpleName();
    interface OnSavePreferenceListener {
        /**
         * Called when this preference needs to be saved its state.
         *
         * Note that this preference is non-persistent and needs explicitly to be saved its state.
         * Because changing one IME state may change other IMEs' state, this is a place to update
         * other IMEs' state as well.
         *
         * @param pref This preference.
         */
        public void onSaveInputMethodPreference(InputMethodPreference pref);
    }

    private final OnSavePreferenceListener mOnSaveListener;
    private final SettingsPreferenceFragment mFragment;
    private final InputMethodInfo mImi;
    private final InputMethodManager mImm;
    private final boolean mIsValidSystemNonAuxAsciiCapableIme;
    private final boolean mIsSystemIme;
    private final Collator mCollator;

    private AlertDialog mDialog = null;
    private ImageView mInputMethodSettingsButton;
    private TextView mTitleText;
    private TextView mSummaryText;
    private View mInputMethodPref;
    private OnPreferenceChangeListener mOnImePreferenceChangeListener;
    private final InputMethodSettingValuesWrapper mInputMethodSettingValues;

    public InputMethodPreference(SettingsPreferenceFragment fragment,
            InputMethodManager imm, InputMethodInfo imi, final boolean isImeEnabler, final OnSavePreferenceListener onSaveListener) {
        super(fragment.getActivity());
        mOnSaveListener = onSaveListener;
        setPersistent(false);
        if(!isImeEnabler){
         // Hide switch widget.
            setWidgetLayoutResource(0/* widgetLayoutResId */);
        }
        mFragment = fragment;
        mImm = imm;
        mImi = imi;
        mIsSystemIme = InputMethodUtils.isSystemIme(imi);
        setKey(imi.getId());
        if(fragment.getActivity() != null){
            setTitle(imi.loadLabel(fragment.getActivity().getPackageManager()));
        }
        final String settingsActivity = imi.getSettingsActivity();
        if (TextUtils.isEmpty(settingsActivity)) {
            setIntent(null);
        } else {
            // Set an intent to invoke settings activity of an input method.
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(imi.getPackageName(), settingsActivity);
            setIntent(intent);
        }
        mCollator = Collator.getInstance(fragment.getResources().getConfiguration().locale);
        final Context context = fragment.getActivity();
        mIsValidSystemNonAuxAsciiCapableIme = InputMethodSettingValuesWrapper
                .getInstance(context).isValidSystemNonAuxAsciiCapableIme(imi, context);
        mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(context);
        setOnPreferenceClickListener(this);
        setOnPreferenceChangeListener(this);
    }

    private boolean isImeEnabler() {
        // If this {@link SwitchPreference} doesn't have a widget layout, we explicitly hide the
        // checkbox widget at constructor.
        return getWidgetLayoutResource() != 0;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public InputMethodInfo getInputMethodInfo() {
        return mImi;
    }

    public void updatePreferenceViews() {
        final boolean isAlwaysChecked = mInputMethodSettingValues.isAlwaysCheckedIme(
                mImi, getContext());
        // Only when this preference has a switch and an input method should be always enabled,
        // this preference should be disabled to prevent accidentally disabling an input method.
        setEnabled(!(isAlwaysChecked && isImeEnabler()));
        setChecked(mInputMethodSettingValues.isEnabledImi(mImi));
        setSummary(getSummaryString());
    }

    public static boolean startFragment(
            Fragment fragment, String fragmentClass, int requestCode, Bundle extras) {
        if (fragment.getActivity() instanceof PreferenceActivity) {
            PreferenceActivity preferenceActivity = (PreferenceActivity)fragment.getActivity();
            preferenceActivity.startPreferencePanel(fragmentClass, extras, 0, null, fragment,
                    requestCode);
            return true;
        } else {
            Log.w(TAG, "Parent isn't PreferenceActivity, thus there's no way to launch the "
                    + "given Fragment (name: " + fragmentClass + ", requestCode: " + requestCode
                    + ")");
            return false;
        }
    }

    private String getSummaryString() {
        final StringBuilder builder = new StringBuilder();
        final List<InputMethodSubtype> subtypes = mImm.getEnabledInputMethodSubtypeList(mImi, true);
        for (InputMethodSubtype subtype : subtypes) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            final CharSequence subtypeLabel = subtype.getDisplayName(mFragment.getActivity(),
                    mImi.getPackageName(), mImi.getServiceInfo().applicationInfo);
            builder.append(subtypeLabel);
        }
        return builder.toString();
    }

    private void updateSummary() {
        final String summary = getSummaryString();
        if (TextUtils.isEmpty(summary)) {
            return;
        }
        setSummary(summary);
    }

    public void setOnImePreferenceChangeListener(OnPreferenceChangeListener listener) {
        mOnImePreferenceChangeListener = listener;
    }

    private void showSecurityWarnDialog(InputMethodInfo imi, final InputMethodPreference chkPref) {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        // SPRD: bug316212 if Activity is null, do not show dialog
        if (mFragment.getActivity() == null) {
            return;
        }
        mDialog = (new AlertDialog.Builder(mFragment.getActivity()))
                .setTitle(android.R.string.dialog_alert_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                     // The user confirmed to enable a 3rd party IME.
                      //  chkPref.setChecked(true, true);
                        setChecked(true);
                        mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
                        notifyChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                     // The user canceled to enable a 3rd party IME.
                        setChecked(false);
                        mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
                        notifyChanged();
                    }
                })
                .create();
        mDialog.setMessage(mFragment.getResources().getString(R.string.ime_security_warning,
                imi.getServiceInfo().applicationInfo.loadLabel(
                        mFragment.getActivity().getPackageManager())));
        mDialog.show();
    }

    @Override
    public int compareTo(Preference p) {
        if (!(p instanceof InputMethodPreference)) {
            return super.compareTo(p);
        }
        final InputMethodPreference imp = (InputMethodPreference) p;
        final boolean priority0 = mIsSystemIme && mIsValidSystemNonAuxAsciiCapableIme;
        final boolean priority1 = imp.mIsSystemIme && imp.mIsValidSystemNonAuxAsciiCapableIme;
        if (priority0 == priority1) {
            final CharSequence t0 = getTitle();
            final CharSequence t1 = imp.getTitle();
            if (TextUtils.isEmpty(t0)) {
                return 1;
            }
            if (TextUtils.isEmpty(t1)) {
                return -1;
            }
            return mCollator.compare(t0.toString(), t1.toString());
        }
        // Prefer always checked system IMEs
        return priority0 ? -1 : 1;
    }

    private void saveImeSettings() {
        InputMethodAndSubtypeUtil.saveInputMethodSubtypeList(
                mFragment, mFragment.getActivity().getContentResolver(), mImm.getInputMethodList(),
                mFragment.getResources().getConfiguration().keyboard
                        == Configuration.KEYBOARD_QWERTY);
    }
    /* SPRD: Add 20131231 Spreadst of bug259487, checkbox state of IMEs change wrongly @{ */
    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        super.persistBoolean(isChecked());
     }
    /* @} */

    /* SPRD: Add 20140123 Spreadst of bug271275, checkbox of 3rd-party inputmethod cannot
     * be checked even though positive button is clicked in securityWarning dialog @{ */
    protected void releaseIfNeed() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
            mDialog = null;
        }
    }
    /* @} */

    /**
     * Sets the checkbox state and optionally saves the settings.
     * @param checked whether to check the box
     * @param save whether to save IME settings
     */
    private void setChecked(boolean checked, boolean save) {
        final boolean wasChecked = isChecked();
        super.setChecked(checked);
        if (save) {
            saveImeSettings();
            if (wasChecked != checked && mOnImePreferenceChangeListener != null) {
                mOnImePreferenceChangeListener.onPreferenceChange(this, checked);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
     // Always returns false to prevent default behavior.
        // See {@link TwoStatePreference#onClick()}.
        if (!isImeEnabler()) {
            // Prevent disabling an IME because this preference is for invoking a settings activity.
            return false;
        }
        if (isChecked()) {
            setChecked(false);
            mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
        } else {
            if (mIsSystemIme) {
                setChecked(true);
                mOnSaveListener.onSaveInputMethodPreference(InputMethodPreference.this);
            } else {
                showSecurityWarnDialog(mImi, InputMethodPreference.this);
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
     // Always returns true to prevent invoking an intent without catching exceptions.
        // See {@link Preference#performClick(PreferenceScreen)}/
        if (isImeEnabler()) {
            // Prevent invoking a settings activity because this preference is for enabling and
            // disabling an input method.
            return true;
        }
        final Context context = getContext();
        try {
            final Intent intent = getIntent();
            if (intent != null) {
                // Invoke a settings activity of an input method.
                context.startActivity(intent);
            }
        } catch (final ActivityNotFoundException e) {
            Log.d(TAG, "IME's Settings Activity Not Found", e);
            final String message = context.getString(
                    R.string.failed_to_open_app_settings_toast,
                    mImi.loadLabel(context.getPackageManager()));
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
        return true;
    }
}
